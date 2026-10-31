/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, face culling over stored surface data (pure Kotlin, unit-tested).
 * See LOD-DESIGN.md, section 1.2.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

fun interface SurfaceWorld {
	fun chunk(cx: Int, cz: Int): SurfaceChunk?
}

fun interface FaceShapes {
	fun covers(self: String, neighbour: String, dir: Int): Boolean

	companion object {

		fun byClass(classifier: BlockClassifier): FaceShapes = FaceShapes { self, n, _ ->
			when (classifier.classify(n)) {
				BlockClass.OPAQUE -> true
				BlockClass.TRANSPARENT -> n == self
				BlockClass.AIR -> false
			}
		}
	}
}

object SurfaceFaces {
	const val UP = 1
	const val DOWN = 2
	const val NORTH = 4
	const val SOUTH = 8
	const val WEST = 16
	const val EAST = 32

	val DIRECTIONS = intArrayOf(UP, DOWN, NORTH, SOUTH, WEST, EAST)

	fun dx(dir: Int): Int = when (dir) { WEST -> -1; EAST -> 1; else -> 0 }
	fun dy(dir: Int): Int = when (dir) { UP -> 1; DOWN -> -1; else -> 0 }
	fun dz(dir: Int): Int = when (dir) { NORTH -> -1; SOUTH -> 1; else -> 0 }

	fun opposite(dir: Int): Int = when (dir) {
		UP -> DOWN; DOWN -> UP; NORTH -> SOUTH; SOUTH -> NORTH; WEST -> EAST; EAST -> WEST
		else -> throw IllegalArgumentException("direction $dir")
	}

	sealed interface Probe {

		data object Hidden : Probe

		data object Unknown : Probe
		data object Air : Probe
		data class Block(val state: String) : Probe
	}

	fun probe(world: SurfaceWorld, wx: Int, y: Int, wz: Int): Probe {
		val c = world.chunk(wx shr 4, wz shr 4) ?: return Probe.Unknown
		val i = c.stateIndex(wx and 15, y, wz and 15)
		return when (i) {
			SurfaceChunk.BELOW -> Probe.Hidden
			0 -> Probe.Air
			else -> Probe.Block(c.palette[i])
		}
	}

	fun isBottomSlab(state: String): Boolean {
		val b = state.indexOf('[')
		if (b < 0 || !state.substring(0, b).endsWith("_slab")) return false
		return state.indexOf("type=bottom", b) > 0
	}

	fun quadVisible(
		world: SurfaceWorld, classifier: BlockClassifier, shapes: FaceShapes,
		self: String, x: Int, y: Int, z: Int, dir: Int, onFace: Boolean = true,
	): Boolean {
		val n = probe(world, x + dx(dir), y + dy(dir), z + dz(dir))
		if (dir == UP && isBottomSlab(self)) {
			if (n == Probe.Hidden || (n is Probe.Block && classifier.classify(n.state) == BlockClass.OPAQUE)) return false
		}
		if (!onFace) return true
		return when (n) {
			Probe.Hidden -> false
			Probe.Air, Probe.Unknown -> true
			is Probe.Block -> !shapes.covers(self, n.state, dir)
		}
	}

	fun mask(world: SurfaceWorld, classifier: BlockClassifier, wx: Int, y: Int, wz: Int): Int =
		mask(world, classifier, FaceShapes.byClass(classifier), wx, y, wz)

	fun mask(world: SurfaceWorld, classifier: BlockClassifier, shapes: FaceShapes, wx: Int, y: Int, wz: Int): Int {
		val self = probe(world, wx, y, wz) as? Probe.Block ?: return 0
		var m = 0
		for (d in DIRECTIONS) if (quadVisible(world, classifier, shapes, self.state, wx, y, wz, d, onFace = true)) m = m or d
		return m
	}

	inline fun forEachVisible(world: SurfaceWorld, classifier: BlockClassifier, c: SurfaceChunk, out: (wx: Int, y: Int, wz: Int, state: String, mask: Int) -> Unit) =
		forEachVisible(world, classifier, FaceShapes.byClass(classifier), c, out)

	inline fun forEachVisible(world: SurfaceWorld, classifier: BlockClassifier, shapes: FaceShapes, c: SurfaceChunk, out: (wx: Int, y: Int, wz: Int, state: String, mask: Int) -> Unit) {
		val bx = c.x shl 4
		val bz = c.z shl 4
		for (col in 0 until 256) {
			if (c.isEmpty(col)) continue
			val wx = bx + SurfaceIndex.colX(col)
			val wz = bz + SurfaceIndex.colZ(col)
			var y = c.top[col]
			for (i in c.offsets[col] until c.offsets[col + 1]) {
				val p = c.blocks[i]
				if (p < 0) { y += p; continue }
				if (p != 0) {
					val m = mask(world, classifier, shapes, wx, y, wz)
					if (m != 0) out(wx, y, wz, c.palette[p], m)
				}
				y--
			}
			val u = c.underside(col)
			if (u != SurfaceChunk.NO_COLUMN) {
				val m = mask(world, classifier, shapes, wx, u, wz)
				if (m != 0) out(wx, u, wz, c.palette[c.underState!![col]], m)
			}

			if (!endsInGround(c, col)) continue
			val filler = fillerState(c, col) ?: continue
			var fy = c.bottom(col) - 1
			val lowest = c.bottom(col) - MAX_SKIRT
			while (fy >= lowest && fy > (if (u != SurfaceChunk.NO_COLUMN) u else Int.MIN_VALUE)) {
				val m = skirtMask(world, wx, fy, wz, c.bottom(col))
				if (m == 0) break
				out(wx, fy, wz, filler, m)
				fy--
			}
		}
	}

	const val MAX_SKIRT = 64
	const val UNKNOWN_SKIRT = 8

	fun skirtMask(world: SurfaceWorld, wx: Int, y: Int, wz: Int, runBottom: Int): Int {
		if (probe(world, wx, y, wz) != Probe.Hidden) return 0
		var m = 0
		for (d in SIDES) {
			when (probe(world, wx + dx(d), y, wz + dz(d))) {
				Probe.Air -> m = m or d
				Probe.Unknown -> if (runBottom - y <= UNKNOWN_SKIRT) m = m or d
				else -> {}
			}
		}
		return m
	}

	val SIDES = intArrayOf(NORTH, SOUTH, WEST, EAST)

	fun endsInGround(c: SurfaceChunk, col: Int): Boolean {
		if (c.isEmpty(col) || c.underside(col) != SurfaceChunk.NO_COLUMN) return false
		val last = c.offsets[col + 1] - 1
		return last >= c.offsets[col] && c.blocks[last] > 0
	}

	fun fillerState(c: SurfaceChunk, col: Int): String? {
		if (c.isEmpty(col)) return null
		val p = c.lowestBlock(col)
		return if (p != 0) fillerOf(c.palette[p]) else null
	}

	fun fillerOf(state: String): String {
		val b = state.indexOf('[')
		val name = if (b < 0) state else state.substring(0, b)
		return when (name) {
			"minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium", "minecraft:dirt_path", "minecraft:farmland" -> "minecraft:dirt"
			"minecraft:crimson_nylium", "minecraft:warped_nylium" -> "minecraft:netherrack"
			else -> state
		}
	}

	fun faceCount(mask: Int): Int = Integer.bitCount(mask)
}
