// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.fishing.HotspotRadarMath
import dev.ashtonite365.apex.features.garden.GardenPests
import dev.ashtonite365.apex.features.garden.PestTracker
import dev.ashtonite365.apex.features.garden.PestTracker.Source
import dev.ashtonite365.apex.features.garden.PestTrailMath

class PestTrackerTest {
	private fun near(a: Vec3, b: Vec3, eps: Double = 1e-3) = assertTrue(a.distanceTo(b) < eps, "expected $b, got $a")

	private fun trail(p0: Vec3, pitchDeg: Double, yawDeg: Double, p2: Vec3, p3: Vec3, n: Int, count: Int): List<Vec3> {
		val x = Math.toRadians(pitchDeg); val yaw = Math.toRadians(yawDeg)
		val look = Vec3(-sin(yaw) * cos(x), -sin(x), cos(yaw) * cos(x))
		val p1 = p0.add(look.scale(4.0)).add(0.0, 3.0, 0.0)
		return (0 until count).map { i ->
			val s = i.toDouble() / n; val u = 1 - s
			p0.scale(u * u * u).add(p1.scale(3 * u * u * s)).add(p2.scale(3 * u * s * s)).add(p3.scale(s * s * s))
		}
	}

	@Test
	fun knownParticleSequenceGivesThePest() {
		val p0 = Vec3(0.0, 70.0, 0.0)
		val pest = Vec3(-40.0, 68.0, 30.0)
		val pts = trail(p0, 60.0, 30.0, Vec3(-20.0, 72.0, 20.0), pest, 12, 6)
		val guess = PestTrailMath.solve(pts)!!
		near(guess, pest)

		near(guess.subtract(p0).normalize(), pest.subtract(p0).normalize(), 1e-5)
		for (pitch in listOf(-60.0, -20.0, 0.0, 20.0, 45.0, 80.0)) for (yaw in listOf(0.0, 90.0, 200.0)) {
			near(PestTrailMath.solve(trail(p0, pitch, yaw, Vec3(10.0, 75.0, -15.0), Vec3(25.0, 72.0, -60.0), 20, 8))!!,
				Vec3(25.0, 72.0, -60.0), 1e-2)
		}
	}

	@Test
	fun oldFormulaPointedTheWrongWay() {

		val p0 = Vec3(0.0, 70.0, 0.0)
		val pest = Vec3(-40.0, 68.0, 30.0)
		val pts = trail(p0, 60.0, 30.0, Vec3(-20.0, 72.0, 20.0), pest, 12, 6)
		val old = HotspotRadarMath.guessFromPitch(pts, 60.0)
		assertTrue(old == null || old.distanceTo(pest) > 100, "old guess $old")
		assertNull(PestTrailMath.solve(pts.take(3)))
	}

	@Test
	fun lookPitchInversion() {
		for (deg in listOf(-80.0, -30.0, 0.0, 48.0, 75.0)) {
			val x = Math.toRadians(deg)
			val slope = kotlin.math.atan2(sin(x) - 0.75, cos(x))
			assertEquals(x, PestTrailMath.lookPitchFromSlope(slope), 1e-9)
		}
		assertEquals(1.0, PestTrailMath.controlPointDistance(Math.PI / 2), 1e-9)
	}

	@Test
	fun lineEndpointsAndMaxDistance() {
		val eye = Vec3(10.0, 71.62, 5.0)
		val start = PestTracker.lineStart(eye)
		near(start, Vec3(10.0, 71.12, 5.0))
		val pest = Vec3(20.0, 75.0, -5.0)
		val (a, b) = PestTracker.cameraRelative(start, pest, eye)
		near(a, Vec3(0.0, -0.5, 0.0))
		near(b, Vec3(10.0, 3.38, -10.0))
		near(b.subtract(a).normalize(), pest.subtract(start).normalize(), 1e-9)

		val pests = listOf(Vec3(10.0, 71.0, 40.0), Vec3(10.0, 71.0, 25.0), Vec3(10.0, 71.0, 500.0))
		val nearest = PestTracker.select(pests, start, 64.0, false) { error("no guess while a pest is visible") }
		assertEquals(listOf(pests[1]), nearest.entities)
		assertNull(nearest.guess)
		assertEquals(Source.ENTITY, nearest.waypoint!!.source)
		assertEquals(listOf(pests[1], pests[0]), PestTracker.select(pests, start, 64.0, true) { null }.entities)
		val far = PestTracker.select(listOf(pests[2]), start, 64.0, false) { PestTracker.Target(Vec3.ZERO, Source.PARTICLE) }
		assertTrue(far.entities.isEmpty())
		assertEquals(Source.PARTICLE, far.waypoint!!.source)
	}

	@Test
	fun pestPositionRaisedOneBlock() {
		assertEquals(1.0, PestTracker.PEST_Y_OFFSET)

		assertEquals(Vec3(10.5, 72.0, -3.25), PestTracker.pestPoint(Vec3(10.5, 71.0, -3.25)))

		val mid = { id: Int -> Vec3(id * 100.0, 70.0, 0.0) }
		val from = Vec3(390.0, 70.0, 0.0)
		assertEquals(Vec3(1.0, 3.0, 3.0), PestTracker.chooseGuess(Vec3(1.0, 2.0, 3.0), emptyList(), null, from, mid)!!.pos)
		assertEquals(Vec3(400.0, 71.0, 0.0), PestTracker.chooseGuess(null, listOf(1, 4, 8), null, from, mid)!!.pos)
		assertEquals(Vec3(200.0, 71.0, 0.0), PestTracker.chooseGuess(null, emptyList(), 2, from, mid)!!.pos)

		val raised = PestTracker.pestPoint(Vec3(0.0, 70.0, 5.0))
		val sel = PestTracker.select(listOf(raised), Vec3.ZERO, 1000.0, false) { null }
		assertEquals(raised, sel.waypoint!!.pos)
		assertEquals(listOf(raised), sel.entities)
		val guessed = PestTracker.select(emptyList(), from, 1000.0, false) {
			PestTracker.chooseGuess(Vec3(1.0, 2.0, 3.0), emptyList(), null, from, mid)
		}
		assertEquals(Vec3(1.0, 3.0, 3.0), PestTracker.applyClear(guessed, null).waypoint!!.pos)
	}

	@Test
	fun guessSourceSelection() {
		val mid = { id: Int -> Vec3(id * 100.0, 70.0, 0.0) }
		val from = Vec3(390.0, 70.0, 0.0)
		assertEquals(Source.PARTICLE, PestTracker.chooseGuess(Vec3(1.0, 2.0, 3.0), listOf(4), 2, from, mid)!!.source)
		val tab = PestTracker.chooseGuess(null, listOf(1, 4, 8), 2, from, mid)!!
		assertEquals(Source.TAB_PLOT, tab.source); assertEquals(4, tab.plot)
		val chat = PestTracker.chooseGuess(null, emptyList(), 2, from, mid)!!
		assertEquals(Source.CHAT_PLOT, chat.source); near(chat.pos, Vec3(200.0, 71.0, 0.0))
		assertNull(PestTracker.chooseGuess(null, emptyList(), null, from, mid))
		assertEquals(8, GardenPests.parseSpawnPlot("EWW! 2 Pest have spawned in Plot - 8!"))
		assertNull(GardenPests.parseSpawnPlot("EWW! A Pest has spawned in Plot - Barn!"))
	}

	private fun cfg(s: String) = JsonParser.parseString(s).asJsonObject

	@Test
	fun mergeMigration() {
		val on = cfg("""{"garden-tools":{"pest-waypoint":true,"pest-waypoint-line":true,"pest-finder":true}}""")
		PestTracker.migrate(on)
		assertEquals(cfg("""{"garden-tools":{"pest-finder":true,"pest-tracker-waypoint":true,"pest-tracker-guess-line":true}}"""), on)
		PestTracker.migrate(on)
		assertEquals(cfg("""{"garden-tools":{"pest-finder":true,"pest-tracker-waypoint":true,"pest-tracker-guess-line":true}}"""), on)
		val lineOnly = cfg("""{"garden-tools":{"pest-waypoint":false,"pest-waypoint-line":true}}""")
		PestTracker.migrate(lineOnly)
		assertEquals(cfg("""{"garden-tools":{"pest-tracker-waypoint":false,"pest-tracker-guess-line":false}}"""), lineOnly)
		val keep = cfg("""{"garden-tools":{"pest-waypoint":true,"pest-tracker-waypoint":false}}""")
		PestTracker.migrate(keep)
		assertEquals(cfg("""{"garden-tools":{"pest-tracker-waypoint":false,"pest-tracker-guess-line":false}}"""), keep)
		val untouched = cfg("""{"other":{"pest-waypoint":true}}""")
		PestTracker.migrate(untouched)
		assertEquals(cfg("""{"other":{"pest-waypoint":true}}"""), untouched)
		val empty = cfg("""{"garden-tools":{}}""").also { PestTracker.migrate(it) }
		assertEquals(JsonObject().apply { add("garden-tools", JsonObject()) }, empty)
	}

	private val from = Vec3(0.0, 70.0, 0.0)
	private val nearGuess = PestTracker.Target(Vec3(40.0, 70.0, 0.0), Source.TAB_PLOT, 3)
	private val farGuess = PestTracker.Target(Vec3(200.0, 70.0, 0.0), Source.CHAT_PLOT, 9)

	@Test fun parsesAliveFromTab() {
		assertEquals(3, GardenPests.parseAlive(listOf("Pests:", " Alive: 3", " Plots: 3, 9")))
		assertEquals(0, GardenPests.parseAlive(listOf(" Alive: 0")))
		assertNull(GardenPests.parseAlive(listOf(" Plots: 3")))
	}

	@Test fun clearsWhenNoPestsLeft() {
		assertEquals(PestTracker.ClearReason.NO_PESTS_LEFT, PestTracker.clearReason(0, listOf(3), nearGuess, from, 128.0))
	}

	@Test fun clearsWhenNoInfestedPlots() {
		assertEquals(PestTracker.ClearReason.NO_INFESTED_PLOTS, PestTracker.clearReason(2, emptyList(), nearGuess, from, 128.0))
	}

	@Test fun clearsWhenGuessOutOfRange() {
		assertEquals(PestTracker.ClearReason.OUT_OF_RANGE, PestTracker.clearReason(2, listOf(9), farGuess, from, 128.0))
		assertEquals(PestTracker.ClearReason.OUT_OF_RANGE, PestTracker.clearReason(null, emptyList(), farGuess, from, 128.0))
	}

	@Test fun keepsGuessOtherwise() {
		assertNull(PestTracker.clearReason(2, listOf(3), nearGuess, from, 128.0))
		assertNull(PestTracker.clearReason(null, emptyList(), nearGuess, from, 128.0))
		assertNull(PestTracker.clearReason(2, listOf(3), null, from, 128.0))
	}

	@Test fun clearDropsOnlyTheGuessNotVisiblePests() {
		val pest = Vec3(5.0, 70.0, 0.0)
		val withGuess = PestTracker.Selection(emptyList(), nearGuess)
		val cleared = PestTracker.applyClear(withGuess, PestTracker.ClearReason.NO_PESTS_LEFT)
		assertNull(cleared.guess); assertNull(cleared.waypoint)
		val visible = PestTracker.Selection(listOf(pest), null)
		assertEquals(visible, PestTracker.applyClear(visible, PestTracker.ClearReason.OUT_OF_RANGE))
		assertEquals(pest, PestTracker.applyClear(visible, PestTracker.ClearReason.OUT_OF_RANGE).waypoint?.pos)
		assertEquals(withGuess, PestTracker.applyClear(withGuess, null))
	}
}
