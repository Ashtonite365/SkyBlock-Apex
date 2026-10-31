// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.BlockClass
import dev.ashtonite365.apex.features.render.farterrain.surface.BlockClassifier
import dev.ashtonite365.apex.features.render.farterrain.surface.ColumnReader
import dev.ashtonite365.apex.features.render.farterrain.surface.FaceShapes
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceExtractor
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceWorld

const val STONE = "minecraft:stone"
const val GRASS = "minecraft:grass_block[snowy=false]"
const val SAND = "minecraft:sand"
const val WATER = "minecraft:water[level=0]"
const val GLASS = "minecraft:glass"
const val LEAVES = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"

const val BOTTOM_SLAB = "minecraft:stone_slab[type=bottom,waterlogged=false]"
const val TOP_SLAB = "minecraft:stone_slab[type=top,waterlogged=false]"
const val STAIRS_N = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
const val STAIRS_S = "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"

val testClassifier = BlockClassifier { s ->
	when {
		s == SurfaceChunk.AIR -> BlockClass.AIR
		s == WATER || s == GLASS || s == LEAVES -> BlockClass.TRANSPARENT
		"_slab[" in s || "_stairs[" in s -> BlockClass.TRANSPARENT
		else -> BlockClass.OPAQUE
	}
}

object TestShapes : FaceShapes {
	const val FULL = 15
	const val LOWER = 3
	const val UPPER = 12

	fun face(state: String, dir: Int): Int = when {
		state == SurfaceChunk.AIR -> 0
		state == BOTTOM_SLAB -> when (dir) { SurfaceFaces.DOWN -> FULL; SurfaceFaces.UP -> 0; else -> LOWER }
		state == TOP_SLAB -> when (dir) { SurfaceFaces.UP -> FULL; SurfaceFaces.DOWN -> 0; else -> UPPER }

		state == STAIRS_N -> when (dir) {
			SurfaceFaces.DOWN, SurfaceFaces.NORTH -> FULL
			SurfaceFaces.UP -> 1 or 2
			SurfaceFaces.SOUTH -> LOWER
			else -> LOWER or 4
		}
		state == STAIRS_S -> when (dir) {
			SurfaceFaces.DOWN, SurfaceFaces.SOUTH -> FULL
			SurfaceFaces.UP -> 4 or 8
			SurfaceFaces.NORTH -> LOWER
			else -> LOWER or 8
		}
		testClassifier.classify(state) == BlockClass.TRANSPARENT -> 0
		else -> FULL
	}

	override fun covers(self: String, neighbour: String, dir: Int): Boolean {
		val n = face(neighbour, SurfaceFaces.opposite(dir))
		if (n == FULL) return true
		if (neighbour == self && testClassifier.classify(self) == BlockClass.TRANSPARENT) return true
		val own = face(self, dir)
		if (n == 0 || own == 0) return false
		return own and n.inv() == 0
	}
}

class TestTerrain(private val ground: (wx: Int, y: Int, wz: Int) -> String = { _, y, _ -> if (y <= 64) STONE else SurfaceChunk.AIR }) {
	val minY = 0
	val maxY = 255
	private val blocks = HashMap<Triple<Int, Int, Int>, String>()
	val loaded = HashSet<Long>()
	var biome: (wx: Int, wz: Int) -> String = { _, _ -> "minecraft:plains" }

	fun set(wx: Int, y: Int, wz: Int, s: String) { blocks[Triple(wx, y, wz)] = s }
	fun fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, s: String) {
		for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) set(x, y, z, s)
	}
	fun state(wx: Int, y: Int, wz: Int): String = blocks[Triple(wx, y, wz)] ?: ground(wx, y, wz)
	fun load(vararg chunks: Pair<Int, Int>) = chunks.forEach { loaded.add(SurfaceIndex.key(it.first, it.second)) }

	fun reader(cx: Int, cz: Int): ColumnReader = object : ColumnReader {
		override val minY: Int = this@TestTerrain.minY
		override fun maxY(lx: Int, lz: Int): Int = this@TestTerrain.maxY
		override fun has(lx: Int, lz: Int): Boolean = SurfaceIndex.key(cx + Math.floorDiv(lx, 16), cz + Math.floorDiv(lz, 16)) in loaded
		override fun state(lx: Int, y: Int, lz: Int): String = this@TestTerrain.state(cx * 16 + lx, y, cz * 16 + lz)
		override fun biome(lx: Int, y: Int, lz: Int): String = biome(cx * 16 + lx, cz * 16 + lz)
	}

	fun extract(cx: Int, cz: Int, now: Long = 1L): SurfaceChunk = SurfaceExtractor.extract(cx, cz, reader(cx, cz), testClassifier, now)

	fun world(): MapWorld = MapWorld().also { w -> loaded.forEach { k -> w.put(extract(SurfaceIndex.keyX(k), SurfaceIndex.keyZ(k))) } }
}

class MapWorld : SurfaceWorld {
	val chunks = HashMap<Long, SurfaceChunk>()
	fun put(c: SurfaceChunk) { chunks[c.key] = c }
	override fun chunk(cx: Int, cz: Int): SurfaceChunk? = chunks[SurfaceIndex.key(cx, cz)]
}
