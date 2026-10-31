// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.connected.FarConnect
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceMesher
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRemeshAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceRemeshAllTest {
	@Test fun `only a change of the settings triggers a re-mesh, never the first look`() {
		val r = SurfaceRemeshAll()
		assertFalse(r.changed(0))
		assertFalse(r.changed(0))
		assertTrue(r.changed(7))
		assertFalse(r.changed(7))
		assertTrue(r.changed(0))
	}

	@Test fun `regions go to the worker a few at a time and only while its queue is short`() {
		val r = SurfaceRemeshAll(maxQueued = 2, perTick = 1)
		r.replace((0L until 50L).toList())
		var ticks = 0
		var queued = 0
		val got = ArrayList<Long>()
		while (r.remaining > 0) {
			ticks++
			val now = r.take(queued)
			assertTrue(now.size <= 1, "more than one region in one tick")
			got.addAll(now.toList())
			queued += now.size
			assertTrue(queued <= 2, "worker queue grew past the limit")
			if (ticks % 3 == 0 && queued > 0) queued--
		}
		assertEquals((0L until 50L).toList(), got, "every region exactly once, in order")
		assertTrue(ticks >= 50)
		assertEquals(0, r.take(0).size)

		r.replace(listOf(1L, 2L)); r.replace(listOf(3L))
		assertEquals(listOf(3L), r.take(0).toList())
	}

	@Test fun `regions are listed once each, nearest to the player first`() {
		val chunks = listOf(SurfaceIndex.key(100, 100), SurfaceIndex.key(1, 1), SurfaceIndex.key(2, 3), SurfaceIndex.key(-9, 0), SurfaceIndex.key(40, -40))
		val regions = SurfaceRemeshAll.regionsNearestFirst(chunks, 0, 0)
		assertEquals(4, regions.size)
		assertEquals(SurfaceIndex.key(0, 0), regions[0])
		assertEquals(SurfaceIndex.key(-2, 0), regions[1])
		assertEquals(SurfaceIndex.key(12, 12), regions.last())
	}

	@Test fun `far terrain connected textures never touch another renderer`() {
		for (c in listOf(FarConnect::class.java, SurfaceMesher::class.java, SurfaceRemeshAll::class.java)) {
			val bytes = c.getResourceAsStream(c.simpleName + ".class")!!.use { it.readBytes() }
			val text = String(bytes, Charsets.ISO_8859_1).lowercase()
			assertFalse("sodium" in text || "caffeinemc" in text, "${c.simpleName} refers to another renderer")
		}
	}
}
