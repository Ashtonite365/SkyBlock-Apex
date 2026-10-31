package dev.ashtonite365.apex.test.features.items

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.features.items.EtherwarpOverlay
import dev.ashtonite365.apex.features.items.EtherwarpOverlay.Verdict
import dev.ashtonite365.apex.util.render.WireBox

class EtherwarpLogicTest {
	@Test
	fun range() {
		assertEquals(57.0, EtherwarpOverlay.range(0))
		assertEquals(61.0, EtherwarpOverlay.range(4))
		assertEquals(61.0, EtherwarpOverlay.range(9))
	}

	@Test
	fun itemDetection() {
		assertEquals(60.0, EtherwarpOverlay.etherwarpRange("ASPECT_OF_THE_VOID", true, 3, true))
		assertNull(EtherwarpOverlay.etherwarpRange("ASPECT_OF_THE_VOID", true, 3, false))
		assertNull(EtherwarpOverlay.etherwarpRange("ASPECT_OF_THE_VOID", false, 0, true))
		assertEquals(57.0, EtherwarpOverlay.etherwarpRange("ETHERWARP_CONDUIT", false, 0, false))
	}

	@Test
	fun eachFailureReason() {
		assertEquals(Verdict.LANDS, EtherwarpOverlay.evaluate(false, 20.0, 57.0, true, true))
		assertEquals(Verdict.INTERACTABLE_IN_WAY, EtherwarpOverlay.evaluate(true, 3.0, 57.0, true, true))
		assertEquals(Verdict.OUT_OF_RANGE, EtherwarpOverlay.evaluate(false, 58.0, 57.0, true, true))
		assertEquals(Verdict.OUT_OF_RANGE, EtherwarpOverlay.evaluate(false, null, 57.0, true, true))
		assertEquals(Verdict.NO_ROOM, EtherwarpOverlay.evaluate(false, 10.0, 57.0, false, true))
		assertEquals(Verdict.NO_ROOM, EtherwarpOverlay.evaluate(false, 10.0, 57.0, true, false))
	}

	@Test
	fun ordering() {

		assertEquals(Verdict.INTERACTABLE_IN_WAY, EtherwarpOverlay.evaluate(true, null, 57.0, false, false))
		assertEquals(Verdict.OUT_OF_RANGE, EtherwarpOverlay.evaluate(false, 70.0, 57.0, false, false))
	}

	@Test
	fun raycastHitsFirstSolidAndDistance() {
		val solid = setOf(BlockPos(5, 64, 0), BlockPos(9, 64, 0))
		val hit = EtherwarpOverlay.raycast(Vec3(0.5, 64.5, 0.5), Vec3(1.0, 0.0, 0.0), 57.0) { it in solid }!!
		assertEquals(BlockPos(5, 64, 0), hit.first)
		assertEquals(4.5, hit.second, 1e-9)
		assertNull(EtherwarpOverlay.raycast(Vec3(0.5, 64.5, 0.5), Vec3(0.0, 1.0, 0.0), 57.0) { it in solid })
	}

	@Test
	fun wireBoxEdges() {
		val e = WireBox.edges(Vector3f(0f), Vector3f(1f))
		assertEquals(12, e.size)
		val corners = (0..7).map { Vector3f((it and 1).toFloat(), (it shr 1 and 1).toFloat(), (it shr 2 and 1).toFloat()) }.toSet()
		for (edge in e) {
			assertTrue(edge.from in corners && edge.to in corners)
			assertEquals(1f, edge.from.distance(edge.to), 1e-6f)
			val d = Vector3f(edge.to).sub(edge.from)
			assertEquals(d, edge.normal)
		}
		assertEquals(12, e.map { setOf(it.from, it.to) }.toSet().size)
	}

	@Test
	fun boundsIndependentOfDistance() {

		val a = WireBox.blockEdges(); val b = WireBox.blockEdges()
		assertEquals(a, b)
		val all = a.flatMap { listOf(it.from, it.to) }
		assertEquals(-WireBox.EPSILON, all.minOf { it.x }); assertEquals(1 + WireBox.EPSILON, all.maxOf { it.y })
	}
}
