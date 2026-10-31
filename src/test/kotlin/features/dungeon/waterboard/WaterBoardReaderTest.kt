package dev.ashtonite365.apex.test.features.dungeon.waterboard

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.waterboard.Door
import dev.ashtonite365.apex.features.dungeon.waterboard.DoorTracker
import dev.ashtonite365.apex.features.dungeon.waterboard.FlowAtlas
import dev.ashtonite365.apex.features.dungeon.waterboard.Gate
import dev.ashtonite365.apex.features.dungeon.waterboard.LeverPolarity
import dev.ashtonite365.apex.features.dungeon.waterboard.P
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardGuide
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardReader
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterObservation
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterStep
import dev.ashtonite365.apex.test.features.dungeon.waterboard.WaterBoardFixtures.X0
import dev.ashtonite365.apex.test.features.dungeon.waterboard.WaterBoardFixtures.Y0

class WaterBoardReaderTest {
	private val box = WaterBoardReader.Box(P(X0 - 30, Y0 - 2, -12), P(X0 + 14, Y0 + 40, 6))
	private fun mask(vararg g: Gate) = g.fold(0) { m, x -> m or x.bit }
	private val five = mask(Gate.EMERALD, Gate.DIAMOND, Gate.COAL, Gate.GOLD, Gate.QUARTZ)
	private fun locate(w: dev.ashtonite365.apex.features.dungeon.waterboard.BlockView) = WaterBoardReader.locate(w, box)

	@Test fun findsTheReferenceBoardInARoom() {
		val w = WaterBoardFixtures.world(WaterBoardFixtures.REFERENCE)
		val why = mutableListOf<String>()
		val lay = WaterBoardReader.locate(w, box, why)
		assertNotNull(lay, why.joinToString())
		lay!!
		assertEquals(Door.PURPLE.bit or Door.BLUE.bit, lay.board.doors, lay.board.render())
		assertEquals(five, lay.board.usedGates)
		val atlas = FlowAtlas(lay.board)
		assertEquals(Door.BLUE.bit, atlas.doors(0), lay.board.render())
		assertEquals(Door.PURPLE.bit, atlas.doors(five), lay.board.render(five))
		assertEquals(Gate.entries.toSet(), lay.gateLevers.keys)
		assertEquals(P(X0 - 5, Y0, -8), lay.waterLever)
		assertEquals(P(X0, Y0, -5), lay.gateLevers[Gate.QUARTZ])

		assertEquals(P(X0 - 7, Y0 + 10, 2), lay.cellPos(lay.board.entry))
	}

	@Test fun notAWaterBoardRoom() {
		val w = WaterBoardReader.MapView()
		for (x in 0..10) for (z in 0..10) w.set(P(x, 60, z), "stone")
		val why = mutableListOf<String>()
		assertNull(WaterBoardReader.locate(w, WaterBoardReader.Box(P(0, 58, 0), P(10, 70, 10)), why))
		assertTrue(why.isNotEmpty())
	}

	@Test fun readsLeversGatesWaterAndDoors() {
		val w = WaterBoardFixtures.world(WaterBoardFixtures.REFERENCE)
		val lay = locate(w)!!
		var seen = WaterBoardReader.observe(w, lay)
		assertEquals(0, seen.config)
		assertEquals(false, seen.leverPowered)
		assertEquals(0, seen.touching)

		WaterBoardFixtures.flip(w, Gate.EMERALD, X0 - 20..X0, Y0..Y0 + 12)
		assertEquals(Gate.EMERALD.bit, WaterBoardReader.observe(w, lay).config)

		WaterBoardFixtures.flip(w, Gate.COAL, X0 - 7..X0 - 7, Y0..Y0 + 12)
		assertNull(WaterBoardReader.observe(w, lay).config)
		WaterBoardFixtures.flip(w, Gate.COAL, X0 - 5..X0 - 5, Y0..Y0 + 12)
		assertEquals(mask(Gate.EMERALD, Gate.COAL), WaterBoardReader.observe(w, lay).config)

		w.set(lay.waterLever!!, "lever", mapOf("face" to "floor", "facing" to "north", "powered" to "true"))
		w.set(P(X0 - 7, Y0, 2), "water", mapOf("level" to "8"))
		seen = WaterBoardReader.observe(w, lay)
		assertEquals(true, seen.leverPowered)
		assertEquals(Door.BLUE.bit, seen.touching)
		assertEquals(8, seen.water[lay.board.index(lay.frame.columnOf(P(X0 - 7, Y0, 2)), 1)])
		assertEquals(mapOf(Door.PURPLE to 1, Door.BLUE to 1), seen.gateWoolCount)
	}

	@Test fun doorsAreDoneWhenTheirRoomWoolChangesOrWaterReachedThem() {
		val w = WaterBoardFixtures.world(WaterBoardFixtures.REFERENCE)
		val lay = locate(w)!!
		val s0 = WaterBoardReader.observe(w, lay)
		val t = DoorTracker(lay.board.doors, s0.gateWoolCount, s0.rowWoolCount)
		assertEquals(Door.PURPLE.bit or Door.BLUE.bit, t.wanted)
		assertEquals(t.wanted, t.remaining(s0.gateWoolCount, s0.rowWoolCount))

		lay.gateWool[Door.BLUE]!!.forEach { w.set(it, "air") }
		val s1 = WaterBoardReader.observe(w, lay)
		assertEquals(Door.PURPLE.bit, t.remaining(s1.gateWoolCount, s1.rowWoolCount))

		t.noteTouching(Door.PURPLE.bit, waterOn = false)
		assertEquals(Door.PURPLE.bit, t.remaining(s1.gateWoolCount, s1.rowWoolCount))
		t.noteTouching(Door.PURPLE.bit, waterOn = true)
		assertEquals(0, t.remaining(s1.gateWoolCount, s1.rowWoolCount))
	}

	@Test fun noRoomWoolMeansEveryBoardDoorIsWanted() {
		val t = DoorTracker(Door.PURPLE.bit or Door.LIME.bit, emptyMap(), mapOf(Door.PURPLE to 1, Door.LIME to 1))
		assertEquals(Door.PURPLE.bit or Door.LIME.bit, t.wanted)
		assertEquals(Door.LIME.bit, t.done(emptyMap(), mapOf(Door.PURPLE to 1, Door.LIME to 0)))
	}

	@Test fun leverPolarityFollowsTheInlet() {
		val p = LeverPolarity()
		assertTrue(p.waterOn(true, true))
		assertFalse(p.waterOn(false, false))
		assertTrue(p.waterOn(null, true))
		repeat(LeverPolarity.SWITCH_AFTER) { p.waterOn(false, true) }
		assertTrue(p.inverted)
		assertTrue(p.waterOn(false, true))
	}

	@Test fun endToEndOnTheWorld() {
		val w = WaterBoardFixtures.world(WaterBoardFixtures.REFERENCE)
		val lay = locate(w)!!
		val g = WaterBoardGuide(FlowAtlas(lay.board))
		val s = WaterBoardReader.observe(w, lay)
		val t = DoorTracker(lay.board.doors, s.gateWoolCount, s.rowWoolCount)
		val step = g.update(WaterObservation(s.config, false, t.remaining(s.gateWoolCount, s.rowWoolCount), s.touching, s.water))
		assertEquals(WaterStep.WaterOn(Door.BLUE.bit), step)
	}

	@Test fun dumpRoundTrips() {
		val w = WaterBoardFixtures.world(WaterBoardFixtures.REFERENCE)
		val text = WaterBoardReader.dump(w, box, listOf("test")) { x, y, z ->
			w.props(P(x, y, z)).entries.joinToString(",") { "${it.key}=${it.value}" }
		}
		val (view, b) = WaterBoardReader.parseDump(text)
		assertEquals(box, b)
		val a = locate(w)!!
		val c = WaterBoardReader.locate(view, b!!)!!
		assertEquals(a.board.signature, c.board.signature)
		assertEquals(a.gateLevers, c.gateLevers)
		assertEquals(a.waterLever, c.waterLever)
	}

	@Test fun oldWaterBoardSettingsAreDropped() {
		val saved = JsonObject(mapOf(
			"water-board" to JsonPrimitive(true), "water-board-mode" to JsonPrimitive("x"), "water-board-hud" to JsonPrimitive(false),
			"water-board-solver" to JsonPrimitive(true), "blaze-solver" to JsonPrimitive(true),
		))
		assertEquals(JsonObject(mapOf("water-board-solver" to JsonPrimitive(true), "blaze-solver" to JsonPrimitive(true))),
			PuzzleSolvers.foldOldWaterBoard(saved))
		val clean = JsonObject(mapOf("blaze-solver" to JsonPrimitive(true)))
		assertTrue(PuzzleSolvers.foldOldWaterBoard(clean) === clean)
	}
}
