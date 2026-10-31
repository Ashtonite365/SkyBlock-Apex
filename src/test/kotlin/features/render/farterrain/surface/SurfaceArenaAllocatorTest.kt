// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceArenaAllocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SurfaceArenaAllocatorTest {
	@Test
	fun bestFitSplitAndMerge() {
		val a = SurfaceArenaAllocator(100)
		assertEquals(0, a.alloc(10))
		assertEquals(10, a.alloc(20))
		assertEquals(30, a.alloc(10))
		a.free(10, 20)
		assertEquals(2, a.freeBlocks)
		assertEquals(10, a.alloc(15), "best fit: the 20-hole, not the 60-tail")
		a.free(0, 10)
		a.free(30, 10)
		a.free(10, 15)
		assertEquals(1, a.freeBlocks, "every free neighbour merged")
		assertEquals(0, a.used)
		assertEquals(100, a.largestFree())
	}

	@Test
	fun doubleFreeAndOutOfRangeThrow() {
		val a = SurfaceArenaAllocator(64)
		val x = a.alloc(8)
		a.free(x, 8)
		assertThrows(IllegalStateException::class.java) { a.free(x, 8) }
		assertThrows(IllegalArgumentException::class.java) { a.free(60, 8) }
		val y = a.alloc(16)
		assertThrows(IllegalStateException::class.java) { a.free(y + 4, 16) }
	}

	@Test
	fun growExtendsTheFreeTailAndHighWater() {
		val a = SurfaceArenaAllocator(0)
		assertEquals(-1, a.alloc(1))
		a.grow(10)
		assertEquals(0, a.alloc(6))
		assertEquals(6, a.highWater())
		assertEquals(-1, a.alloc(5))
		a.grow(20)
		assertEquals(1, a.freeBlocks, "new space merged with the free tail")
		assertEquals(6, a.alloc(14))
		assertEquals(20, a.highWater())
		a.free(6, 14)
		assertEquals(6, a.highWater())
		a.clear()
		assertEquals(0, a.highWater()); assertEquals(20, a.largestFree())
	}

	@Test
	fun randomOpsMatchABitmapModel() {
		val rnd = Random(1027)
		val cap = 4096
		val a = SurfaceArenaAllocator(cap)
		val used = BooleanArray(cap)
		val live = ArrayList<IntArray>()
		repeat(40_000) {
			if (live.isEmpty() || rnd.nextInt(100) < 55) {
				val size = 1 + rnd.nextInt(if (rnd.nextInt(10) == 0) 300 else 40)
				val at = a.alloc(size)

				var run = 0; var any = false
				for (u in used) { run = if (u) 0 else run + 1; if (run >= size) { any = true; break } }
				assertEquals(any, at >= 0, "alloc($size) must succeed exactly when a hole fits")
				if (at >= 0) {
					for (i in at until at + size) { assertTrue(!used[i], "overlap at $i"); used[i] = true }
					live.add(intArrayOf(at, size))
				}
			} else {
				val r = live.removeAt(rnd.nextInt(live.size))
				a.free(r[0], r[1])
				for (i in r[0] until r[0] + r[1]) used[i] = false
			}
			assertEquals(used.count { it }, a.used)
			assertEquals(live.size, a.liveRanges)
		}

		var runs = 0; var prev = true
		for (u in used) { if (!u && prev) runs++; prev = u }
		assertEquals(runs, a.freeBlocks)
	}

	@Test
	fun speedCheck() {

		fun run(): Long {
			val rnd = Random(7)
			val a = SurfaceArenaAllocator(0)
			a.grow(64 shl 20)
			val start = IntArray(400); val size = IntArray(400)
			for (i in 0 until 400) { size[i] = 2_000 + rnd.nextInt(58_000); start[i] = a.alloc(size[i]) }
			val t0 = System.nanoTime()
			repeat(100_000) {
				val i = rnd.nextInt(400)
				a.free(start[i], size[i])
				size[i] = 2_000 + rnd.nextInt(58_000)
				start[i] = a.alloc(size[i])
				check(start[i] >= 0)
			}
			return System.nanoTime() - t0
		}
		run()
		val ns = (0 until 3).minOf { run() }
		val perOp = ns / 200_000.0
		println("[apex-bench] shared-buffer allocator: 100k free+alloc pairs, 400 live regions: %.1f ms total, %.0f ns per op".format(ns / 1e6, perOp))
		assertTrue(perOp < 20_000, "allocator too slow: $perOp ns/op")
	}
}
