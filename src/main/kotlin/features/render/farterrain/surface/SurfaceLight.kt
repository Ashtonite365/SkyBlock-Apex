/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, baked light levels for far terrain (pure Kotlin, unit-tested).
 *
 * The far-terrain mesh stores vanilla light coordinates per vertex (sky level and block level, 0..15) and the game's
 * live lightmap turns them into colour every frame, exactly like its own terrain: night, sunrise, rain, lightning,
 * night vision and the gamma setting all follow without re-meshing. This file estimates the two levels from stored
 * surface data alone:
 *
 *  * Sky light: 15 straight under open sky; minus the dampening of every see-through block above (leaves, water: 1
 *    each); 0 under anything opaque or hidden ground. Positions under a cover borrow from the nearest open column on
 *    the same height, losing one level per block (an overhang's underside, a doorway), like vanilla's spread.
 *  * Block light: a flood fill from every stored emitter (torch, lantern, glowstone, lava...) through air and
 *    see-through blocks, losing at least one level per block, stopped by opaque blocks and hidden ground. Emitters the
 *    surface store does not hold (buried, inside a sealed room) do not count.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList

interface LightProps {

	fun emission(state: String): Int

	fun dampening(state: String): Int
}

class SurfaceLight(
	private val world: SurfaceWorld,
	private val props: LightProps,

	private val minX: Int, private val minZ: Int, private val maxX: Int, private val maxZ: Int,
) {
	private val direct = Long2ByteOpenHashMap().apply { defaultReturnValue(-1) }
	private val skyCache = Long2ByteOpenHashMap().apply { defaultReturnValue(-1) }
	private val blockField: Long2ByteOpenHashMap by lazy { floodBlockLight() }
	private val dampCache = HashMap<String, Int>()
	private val emitCache = HashMap<String, Int>()

	private fun damp(state: String): Int = dampCache.getOrPut(state) { props.dampening(state).coerceIn(0, 15) }
	private fun emit(state: String): Int = emitCache.getOrPut(state) { props.emission(state).coerceIn(0, 15) }

	private fun at(x: Int, y: Int, z: Int): String? {
		val c = world.chunk(x shr 4, z shr 4) ?: return null
		val i = c.stateIndex(x and 15, y, z and 15)
		return when (i) {
			SurfaceChunk.BELOW -> HIDDEN
			0 -> null
			else -> c.palette[i]
		}
	}

	private fun blocksLight(x: Int, y: Int, z: Int): Boolean {
		val s = at(x, y, z) ?: return false
		return s === HIDDEN || damp(s) >= 15
	}

	fun directSky(x: Int, y: Int, z: Int): Int {
		val k = pack(x, y, z)
		val cached = direct.get(k)
		if (cached >= 0) return cached.toInt()
		val v = computeDirect(x, y, z)
		direct.put(k, v.toByte())
		return v
	}

	private fun computeDirect(x: Int, y: Int, z: Int): Int {
		val c = world.chunk(x shr 4, z shr 4) ?: return 15
		val lx = x and 15
		val lz = z and 15
		val self = c.stateIndex(lx, y, lz)
		if (self == SurfaceChunk.BELOW) return 0
		if (self != 0 && damp(c.palette[self]) >= 15) return 0
		val col = SurfaceIndex.col(lx, lz)
		val t = c.top[col]
		if (t == SurfaceChunk.NO_COLUMN || y >= t) return 15
		var light = 15
		var yy = t
		while (yy > y) {
			val i = c.stateIndex(lx, yy, lz)
			if (i == SurfaceChunk.BELOW) return 0
			if (i != 0) {
				light -= damp(c.palette[i])
				if (light <= 0) return 0
			}
			yy--
		}
		return light
	}

	fun sky(x: Int, y: Int, z: Int): Int {
		val k = pack(x, y, z)
		val cached = skyCache.get(k)
		if (cached >= 0) return cached.toInt()
		val v = computeSky(x, y, z)
		skyCache.put(k, v.toByte())
		return v
	}

	private fun computeSky(x: Int, y: Int, z: Int): Int {
		if (blocksLight(x, y, z)) return 0
		var best = directSky(x, y, z)

		var r = 1
		while (r <= MAX_SPREAD && 15 - r > best) {
			for (i in 0 until r) {

				best = maxOf(best, directSky(x + r - i, y, z + i) - r, directSky(x - i, y, z + r - i) - r,
					directSky(x - r + i, y, z - i) - r, directSky(x + i, y, z - r + i) - r)
			}
			r++
		}
		return best.coerceIn(0, 15)
	}

	fun canSeeSky(x: Int, y: Int, z: Int): Boolean = directSky(x, y, z) >= 15

	fun block(x: Int, y: Int, z: Int): Int = blockField.get(pack(x, y, z)).toInt().coerceAtLeast(0)

	private fun floodBlockLight(): Long2ByteOpenHashMap {
		val field = Long2ByteOpenHashMap().apply { defaultReturnValue(0) }

		val buckets = Array(16) { LongArrayList() }
		val m = 15
		for (cx in ((minX - m) shr 4)..((maxX + m) shr 4)) for (cz in ((minZ - m) shr 4)..((maxZ + m) shr 4)) {
			val c = world.chunk(cx, cz) ?: continue
			forEachStored(c) { x, y, z, state ->
				val e = emit(state)
				if (e > 0 && x >= minX - m && x <= maxX + m && z >= minZ - m && z <= maxZ + m) {
					val k = pack(x, y, z)
					if (field.get(k) < e) { field.put(k, e.toByte()); buckets[e].add(k) }
				}
			}
		}
		var visited = 0
		for (level in 15 downTo 2) {
			val list = buckets[level]
			var i = 0
			while (i < list.size) {
				val k = list.getLong(i++)
				if (field.get(k) != level.toByte()) continue
				val x = unpackX(k); val y = unpackY(k); val z = unpackZ(k)
				for (d in 0 until 6) {
					val nx = x + DX[d]; val ny = y + DY[d]; val nz = z + DZ[d]
					if (nx < minX - m || nx > maxX + m || nz < minZ - m || nz > maxZ + m) continue
					val s = at(nx, ny, nz)
					val loss = when {
						s == null -> 1
						s === HIDDEN -> continue
						else -> maxOf(1, damp(s))
					}
					if (loss >= 15) continue
					val v = level - loss
					if (v <= 0) continue
					val nk = pack(nx, ny, nz)
					if (field.get(nk) >= v) continue
					field.put(nk, v.toByte())
					buckets[v].add(nk)
					if (++visited > MAX_LIT_CELLS) return field
				}
			}
		}
		return field
	}

	companion object {

		const val MAX_SPREAD = 14

		const val MAX_LIT_CELLS = 4_000_000

		private const val HIDDEN = "\u0000hidden"
		private val DX = intArrayOf(1, -1, 0, 0, 0, 0)
		private val DY = intArrayOf(0, 0, 1, -1, 0, 0)
		private val DZ = intArrayOf(0, 0, 0, 0, 1, -1)

		fun pack(x: Int, y: Int, z: Int): Long =
			((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

		fun unpackX(k: Long): Int = (k shr 38).toInt()
		fun unpackZ(k: Long): Int = ((k shl 26) shr 38).toInt()
		fun unpackY(k: Long): Int = ((k shl 52) shr 52).toInt()

		inline fun forEachStored(c: SurfaceChunk, out: (x: Int, y: Int, z: Int, state: String) -> Unit) {
			val bx = c.x shl 4
			val bz = c.z shl 4
			for (col in 0 until 256) {
				if (c.isEmpty(col)) continue
				val wx = bx + SurfaceIndex.colX(col)
				val wz = bz + SurfaceIndex.colZ(col)
				c.forEachInColumn(col) { y, p -> if (p != 0) out(wx, y, wz, c.palette[p]) }
				val u = c.underside(col)
				if (u != SurfaceChunk.NO_COLUMN) out(wx, u, wz, c.palette[c.underState!![col]])
			}
		}
	}
}
