// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object DungeonBlockIndex {
	const val GRID_MIN = -200
	const val GRID_MAX = GRID_MIN + 32 * RoomCornerFinder.GRID - 1

	const val MIN_Y = 48
	const val MAX_Y = 143
	const val SECTIONS_PER_TICK = 6
	const val RESCAN_MS = 5000L

	const val RESCAN_RANGE = 40

	private class Section(val chests: LongArray, val cracked: LongArray, val crypts: List<Tombs.Tomb>, val levers: LongArray, val heads: LongArray)

	private val sections = HashMap<Long, Section>()
	private val scannedAt = HashMap<Long, Long>()
	private var startedAt = 0L
	private var completeLogged = false

	var version = 0
		private set

	@Volatile var wanted = false

	val chests: List<Pos3> get() = sections.values.flatMap { s -> s.chests.map(::pos) }
	val cracked: List<Pos3> get() = sections.values.flatMap { s -> s.cracked.map(::pos) }
	val crypts: List<Tombs.Tomb> get() = sections.values.flatMap { it.crypts }.distinctBy { it.air }
	val levers: List<Pos3> get() = sections.values.flatMap { s -> s.levers.map(::pos) }

	val heads: List<Pos3> get() = sections.values.flatMap { s -> s.heads.map(::pos) }

	private fun pos(l: Long) = Pos3(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l))

	fun allSectionKeys(): List<Long> {
		val out = ArrayList<Long>()
		for (cx in (GRID_MIN shr 4)..(GRID_MAX shr 4)) for (cz in (GRID_MIN shr 4)..(GRID_MAX shr 4))
			for (sy in (MIN_Y shr 4)..(MAX_Y shr 4)) out += SectionPos.asLong(cx, sy, cz)
		return out
	}

	fun reset() {
		sections.clear(); scannedAt.clear(); startedAt = 0L; completeLogged = false; version++
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { reset() }

	@Subscribe
	fun onTick(event: TickEvent) {
		val want = wanted
		wanted = false
		if (!want || !DungeonUtil.isInDungeonIsland) { if (sections.isNotEmpty() || scannedAt.isNotEmpty()) reset(); return }
		val level = Client.level ?: return
		val player = Client.player ?: return
		val now = System.currentTimeMillis()
		if (startedAt == 0L) startedAt = now
		val px = player.blockX; val pz = player.blockZ
		val keys = allSectionKeys()
		val due = keys.filter { k ->
			val at = scannedAt[k] ?: return@filter true
			now - at > RESCAN_MS && near(k, px, pz)
		}.sortedBy { k -> dist2(k, px, pz) }
		var done = 0
		for (k in due) {
			if (done >= SECTIONS_PER_TICK) break
			if (!level.hasChunk(SectionPos.x(k), SectionPos.z(k))) continue
			scan(level, k, now)
			done++
		}
		if (!completeLogged && keys.all { it in scannedAt }) {
			completeLogged = true
			Apex.logger.info("[ApexScan] whole grid scanned in ${now - startedAt} ms: ${keys.size} sections, chests=${chests.size} " +
				"cracked=${cracked.size} crypts=${crypts.size} loadedRadius=${loadedRadius(level, player.chunkPosition().x, player.chunkPosition().z)}")
		}
	}

	private fun near(k: Long, px: Int, pz: Int): Boolean {
		val x = (SectionPos.x(k) shl 4) + 8; val z = (SectionPos.z(k) shl 4) + 8
		return Math.abs(x - px) <= RESCAN_RANGE + 8 && Math.abs(z - pz) <= RESCAN_RANGE + 8
	}

	private fun dist2(k: Long, px: Int, pz: Int): Int {
		val x = (SectionPos.x(k) shl 4) + 8 - px; val z = (SectionPos.z(k) shl 4) + 8 - pz
		return x * x + z * z
	}

	fun loadedRadius(level: Level, cx: Int, cz: Int): Int {
		var r = 0
		while (r < 32) {
			val n = r + 1
			if (!level.hasChunk(cx + n, cz) && !level.hasChunk(cx - n, cz) && !level.hasChunk(cx, cz + n) && !level.hasChunk(cx, cz - n)) break
			r = n
		}
		return r
	}

	private fun isChest(st: BlockState) = st.`is`(Blocks.CHEST) || st.`is`(Blocks.TRAPPED_CHEST)
	private fun isCracked(st: BlockState) = st.`is`(Blocks.CRACKED_STONE_BRICKS)
	private fun isLever(st: BlockState) = st.`is`(Blocks.LEVER)
	private fun isHead(st: BlockState) = st.`is`(Blocks.PLAYER_HEAD) || st.`is`(Blocks.PLAYER_WALL_HEAD)

	fun tombBlock(level: Level, m: BlockPos.MutableBlockPos, x: Int, y: Int, z: Int): TombBlock {
		m.set(x, y, z)
		if (!level.isLoaded(m)) return TombBlock.UNLOADED
		val st = level.getBlockState(m)
		return when {
			st.isAir -> TombBlock.AIR
			isCracked(st) -> TombBlock.CRACKED_BRICK
			isBrick(st) -> TombBlock.BRICK
			else -> TombBlock.OTHER_SOLID
		}
	}

	private fun isBrick(st: BlockState): Boolean {
		if (st.`is`(Blocks.STONE_BRICKS) || st.`is`(Blocks.MOSSY_STONE_BRICKS) || st.`is`(Blocks.CHISELED_STONE_BRICKS)) return true
		val b = st.block
		if (b is SlabBlock || b is StairBlock) return b == Blocks.STONE_BRICK_SLAB || b == Blocks.STONE_BRICK_STAIRS ||
			b == Blocks.MOSSY_STONE_BRICK_SLAB || b == Blocks.MOSSY_STONE_BRICK_STAIRS
		return false
	}

	private fun scan(level: Level, key: Long, now: Long) {
		scannedAt[key] = now
		val cx = SectionPos.x(key); val sy = SectionPos.y(key); val cz = SectionPos.z(key)
		val chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false) ?: run { scannedAt.remove(key); return }
		if (sy < level.minSectionY || sy > level.maxSectionY) return
		val section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy))
		if (section.hasOnlyAir() || !section.maybeHas { isChest(it) || isCracked(it) || isLever(it) || isHead(it) }) {
			if (sections.remove(key) != null) version++
			return
		}
		val chests = ArrayList<Long>(); val cracked = ArrayList<Long>(); val levers = ArrayList<Long>(); val heads = ArrayList<Long>()
		for (ly in 0 until 16) for (lz in 0 until 16) for (lx in 0 until 16) {
			val st = section.getBlockState(lx, ly, lz)
			val x = (cx shl 4) + lx; val y = (sy shl 4) + ly; val z = (cz shl 4) + lz
			if (x !in GRID_MIN..GRID_MAX || z !in GRID_MIN..GRID_MAX) continue
			when {
				isChest(st) -> chests += BlockPos.asLong(x, y, z)
				isCracked(st) -> cracked += BlockPos.asLong(x, y, z)
				isLever(st) -> levers += BlockPos.asLong(x, y, z)
				isHead(st) -> heads += BlockPos.asLong(x, y, z)
			}
		}
		val m = BlockPos.MutableBlockPos()
		val tombs = Tombs.find(cracked.map(::pos)) { x, y, z -> tombBlock(level, m, x, y, z) }
		for (t in tombs) if (crypts.none { it.air == t.air })
			Apex.logger.info("[ApexCrypt] tomb at ${t.minX},${t.minY},${t.minZ}..${t.maxX},${t.maxY},${t.maxZ} air=${t.air.size} cracked=${t.cracked.size}")
		val old = sections[key]
		val next = Section(chests.toLongArray(), cracked.toLongArray(), tombs, levers.toLongArray(), heads.toLongArray())
		if (old == null || !old.chests.contentEquals(next.chests) || !old.cracked.contentEquals(next.cracked) ||
			!old.levers.contentEquals(next.levers) || !old.heads.contentEquals(next.heads)) version++
		sections[key] = next
	}
}
