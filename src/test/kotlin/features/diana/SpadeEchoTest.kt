
package dev.ashtonite365.apex.test.features.diana

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.diana.SpadeEcho

class SpadeEchoTest {
	private fun echo(vararg pitches: Float, trail: List<Vec3> = listOf(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0))) =
		SpadeEcho().apply { trail.forEach(::particle); pitches.forEach(::note) }

	@Test
	fun needsThreeTrailParticles() {
		assertNull(echo(0.5f, 0.6f, trail = listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0))).estimate(Vec3.ZERO))
	}

	@Test
	fun distanceIsEOverMeanStepMinusTrailStart() {
		val e = echo(0.5f, 0.6f, 0.7f)
		val step = ((0.6f - 0.5f).toDouble() + (0.7f - 0.6f).toDouble()) / 2
		val guess = e.estimate(Vec3(2.0, 0.0, 0.0))!!
		assertEquals(2.0 + Math.E / step - 2.0, guess.x, 1e-9)
		assertEquals(0.0, guess.y, 1e-9)
		assertEquals(0.0, guess.z, 1e-9)
	}

	@Test
	fun headingUsesTheLastThreeParticles() {
		val trail = listOf(Vec3(5.0, 5.0, 5.0), Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, 2.0))
		val guess = echo(1.0f, 1.5f, trail = trail).estimate(Vec3(0.0, 0.0, 2.0))!!
		val distance = Math.E / 0.5 - Vec3(5.0, 5.0, 5.0).distanceTo(Vec3(0.0, 0.0, 2.0))
		assertEquals(0.0, guess.x, 1e-9)
		assertEquals(2.0 + distance, guess.z, 1e-9)
	}

	@Test
	fun farEstimatesAreDropped() {
		assertNull(echo(0.5f, 0.501f).estimate(Vec3.ZERO))
	}

	@Test
	fun oneNoteGivesNoUsableStep() {
		val guess = echo(0.5f).estimate(Vec3.ZERO)!!
		assertTrue(guess.x.isNaN())
	}

	@Test
	fun keepsTheNewestTwenty() {
		val e = SpadeEcho()
		repeat(25) { e.particle(Vec3(it.toDouble(), 0.0, 0.0)) }
		assertEquals(20, e.trail.size)
		assertEquals(5.0, e.trail.first().x)
		e.clear()
		assertEquals(0, e.trail.size)
	}
}
