/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, reused mesh staging memory.
 *
 * A region mesh is written on the worker into native vertex builders and copied to the GPU on the render thread.
 * Instead of allocating fresh native blocks for every mesh and freeing them after the upload, the builders of an
 * uploaded mesh go back to a small pool the worker takes them from again (kept at their grown size, so a re-mesh
 * never re-grows). [SurfacePool] is the pure, thread-safe pool; [SurfaceStaging] binds it to the game's builder.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class SurfacePool<T : Any>(private val max: Int, private val create: () -> T, private val dispose: (T) -> Unit) {
	private val idle = ConcurrentLinkedQueue<T>()
	private val idleCount = AtomicInteger()
	val created = AtomicInteger()
	val reused = AtomicInteger()

	fun acquire(): T {
		val t = idle.poll()
		if (t != null) { idleCount.decrementAndGet(); reused.incrementAndGet(); return t }
		created.incrementAndGet()
		return create()
	}

	fun release(t: T) {
		if (idleCount.incrementAndGet() > max) { idleCount.decrementAndGet(); dispose(t); return }
		idle.add(t)
	}

	val idleSize: Int get() = idleCount.get()

	fun clear() {
		while (true) { val t = idle.poll() ?: break; idleCount.decrementAndGet(); dispose(t) }
	}
}

object SurfaceStaging {

	private const val MAX_IDLE = 6
	private const val INITIAL = 1 shl 20

	private val pool = SurfacePool(MAX_IDLE, { ByteBufferBuilder(INITIAL) }, { runCatching { it.close() } })

	fun acquire(): ByteBufferBuilder = pool.acquire()

	fun release(b: ByteBufferBuilder, ok: Boolean = true) {
		if (!ok) { runCatching { b.close() }; return }
		try { b.clear() } catch (_: Throwable) { runCatching { b.close() }; return }
		pool.release(b)
	}

	fun clear() = pool.clear()

	val reused: Int get() = pool.reused.get()
	val created: Int get() = pool.created.get()
}
