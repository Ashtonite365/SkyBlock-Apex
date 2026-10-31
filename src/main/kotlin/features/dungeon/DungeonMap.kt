/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex dungeon map HUD. Redraws the map item Hypixel already puts in hotbar slot 9: either the raw
 * pixels or a cleaned room layout (flat rooms, doors, check marks), plus player markers / heads on top.
 * Display only; nothing here reads the world beyond the map item and loaded players.
 */
package dev.ashtonite365.apex.features.dungeon

import dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours
import dev.ashtonite365.apex.features.dungeon.secrets.MapLayout

import io.github.notenoughupdates.moulconfig.ChromaColour
import org.joml.Vector2i
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.render.textureRegion
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object DungeonMap {
	val identifier: String
		get() = "dungeon-map"

	const val SIZE = 128

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val enabled by toggle("enabled") { true }
		val hud by position("hud", SIZE, SIZE) { Vector2i(4, 4) }
		val onlyInRun by toggle("only-in-run") { true }
		val background by toggle("background") { true }
		val border by toggle("border") { true }
		val markers by toggle("markers") { true }
		val hideEmpty by toggle("transparent-empty") { true }
		val centerOnPlayer by toggle("center-on-player") { false }
		val rotateWithPlayer by toggle("rotate-with-player") { false }
		val roomLayout by toggle("room-layout") { true }

		val roomFootprints by riskyToggle("room-footprints", dev.ashtonite365.apex.gui.config.RiskLevel.RISKY)
		val playerHeads by toggle("player-heads") { true }

		val headFacing by toggle("head-facing") { true }
		val roomTicks by toggle("room-ticks") { true }
		val puzzleFailX by toggle("puzzle-fail-x") { true }
		val puzzleFailXColour by colour("puzzle-fail-x-colour") { ChromaColour.fromStaticRGB(255, 85, 85, 255) }

		val instantSelfMarker by toggle("instant-self-marker") { true }
	}

	private class Pixels(private val raw: ByteArray) {
		operator fun get(x: Int, y: Int): Int =
			if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) 0 else raw[y * SIZE + x].toInt() and 0xFF
	}

	private fun isBlank(packedId: Int) = packedId < 4

	private fun argbOf(packedId: Int) = if (isBlank(packedId)) 0 else MapColor.getColorFromPackedId(packedId)

	private class Canvas(val out: IntArray = IntArray(SIZE * SIZE)) {
		fun set(x: Int, y: Int, argb: Int) {
			if (x in 0 until SIZE && y in 0 until SIZE) out[y * SIZE + x] = argb
		}
		fun rect(x0: Int, y0: Int, w: Int, h: Int, argb: Int) {
			for (y in y0 until y0 + h) for (x in x0 until x0 + w) set(x, y, argb)
		}
	}

	data class Grid(val x: Int, val y: Int, val room: Int) {
		val step get() = room + 4
	}

	var grid: Grid? = null
		private set

	private const val ENTRANCE_ID = 30
	private const val CLEARED_ID = 34
	private const val COMPLETED_ID = 30

	private const val CROSS_ID = 18

	fun detectGrid(colors: ByteArray): Grid? {
		val px = Pixels(colors)
		for (y in 0 until SIZE) for (x in 0 until SIZE) {
			if (px[x, y] != ENTRANCE_ID) continue
			if (px[x - 1, y] == ENTRANCE_ID || px[x, y - 1] == ENTRANCE_ID) continue
			val run = (x until SIZE + 1).first { px[it, y] != ENTRANCE_ID } - x
			if (run < 14 || run > 20) continue
			val step = run + 4
			return Grid(Math.floorMod(x, step), Math.floorMod(y, step), run)
		}
		return null
	}

	private data class Cell(val col: Int, val row: Int, val left: Int, val top: Int)

	private fun cells(g: Grid): Sequence<Cell> = sequence {
		var row = 0
		var top = g.y
		while (top < SIZE) {
			var col = 0
			var left = g.x
			while (left < SIZE) {
				yield(Cell(col, row, left, top))
				left += g.step; col++
			}
			top += g.step; row++
		}
	}

	enum class Tick(val argb: Int) { WHITE(0xFFFFFFFF.toInt()), GREEN(0xFF55FF55.toInt()), FAILED(0xFFFF5555.toInt()) }

	private fun tickAt(px: Pixels, g: Grid, left: Int, top: Int): Tick? {
		val body = px[left + 1, top + 1]
		if (isBlank(body) || HypixelMapColours.isUnentered(body)) return null
		val mid = px[left + g.room / 2, top + g.room / 2]
		if (mid == body) return null
		return when (mid) {
			CLEARED_ID -> Tick.WHITE
			COMPLETED_ID -> Tick.GREEN
			CROSS_ID -> Tick.FAILED
			else -> null
		}
	}

	fun roomTick(colors: ByteArray, grid: Grid, gx: Int, gy: Int): Tick? = tickAt(Pixels(colors), grid, gx, gy)

	fun roomTicks(colors: ByteArray, grid: Grid): Map<Pair<Int, Int>, Tick> {
		val px = Pixels(colors)
		return cells(grid).mapNotNull { c -> tickAt(px, grid, c.left, c.top)?.let { (c.col to c.row) to it } }.toMap(LinkedHashMap())
	}

	private class Glyph(val rows: List<String>) {
		val height get() = rows.size
		fun on(x: Int, y: Int) = y in rows.indices && x in rows[y].indices && rows[y][x] == '#'
	}

	private val CHECK = Glyph(listOf("......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."))
	private val CROSS = Glyph(listOf("#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"))

	fun glyphOf(tick: Tick): List<String> = (if (tick == Tick.FAILED) CROSS else CHECK).rows

	fun drawTick(put: (Int, Int, Int) -> Unit, cx: Int, cy: Int, tick: Tick, argb: Int = tick.argb) {
		val g = if (tick == Tick.FAILED) CROSS else CHECK
		val x0 = cx - 3
		val y0 = cy - 3
		for (y in -1..g.height) for (x in -1..7) {
			if (g.on(x, y)) continue
			val touches = (y - 1..y + 1).any { ny -> (x - 1..x + 1).any { nx -> g.on(nx, ny) } }
			if (touches) put(x0 + x, y0 + y, 0xFF000000.toInt())
		}
		for (y in 0 until g.height) for (x in 0 until 7) if (g.on(x, y)) put(x0 + x, y0 + y, argb)
	}

	fun displayTick(colors: ByteArray, grid: Grid, cell: Pair<Int, Int>, failed: Set<Pair<Int, Int>>, showFailed: Boolean): Tick? {
		if (showFailed && cell in failed) return Tick.FAILED
		val seen = roomTick(colors, grid, grid.x + cell.first * grid.step, grid.y + cell.second * grid.step)
		return seen?.takeUnless { it == Tick.FAILED && !showFailed }
	}

	fun doorStripColour(colors: ByteArray, room: Int, gx: Int, gy: Int, east: Boolean): Int? {
		val px = Pixels(colors)
		val mid = room / 2
		val samples = ArrayList<Int>(16)
		for (along in 0 until 4) for (across in -2..1)
			samples.add(if (east) px[gx + room + along, gy + mid + across] else px[gx + mid + across, gy + room + along])
		return HypixelMapColours.doorColour(samples)
	}

	data class DoorKey(val col: Int, val row: Int, val east: Boolean)

	@Volatile var blockDoors: Map<DoorKey, Int> = emptyMap()

	fun frontierFootprints(
		entered: Set<Pair<Int, Int>>, unknown: Set<Pair<Int, Int>>,
		connected: (Pair<Int, Int>, Pair<Int, Int>) -> Boolean,
		seeds: Set<Pair<Int, Int>> = unknown,
		shapeOf: (Pair<Int, Int>) -> Collection<Pair<Int, Int>>?,
	): List<Set<Pair<Int, Int>>> {
		val out = mutableListOf<Set<Pair<Int, Int>>>()
		val done = HashSet<Pair<Int, Int>>()
		fun nb(c: Pair<Int, Int>) = listOf(c.first + 1 to c.second, c.first - 1 to c.second, c.first to c.second + 1, c.first to c.second - 1)
		for (u in unknown.sortedWith(compareBy({ it.second }, { it.first }))) {
			if (u in done || u !in seeds) continue
			if (nb(u).none { it in entered && connected(it, u) }) continue
			val shape = shapeOf(u)?.toSet()?.takeIf { u in it && it.size in 1..4 && it.all { c -> c in unknown && c !in done } } ?: setOf(u)
			done += shape
			out += shape
		}
		return out
	}

	val FOOTPRINT_ARGB: Int = dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.argb(dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.UNOPENED)!!

	private fun drawFootprint(canvas: Canvas, grid: Grid, shape: Set<Pair<Int, Int>>) {
		val fill = FOOTPRINT_ARGB
		val r = grid.room
		fun left(c: Pair<Int, Int>) = grid.x + c.first * grid.step
		fun top(c: Pair<Int, Int>) = grid.y + c.second * grid.step
		for (c in shape) {
			canvas.rect(left(c), top(c), r, r, fill)
			val e = c.first + 1 to c.second; val s = c.first to c.second + 1; val se = c.first + 1 to c.second + 1
			if (e in shape) canvas.rect(left(c) + r, top(c), 4, r, fill)
			if (s in shape) canvas.rect(left(c), top(c) + r, r, 4, fill)
			if (e in shape && s in shape && se in shape) canvas.rect(left(c) + r, top(c) + r, 4, 4, fill)
		}
	}

	fun footprintsOf(
		colors: ByteArray, grid: Grid, connections: Set<DoorKey>, shapeOf: (Pair<Int, Int>) -> Collection<Pair<Int, Int>>?,
	): List<Set<Pair<Int, Int>>> {
		val px = Pixels(colors)
		val entered = HashSet<Pair<Int, Int>>(); val unknown = HashSet<Pair<Int, Int>>(); val grey = HashSet<Pair<Int, Int>>()
		for (c in cells(grid)) {
			val body = px[c.left + 1, c.top + 1]
			when {
				isBlank(body) -> unknown += c.col to c.row
				HypixelMapColours.isUnentered(body) -> { unknown += c.col to c.row; grey += c.col to c.row }
				else -> entered += c.col to c.row
			}
		}
		fun connected(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
			val (lo, hi) = if (a.first + a.second <= b.first + b.second) a to b else b to a
			val east = hi.first > lo.first
			if (DoorKey(lo.first, lo.second, east) in connections) return true
			val gx = grid.x + lo.first * grid.step; val gy = grid.y + lo.second * grid.step
			return doorStripColour(colors, grid.room, gx, gy, east) != null
		}
		return frontierFootprints(entered, unknown, ::connected, seeds = grey, shapeOf = shapeOf)
	}

	fun shownBlockDoors(colors: ByteArray, grid: Grid, blockDoors: Map<DoorKey, Int>): Map<DoorKey, Int> {
		if (blockDoors.isEmpty()) return blockDoors
		val px = Pixels(colors)
		fun entered(col: Int, row: Int): Boolean {
			val body = px[grid.x + col * grid.step + 1, grid.y + row * grid.step + 1]
			return !isBlank(body) && !HypixelMapColours.isUnentered(body)
		}
		return blockDoors.filter { (k, _) ->
			val (bc, br) = if (k.east) k.col + 1 to k.row else k.col to k.row + 1
			entered(k.col, k.row) || entered(bc, br)
		}
	}

	fun layoutPixels(
		colors: ByteArray, grid: Grid, ticks: Boolean = true,
		failed: Set<Pair<Int, Int>> = emptySet(), showFailed: Boolean = true, failArgb: Int = Tick.FAILED.argb,
		blockDoors: Map<DoorKey, Int> = emptyMap(), footprints: List<Set<Pair<Int, Int>>> = emptyList(),
		noCoalDoors: Set<DoorKey> = emptySet(), fairyCells: Set<Pair<Int, Int>> = emptySet(),
	): IntArray {
		val px = Pixels(colors)
		val canvas = Canvas()
		val r = grid.room
		val half = r / 2
		for (shape in footprints) drawFootprint(canvas, grid, shape)
		val inFootprint = footprints.flatten().toSet()
		val shownDoors = shownBlockDoors(colors, grid, blockDoors)

		fun mapDoor(key: DoorKey, pixel: Int): Int = if (pixel / 4 == 29 &&
			(key in noCoalDoors || (key.col to key.row) in fairyCells || (if (key.east) key.col + 1 to key.row else key.col to key.row + 1) in fairyCells)
		) HypixelMapColours.NORMAL else pixel

		val bloodShown = cells(grid).any { px[it.left + 1, it.top + 1] == HypixelMapColours.BLOOD }
		for (c in cells(grid)) {
			val body = px[c.left + 1, c.top + 1]

			val realEast = shownDoors[DoorKey(c.col, c.row, true)]
			val realSouth = shownDoors[DoorKey(c.col, c.row, false)]
			if (isBlank(body) || (c.col to c.row) in inFootprint && HypixelMapColours.isUnentered(body)) {

				fun explored(x: Int, y: Int) = px[x, y].let { !isBlank(it) && !HypixelMapColours.isUnentered(it) }
				val east = (realEast ?: px[c.left + r + 1, c.top + half].takeUnless(::isBlank)?.let { mapDoor(DoorKey(c.col, c.row, true), it) })
					?.takeIf { (it != HypixelMapColours.BLOOD || bloodShown || realEast == HypixelMapColours.BLOOD) && explored(c.left + grid.step + 1, c.top + 1) }
				val south = (realSouth ?: px[c.left + half, c.top + r + 1].takeUnless(::isBlank)?.let { mapDoor(DoorKey(c.col, c.row, false), it) })
					?.takeIf { (it != HypixelMapColours.BLOOD || bloodShown || realSouth == HypixelMapColours.BLOOD) && explored(c.left + 1, c.top + grid.step + 1) }
				east?.let { canvas.rect(c.left + r, c.top + half - 2, 4, 4, argbOf(it)) }
				south?.let { canvas.rect(c.left + half - 2, c.top + r, 4, 4, argbOf(it)) }
				continue
			}

			val grey = HypixelMapColours.isUnentered(body)
			val fill = argbOf(body)
			canvas.rect(c.left, c.top, r, r, fill)

			if (realEast != null) canvas.rect(c.left + r, c.top + half - 2, 4, 4, argbOf(realEast))
			else if (!isBlank(px[c.left + r + 1, c.top + half])) {
				val door = argbOf(mapDoor(DoorKey(c.col, c.row, true), px[c.left + r + 1, c.top + half]))
				if (px[c.left + r + 1, c.top + 1] == body) canvas.rect(c.left + r, c.top, 4, r, fill)
				else canvas.rect(c.left + r, c.top + half - 2, 4, 4, door)
			}
			if (realSouth != null) canvas.rect(c.left + half - 2, c.top + r, 4, 4, argbOf(realSouth))
			else if (!isBlank(px[c.left + half, c.top + r + 1])) {
				val door = argbOf(mapDoor(DoorKey(c.col, c.row, false), px[c.left + half, c.top + r + 1]))
				if (px[c.left + 1, c.top + r + 1] == body) canvas.rect(c.left, c.top + r, r, 4, fill)
				else canvas.rect(c.left + half - 2, c.top + r, 4, 4, door)
			}

			val key = c.col to c.row
			if (grey && key !in failed) continue
			val mark = displayTick(colors, grid, key, failed, showFailed) ?: continue
			val cx = c.left + half
			val cy = c.top + half
			when {
				mark == Tick.FAILED -> drawTick(canvas::set, cx, cy, mark, failArgb)
				ticks -> drawTick(canvas::set, cx, cy, mark)
				else -> canvas.rect(cx - 2, cy - 2, 4, 4, argbOf(px[cx, cy]))
			}
		}
		return canvas.out
	}

	fun rawPixels(colors: ByteArray, hideEmpty: Boolean, grid: Grid?, failed: Set<Pair<Int, Int>>, failArgb: Int): IntArray {
		val px = Pixels(colors)
		val canvas = Canvas()
		for (y in 0 until SIZE) for (x in 0 until SIZE) {
			val id = px[x, y]
			canvas.set(x, y, if (isBlank(id) && hideEmpty) 0 else MapColor.getColorFromPackedId(id))
		}
		if (grid != null) for ((col, row) in failed)
			drawTick(canvas::set, grid.x + col * grid.step + grid.room / 2, grid.y + row * grid.step + grid.room / 2, Tick.FAILED, failArgb)
		return canvas.out
	}

	private val textureId = Apex.identifier("dungeon_map")
	private var texture: DynamicTexture? = null
	private var uploadedKey = 0

	private fun texture(): DynamicTexture = texture ?: DynamicTexture({ textureId.path }, SIZE, SIZE, true).also {
		Client.textures.register(textureId, it)
		texture = it
	}

	private fun textureKey(colors: ByteArray, failArgb: Int): Int {
		var flags = 0
		if (TConfig.roomLayout) flags += 1
		if (TConfig.roomTicks) flags += 2
		if (TConfig.puzzleFailX) flags += 4
		return (((colors.contentHashCode() * 31 + flags) * 31 + PuzzleFailMarks.state.version) * 31 + failArgb) * 31 + blockDoors.hashCode()
	}

	private val shapeCache = HashMap<Pair<Int, Int>, Pair<Long, List<Pair<Int, Int>>>>()
	private const val SHAPE_MS = 2_000L

	private fun liveShape(cell: Pair<Int, Int>): List<Pair<Int, Int>>? {
		val level = Client.level ?: return null
		val now = System.currentTimeMillis()
		shapeCache[cell]?.takeIf { now - it.first < SHAPE_MS }?.let { return it.second }
		val kind = DungeonRoomTracker.blockKind(level)
		val roofs = HashMap<Pair<Int, Int>, Int?>()
		val cells = RoomCornerFinder.roomCells(cell.first, cell.second,
			{ x, z -> roofs.getOrPut(x to z) { RoomCornerFinder.scanRoof(x, z, kind) } }, kind)
		shapeCache[cell] = now to cells
		return cells
	}

	private fun liveFootprints(colors: ByteArray, g: Grid?): List<Set<Pair<Int, Int>>> {
		if (g == null || !TConfig.roomLayout || !TConfig.roomFootprints || !DungeonUtil.isInDungeonIsland) {
			shapeCache.clear(); return emptyList()
		}
		return footprintsOf(colors, g, scannedConnections, ::liveShape)
	}

	@Volatile var noCoalDoors: Set<DoorKey> = emptySet()

	private var fairyAt = 0L
	private var fairyCache: Set<Pair<Int, Int>> = emptySet()

	private fun liveFairyCells(): Set<Pair<Int, Int>> {
		val level = Client.level
		if (level == null || !DungeonUtil.isInDungeonIsland) { fairyCache = emptySet(); return fairyCache }
		val now = System.currentTimeMillis()
		if (now - fairyAt < SHAPE_MS) return fairyCache
		fairyAt = now
		val out = HashSet<Pair<Int, Int>>()
		for (i in 0 until 6) for (j in 0 until 6) if (DungeonRoomTracker.roomTypeAt(level, i to j) == "FAIRY") out += i to j
		fairyCache = out
		return out
	}

	@Volatile var scannedConnections: Set<DoorKey> = emptySet()

	private fun refreshTexture(colors: ByteArray) {
		val failArgb = TConfig.puzzleFailXColour.getEffectiveColourRGB()
		val failed = PuzzleFailMarks.cells()
		val g = detectGrid(colors)
		val footprints = liveFootprints(colors, g)
		val fairy = liveFairyCells()
		val key = ((textureKey(colors, failArgb) * 31 + footprints.hashCode()) * 31 + noCoalDoors.hashCode()) * 31 + fairy.hashCode()
		val tex = texture()
		if (key == uploadedKey) return
		uploadedKey = key
		grid = g
		val argb = if (TConfig.roomLayout && g != null) layoutPixels(colors, g, TConfig.roomTicks, failed, TConfig.puzzleFailX, failArgb, blockDoors, footprints, noCoalDoors, fairy)
		else rawPixels(colors, TConfig.hideEmpty, g, failed, failArgb)
		val img = tex.pixels
		for (i in argb.indices) img.setPixel(i % SIZE, i / SIZE, argb[i])
		tex.upload()
	}

	fun doorPixel(a: Pair<Int, Int>, b: Pair<Int, Int>): Int? {
		val player = Client.player ?: return null
		val level = Client.level ?: return null
		val stack = player.inventory.getItem(8)
		if (!stack.`is`(Items.FILLED_MAP)) return null
		val colors = MapItem.getSavedData(stack, level)?.colors ?: return null
		val g = detectGrid(colors) ?: return null
		val cal = calibrator.calibration ?: return null
		return doorPixelOf(colors, MapLayout.MapFrame(cal.originX, cal.originY, g.step, g.room), a, b)
	}

	fun doorPixelOf(colors: ByteArray, f: MapLayout.MapFrame, a: Pair<Int, Int>, b: Pair<Int, Int>): Int? =
		doorStripColour(colors, f.room, f.x(a), f.y(a), east = b.first > a.first)

	private fun markerPx(b: Byte) = (b + 128) / 2.0

	fun pairMarkers(markers: List<Pair<Double, Double>>, expected: List<Pair<Double, Double>>): Map<Int, Int> {
		val free = markers.indices.toMutableList()
		val out = LinkedHashMap<Int, Int>()
		for ((pi, e) in expected.withIndex()) {
			if (free.isEmpty()) break
			fun d2(i: Int) = (markers[i].first - e.first).let { it * it } + (markers[i].second - e.second).let { it * it }
			val pick = free.minBy(::d2)
			if (d2(pick) > 9.0) continue
			out[pi] = pick
			free.remove(pick)
		}
		return out
	}

	fun matchHeads(
		self: net.minecraft.client.player.AbstractClientPlayer,
		selfMarker: MapDecoration?,
		decorations: List<MapDecoration>,
	): Map<MapDecoration, net.minecraft.client.player.AbstractClientPlayer> {
		val out = LinkedHashMap<MapDecoration, net.minecraft.client.player.AbstractClientPlayer>()
		if (selfMarker == null) return out
		out[selfMarker] = self
		val g = grid ?: return out
		val level = Client.level ?: return out
		val scale = g.step / 32.0
		val others = decorations.filter { it !== selfMarker }
		val players = level.players().filter { it !== self && !it.isInvisible }
		val sx = markerPx(selfMarker.x())
		val sy = markerPx(selfMarker.y())
		val pairs = pairMarkers(
			others.map { markerPx(it.x()) to markerPx(it.y()) },
			players.map { (sx + (it.x - self.x) * scale) to (sy + (it.z - self.z) * scale) },
		)
		for ((pi, mi) in pairs) out[others[mi]] = players[pi]
		return out
	}

	fun headAngle(yaw: Float): Float = ((yaw + 180F) % 360F + 360F) % 360F

	fun decorationYaw(rot: Int): Float = rot * 360F / 16F

	fun teammateYaw(entityYaw: Float?, decorationRot: Int): Float = entityYaw ?: decorationYaw(decorationRot)

	fun pipOffset(angle: Float, radius: Float): Pair<Float, Float> {
		val rad = Math.toRadians(angle.toDouble())
		return (radius * Math.sin(rad)).toFloat() to (-radius * Math.cos(rad)).toFloat()
	}

	const val PIP_RADIUS = 5F

	fun pipPixel(self: Boolean, angle: Float): Pair<Int, Int> {
		val (x, y) = pipOffset(if (self) 0F else angle, PIP_RADIUS)
		return Math.round(x) to Math.round(y)
	}

	fun <D> packetMarkers(decorations: List<D>, isSelf: (D) -> Boolean, selfLive: Boolean): List<D> =
		if (!selfLive) decorations else decorations.filter { !isSelf(it) }

	private val calibrator = MapSelfMarker.Calibrator()

	@Subscribe
	fun onWorld(event: dev.ashtonite365.apex.events.WorldReadyEvent) { calibrator.reset() }

	private fun ownPlacement(player: net.minecraft.client.player.LocalPlayer, frame: MapDecoration?, partial: Float): MapSelfMarker.Placement? {
		val fromPacket = frame?.let {
			Triple(MapSelfMarker.decorationPixel(it.x().toInt()), MapSelfMarker.decorationPixel(it.y().toInt()), decorationYaw(it.rot().toInt()))
		}
		val inBoss = dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonState.inBoss
		val cal = if (TConfig.instantSelfMarker && !inBoss)
			calibrator.update(grid, fromPacket?.first, fromPacket?.second, player.x, player.z)
		else calibrator.calibration
		return MapSelfMarker.place(
			TConfig.instantSelfMarker, cal, inBoss,
			MapSelfMarker.lerp(partial, player.xo, player.x),
			MapSelfMarker.lerp(partial, player.zo, player.z),
			MapSelfMarker.lerpYaw(partial, player.yRotO, player.yRot),
			fromPacket,
		)
	}

	private fun isFrame(d: MapDecoration) = d.type().value() == MapDecorationTypes.FRAME.value()

	private val DEG = (Math.PI / 180).toFloat()

	private fun shouldShow(): Boolean {
		if (!TConfig.enabled) return false
		return if (TConfig.onlyInRun) DungeonUtil.isInActiveDungeon else DungeonUtil.isInDungeonIsland
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!shouldShow()) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		val map = player.inventory.getItem(8)
		if (!map.`is`(Items.FILLED_MAP)) return
		val data = MapItem.getSavedData(map, level) ?: return
		refreshTexture(data.colors)
		val ctx = event.context
		val pose = ctx.pose()
		pose.pushMatrix()
		TConfig.hud.applyTo(pose)
		if (TConfig.background) ctx.fill(0, 0, SIZE, SIZE, 0x80000000.toInt())
		val frame = data.decorations.firstOrNull(::isFrame)
		val own = ownPlacement(player, frame, event.tickDelta.getGameTimeDeltaPartialTick(true))
		val live = own?.source == MapSelfMarker.Source.LIVE
		val moved = TConfig.centerOnPlayer || TConfig.rotateWithPlayer
		if (moved) ctx.enableScissor(0, 0, SIZE, SIZE)
		pose.pushMatrix()
		if (moved) {
			pose.translate(SIZE / 2F, SIZE / 2F)
			if (TConfig.rotateWithPlayer) pose.rotate(-((player.yRot + 180F) * DEG))
			if (TConfig.centerOnPlayer && own != null) pose.translate(-own.x.toFloat(), -own.y.toFloat())
			else pose.translate(-SIZE / 2F, -SIZE / 2F)
		}
		ctx.textureRegion(textureId, 0, 0, 0F, 0F, SIZE, SIZE, SIZE, SIZE)
		val decorations = data.decorations.toList()
		val heads = if (TConfig.playerHeads) matchHeads(player, frame, decorations) else emptyMap()
		if (TConfig.markers) {
			if (live && own != null) drawOwnLive(ctx, player, own, frame != null && heads[frame] != null)
			for (d in packetMarkers(decorations, ::isFrame, live)) {
				val x = markerPx(d.x()).toFloat()
				val y = markerPx(d.y()).toFloat()
				val who = heads[d]
				pose.pushMatrix()
				pose.translate(x, y)
				if (who != null) {
					val mine = who == player
					if (TConfig.headFacing && mine) pose.rotate(Math.toRadians(headAngle(player.yRot).toDouble()).toFloat())
					headSquare(ctx, who, if (mine) 0xFF00FF00.toInt() else 0xFF000000.toInt())
					if (TConfig.headFacing) pip(ctx, pipPixel(mine, if (mine) 0F else headAngle(teammateYaw(who.yRot, d.rot().toInt()))))
				} else {
					pose.rotate(decorationYaw(d.rot().toInt()) * DEG)
					arrow(ctx, if (isFrame(d)) 0xFF00FF00.toInt() else 0xFF3399FF.toInt())
				}
				pose.popMatrix()
			}
		}
		pose.popMatrix()
		if (moved) ctx.disableScissor()
		if (TConfig.border) ctx.outlineBox(-1, -1, SIZE + 1, SIZE + 1, 0xFF333333.toInt())
		pose.popMatrix()
	}

	private fun headSquare(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, who: net.minecraft.client.player.AbstractClientPlayer, frame: Int) {
		ctx.fill(-4, -4, 4, 4, frame)
		PlayerFaceExtractor.extractRenderState(ctx, who.skin, -3, -3, 6)
	}

	private fun pip(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, at: Pair<Int, Int>) =
		ctx.fill(at.first - 1, at.second - 1, at.first + 1, at.second + 1, 0xFFFFFFFF.toInt())

	private fun arrow(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, colour: Int) {
		ctx.fill(-2, -1, 2, 3, 0xFF000000.toInt())
		ctx.fill(-1, 0, 1, 2, colour)
		ctx.fill(-1, -3, 1, 0, colour)
	}

	private fun drawOwnLive(
		ctx: net.minecraft.client.gui.GuiGraphicsExtractor, player: net.minecraft.client.player.LocalPlayer,
		p: MapSelfMarker.Placement, asHead: Boolean,
	) {
		val pose = ctx.pose()
		pose.pushMatrix()
		pose.translate(p.x.toFloat(), p.y.toFloat())
		if (asHead) {
			if (TConfig.headFacing) pose.rotate(Math.toRadians(headAngle(p.rotation).toDouble()).toFloat())
			headSquare(ctx, player, 0xFF00FF00.toInt())
			if (TConfig.headFacing) pip(ctx, pipPixel(true, 0F))
		} else {
			pose.rotate(p.rotation * DEG)
			arrow(ctx, 0xFF00FF00.toInt())
		}
		pose.popMatrix()
	}
}
