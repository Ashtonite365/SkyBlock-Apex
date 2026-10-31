/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Ice Fill solver, written from scratch. Visual only: it draws lines and markers and never
 * moves, clicks or sends packets. Path search lives in IceFillPath.kt (pure, unit-tested).
 *
 * Every 4 ticks it reads the ice layers around the player live from the blocks: a layer is a 4-connected group of
 * ice/packed ice blocks at one height with room to stand above (obstacles are the blocks standing on the floor).
 * Start (entrance ledge) and exit (the step up to the next layer or the chest platform) are read from the blocks
 * around the layer's outline, so any rotation works. Layers are solved on a background thread with a complete
 * search and cached per layer; the optional bundled patterns are a fast path, used only once verified on the
 * live layer. While you walk, the route follows you; if you leave it, it is re-planned from your tile over the
 * tiles still unwalked, and if no route from your tile exists the full route from the start is shown with a hint.
 */
package dev.ashtonite365.apex.features.dungeon

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.IceFillPath.Cell
import dev.ashtonite365.apex.features.kit.puzzles.IceFillFloors
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object IceFillSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	private val log = org.slf4j.LoggerFactory.getLogger("Apex/IceFill")

	private data class LayerKey(val y: Int, val tiles: Set<Cell>, val bundled: Boolean, val optimal: Boolean)

	data class View(val y: Int, val remaining: List<Cell>, val onIt: Boolean, val status: String?, val centre: Vec3, val fromBundle: Boolean)

	private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "Apex Ice Fill").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
	private val generation = AtomicInteger()
	private val plans = ConcurrentHashMap<LayerKey, CompletableFuture<IceFillPath.Plan?>>()
	private val replans = object : LinkedHashMap<Triple<LayerKey, Set<Cell>, Cell>, CompletableFuture<List<Cell>?>>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<LayerKey, Set<Cell>, Cell>, CompletableFuture<List<Cell>?>>) = size > 64
	}

	private val lastAt = HashMap<Int, Cell>()
	private var bundledFor: Pair<BlockPos, Boolean>? = null

	private var bundledFloors: List<Pair<Int, List<Cell>>>? = null
	@Volatile var views: List<View> = emptyList()
		private set

	private const val SCAN_RADIUS = 24
	private const val SCAN_DOWN = 4
	private const val SCAN_UP = 3
	private const val MIN_TILES = 4
	private const val MAX_TILES = 200

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	private fun reset() {
		generation.incrementAndGet()
		plans.clear(); synchronized(replans) { replans.clear() }; lastAt.clear()
		bundledFor = null; bundledFloors = null; views = emptyList()
	}

	private fun isIce(s: BlockState) = s.`is`(Blocks.ICE) || s.`is`(Blocks.PACKED_ICE)
	private fun passable(level: Level, p: BlockPos) = level.getBlockState(p).getCollisionShape(level, p).isEmpty

	private fun <T> submit(task: (cancelled: () -> Boolean) -> T): CompletableFuture<T> {
		val gen = generation.get()
		val cancelled = { generation.get() != gen }
		return CompletableFuture.supplyAsync({
			try { task(cancelled) } catch (t: Throwable) { log.warn("[ApexIceFill] solver error", t); throw t }
		}, worker)
	}

	private fun <T> CompletableFuture<T>.now(): T? = if (isDone && !isCompletedExceptionally) getNow(null) else null

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 4 != 0) return
		if (!cfg.iceFillSolver || !DungeonUtil.isInDungeonIsland) { if (views.isNotEmpty() || plans.isNotEmpty()) reset(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val feet = player.blockPosition()
		val layers = scanLayers(level, feet)
		if (!IceFillPath.looksLikeRoom(layers.map { it.first })) { views = emptyList(); return }
		val bundled = if (cfg.iceFillBundled) bundledCandidates(level) else emptyMap()
		val out = mutableListOf<View>()
		for ((y, tiles) in layers) {
			val ice = tiles.filterTo(HashSet()) { level.getBlockState(BlockPos(it.x, y, it.z)).`is`(Blocks.ICE) }
			if (ice.isEmpty()) continue
			val walked: Set<Cell> = tiles - ice
			val centre = Vec3(tiles.map { it.x }.average() + 0.5, y + 1.5, tiles.map { it.z }.average() + 0.5)

			val here = Cell(feet.x, feet.z).takeIf { it in tiles && player.y - y in 0.9..2.6 }
			val at = here ?: lastAt[y]?.takeIf { it in tiles && player.y - y in 0.9..2.6 && Math.abs(player.x - (it.x + 0.5)) < 1.2 && Math.abs(player.z - (it.z + 0.5)) < 1.2 }
			if (here != null && player.onGround()) lastAt[y] = here else if (at == null) lastAt.remove(y)
			val key = LayerKey(y, tiles, cfg.iceFillBundled, cfg.iceFillOptimal)
			val future = plans.computeIfAbsent(key) {

				val upper = layers.filter { it.first == y + 1 }.maxByOrNull { it.second.size }?.second?.let { u ->
					u.map { it.x }.average() + 0.5 to u.map { it.z }.average() + 0.5
				}
				val edges = HashMap<Pair<Cell, Cell>, IceFillPath.Edge>()
				val outside = IceFillPath.outsideCells(tiles)
				for (t in tiles) for (o in t.neighbours()) if (o in outside) edges[t to o] = edge(level, o, y)
				val ends = IceFillPath.detectEnds(tiles, { t, o -> edges[t to o] ?: IceFillPath.Edge.WALL }, null, upper)
				val patterns = bundled[y].orEmpty()
				submit { cancelled ->
					val t0 = System.nanoTime()
					val plan = IceFillPath.solveLayer(tiles, ends, patterns, cancelled = cancelled)
					val ms = (System.nanoTime() - t0) / 1_000_000
					if (!cancelled()) log.info(
						"[ApexIceFill] layer y={} tiles={} ends tried={} first={} -> {} in {} ms grid={}", y, tiles.size, ends.size, ends.firstOrNull(),
						when { plan == null -> "NO covering path"; plan.fromBundle -> "bundled ${plan.ends}"; plan.guessed -> "fallback ${plan.ends}"; else -> "computed ${plan.ends}" },
						ms, IceFillPath.describe(tiles, emptySet(), plan?.ends ?: ends.firstOrNull()),
					)
					plan
				}
			}
			if (!future.isDone) { out += View(y, emptyList(), at != null, "§7Ice Fill: solving…", centre, false); continue }
			val plan = future.now()
			if (plan == null) { out += View(y, emptyList(), at != null, "§cIce Fill: this layer has no route", centre, false); continue }
			var pending = false
			val g = IceFillPath.guide(plan, walked, at) {
				val rk = Triple(key, walked, at!!)
				val f = synchronized(replans) {
					replans.getOrPut(rk) {
						submit { cancelled ->
							val r = IceFillPath.replan(tiles, walked, rk.third, plan.ends.exit, cancelled = cancelled)
							if (r !is IceFillPath.Result.Path && !cancelled())
								log.info("[ApexIceFill] no route from {} on y={} ({}) walked={} grid={}", rk.third, y, r, walked.size, IceFillPath.describe(tiles, walked, plan.ends))
							(r as? IceFillPath.Result.Path)?.cells
						}
					}
				}
				if (!f.isDone) { pending = true; null } else f.now()
			}
			if (pending) { out += View(y, emptyList(), true, "§7Ice Fill: solving…", centre, plan.fromBundle); continue }
			val status = g.hint?.let { "§eIce Fill: $it" }
			out += View(y, g.route, g.fromPlayer, status, centre, plan.fromBundle)
		}
		views = out
	}

	private fun scanLayers(level: Level, feet: BlockPos): List<Pair<Int, Set<Cell>>> {
		val p = BlockPos.MutableBlockPos()
		val q = BlockPos.MutableBlockPos()
		val out = mutableListOf<Pair<Int, Set<Cell>>>()
		for (y in feet.y - SCAN_DOWN..feet.y + SCAN_UP) {
			val found = HashSet<Cell>()
			for (x in feet.x - SCAN_RADIUS..feet.x + SCAN_RADIUS) for (z in feet.z - SCAN_RADIUS..feet.z + SCAN_RADIUS) {
				if (!isIce(level.getBlockState(p.set(x, y, z)))) continue
				if (!passable(level, q.set(x, y + 1, z)) || !passable(level, q.set(x, y + 2, z))) continue
				found += Cell(x, z)
			}
			val left = HashSet(found)
			while (left.isNotEmpty()) {
				val seed = left.first()
				val group = HashSet<Cell>()
				val stack = ArrayDeque(listOf(seed)); left -= seed
				while (stack.isNotEmpty()) {
					val c = stack.removeLast(); group += c
					for (n in c.neighbours()) if (left.remove(n)) stack += n
				}
				if (group.size in MIN_TILES..MAX_TILES) out += y to group
			}
		}
		return out
	}

	private fun edge(level: Level, o: Cell, y: Int): IceFillPath.Edge {
		val floor = !passable(level, BlockPos(o.x, y, o.z))
		val b1 = passable(level, BlockPos(o.x, y + 1, o.z))
		val b2 = passable(level, BlockPos(o.x, y + 2, o.z))
		val b3 = passable(level, BlockPos(o.x, y + 3, o.z))
		return when {
			b1 && b2 -> if (floor) IceFillPath.Edge.LEVEL else IceFillPath.Edge.NONE
			!b1 && b2 && b3 -> IceFillPath.Edge.STEP_UP
			else -> IceFillPath.Edge.WALL
		}
	}

	private fun bundledCandidates(level: Level): Map<Int, List<List<Cell>>> {
		val corner = PuzzleRoom.cornerFor("Ice Fill") ?: return emptyMap()
		val data = IceFillFloors.data ?: return emptyMap()
		val k = corner to cfg.iceFillOptimal
		if (bundledFor != k) {
			bundledFor = k
			val isAir = { p: BlockPos -> level.getBlockState(p).isAir }
			val modes = if (cfg.iceFillOptimal) listOf(true, false) else listOf(false)
			bundledFloors = modes.flatMap { IceFillFloors.floors(data, corner, it, isAir).orEmpty() }.mapNotNull { f ->
				if (f.size < 2) return@mapNotNull null
				val cells = IceFillPath.expand(f.map { Cell(it.x, it.z) }) ?: return@mapNotNull null
				(f.first().y - 1) to cells
			}
		}
		return bundledFloors.orEmpty().groupBy({ it.first }, { it.second })
	}

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.iceFillSolver) return
		val list = views
		if (list.isEmpty()) return
		val lineColour = cfg.iceFillColour.getEffectiveColourRGB() or 0xFF000000.toInt()
		val next = cfg.iceFillNextColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			for (v in list) {
				v.status?.let { text(v.centre, Component.literal(it)) }
				if (v.remaining.isEmpty()) continue
				val top = v.y + 1.06
				val pts = v.remaining.map { Vec3(it.x + 0.5, top, it.z + 0.5) }
				if (pts.size >= 2) line(pts, color = lineColour, lineWidth = 3F)
				val target = if (v.onIt) v.remaining.getOrNull(1) else v.remaining.firstOrNull()
				target?.let { box(AABB(it.x + 0.05, v.y + 1.0, it.z + 0.05, it.x + 0.95, v.y + 1.08, it.z + 0.95), next) }
				if (v.onIt && v.remaining.size >= 2)
					PuzzleDraw.arrow(this, pts[0], pts[1], next or 0xFF000000.toInt())
			}
		}
	}
}
