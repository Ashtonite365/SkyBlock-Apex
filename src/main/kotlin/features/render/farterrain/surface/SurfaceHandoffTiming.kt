/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, WHEN a chunk passes between far terrain and the game (pure Kotlin, unit-tested).
 *
 * [SurfaceHandoff] says whether the game has a chunk at all; this file decides the order and timing, so the chunk on
 * screen always looks fully built and there is never a frame with nothing drawn:
 *
 *  * The game's freshly built chunk fades in from the fog colour (its "chunk fade-in" option) and a chunk it just
 *    re-meshed can be missing a section for a frame. Handing off on the first compiled frame showed that as a flash.
 *    So far terrain keeps the chunk until the game has built EVERY non-empty section, uploaded and drawable, then keeps
 *    it [HOLD_MS] longer (wall clock), and until the game's fade-in is complete. A rebuild or a section going missing
 *    during the wait restarts it. While far terrain holds a chunk, the game's own sections for it are held back (not
 *    drawn), so the two never overlap; see [SurfaceHandoffGeometry] for the pre-draw before the game drops a chunk.
 *  * Taking a chunk back is immediate: the frame the game no longer has it, far terrain draws its copy.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

class SurfaceCameraMotion {
	var x = 0.0
		private set
	var z = 0.0
		private set

	var vx = 0.0
		private set
	var vz = 0.0
		private set
	private var has = false
	private var anchorX = 0.0
	private var anchorZ = 0.0
	private var anchorMs = 0L
	private var unreliableUntil = Long.MIN_VALUE

	val speed: Double get() = Math.sqrt(vx * vx + vz * vz)

	fun reset() { has = false; vx = 0.0; vz = 0.0 }

	fun frame(cx: Double, cz: Double, nowMs: Long) {
		if (!has) {
			has = true
			x = cx; z = cz; anchorX = cx; anchorZ = cz; anchorMs = nowMs
			vx = 0.0; vz = 0.0
			unreliableUntil = maxOf(unreliableUntil, nowMs + JOIN_MS)
			return
		}
		val jx = cx - x
		val jz = cz - z
		x = cx; z = cz
		if (jx * jx + jz * jz > TELEPORT * TELEPORT) {
			vx = 0.0; vz = 0.0
			anchorX = cx; anchorZ = cz; anchorMs = nowMs
			unreliableUntil = maxOf(unreliableUntil, nowMs + TELEPORT_MS)
			return
		}
		val dt = nowMs - anchorMs
		if (dt < WINDOW_MS) return
		val nvx = (cx - anchorX) * 1000.0 / dt
		val nvz = (cz - anchorZ) * 1000.0 / dt
		anchorX = cx; anchorZ = cz; anchorMs = nowMs
		val oldSpeed = speed
		val newSpeed = Math.sqrt(nvx * nvx + nvz * nvz)
		if (oldSpeed >= SLOW && newSpeed >= SLOW && nvx * vx + nvz * vz < 0) unreliableUntil = maxOf(unreliableUntil, nowMs + ERRATIC_MS)
		vx = nvx; vz = nvz
	}

	fun reliable(nowMs: Long): Boolean = has && nowMs >= unreliableUntil && speed >= SLOW

	companion object {
		const val WINDOW_MS = 50L

		const val SLOW = 2.0
		const val TELEPORT = 8.0
		const val TELEPORT_MS = 1000L
		const val ERRATIC_MS = 500L
		const val JOIN_MS = 1000L
	}
}

object SurfaceHandoffGeometry {
	fun chunkOf(coord: Double): Int = Math.floor(coord).toInt() shr 4

	fun nearestSq(cx: Int, cz: Int, camX: Double, camZ: Double): Double {
		val x0 = cx * 16.0; val z0 = cz * 16.0
		val dx = if (camX < x0) x0 - camX else if (camX > x0 + 16) camX - x0 - 16 else 0.0
		val dz = if (camZ < z0) z0 - camZ else if (camZ > z0 + 16) camZ - z0 - 16 else 0.0
		return dx * dx + dz * dz
	}

	fun limit(radius: Int, fogEnd: Double = Double.POSITIVE_INFINITY): Double = minOf(radius * 16.0, fogEnd)

	fun gameOwns(cx: Int, cz: Int, camX: Double, camZ: Double, radius: Int, limit: Double = radius * 16.0): Boolean =
		SurfaceHandoff.inDisc(chunkOf(camX), chunkOf(camZ), radius, cx, cz) && nearestSq(cx, cz, camX, camZ) < limit * limit

	fun sectionOf(y: Double): Int = Math.floor(y).toInt() shr 4

	fun verticalOwns(minY: Int, maxY: Int, camY: Double, radius: Int, marginSections: Int = 0, limit: Double = radius * 16.0): Boolean {
		if (maxY <= minY) return true
		val r = radius - marginSections
		if (r <= 0) return false
		val camSec = sectionOf(camY)
		val lo = minY shr 4
		val hi = (maxY - 1) shr 4
		if (camSec - lo > r || hi - camSec > r) return false
		val lim = minOf(r * 16.0, limit - marginSections * 16.0)

		val below = camY - (lo * 16 + 16)
		val above = hi * 16 - camY
		return below < lim && above < lim
	}

	fun verticalEdge(minY: Int, maxY: Int, camY: Double, radius: Int, limit: Double = radius * 16.0): Boolean =
		!verticalOwns(minY, maxY, camY, radius, VERTICAL_MARGIN, limit)

	const val VERTICAL_MARGIN = 1

	fun outerRings(cx: Int, cz: Int, camX: Double, camZ: Double, radius: Int, limit: Double = radius * 16.0): Boolean {
		if (!SurfaceHandoff.inDisc(chunkOf(camX), chunkOf(camZ), radius - RINGS, cx, cz)) return true
		val r = minOf((radius - RINGS) * 16.0, limit - RINGS * 16.0)
		return r <= 0 || nearestSq(cx, cz, camX, camZ) >= r * r
	}

	const val RINGS = 2

	const val LEAD_MS = 500L
	const val MAX_AHEAD = 16.0

	class Prediction(val camX: Double, val camZ: Double, vx: Double, vz: Double) {
		val xs = DoubleArray(4 + 8)
		val zs = DoubleArray(4 + 8)
		var n = 0
			private set

		init {

			for (dx in -1..1) for (dz in -1..1) if (dx != 0 || dz != 0) {
				xs[n] = camX + dx * JITTER; zs[n] = camZ + dz * JITTER; n++
			}
			val speed = Math.sqrt(vx * vx + vz * vz)
			if (speed > 0) {
				val ahead = minOf(speed * LEAD_MS / 1000.0, MAX_AHEAD)
				val t1 = ahead / speed
				val ts = DoubleArray(3); var m = 0
				fun crossing(p: Double, v: Double) {
					if (v == 0.0) return
					val lower = Math.floor(p / 16.0) * 16.0
					val t = ((if (v > 0) lower + 16.0 else lower) - p) / v
					if (t in 0.0..t1) ts[m++] = t
				}
				crossing(camX, vx)
				crossing(camZ, vz)
				ts[m++] = t1
				for (i in 0 until m) {
					val t = ts[i] + EPS / speed
					xs[n] = camX + vx * t; zs[n] = camZ + vz * t; n++
				}
			}
		}

		fun drops(cx: Int, cz: Int, radius: Int, limit: Double = radius * 16.0): Boolean {
			if (!outerRings(cx, cz, camX, camZ, radius, limit)) return false
			for (i in 0 until n) if (!gameOwns(cx, cz, xs[i], zs[i], radius, limit)) return true
			return false
		}
	}

	private const val EPS = 1e-3

	const val JITTER = 2.0
}

class SurfaceBuiltWait(private val lowerWaitMs: Long = LOWER_WAIT_MS) {
	private val since = it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap().apply { defaultReturnValue(Long.MIN_VALUE) }

	fun built(key: Long, now: Long, topBuilt: Boolean, allBuilt: Boolean): Boolean {
		if (!topBuilt) { since.remove(key); return false }
		if (allBuilt) return true
		var t = since.get(key)
		if (t == Long.MIN_VALUE) { t = now; since.put(key, now) }
		return now - t >= lowerWaitMs
	}

	fun release(key: Long) { since.remove(key) }

	fun prune(keep: (Long) -> Boolean) {
		val it = since.keys.iterator()
		while (it.hasNext()) if (!keep(it.nextLong())) it.remove()
	}

	fun clear() = since.clear()

	val size: Int get() = since.size

	companion object {

		const val LOWER_WAIT_MS = 100L
	}
}

class SurfaceHandoffTimer(private val holdMs: Long = HOLD_MS) {
	private class Entry(var since: Long, var signature: Long, var shown: Boolean, var seen: Long)

	private val entries = Long2ObjectOpenHashMap<Entry>()

	fun update(key: Long, now: Long, built: Boolean, signature: Long, fadeDone: Boolean): Boolean {
		val e = entries.get(key)

		if (e != null && e.shown) { e.seen = now; e.signature = signature; return true }
		if (!built) { entries.remove(key); return false }
		if (e == null) {
			entries.put(key, Entry(now, signature, holdMs <= 0 && fadeDone, now))
			return holdMs <= 0 && fadeDone
		}
		e.seen = now
		if (e.signature != signature) { e.signature = signature; e.since = now }
		if (now - e.since >= holdMs && fadeDone) e.shown = true
		return e.shown
	}

	fun release(key: Long) { entries.remove(key) }

	fun isShown(key: Long): Boolean = entries.get(key)?.shown == true

	fun prune(keep: (Long) -> Boolean) {
		val it = entries.long2ObjectEntrySet().fastIterator()
		while (it.hasNext()) if (!keep(it.next().longKey)) it.remove()
	}

	fun clear() = entries.clear()

	val size: Int get() = entries.size

	companion object {

		const val HOLD_MS = 100L
	}
}

object SurfaceHandoffOrder {
	const val APEX = 1
	const val GAME = 2
	const val BOTH = APEX or GAME

	fun decide(apexHas: Boolean, shown: Boolean, preDraw: Boolean): Int {
		if (!apexHas) return GAME
		if (!shown) return APEX
		if (preDraw) return BOTH
		return GAME
	}
}

class SurfaceHandoffGrid(val ccx: Int, val ccz: Int, extent: Int) {
	val radius = maxOf(0, extent) + MARGIN
	private val size = 2 * radius + 1
	private val cells = ByteArray(size * size)
	private val held = BooleanArray(size * size)

	fun inside(x: Int, z: Int): Boolean = Math.abs(x - ccx) <= radius && Math.abs(z - ccz) <= radius

	private fun index(x: Int, z: Int) = (x - ccx + radius) * size + (z - ccz + radius)

	fun get(x: Int, z: Int): Int = cells[index(x, z)].toInt()

	fun put(x: Int, z: Int, d: Int) { cells[index(x, z)] = d.toByte() }

	fun markHeld(x: Int, z: Int): Boolean {
		val i = index(x, z)
		if (held[i]) return false
		held[i] = true
		return true
	}

	companion object {

		const val MARGIN = 1

		const val OUTSIDE = SurfaceHandoffOrder.BOTH
	}
}
