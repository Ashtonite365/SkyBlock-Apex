package dev.ashtonite365.apex.test.features.dungeon.waterboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.waterboard.Board
import dev.ashtonite365.apex.features.dungeon.waterboard.Door
import dev.ashtonite365.apex.features.dungeon.waterboard.FlowAtlas
import dev.ashtonite365.apex.features.dungeon.waterboard.Gate
import dev.ashtonite365.apex.features.dungeon.waterboard.OffReason
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardGuide
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardPlanner
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterObservation
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterStep

class WaterBoardPlannerTest {
	private fun mask(vararg g: Gate) = g.fold(0) { m, x -> m or x.bit }
	private val five = mask(Gate.EMERALD, Gate.DIAMOND, Gate.COAL, Gate.GOLD, Gate.QUARTZ)
	private val pb = Door.PURPLE.bit or Door.BLUE.bit
	private fun ref() = FlowAtlas(Board.parse(WaterBoardFixtures.REFERENCE))

	@Test fun referencePlanIsBlueThenPurple() {
		val plan = WaterBoardPlanner.plan(ref(), 0, pb)
		assertNotNull(plan)
		assertEquals(listOf(Door.BLUE.bit, Door.PURPLE.bit), plan!!.pulses.map { it.doors })
		assertEquals(0, plan.pulses[0].config, "blue needs no flips")
		assertEquals(Door.PURPLE.bit, ref().doors(plan.pulses[1].config))
		assertTrue(plan.flips <= 5)
	}

	@Test fun planNeverTouchesADoneDoor() {
		val atlas = ref()
		val plan = WaterBoardPlanner.plan(atlas, 0, Door.PURPLE.bit)!!
		assertEquals(1, plan.pulses.size)
		assertEquals(0, atlas.doors(plan.pulses[0].config) and Door.BLUE.bit)
	}

	@Test fun multiDoorPlanSharesFlips() {
		val atlas = FlowAtlas(Board.parse(WaterBoardFixtures.COMB))
		val all = Door.PURPLE.bit or Door.BLUE.bit or Door.RED.bit
		assertEquals(0, atlas.doors(0))
		assertEquals(Door.PURPLE.bit, atlas.doors(Gate.QUARTZ.bit))
		assertEquals(Door.PURPLE.bit or Door.RED.bit, atlas.doors(mask(Gate.QUARTZ, Gate.COAL)))
		assertEquals(Door.BLUE.bit, atlas.doors(mask(Gate.QUARTZ, Gate.COAL, Gate.GOLD)))
		val plan = WaterBoardPlanner.plan(atlas, 0, all)!!
		assertEquals(3, plan.flips)
		assertEquals(listOf(Door.PURPLE.bit or Door.RED.bit, Door.BLUE.bit), plan.pulses.map { it.doors })

		val pbPlan = WaterBoardPlanner.plan(atlas, 0, Door.PURPLE.bit or Door.BLUE.bit)!!
		assertTrue(pbPlan.pulses.all { atlas.doors(it.config) and Door.RED.bit == 0 })
	}

	@Test fun unreachableDoorIsStuck() {
		val atlas = FlowAtlas(Board.parse(listOf("#S#.#", "#.#.#", "#B#L#")))
		assertNull(WaterBoardPlanner.plan(atlas, 0, Door.LIME.bit or Door.BLUE.bit))
		val g = WaterBoardGuide(atlas)
		val s = g.update(WaterObservation(0, false, Door.LIME.bit or Door.BLUE.bit, 0))
		assertTrue(s is WaterStep.Stuck)
	}

	@Test fun onOffInstructionsForTheReferenceBoard() {
		val g = WaterBoardGuide(ref())

		assertEquals(WaterStep.WaterOn(Door.BLUE.bit), g.update(WaterObservation(0, false, pb, 0)))

		assertEquals(WaterStep.KeepOn(Door.BLUE.bit), g.update(WaterObservation(0, true, pb, 0)))

		assertEquals(WaterStep.WaterOff(OffReason.TARGET_REACHED), g.update(WaterObservation(0, true, pb, Door.BLUE.bit)))

		assertEquals(WaterStep.WaterOff(OffReason.TARGET_REACHED), g.update(WaterObservation(0, true, Door.PURPLE.bit, Door.BLUE.bit)))

		val flip = g.update(WaterObservation(0, false, Door.PURPLE.bit, 0))
		assertTrue(flip is WaterStep.Flip)
		flip as WaterStep.Flip
		assertEquals(Door.PURPLE.bit, flip.target)
		assertEquals(Gate.of(five).toSet(), flip.order.toSet())

		assertEquals(WaterStep.WaterOn(Door.PURPLE.bit), g.update(WaterObservation(five, false, Door.PURPLE.bit, 0)))
		assertEquals(WaterStep.KeepOn(Door.PURPLE.bit), g.update(WaterObservation(five, true, Door.PURPLE.bit, 0)))
		assertEquals(WaterStep.WaterOff(OffReason.TARGET_REACHED), g.update(WaterObservation(five, true, Door.PURPLE.bit, Door.PURPLE.bit)))

		val chest = g.update(WaterObservation(five, false, 0, 0))
		val quiet = WaterBoardPlanner.quietConfig(g.atlas, five)!!
		assertEquals(0, g.atlas.doors(quiet))
		if (quiet == five) assertEquals(WaterStep.WaterOn(0, chest = true), chest)
		else assertTrue(chest is WaterStep.Flip && chest.chest)
		assertEquals(WaterStep.WaterOff(OffReason.CHEST_PULSE), g.update(WaterObservation(quiet, true, 0, 0)))
		assertEquals(WaterStep.Done, g.update(WaterObservation(quiet, false, 0, 0)))
	}

	@Test fun noChestPulseWhenDisabled() {
		val g = WaterBoardGuide(ref(), chestPulse = false)
		assertEquals(WaterStep.Done, g.update(WaterObservation(0, false, 0, 0)))
	}

	@Test fun recalculatesAfterAWrongFlip() {
		val g = WaterBoardGuide(ref())

		val s1 = g.update(WaterObservation(Gate.COAL.bit or Gate.TERRACOTTA.bit, false, Door.PURPLE.bit, 0)) as WaterStep.Flip
		assertEquals(Gate.of(five and Gate.COAL.bit.inv()).toSet(), s1.order.toSet())

		val s2 = g.update(WaterObservation(five and Gate.EMERALD.bit.inv(), false, Door.PURPLE.bit, 0)) as WaterStep.Flip
		assertEquals(listOf(Gate.EMERALD), s2.order)
	}

	@Test fun waterOnWithWrongLeversSaysOff() {
		val g = WaterBoardGuide(ref())

		assertEquals(WaterStep.WaterOff(OffReason.WRONG_LEVERS), g.update(WaterObservation(0, true, Door.PURPLE.bit, 0)))
	}

	@Test fun leversChangedWhileWaterOn() {
		val g = WaterBoardGuide(ref())
		g.update(WaterObservation(0, false, pb, 0))
		assertEquals(WaterStep.KeepOn(Door.BLUE.bit), g.update(WaterObservation(0, true, pb, 0)))
		assertEquals(WaterStep.WaterOff(OffReason.LEVERS_CHANGED), g.update(WaterObservation(Gate.COAL.bit, true, pb, 0)))
	}

	@Test fun gatesMidMoveWait() {
		assertEquals(WaterStep.Reading, WaterBoardGuide(ref()).update(WaterObservation(null, false, pb, 0)))
	}

	@Test fun drainCheck() {
		val atlas = ref()
		val g = WaterBoardGuide(atlas)
		assertTrue(g.drainOk(five, IntArray(atlas.board.size), Door.PURPLE.bit), "dry board")
	}

	@Test fun walkOrderIsNearestFirst() {
		val levers = mapOf(
			Gate.QUARTZ to Triple(0.0, 0.0, 0.0), Gate.GOLD to Triple(10.0, 0.0, 0.0), Gate.COAL to Triple(5.0, 0.0, 0.0),
		)
		assertEquals(listOf(Gate.GOLD, Gate.COAL, Gate.QUARTZ),
			WaterBoardPlanner.walkOrder(listOf(Gate.QUARTZ, Gate.GOLD, Gate.COAL), Triple(11.0, 0.0, 0.0), levers))
		assertEquals(listOf(Gate.COAL, Gate.DIAMOND),
			WaterBoardPlanner.walkOrder(listOf(Gate.DIAMOND, Gate.COAL), Triple(0.0, 0.0, 0.0), levers), "levers not found go last")
	}
}
