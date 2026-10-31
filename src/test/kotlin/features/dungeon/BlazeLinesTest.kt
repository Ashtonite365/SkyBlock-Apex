package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BlazeOrder
import dev.ashtonite365.apex.features.dungeon.BlazeOrder.Lines
import dev.ashtonite365.apex.features.dungeon.BlazeOrder.Plan
import dev.ashtonite365.apex.features.dungeon.BlazeOrder.Status

class BlazeLinesTest {
	@Test fun targetAndNext() {
		val plan = Plan(Status.SOLVING, boxes = listOf(7), next = 9)
		assertEquals(Lines(7, 9), BlazeOrder.lines(plan, targetLine = true, nextLine = true))
		assertEquals(Lines(7, null), BlazeOrder.lines(plan, targetLine = true, nextLine = false))

		assertEquals(Lines(7, 9), BlazeOrder.lines(plan, targetLine = false, nextLine = true))
		assertEquals(Lines(null, null), BlazeOrder.lines(plan, targetLine = false, nextLine = false))
	}

	@Test fun noNextBlaze() {
		assertEquals(Lines(7, null), BlazeOrder.lines(Plan(Status.SOLVING, boxes = listOf(7)), true, true))
		assertEquals(Lines(7, null), BlazeOrder.lines(Plan(Status.SOLVING, boxes = listOf(7), next = 7), true, true))
	}

	@Test fun nothingWithoutATarget() {
		val none = Lines(null, null)
		assertEquals(none, BlazeOrder.lines(Plan(Status.SOLVING, targetOutOfView = true), true, true))
		assertEquals(none, BlazeOrder.lines(Plan(Status.SOLVING, boxes = listOf(1, 2), next = 3, tie = true), true, true))
		for (s in listOf(Status.IDLE, Status.FAILED, Status.DONE, Status.FINDING_DIRECTION))
			assertEquals(none, BlazeOrder.lines(Plan(s, boxes = listOf(7), next = 9), true, true))
	}

	@Test fun trackerPlanFeedsLines() {
		val t = BlazeOrder.Tracker()
		val blazes = listOf(BlazeOrder.Blaze(1, 100L, 100L, 0.0, 70.0, 0.0), BlazeOrder.Blaze(2, 200L, 200L, 0.0, 72.0, 0.0), BlazeOrder.Blaze(3, 300L, 300L, 0.0, 74.0, 0.0))
		t.observe(blazes, emptySet(), emptySet(), playerNear = true)
		t.direction = BlazeOrder.Direction.LOWEST_FIRST
		val plan = t.plan { true }
		val l = BlazeOrder.lines(plan, true, true)
		assertEquals(plan.boxes.single(), l.target)
		assertEquals(plan.next, l.next)

		assertEquals(Lines(null, null), BlazeOrder.lines(t.plan { false }, true, true))
	}

	@Test fun blockedLineDistances() {
		val a = doubleArrayOf(0.0, 70.0, 0.0); val b = doubleArrayOf(10.0, 70.0, 0.0)
		assertEquals(false, BlazeOrder.blocked(a, b, emptyList()))
		assertEquals(false, BlazeOrder.blocked(a, b, listOf(doubleArrayOf(5.0, 75.0, 5.0))))
		assertEquals(true, BlazeOrder.blocked(a, b, listOf(doubleArrayOf(5.0, 70.9, 0.0))))
		assertEquals(false, BlazeOrder.blocked(a, b, listOf(doubleArrayOf(5.0, 71.1, 0.0))))

		assertEquals(false, BlazeOrder.blocked(a, b, listOf(doubleArrayOf(11.5, 70.0, 0.0))))
		assertEquals(0.9, BlazeOrder.segmentDistance(0.0, 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 4.0, 0.9), 1e-9)
	}
}
