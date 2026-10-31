/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex Secrets > Breakable wall locator and Superboom (feat-secrets-1025). Original Apex code.
 *
 * Only the live scan: exposed cracked stone bricks of the dungeon grid ([DungeonBlockIndex], shared with Chests and
 * Crypts) grouped into walls ([clusters], [isWall]: at least [MIN_CLUSTER] blocks, [MIN_HEIGHT] tall, at most
 * [MAX_THICKNESS] thick). No per-room data. Walls of the room you stand in are drawn (its cells and the one-block gap
 * round them, [touchesRoom] / [inRoom]), each once:
 *  - Superboom (its own switch): a wall with a chest or a crypt behind it ([SuperboomRule], tuned in
 *    DungeonSecrets). A cracked ledge with nothing behind is never a Superboom wall.
 *  - Breakable wall (the locator switch): every other wall, in the wall colour.
 * Line of sight by default (hidden behind blocks); the ⚠ through-walls option draws them through blocks. Each wall
 * is one shape ([mesh]) or one box per block ([WallStyle]). [ApexWall] logs which source and rule drew a wall.
 */
package dev.ashtonite365.apex.features.dungeon

import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms
import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.secrets.DungeonBlockIndex
import dev.ashtonite365.apex.features.dungeon.secrets.DungeonSecrets
import dev.ashtonite365.apex.features.dungeon.secrets.SuperboomRule
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

typealias WallPos = Triple<Int, Int, Int>

object BreakableWalls {
	val identifier: String
		get() = "breakable-walls"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {

		val enabled by toggle("enabled") { false }

		val superboom by toggle("superboom") { false }
		val labels by toggle("labels") { true }
		val superboomColour by colour("superboom-colour") { ChromaColour.fromStaticRGB(0xFF, 0x55, 0x55, 0x70) }

		val stonkColour by colour("stonk-colour") { ChromaColour.fromStaticRGB(0x55, 0x99, 0xFF, 0x70) }

		val style by choice("style") { WallStyle.SINGLE_BLOCK }
		val throughWalls by riskyToggle("through-walls", RiskLevel.RISKY)

		override fun migrateFromAll(saved: JsonObject, all: JsonObject): JsonObject = migrateOldWalls(saved)
	}

	fun migrateOldWalls(saved: JsonObject): JsonObject {
		if (saved.isEmpty() || "through-walls" in saved) return saved
		fun on(k: String) = (saved[k] as? JsonPrimitive)?.content == "true"
		val out = saved.toMutableMap()
		out["superboom"] = JsonPrimitive(on("enabled") && (saved["superboom"] == null || on("superboom")))
		out["through-walls"] = JsonPrimitive(on("enabled"))
		out.remove("stonk")
		return JsonObject(out)
	}

	enum class Kind(val label: String) { SUPERBOOM("Superboom"), WALL("Breakable wall") }

	fun kindOf(behind: Boolean, superboom: Boolean, locator: Boolean): Kind? = when {
		behind && superboom -> Kind.SUPERBOOM
		locator -> Kind.WALL
		else -> null
	}

	enum class WallStyle(val id: String) : net.minecraft.util.StringRepresentable {
		SINGLE_BLOCK("single-block"), MESH("mesh");
		override fun getSerializedName() = id
	}

	fun shape(blocks: Set<WallPos>, style: WallStyle): WallMesh = when (style) {
		WallStyle.MESH -> mesh(blocks)
		WallStyle.SINGLE_BLOCK -> blocks.map { mesh(setOf(it)) }.let { ms -> WallMesh(ms.flatMap { it.faces }, ms.flatMap { it.edges }) }
	}

	data class Face(val x: Int, val y: Int, val z: Int, val face: Int)

	data class Segment(val from: WallPos, val to: WallPos)

	data class WallMesh(val faces: List<Face>, val edges: List<Segment>)

	private fun axis(p: WallPos, a: Int) = when (a) { 0 -> p.first; 1 -> p.second; else -> p.third }
	private fun step(p: WallPos, f: Int) = WallPos(p.first + NX[f], p.second + NY[f], p.third + NZ[f])

	fun mesh(blocks: Set<WallPos>): WallMesh {
		val faces = ArrayList<Face>()
		val unit = HashSet<Pair<WallPos, Int>>()
		val c = IntArray(3)
		for (b in blocks) for (f in 0 until 6) {
			if (step(b, f) in blocks) continue
			faces += Face(b.first, b.second, b.third, f)
			val a = f / 2
			for (d in 0 until 6) {
				val da = d / 2
				if (da == a) continue
				val side = step(b, d)
				if (side in blocks && step(side, f) !in blocks) continue
				val w = 3 - a - da
				c[a] = axis(b, a) + (if (f % 2 == 0) 1 else 0)
				c[da] = axis(b, da) + (if (d % 2 == 0) 1 else 0)
				c[w] = axis(b, w)
				unit += WallPos(c[0], c[1], c[2]) to w
			}
		}
		val edges = ArrayList<Segment>()
		unit.groupBy({ (p, w) -> Triple(w, if (w == 0) p.second else p.first, if (w == 2) p.second else p.third) }, { it.first })
			.forEach { (key, starts) ->
				val w = key.first
				val sorted = starts.sortedBy { axis(it, w) }
				var from = sorted[0]; var last = axis(from, w)
				fun close() {
					val to = when (w) { 0 -> WallPos(last + 1, from.second, from.third); 1 -> WallPos(from.first, last + 1, from.third); else -> WallPos(from.first, from.second, last + 1) }
					edges += Segment(from, to)
				}
				for (p in sorted.drop(1)) {
					val v = axis(p, w)
					if (v == last + 1) { last = v; continue }
					close(); from = p; last = v
				}
				close()
			}
		return WallMesh(faces, edges)
	}

	fun cellStart(i: Int) = DungeonRooms.GRID_ORIGIN + DungeonRooms.CELL * i

	fun touchesRoom(x: Int, z: Int, cells: Collection<Pair<Int, Int>>): Boolean = cells.any { (cx, cz) ->
		val x0 = cellStart(cx); val z0 = cellStart(cz)
		x in x0 - 1..x0 + DungeonRooms.CELL - 1 && z in z0 - 1..z0 + DungeonRooms.CELL - 1
	}

	fun inRoom(wall: Collection<WallPos>, cells: Collection<Pair<Int, Int>>): Boolean = wall.any { touchesRoom(it.first, it.third, cells) }

	const val SCAN_RADIUS = 24

	const val ROOM_SCAN_MARGIN = 3
	const val MIN_CLUSTER = 6
	const val MIN_HEIGHT = 2
	const val MAX_THICKNESS = 2
	const val MAX_CLUSTERS = 16
	const val MAX_BLOCKS = 160
	const val REBUILD_TICKS = 10
	private val NX = intArrayOf(1, -1, 0, 0, 0, 0)
	private val NY = intArrayOf(0, 0, 1, -1, 0, 0)
	private val NZ = intArrayOf(0, 0, 0, 0, 1, -1)

	fun clusters(blocks: Set<WallPos>): List<List<WallPos>> {
		val left = HashSet(blocks)
		val out = ArrayList<List<WallPos>>()
		while (left.isNotEmpty()) {
			val first = left.first(); left.remove(first)
			val group = arrayListOf(first)
			var i = 0
			while (i < group.size) {
				val (x, y, z) = group[i++]
				for (n in listOf(WallPos(x + 1, y, z), WallPos(x - 1, y, z), WallPos(x, y + 1, z), WallPos(x, y - 1, z), WallPos(x, y, z + 1), WallPos(x, y, z - 1)))
					if (left.remove(n)) group += n
			}
			out += group
		}
		return out
	}

	fun isWall(c: List<WallPos>): Boolean {
		if (c.size < MIN_CLUSTER) return false
		val h = c.maxOf { it.second } - c.minOf { it.second } + 1
		val dx = c.maxOf { it.first } - c.minOf { it.first } + 1
		val dz = c.maxOf { it.third } - c.minOf { it.third } + 1
		return h >= MIN_HEIGHT && minOf(dx, dz) <= MAX_THICKNESS
	}

	fun scanWalls(cracked: Set<WallPos>, px: Double, py: Double, pz: Double): List<List<WallPos>> {
		fun d2(p: WallPos) = (p.first + 0.5 - px).let { it * it } + (p.second + 0.5 - py).let { it * it } + (p.third + 0.5 - pz).let { it * it }
		val out = ArrayList<List<WallPos>>(); var blocks = 0
		for (c in clusters(cracked).filter { isWall(it) }.sortedBy { c -> c.minOf(::d2) }) {
			if (out.size >= MAX_CLUSTERS || blocks + c.size > MAX_BLOCKS) break
			out += c; blocks += c.size
		}
		return out
	}

	fun labelPos(c: List<WallPos>): Vec3 = Vec3(
		(c.minOf { it.first } + c.maxOf { it.first } + 1) / 2.0, c.maxOf { it.second } + 1.5, (c.minOf { it.third } + c.maxOf { it.third } + 1) / 2.0)

	private var walls: List<List<WallPos>> = emptyList()

	class Drawn(val kind: Kind, val mesh: WallMesh, val label: Vec3)
	private var drawn: List<Drawn> = emptyList()

	private var meshStyle: WallStyle? = null
	private var meshCache = HashMap<Pair<Kind, Set<WallPos>>, WallMesh>()
	private val logged = HashSet<String>()

	private fun clearAll() { walls = emptyList(); drawn = emptyList(); meshCache.clear(); logged.clear() }

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { clearAll() }

	@Subscribe
	fun onTick(event: TickEvent) {
		if ((!TConfig.enabled && !TConfig.superboom) || !DungeonUtil.isInDungeonIsland) { if (drawn.isNotEmpty() || walls.isNotEmpty()) clearAll(); return }
		DungeonBlockIndex.wanted = true
		val level = Client.level ?: return
		val player = Client.player ?: return
		val cells = DungeonRoomTracker.currentCells()
		if (cells.isNullOrEmpty()) { walls = emptyList(); drawn = emptyList(); return }
		if (event.tickCount % REBUILD_TICKS == 0) rebuild(level, cells, player.x, player.y, player.z)
		drawn = buildDrawn(level, Triple(player.x, player.y, player.z))
	}

	private fun roomBox(cells: List<Pair<Int, Int>>): IntArray {
		val m = ROOM_SCAN_MARGIN; val s = DungeonRooms.CELL
		return intArrayOf(cells.minOf { cellStart(it.first) } - 1 - m, cells.maxOf { cellStart(it.first) } + s - 1 + m,
			cells.minOf { cellStart(it.second) } - 1 - m, cells.maxOf { cellStart(it.second) } + s - 1 + m)
	}

	private fun rebuild(level: Level, cells: List<Pair<Int, Int>>, px: Double, py: Double, pz: Double) {
		val m = BlockPos.MutableBlockPos()
		val (x0, x1, z0, z1) = roomBox(cells)
		val cracked = HashSet<WallPos>()
		for ((x, y, z) in DungeonBlockIndex.cracked) {
			if (x !in x0..x1 || z !in z0..z1 || Math.abs(y + 0.5 - py) > SCAN_RADIUS) continue
			if (!level.getBlockState(m.set(x, y, z)).`is`(Blocks.CRACKED_STONE_BRICKS)) continue
			if ((0 until 6).none { i -> level.getBlockState(m.set(x + NX[i], y + NY[i], z + NZ[i])).isAir }) continue
			cracked += WallPos(x, y, z)
		}
		walls = scanWalls(cracked, px, py, pz).filter { inRoom(it, cells) }
	}

	private fun buildDrawn(level: Level, viewer: Triple<Double, Double, Double>): List<Drawn> {
		val style = TConfig.style
		if (style != meshStyle) { meshCache.clear(); meshStyle = style }
		val m = BlockPos.MutableBlockPos()
		val out = ArrayList<Drawn>()
		val next = HashMap<Pair<Kind, Set<WallPos>>, WallMesh>()
		val chests = DungeonBlockIndex.chests
		val crypts = DungeonBlockIndex.crypts.map { it.air }
		val params = DungeonSecrets.superboomParams()
		for (wall in walls) {
			val standing = wall.filterTo(HashSet()) { level.getBlockState(m.set(it.first, it.second, it.third)).`is`(Blocks.CRACKED_STONE_BRICKS) }
			if (standing.isEmpty()) continue
			val reason = if (TConfig.superboom) SuperboomRule.reason(wall, chests, crypts, viewer, params) else null
			val kind = kindOf(reason != null, TConfig.superboom, TConfig.enabled) ?: continue
			val first = wall.minWith(compareBy({ it.first }, { it.second }, { it.third }))
			if (logged.add("$first $kind"))
				Apex.logger.info("[ApexWall] source=live-scan kind=$kind at=${first.first},${first.second},${first.third} blocks=${wall.size} " +
					(reason?.let { "reason=${it.what}@${it.at.first},${it.at.second},${it.at.third} d=${"%.1f".format(it.distance)}" } ?: "reason=none"))
			val k = kind to standing
			val mesh = next[k] ?: meshCache[k] ?: shape(standing, style)
			next[k] = mesh
			out += Drawn(kind, mesh, labelPos(wall))
		}
		meshCache = next
		return out
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if ((!TConfig.enabled && !TConfig.superboom) || !DungeonUtil.isInDungeonIsland) return
		val list = drawn
		if (list.isEmpty()) return
		val boom = TConfig.superboomColour.getEffectiveColourRGB()
		val plain = TConfig.stonkColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			depthTest = !TConfig.throughWalls
			for (w in list) {
				val fill = if (w.kind == Kind.SUPERBOOM) boom else plain
				val outline = fill or 0xFF000000.toInt()
				for (f in w.mesh.faces) blockFace(f.x, f.y, f.z, f.face, fill)
				for (e in w.mesh.edges)
					line(Vec3(e.from.first.toDouble(), e.from.second.toDouble(), e.from.third.toDouble()),
						Vec3(e.to.first.toDouble(), e.to.second.toDouble(), e.to.third.toDouble()), color = outline, lineWidth = 3F)
				if (TConfig.labels) text(w.label, Component.literal(w.kind.label))
			}
			depthTest = false
		}
	}
}
