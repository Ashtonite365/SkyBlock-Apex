package dev.ashtonite365.apex.test.features.fishing

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.fishing.ReachedTargets

class HotspotReachedTest {
	private val a = Vec3(100.5, 65.0, 200.5)
	private val b = Vec3(140.5, 65.0, 180.5)
	private val far = Vec3(60.0, 66.0, 200.0)

	@Test
	fun reachedByRadius() {
		val t = ReachedTargets()
		assertEquals(a, t.pick(listOf(a), far, null), "far away: still tracked")
		assertNull(t.pick(listOf(a), Vec3(103.0, 66.0, 201.0), null), "player inside the radius: tracking stops")
		assertTrue(t.isReached(a))
		assertFalse(ReachedTargets().inside(a, Vec3(100.5, 80.0, 200.5)), "far above is not inside")
	}

	@Test
	fun reachedByBobber() {
		val t = ReachedTargets()
		assertNull(t.pick(listOf(a), far, Vec3(101.0, 64.0, 199.0)), "bobber fishing in the hotspot: tracking stops")
		assertTrue(t.isReached(a))
	}

	@Test
	fun neverRePicked() {
		val t = ReachedTargets()
		t.pick(listOf(a), a, null)
		assertNull(t.pick(listOf(a), far, null), "walking away does not pick it again")

		assertNull(t.pick(listOf(Vec3(100.9, 65.2, 201.1)), far, null))

		assertNull(t.track(Vec3(104.0, 70.0, 205.0), far, null))
	}

	@Test
	fun differentHotspotStillTracked() {
		val t = ReachedTargets()
		t.pick(listOf(a, b), a, null)
		assertEquals(b, t.pick(listOf(a, b), far, null))
		assertEquals(b, t.track(b, far, null), "a guess at another hotspot is still followed")
	}

	@Test
	fun guessReachedHorizontally() {
		val t = ReachedTargets()
		val g = Vec3(120.0, 90.0, 120.0)
		assertEquals(g, t.track(g, far, null, ReachedTargets.GUESS_REACH))
		assertNull(t.track(g, Vec3(123.0, 64.0, 121.0), null, ReachedTargets.GUESS_REACH), "height of the guess is ignored")
		assertNull(t.track(g, far, null), "and it stays dropped")
	}

	@Test
	fun resetOnWorldChange() {
		val t = ReachedTargets()
		t.pick(listOf(a), a, null)
		t.reset()
		assertFalse(t.isReached(a))
		assertEquals(a, t.pick(listOf(a), far, null))
	}
}
