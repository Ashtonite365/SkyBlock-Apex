/*
 * SPDX-License-Identifier: GPL-3.0-or-later AND BSD-3-Clause
 *
 * SkyBlock Apex: dungeon room identity data + room detection. The one room detection in Apex; DungeonRoomTracker runs
 * it on the live world and the Secrets features, Breakable Walls read it.
 *
 * Data (bundled, read from the jar only): assets/apex/dungeon-rooms/secret-rooms.json, built offline by
 * tools/build-secret-rooms.py: room names, types, shapes, core hashes, secret and crypt counts. No positions.
 *
 * The core hash format (String.hashCode of a cell's core column at x/z = cell * 32 - 185, y 140 down to 12, plus the
 * highest block), the clay corner / rotation rules and the room list follow a third-party BSD-3-Clause source
 * (CREDITS.md, NOTICE, LICENSES/BSD-3-Clause.txt).
 * Apex differences: the room's cells come from RoomCornerFinder (roof + wall gaps) instead of map tiles, one known
 * core among them is enough, and the block id string is built from the registry id ("Block{minecraft:stone}").
 */
package dev.ashtonite365.apex.features.dungeon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SecretRooms {

	enum class Rotation(val dx: Int, val dz: Int) {
		NORTH(15, 15), SOUTH(-15, -15), WEST(15, -15), EAST(-15, 15),
	}

	data class Info(
		val name: String, val type: String, val shape: String, val cores: List<Int>, val maxSecrets: Int?, val crypts: Int?,
	) {
		val tileAmount get() = SHAPE_TILES[shape] ?: 4
	}

	private val SHAPE_TILES = mapOf("1x1" to 1, "1x2" to 2, "1x3" to 3, "L" to 3)

	const val RESOURCE = "/assets/apex/dungeon-rooms/secret-rooms.json"

	private fun JsonObject.present(key: String): JsonElement? = get(key)?.let { if (it is JsonNull) null else it }

	private fun readRoom(entry: JsonElement): Info {
		val obj = entry as JsonObject
		val name = obj["name"]!!.jsonPrimitive.content
		val type = obj.present("type")?.jsonPrimitive?.content ?: "NORMAL"
		val shape = obj["shape"]!!.jsonPrimitive.content
		val cores = obj["cores"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
		val secrets = obj.present("maxSecrets")?.jsonPrimitive?.intOrNull
		val crypts = obj.present("crypts")?.jsonPrimitive?.intOrNull
		return Info(name, type, shape, cores, secrets, crypts)
	}

	fun parse(json: String): List<Info> {
		val top = Json.parseToJsonElement(json).jsonObject
		val list = top["rooms"] as JsonArray
		val result = ArrayList<Info>(list.size)
		for (entry in list) result.add(readRoom(entry))
		return result
	}

	val rooms: List<Info> by lazy {
		val stream = SecretRooms::class.java.getResourceAsStream(RESOURCE)
		stream?.reader()?.use { parse(it.readText()) } ?: emptyList()
	}
	val byName: Map<String, Info> by lazy { rooms.associateBy { it.name } }
	private val coreIndex: Map<Int, Info> by lazy {
		val index = LinkedHashMap<Int, Info>()
		for (room in rooms) for (c in room.cores) index[c] = room
		index
	}

	fun info(core: Int): Info? = coreIndex[core]

	const val IGNORED_CORE = -318865360

	data class ColumnBlock(val id: String, val air: Boolean)

	private const val TOP_Y = 140
	private const val BOTTOM_Y = 12
	private const val GOLD = "minecraft:gold_block"
	private const val BEDROCK = "minecraft:bedrock"
	private val LEFT_OUT = setOf("minecraft:oak_planks", "minecraft:trapped_chest", "minecraft:chest")

	fun blockString(id: String) = "Block{$id}"

	fun core(block: (Int) -> ColumnBlock): Pair<Int, Int> {
		val text = StringBuilder(1024)
		var top: Int? = null
		var bedrockRun = 0
		var y = TOP_Y
		while (y >= BOTTOM_Y) {
			val b = block(y)
			if (top == null && (b.air || b.id == GOLD)) {
				text.append('0')
			} else {
				if (top == null) top = y
				if (b.air && bedrockRun >= 2 && y < 69) {
					text.append("0".repeat(y - (BOTTOM_Y - 1)))
					break
				}
				val isBedrock = b.id == BEDROCK
				bedrockRun = if (isBedrock) bedrockRun + 1 else 0
				if (isBedrock || b.id !in LEFT_OUT) text.append(blockString(b.id))
			}
			y--
		}
		return text.toString().hashCode() to (top ?: 0)
	}

	fun cellOrigin(cell: Pair<Int, Int>) = cell.first * 32 - 185 to cell.second * 32 - 185

	private val L_ROTATION = arrayOf(Rotation.EAST, Rotation.SOUTH, Rotation.NORTH, Rotation.WEST)

	private fun missingCorner(cells: List<Pair<Int, Int>>): Pair<Int, Int>? {
		val xs = cells.map { it.first }
		val zs = cells.map { it.second }
		val x0 = xs.min(); val x1 = xs.max(); val z0 = zs.min(); val z1 = zs.max()
		if (x1 - x0 != 1 || z1 - z0 != 1) return null
		var found: Pair<Int, Int>? = null
		for (corner in listOf(x0 to z0, x1 to z0, x0 to z1, x1 to z1)) {
			if (corner in cells) continue
			if (found != null) return null
			found = corner
		}
		return found
	}

	fun geometryRotation(shape: String, cells: List<Pair<Int, Int>>): Rotation? {
		val minX = cells.minOf { it.first }
		if (shape == "L") {
			val gap = missingCorner(cells) ?: return null
			val atMaxX = gap.first == cells.maxOf { it.first }
			val atMaxZ = gap.second == cells.maxOf { it.second }
			return L_ROTATION[(if (atMaxX) 1 else 0) + (if (atMaxZ) 2 else 0)]
		}
		var last = cells[0]
		var lastKey = last.first * 1000 + last.second
		for (i in 1 until cells.size) {
			val k = cells[i].first * 1000 + cells[i].second
			if (k > lastKey) { last = cells[i]; lastKey = k }
		}
		return if (last.first == minX) Rotation.WEST else Rotation.SOUTH
	}

	fun clayPos(shape: String, cells: List<Pair<Int, Int>>, rotation: Rotation): Pair<Int, Int>? {
		val (lowX, lowZ) = cellOrigin(cells.minOf { it.first } to cells.minOf { it.second })
		if (shape != "L") return (if (rotation == Rotation.WEST) lowX + 15 else lowX - 15) to lowZ - 15
		val (highX, highZ) = cellOrigin(cells.maxOf { it.first } to cells.maxOf { it.second })
		val x = if (rotation == Rotation.WEST || rotation == Rotation.NORTH) highX + 15 else lowX - 15
		val z = if (rotation == Rotation.NORTH || rotation == Rotation.EAST) highZ + 15 else lowZ - 15
		return x to z
	}

	fun rotateAroundNorth(x: Int, z: Int, r: Rotation) = when (r) {
		Rotation.SOUTH -> x to z
		Rotation.NORTH -> -x to -z
		Rotation.WEST -> -z to x
		Rotation.EAST -> z to -x
	}

	fun rotateToNorth(x: Int, z: Int, r: Rotation) = when (r) {
		Rotation.SOUTH -> x to z
		Rotation.NORTH -> -x to -z
		Rotation.WEST -> z to -x
		Rotation.EAST -> -z to x
	}

	data class Detected(val info: Info, val rotation: Rotation, val clayX: Int, val clayZ: Int, val cells: List<Pair<Int, Int>>, val core: Int, val why: String) {

		fun toReal(x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
			val (a, b) = rotateAroundNorth(x, z, rotation)
			return Triple(a + clayX, y, b + clayZ)
		}

		fun toRelative(x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
			val (a, b) = rotateToNorth(x - clayX, z - clayZ, rotation)
			return Triple(a, y, b)
		}
	}

	sealed interface Result {
		data class Found(val room: Detected) : Result

		data class Unknown(val cores: List<Pair<Pair<Int, Int>, Int>>, val waiting: List<Pair<Int, Int>>) : Result
		data class NotPlaced(val info: Info, val why: String) : Result
	}

	fun detect(cells: List<Pair<Int, Int>>, coreOf: (Pair<Int, Int>) -> Pair<Int, Int>?, isClay: (Int, Int, Int) -> Boolean): Result {
		val scanned = LinkedHashMap<Pair<Int, Int>, Pair<Int, Int>?>()
		for (c in cells) scanned[c] = coreOf(c)

		var anchor: Pair<Int, Int>? = null
		var anchorRead: Pair<Int, Int>? = null
		var room: Info? = null
		for (c in cells) {
			val r = scanned[c] ?: continue
			val known = info(r.first) ?: continue
			anchor = c; anchorRead = r; room = known
			break
		}
		if (room == null || anchor == null || anchorRead == null) {
			val cores = ArrayList<Pair<Pair<Int, Int>, Int>>()
			val waiting = ArrayList<Pair<Int, Int>>()
			for ((c, r) in scanned) if (r == null) waiting.add(c) else cores.add(c to r.first)
			return Result.Unknown(cores, waiting)
		}
		val (core, highest) = anchorRead
		val need = room.tileAmount
		val tiles = if (cells.size == need) cells else {
			val own = cells.filter { c -> scanned[c]?.let { info(it.first) } == room }
			if (own.size != need) return Result.NotPlaced(room, "cells ${cells.size} != shape ${room.shape} ($need)")
			own
		}
		val why = "core $core of cell ${anchor.first},${anchor.second}"
		val (ox, oz) = cellOrigin(tiles.first())
		if (room.name == "Fairy") {
			val r = Rotation.SOUTH
			return Result.Found(Detected(room, r, ox + r.dx, oz + r.dz, tiles, core, "$why, Fairy is always SOUTH"))
		}
		if (need == 1) {
			val r = Rotation.entries.firstOrNull { isClay(ox + it.dx, highest, oz + it.dz) }
				?: return Result.NotPlaced(room, "no blue terracotta at the 4 corners at y=$highest")
			return Result.Found(Detected(room, r, ox + r.dx, oz + r.dz, tiles, core, "$why, blue terracotta at y=$highest"))
		}
		val rot = geometryRotation(room.shape, tiles) ?: return Result.NotPlaced(room, "no rotation for ${room.shape} cells $tiles")
		val (x, z) = clayPos(room.shape, tiles, rot) ?: return Result.NotPlaced(room, "no clay for cells $tiles")
		return Result.Found(Detected(room, rot, x, z, tiles, core, "$why, ${room.shape} layout"))
	}
}
