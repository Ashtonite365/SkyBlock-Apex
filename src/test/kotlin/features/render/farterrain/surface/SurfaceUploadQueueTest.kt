// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfacePool
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceUploadQueue
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceUploadQueue.Budget
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceUploadQueue.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceUploadQueueTest {
	private class Mesh(val id: String) { var closed = false }

	private class Harness(val budget: Budget = Budget()) {
		val q = SurfaceUploadQueue<Mesh> { it.closed = true }
		val resident = HashSet<Long>()
		val uploaded = ArrayList<Pair<String, Kind>>()
		val removed = ArrayList<Long>()
		var now = 0L

		var uploadNanos = 0L
		var frame = 0L

		fun offer(rx: Int, rz: Int, id: String = "$rx,$rz", bytes: Long = 1L shl 20, gen: Int = 0): Mesh {
			val m = Mesh(id)
			q.offer(SurfaceIndex.key(rx, rz), rx, rz, m, gen, bytes, frame)
			return m
		}

		fun drain(ccx: Int = 0, ccz: Int = 0, gen: Int = 0): List<String> {
			val before = uploaded.size
			q.drain(frame++, gen, ccx, ccz, budget, { it in resident }, { now }, { removed.add(it); resident.remove(it) }) { e, kind ->
				uploaded.add(e.mesh!!.id to kind)
				e.mesh!!.closed = true
				resident.add(e.key)
				now += uploadNanos
				e.bytes
			}
			return uploaded.subList(before, uploaded.size).map { it.first }
		}
	}

	@Test
	fun byteBudgetSpreadsUploadsAcrossFrames() {
		val h = Harness(Budget(maxBytes = 4L shl 20, maxCount = 100))
		for (i in 1..10) h.offer(i * 4, 0)
		assertEquals(4, h.drain().size)
		assertEquals(4, h.drain().size)
		assertEquals(2, h.drain().size)
		assertEquals(0, h.q.size)
	}

	@Test
	fun timeBudgetStopsTheFrame() {
		val h = Harness(Budget(maxBytes = Long.MAX_VALUE, maxNanos = 1_000_000L, maxCount = 100))
		h.uploadNanos = 400_000L
		for (i in 1..10) h.offer(i * 4, 0)
		assertEquals(3, h.drain().size)
	}

	@Test
	fun countBudgetAndAlwaysAtLeastOne() {
		val h = Harness(Budget(maxBytes = 1, maxCount = 3))
		repeat(5) { h.offer(10 + it * 4, 0, bytes = 64L shl 20) }
		assertEquals(1, h.drain().size)
		val h2 = Harness(Budget(maxBytes = Long.MAX_VALUE, maxCount = 3))
		repeat(5) { h2.offer(10 + it * 4, 0) }
		assertEquals(3, h2.drain().size)
	}

	@Test
	fun nearestFirstAndNewRegionsBeforeReplacements() {
		val h = Harness(Budget(maxCount = 1))
		h.offer(9, 0); h.offer(3, 0); h.offer(6, 0)
		assertEquals(listOf("3,0"), h.drain())
		assertEquals(listOf("6,0"), h.drain())

		h.resident.add(SurfaceIndex.key(2, 0))
		h.offer(2, 0); h.offer(7, 0)
		assertEquals(listOf("7,0"), h.drain())
		assertEquals(listOf("9,0"), h.drain())
		assertEquals(listOf("2,0"), h.drain())
	}

	@Test
	fun handoffAreaIsUrgentAndBypassesTheBudget() {

		val h = Harness(Budget(maxBytes = 1, maxCount = 1, urgentMax = 8, urgentReachChunks = 14))

		for ((x, z) in listOf(-2 to 0, 1 to 0, 0 to 0, -1 to 0, 0 to -1, -1 to -1)) h.offer(x, z, bytes = 8L shl 20)
		h.offer(-10, -10, bytes = 8L shl 20)
		val first = h.drain()
		assertEquals(6, h.uploaded.count { it.second == Kind.URGENT })
		assertTrue(first.take(6).all { it != "-10,-10" })

		assertEquals(setOf("0,0", "-1,0", "0,-1", "-1,-1"), first.take(4).toSet())

		assertEquals(7, first.size)
		assertEquals(Kind.BUDGET, h.uploaded.last().second)
	}

	@Test
	fun urgentIsCappedPerFrame() {
		val h = Harness(Budget(maxBytes = 1, maxCount = 1, urgentMax = 2, urgentReachChunks = 40))
		for (r in 0 until 5) h.offer(r, 0)
		assertEquals(3, h.drain().size)
		assertEquals(2, h.drain().size)
	}

	@Test
	fun noStarvation() {

		val budget = Budget(maxCount = 1, ageFrames = 30)
		val h = Harness(budget)
		h.offer(50, 50, id = "far")
		var farAt = -1
		for (f in 0 until 200) {

			h.offer(-f - 1, 0, id = "near$f")
			h.offer(-f - 1, 1, id = "near2-$f")
			if ("far" in h.drain()) { farAt = f; break }
		}

		assertTrue(farAt in budget.ageFrames - 1..budget.ageFrames + 8, "far region uploaded at frame $farAt")
	}

	@Test
	fun boundedWaitForEveryMeshUnderLoad() {
		val h = Harness(Budget(maxCount = 2, ageFrames = 20))
		val offeredAt = HashMap<String, Long>()
		var maxWait = 0L
		var k = 0
		for (f in 0 until 400) {

			repeat(2) { val id = "m${k++}"; offeredAt[id] = h.frame; h.offer((k * 37) % 40 - 20, (k * 11) % 40 - 20, id = id) }
			for (id in h.drain()) maxWait = maxOf(maxWait, h.frame - 1 - offeredAt.getValue(id))
		}
		assertTrue(h.q.size <= 40, "queue ${h.q.size}")
		assertTrue(maxWait <= 20 + 40, "max wait $maxWait frames")
	}

	@Test
	fun newerMeshReplacesAWaitingOneAndClosesIt() {
		val h = Harness(Budget(maxCount = 1))
		val a = h.offer(5, 5, id = "a")
		val b = h.offer(5, 5, id = "b")
		assertTrue(a.closed)
		assertEquals(1, h.q.size)
		assertEquals(listOf("b"), h.drain())
		assertTrue(b.closed)
	}

	@Test
	fun removalsAndGenerations() {
		val h = Harness()
		h.resident.add(SurfaceIndex.key(1, 1))
		val m = h.offer(2, 2)
		h.q.offer(SurfaceIndex.key(2, 2), 2, 2, null, 0, 0, 0)
		h.q.offer(SurfaceIndex.key(1, 1), 1, 1, null, 0, 0, 0)
		h.q.offer(SurfaceIndex.key(3, 3), 3, 3, null, 1, 0, 0)
		assertTrue(m.closed)
		assertEquals(emptyList<String>(), h.drain())
		assertEquals(listOf(SurfaceIndex.key(2, 2), SurfaceIndex.key(1, 1)), h.removed)

		h.q.offer(SurfaceIndex.key(4, 4), 4, 4, null, 0, 0, 0)
		h.offer(4, 4)
		h.removed.clear()
		assertEquals(listOf("4,4"), h.drain())
		assertTrue(h.removed.isEmpty())

		val old = h.offer(6, 6, gen = 3)
		assertEquals(emptyList<String>(), h.drain(gen = 4))
		assertTrue(old.closed)
		val left = h.offer(7, 7)
		h.q.closeAll()
		assertTrue(left.closed)
		assertEquals(0, h.q.size)
	}

	@Test
	fun poolReusesUpToItsLimit() {
		var made = 0
		val disposed = ArrayList<Int>()
		val pool = SurfacePool(2, { ++made }, { disposed.add(it) })
		val a = pool.acquire(); val b = pool.acquire(); val c = pool.acquire()
		assertEquals(3, made)
		pool.release(a); pool.release(b); pool.release(c)
		assertEquals(listOf(3), disposed)
		assertEquals(2, pool.idleSize)
		pool.acquire(); pool.acquire()
		assertEquals(3, made)
		assertEquals(2, pool.reused.get())
		pool.release(1); pool.clear()
		assertEquals(0, pool.idleSize)
		assertEquals(listOf(3, 1), disposed)
	}
}
