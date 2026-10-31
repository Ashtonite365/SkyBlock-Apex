/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, which sections the game ACTUALLY drew, frame by frame (pure Kotlin, unit-tested).
 *
 * The hand-off decides a chunk column is the game's from what the game's renderer really drew, not from a prediction:
 *  * vanilla terrain: the game's own visible-section list for the frame (the list its occlusion graph produced, which
 *    its draw-list step walks), read at the head of that step;
 *  * a replacement terrain renderer: an optional [SurfaceDrawnSource] that reads that renderer's own list. It lives in
 *    a separate source set, is found through the service loader only when its mod is installed, and is never
 *    class-loaded otherwise ([SurfaceDrawnSources]);
 *  * neither: the prediction path (built state, loaded chunks, measured geometry; see SurfaceHandoffGeometry).
 *
 * The list decides when a column becomes the game's (its surface section drawn, now or within
 * [SurfaceDrawnList.RECENT_MS]) and whether the game still draws it (any section of the column in this frame's list,
 * or within RECENT_MS for a renderer's list; see SurfaceHandoffDecider). Nothing here, nor in the hand-off, reads the
 * game's render-distance setting: where the game stops drawing is whatever the list says.
 *
 * When the camera is given to [SurfaceDrawnList.begin], the list also measures how far out the game drew this frame
 * ([SurfaceDrawnList.frameReach], in chunks), which SurfaceGameArea uses instead of a render-distance setting.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap

fun interface SurfaceSectionSink {
	fun drawn(x: Int, y: Int, z: Int)
}

interface SurfaceDrawnSource {

	fun unsupportedReason(): String? = null

	fun collect(sink: SurfaceSectionSink): Boolean
}

enum class SurfaceDrawnMode(val label: String) {

	VANILLA("game's visible list"),

	RENDERER("renderer's drawn list"),

	PREDICTION("prediction"),
}

class SurfaceDrawnList(val recentMs: Long = RECENT_MS) : SurfaceSectionSink {
	private val last = Long2LongOpenHashMap().apply { defaultReturnValue(Long.MIN_VALUE) }

	private val lastColumn = Long2LongOpenHashMap().apply { defaultReturnValue(Long.MIN_VALUE) }
	private var now = 0L
	private var pruneAt = Long.MIN_VALUE
	private var prevColumn = Long.MIN_VALUE

	private var hasCamera = false
	private var camX = 0.0; private var camY = 0.0; private var camZ = 0.0
	private var ccx = 0; private var ccz = 0; private var csy = 0
	private var maxRounded = -1L
	private var maxNearSq = 0.0
	private var maxSecDy = 0
	private var maxBlockDy = 0.0

	var frameSections = 0
		private set

	fun begin(nowMs: Long) {
		now = nowMs
		frameSections = 0
		prevColumn = Long.MIN_VALUE
		hasCamera = false
		maxRounded = -1L; maxNearSq = 0.0; maxSecDy = 0; maxBlockDy = 0.0
		if (nowMs >= pruneAt) {
			pruneAt = nowMs + PRUNE_MS
			val it = last.long2LongEntrySet().fastIterator()
			while (it.hasNext()) if (nowMs - it.next().longValue > recentMs) it.remove()
			val ic = lastColumn.long2LongEntrySet().fastIterator()
			while (ic.hasNext()) if (nowMs - ic.next().longValue > recentMs) ic.remove()
		}
	}

	fun begin(nowMs: Long, x: Double, y: Double, z: Double) {
		begin(nowMs)
		hasCamera = true
		camX = x; camY = y; camZ = z
		ccx = SurfaceHandoff.chunkOf(x); ccz = SurfaceHandoff.chunkOf(z); csy = SurfaceHandoff.chunkOf(y)
	}

	override fun drawn(x: Int, y: Int, z: Int) {
		last.put(key(x, y, z), now)
		frameSections++
		val col = SurfaceIndex.key(x, z)
		if (col != prevColumn) { lastColumn.put(col, now); prevColumn = col }
		if (hasCamera) {
			val r = SurfaceHandoff.roundedDistSq(ccx, ccz, x, z)
			if (r > maxRounded) maxRounded = r
			val n = SurfaceHandoffGeometry.nearestSq(x, z, camX, camZ)
			if (n > maxNearSq) maxNearSq = n
			val dy = Math.abs(y - csy)
			if (dy > maxSecDy) maxSecDy = dy
			val by = if (y * 16 > camY) y * 16 - camY else if (y * 16 + 16 < camY) camY - (y * 16 + 16) else 0.0
			if (by > maxBlockDy) maxBlockDy = by
		}
	}

	fun frameReach(): Int {
		if (!hasCamera || maxRounded < 0) return 0
		var r = Math.floor(Math.sqrt(maxRounded.toDouble())).toInt() + 1
		r = maxOf(r, Math.floor(Math.sqrt(maxNearSq) / 16.0).toInt() + 1)
		r = maxOf(r, maxSecDy, Math.floor(maxBlockDy / 16.0).toInt() + 1)
		return r
	}

	fun columnDrawnThisFrame(x: Int, z: Int): Boolean = lastColumn.get(SurfaceIndex.key(x, z)) == now

	fun columnDrawnRecently(x: Int, z: Int, nowMs: Long): Boolean {
		val t = lastColumn.get(SurfaceIndex.key(x, z))
		return t != Long.MIN_VALUE && nowMs - t <= recentMs
	}

	fun drawnRecently(x: Int, y: Int, z: Int, nowMs: Long): Boolean {
		val t = last.get(key(x, y, z))
		return t != Long.MIN_VALUE && nowMs - t <= recentMs
	}

	fun drawnThisFrame(x: Int, y: Int, z: Int): Boolean = last.get(key(x, y, z)) == now

	fun surfaceDrawn(x: Int, topSection: Int, z: Int, nowMs: Long): Boolean =
		drawnRecently(x, topSection, z, nowMs) || drawnRecently(x, topSection - 1, z, nowMs)

	fun columnDrawnThisFrame(x: Int, lo: Int, hi: Int, z: Int): Boolean {
		for (y in lo..hi) if (drawnThisFrame(x, y, z)) return true
		return false
	}

	fun clear() { last.clear(); lastColumn.clear(); frameSections = 0; prevColumn = Long.MIN_VALUE; maxRounded = -1L }

	val size: Int get() = last.size

	companion object {

		const val RECENT_MS = 100L
		private const val PRUNE_MS = 500L

		fun key(x: Int, y: Int, z: Int): Long =
			((x.toLong() and 0x3FFFFF) shl 42) or ((z.toLong() and 0x3FFFFF) shl 20) or (y.toLong() and 0xFFFFF)
	}
}

class SurfaceDrawnSources(
	private val isModLoaded: (String) -> Boolean,
	private val loadOptional: () -> SurfaceDrawnSource?,
	private val warn: (String, Throwable?) -> Unit,
) {
	private var resolved = false
	private var source: SurfaceDrawnSource? = null

	var failed = false
		private set

	val active: Boolean get() { resolve(); return source != null && !failed }

	private fun resolve() {
		if (resolved) return
		resolved = true
		if (!runCatching { isModLoaded(RENDERER_MOD_ID) }.getOrDefault(false)) return
		val s = try {
			loadOptional()
		} catch (t: Throwable) {
			fail("could not load the replacement renderer's drawn-list reader; using the prediction path", t); return
		}
		if (s == null) { fail("no drawn-list reader for the replacement renderer; using the prediction path", null); return }
		val reason = try { s.unsupportedReason() } catch (t: Throwable) { fail("drawn-list reader failed its version check; using the prediction path", t); return }
		if (reason != null) { fail("replacement renderer's drawn list not used ($reason); using the prediction path", null); return }
		source = s
	}

	private fun fail(message: String, t: Throwable?) {
		failed = true
		source = null
		warn(message, t)
	}

	fun collect(sink: SurfaceSectionSink): Boolean {
		resolve()
		val s = source ?: return false
		if (failed) return false
		return try {
			s.collect(sink)
		} catch (t: Throwable) {
			fail("reading the replacement renderer's drawn list failed; using the prediction path", t)
			false
		}
	}

	companion object {

		const val RENDERER_MOD_ID = "sodium"

		fun serviceLoader(loader: ClassLoader): () -> SurfaceDrawnSource? = {
			java.util.ServiceLoader.load(SurfaceDrawnSource::class.java, loader).iterator().let { if (it.hasNext()) it.next() else null }
		}
	}
}
