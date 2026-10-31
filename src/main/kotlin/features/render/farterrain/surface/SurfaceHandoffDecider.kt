/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, the per-column hand-off decision (pure Kotlin, unit-tested; SurfaceManager and the
 * simulation tests run this same code).
 *
 * Inputs are only what the game has loaded, what it has built and what it drew (SurfaceDrawnList), plus the game's
 * environmental fog end (rain, water, blindness: nothing past it is visible). Never the render-distance setting, the
 * server's view distance or anything derived from them. Where a radius is needed (the pre-draw band of a renderer's
 * list, the prediction path without any list) it is the one SurfaceGameArea measured from loaded chunks and drawn
 * history.
 *
 * Per column, every frame:
 *  1. The game's area: loaded, all eight neighbours loaded (the game never meshes the outer loaded ring), nearer than
 *     the game's fog end; in the prediction path also inside the measured-radius geometry.
 *  2. The caller's probe: does the game have the column's sections (built, uploaded, faded in)? Only the first
 *     hand-off asks for all of it; afterwards only "still there".
 *  3. First hand-off (SurfaceHandoffTimer): the column's surface section was in the drawn list (drawn-list modes),
 *     its top section is built (the rest built or SurfaceBuiltWait's short extra time), then the 100 ms hold.
 *  4. Still drawn (every frame once shown): with the game's own list, the game draws the column only while one of its
 *     sections is in this frame's list; with a renderer's list, while one was drawn within RECENT_MS. A column the
 *     game does not draw although it is in view is far terrain's that very frame (no gap, and never both: the game
 *     did not draw it), and its hand-off starts over. A column outside the view (behind the camera) stays the
 *     game's: nothing is visible there.
 *  5. Pre-draw (renderer's list and prediction path only, where the answer is a frame late or a guess): within the
 *     measured radius's outer band far terrain also draws behind the game's copy.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfaceHandoffDecider(
	val timer: SurfaceHandoffTimer = SurfaceHandoffTimer(),
	val builtWait: SurfaceBuiltWait = SurfaceBuiltWait(),
	val drawn: SurfaceDrawnList = SurfaceDrawnList(),
) {

	class Probe {

		@JvmField var present = true

		@JvmField var topBuilt = true

		@JvmField var allBuilt = true

		@JvmField var signature = 0L

		@JvmField var fadeDone = true

		fun reset() { present = true; topBuilt = true; allBuilt = true; signature = 0L; fadeDone = true }
	}

	interface Game {

		fun loaded(x: Int, z: Int): Boolean

		fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: Probe)

		fun inView(x: Int, z: Int): Boolean
	}

	inner class Frame(
		val mode: SurfaceDrawnMode,
		val camX: Double, val camY: Double, val camZ: Double,
		val now: Long,
		val fogEnd: Double,
		val radius: Int,
		val predict: SurfaceHandoffGeometry.Prediction?,
		val holdBack: Boolean,
		val game: Game,
	) {
		val ccx = SurfaceHandoff.chunkOf(camX)
		val ccz = SurfaceHandoff.chunkOf(camZ)

		val limit = SurfaceHandoffGeometry.limit(radius, fogEnd)
		private val fogSq = if (fogEnd.isInfinite()) Double.POSITIVE_INFINITY else fogEnd * fogEnd
		private val probe = Probe()
		private val loaded: (Int, Int) -> Boolean = game::loaded

		var gameOnly = 0; var both = 0; var notOwned = 0; var noNeighbours = 0; var notBuilt = 0; var waiting = 0

		var notDrawn = 0

		var doubleDrawn = 0

		var lastShown = false; var lastTakenBack = false; var lastPresent = false; var lastPreDraw = false; var lastGameShows = false

		fun decide(x: Int, z: Int, apexHas: Boolean, yLo: Int, yHi: Int, lo: Int, hi: Int): Int {
			val k = SurfaceIndex.key(x, z)
			val prediction = mode == SurfaceDrawnMode.PREDICTION

			val inRange = game.loaded(x, z) && SurfaceHandoffGeometry.nearestSq(x, z, camX, camZ) < fogSq &&
				(!prediction || (SurfaceHandoffGeometry.gameOwns(x, z, camX, camZ, radius, limit) &&
					(!apexHas || SurfaceHandoffGeometry.verticalOwns(yLo, yHi, camY, radius, 0, limit))))

			val neighbours = inRange && SurfaceHandoff.neighboursLoaded(x, z, loaded)
			var present = neighbours
			val settled = present && timer.isShown(k)

			probe.reset()
			if (present) { game.probe(x, z, lo, hi, settled, probe); present = probe.present }
			var topBuilt = present && probe.topBuilt
			val allBuilt = present && probe.allBuilt

			if (topBuilt && !settled && !prediction && !drawn.surfaceDrawn(x, hi, z, now)) topBuilt = false
			val built = present && (settled || builtWait.built(k, now, topBuilt, allBuilt))
			if (!present) builtWait.release(k)
			val shown = if (present) timer.update(k, now, built, probe.signature, probe.fadeDone) else { timer.release(k); false }

			val gameShows = shown && when (mode) {
				SurfaceDrawnMode.VANILLA -> drawn.columnDrawnThisFrame(x, z) || (apexHas && !game.inView(x, z))
				SurfaceDrawnMode.RENDERER -> drawn.columnDrawnRecently(x, z, now) || (apexHas && !game.inView(x, z))
				SurfaceDrawnMode.PREDICTION -> true
			}

			val takenBack = shown && !gameShows
			if (takenBack) { timer.release(k); builtWait.release(k) }

			val preDraw = gameShows && !(mode == SurfaceDrawnMode.VANILLA) &&
				((apexHas && SurfaceHandoffGeometry.verticalEdge(yLo, yHi, camY, radius, limit)) ||
					(predict?.drops(x, z, radius, limit) ?: SurfaceHandoffGeometry.outerRings(x, z, camX, camZ, radius, limit)))
			var d = SurfaceHandoffOrder.decide(apexHas, gameShows, preDraw)
			if (apexHas) when {
				!inRange -> notOwned++
				!neighbours -> noNeighbours++
				!shown -> if (built) waiting++ else notBuilt++
				!gameShows -> notDrawn++
				d == SurfaceHandoffOrder.BOTH -> both++
				else -> gameOnly++
			}

			if (!holdBack) d = d or SurfaceHandoffOrder.GAME
			if (apexHas && !prediction && d == SurfaceHandoffOrder.BOTH && drawn.columnDrawnThisFrame(x, z)) doubleDrawn++
			lastShown = shown && !takenBack; lastTakenBack = takenBack; lastPresent = present; lastPreDraw = preDraw; lastGameShows = gameShows
			return d
		}
	}

	fun prune(keep: (Long) -> Boolean) { timer.prune(keep); builtWait.prune(keep) }

	fun clear() { timer.clear(); builtWait.clear(); drawn.clear() }
}
