package dev.ashtonite365.apex.test.features.fishing

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.fishing.HotspotRadarMath

class HotspotRadarMathTest {
	@Test
	fun filtersAndChains() {
		assertTrue(HotspotRadarMath.isTrailParticle(true, 1, 0f))
		assertFalse(HotspotRadarMath.isTrailParticle(false, 1, 0f))
		assertFalse(HotspotRadarMath.isTrailParticle(true, 5, 0.1f))
		val o = Vec3(0.0, 64.0, 0.0)
		assertFalse(HotspotRadarMath.accept(emptyList(), Vec3(20.0, 64.0, 0.0), o), "stray particle far away")
		assertTrue(HotspotRadarMath.accept(emptyList(), Vec3(1.0, 65.0, 0.0), o))
		assertFalse(HotspotRadarMath.accept(listOf(Vec3(1.0, 65.0, 0.0)), Vec3(10.0, 65.0, 0.0), o))
	}

	@Test
	fun straightTrailPointsAlongTrail() {
		val pts = (0..8).map { Vec3(1.0 + it * 0.5, 65.0, 1.0 + it * 0.5) }
		assertNull(HotspotRadarMath.guess(pts.take(3)))
		val g = HotspotRadarMath.guess(pts)!!
		assertTrue(g.x > 20 && g.z > 20 && kotlin.math.abs(g.x - g.z) < 0.01 && kotlin.math.abs(g.y - 65.0) < 0.01, "$g")
	}

	@Test
	fun curvedTrailUsesEndTangentNotAverage() {

		val pts = (0..10).map { val t = it * 0.1; Vec3(4 * t, 65.0, 4 * t * t) }
		val g = HotspotRadarMath.guess(pts)!!
		val end = pts.last()
		val d = g.subtract(end).normalize()

		assertTrue(d.z > d.x, "guess bends with the trail: $d")
	}

	@Test
	fun cubicBezierDistanceFromPitch() {

		val pitch = 20.0
		val d = HotspotRadarMath.controlPointDistance(pitch)
		val p0 = Vec3(0.0, 65.0, 0.0)
		val look = Vec3(0.0, -kotlin.math.sin(Math.toRadians(pitch)), kotlin.math.cos(Math.toRadians(pitch)))
		val p1 = p0.add(look.scale(d))
		val p2 = Vec3(40.0, 70.0, 60.0)
		val p3 = Vec3(80.0, 62.0, 90.0)
		val n = 60
		fun b(t: Double): Vec3 {
			val u = 1 - t
			return p0.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(p3.scale(t * t * t))
		}
		val pts = (0 until 12).map { b(it.toDouble() / n) }
		val g = HotspotRadarMath.guess(pts, pitch)!!
		assertTrue(g.distanceTo(p3) < 1.0, "guess $g vs $p3")
	}
}
