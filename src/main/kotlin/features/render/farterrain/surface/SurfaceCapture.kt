/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, reading loaded client chunks into [SurfaceChunk]s (main thread only).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.EmptyLevelChunk
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

object SurfaceStates {
	private val states = ConcurrentHashMap<String, BlockState>()
	private val names = ConcurrentHashMap<BlockState, String>()
	private val classes = ConcurrentHashMap<String, BlockClass>()

	fun name(s: BlockState): String = if (s.isAir) SurfaceChunk.AIR else names.computeIfAbsent(s) { BlockStateParser.serialize(it) }

	fun state(name: String): BlockState = states.computeIfAbsent(name) { n ->
		if (n == SurfaceChunk.AIR) Blocks.AIR.defaultBlockState()
		else runCatching { BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, n, false).blockState() }
			.getOrElse { Blocks.AIR.defaultBlockState() }
	}

	val classifier = BlockClassifier { name ->
		classes.computeIfAbsent(name) {
			val s = state(it)
			when {
				s.isAir -> BlockClass.AIR
				s.isSolidRender -> BlockClass.OPAQUE
				else -> BlockClass.TRANSPARENT
			}
		}
	}

	val faceShapes = FaceShapes { self, neighbour, dir ->
		(neighbour == self && classifier.classify(self) == BlockClass.TRANSPARENT) ||
			!Block.shouldRenderFace(state(self), state(neighbour), SurfaceDirections.of(dir))
	}
}

object SurfaceDirections {
	fun of(dir: Int): Direction = when (dir) {
		SurfaceFaces.UP -> Direction.UP
		SurfaceFaces.DOWN -> Direction.DOWN
		SurfaceFaces.NORTH -> Direction.NORTH
		SurfaceFaces.SOUTH -> Direction.SOUTH
		SurfaceFaces.WEST -> Direction.WEST
		else -> Direction.EAST
	}

	fun bit(d: Direction): Int = when (d) {
		Direction.UP -> SurfaceFaces.UP
		Direction.DOWN -> SurfaceFaces.DOWN
		Direction.NORTH -> SurfaceFaces.NORTH
		Direction.SOUTH -> SurfaceFaces.SOUTH
		Direction.WEST -> SurfaceFaces.WEST
		Direction.EAST -> SurfaceFaces.EAST
	}
}

object SurfaceCapture {
	private val biomeNames = IdentityHashMap<Biome, String>()

	private fun biomeName(h: Holder<Biome>): String =
		biomeNames.getOrPut(h.value()) { h.unwrapKey().map { it.identifier().toString() }.orElse("minecraft:plains") }

	fun loadedChunk(level: ClientLevel, cx: Int, cz: Int): LevelChunk? =
		(level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false) as? LevelChunk)?.takeUnless { it is EmptyLevelChunk }

	private class Reader(val level: ClientLevel, val cx: Int, val cz: Int, center: LevelChunk) : ColumnReader {
		private val chunks = arrayOfNulls<LevelChunk>(9)
		private val maxYs = IntArray(9)
		override val minY: Int = level.minY

		init {
			for (dz in -1..1) for (dx in -1..1) {
				val c = if (dx == 0 && dz == 0) center else loadedChunk(level, cx + dx, cz + dz)
				val i = (dx + 1) + (dz + 1) * 3
				chunks[i] = c
				maxYs[i] = if (c == null) minY - 1 else {
					var top = minY - 1
					for ((si, sec) in c.sections.withIndex()) if (sec != null && !sec.hasOnlyAir()) top = (level.getSectionYFromSectionIndex(si) shl 4) + 15
					top
				}
			}
		}

		private fun idx(lx: Int, lz: Int): Int = (Math.floorDiv(lx, 16) + 1) + (Math.floorDiv(lz, 16) + 1) * 3

		override fun maxY(lx: Int, lz: Int): Int = maxYs[idx(lx, lz)]
		override fun has(lx: Int, lz: Int): Boolean = chunks[idx(lx, lz)] != null

		override fun state(lx: Int, y: Int, lz: Int): String {
			val c = chunks[idx(lx, lz)] ?: return SurfaceChunk.AIR
			val si = level.getSectionIndex(y)
			if (si < 0 || si >= c.sections.size) return SurfaceChunk.AIR
			val sec = c.sections[si] ?: return SurfaceChunk.AIR
			if (sec.hasOnlyAir()) return SurfaceChunk.AIR
			return SurfaceStates.name(sec.getBlockState(lx and 15, y and 15, lz and 15))
		}

		override fun biome(lx: Int, y: Int, lz: Int): String {
			val c = chunks[idx(lx, lz)] ?: return "minecraft:plains"
			val si = level.getSectionIndex(y).coerceIn(0, c.sections.size - 1)
			val sec = c.sections[si] ?: return "minecraft:plains"
			return biomeName(sec.getNoiseBiome((lx and 15) shr 2, (y and 15) shr 2, (lz and 15) shr 2))
		}
	}

	fun capture(level: ClientLevel, chunk: LevelChunk, now: Long): SurfaceChunk {
		val pos = chunk.pos
		return SurfaceExtractor.extract(pos.x(), pos.z(), Reader(level, pos.x(), pos.z(), chunk), SurfaceStates.classifier, now)
	}
}
