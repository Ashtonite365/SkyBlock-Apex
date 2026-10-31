// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms
import dev.ashtonite365.apex.features.dungeon.SecretRooms
import dev.ashtonite365.apex.features.dungeon.SecretRooms.ColumnBlock
import dev.ashtonite365.apex.features.dungeon.SecretRooms.Result
import dev.ashtonite365.apex.features.dungeon.SecretRooms.Rotation

class SecretRoomsTest {
	private fun b(id: String) = ColumnBlock(id, id == "minecraft:air")

	@Test
	fun coreHashOnASimulated26_3Column() {

		val col = { y: Int ->
			when {
				y > 101 -> b("minecraft:air"); y == 101 -> b("minecraft:gold_block"); y == 100 -> b("minecraft:stone_bricks")
				y == 99 -> b("minecraft:iron_chain"); y in 90..98 -> b("minecraft:cracked_stone_bricks"); y == 89 -> b("minecraft:oak_planks")
				y == 88 -> b("minecraft:chest"); y in 70..87 -> b("minecraft:stone"); y in 67..69 -> b("minecraft:bedrock"); else -> b("minecraft:air")
			}
		}
		val expected = "0".repeat(40) + "Block{minecraft:stone_bricks}" + "Block{minecraft:iron_chain}" +
			"Block{minecraft:cracked_stone_bricks}".repeat(9) + "Block{minecraft:stone}".repeat(18) + "Block{minecraft:bedrock}".repeat(3) + "0".repeat(66 - 11)
		assertEquals(expected.hashCode() to 100, SecretRooms.core(col))
	}

	@Test
	fun knownKitCores() {
		assertEquals(140, SecretRooms.rooms.size)
		for ((core, name) in listOf(884966227 to "Pirate", 252263088 to "Pirate", 2061161472 to "Pirate", -1779299819 to "Black Flag",
			518379920 to "Admin", 902780459 to "Withermancer", -1879625096 to "Criss-Cross"))
			assertEquals(name, SecretRooms.info(core)?.name, "core $core")
		assertNull(SecretRooms.info(SecretRooms.IGNORED_CORE))

		val all = SecretRooms.rooms.flatMap { it.cores }
		assertEquals(all.size, all.toSet().size)
	}

	@Test
	fun detectsMultiCellRoomsFromOneKnownCore() {
		val cells = listOf(2 to 2, 3 to 2, 2 to 3)

		val r = SecretRooms.detect(cells, { if (it == (2 to 2)) 884966227 to 90 else null }) { _, _, _ -> false }
		val room = (r as Result.Found).room
		assertEquals("Pirate", room.info.name)
		assertEquals(Rotation.WEST, room.rotation)
		assertEquals(-74 to -136, room.clayX to room.clayZ)
		assertTrue("core 884966227" in room.why)

		val u = SecretRooms.detect(cells, { if (it == (2 to 2)) 42 to 90 else null }) { _, _, _ -> false } as Result.Unknown
		assertEquals(listOf((2 to 2) to 42), u.cores)
		assertEquals(listOf(3 to 2, 2 to 3), u.waiting)

		assertTrue(SecretRooms.detect(listOf(2 to 2, 3 to 2), { 884966227 to 90 }) { _, _, _ -> false } is Result.NotPlaced)
	}

	@Test
	fun lRoomsInAllFourOrientations() {
		val l = listOf(
			listOf(2 to 2, 3 to 2, 2 to 3) to (Rotation.WEST to (-74 to -136)),
			listOf(2 to 2, 3 to 2, 3 to 3) to (Rotation.NORTH to (-74 to -74)),
			listOf(2 to 2, 2 to 3, 3 to 3) to (Rotation.SOUTH to (-136 to -136)),
			listOf(3 to 2, 2 to 3, 3 to 3) to (Rotation.EAST to (-136 to -74)),
		)
		for ((cells, want) in l) {
			val rot = SecretRooms.geometryRotation("L", cells)
			assertEquals(want.first, rot, "$cells")
			assertEquals(want.second, SecretRooms.clayPos("L", cells, rot!!), "$cells")
		}
	}

	@Test
	fun detects1x1ByBlueTerracottaAndFairy() {
		val r = SecretRooms.detect(listOf(2 to 3), { -1779299819 to 84 }) { x, y, z -> y == 84 && x == 2 * 32 - 185 - 15 && z == 3 * 32 - 185 + 15 }
		assertEquals(Rotation.EAST, (r as Result.Found).room.rotation)
		assertTrue(SecretRooms.detect(listOf(2 to 3), { -1779299819 to 84 }) { _, _, _ -> false } is Result.NotPlaced)
		val fairy = SecretRooms.rooms.first { it.name == "Fairy" }
		assertEquals(Rotation.SOUTH, (SecretRooms.detect(listOf(1 to 1), { fairy.cores.first() to 80 }) { _, _, _ -> false } as Result.Found).room.rotation)
	}

	@Test
	fun rotationRoundTripsAndEqualsTheDungeonRoomsFrame() {
		val info = SecretRooms.byName["Andesite"]!!
		for (r in Rotation.entries) for ((x, z) in listOf(1 to 28, 6 to 22, 30 to 0, 0 to 30, 13 to 7)) {
			val d = SecretRooms.Detected(info, r, -104, -74, listOf(3 to 3), 0, "")
			val real = d.toReal(x, 69, z)
			assertEquals(Triple(x, 69, z), d.toRelative(real.first, real.second, real.third), "$r $x $z")
			assertEquals(real, DungeonRooms.frameOf(d).toReal(BlockPos(x, 69, z)).let { Triple(it.x, it.y, it.z) }, "$r $x $z")
		}

		val live = SecretRooms.Detected(info, Rotation.EAST, -104, -74, listOf(3 to 3), 0, "")
		assertEquals(Triple(-76, 69, -75), live.toReal(1, 69, 28))
	}

	private val layouts = mapOf(
		"1x1" to listOf(listOf(2 to 2)),
		"1x2" to listOf(listOf(2 to 2, 3 to 2), listOf(2 to 2, 2 to 3)),
		"1x3" to listOf(listOf(1 to 2, 2 to 2, 3 to 2), listOf(2 to 1, 2 to 2, 2 to 3)),
		"1x4" to listOf(listOf(1 to 2, 2 to 2, 3 to 2, 4 to 2), listOf(2 to 1, 2 to 2, 2 to 3, 2 to 4)),
		"2x2" to listOf(listOf(2 to 2, 3 to 2, 2 to 3, 3 to 3)),
		"L" to listOf(listOf(2 to 2, 3 to 2, 2 to 3), listOf(2 to 2, 3 to 2, 3 to 3), listOf(2 to 2, 2 to 3, 3 to 3), listOf(3 to 2, 2 to 3, 3 to 3)),
	)

	@Test
	fun clayCornerAndInteriorLandInsideTheRoomInEveryLayout() {
		var checked = 0
		for (info in SecretRooms.rooms) {
			for (cells in layouts.getValue(info.shape)) {
				val rots = if (info.shape == "1x1") Rotation.entries else listOf(SecretRooms.geometryRotation(info.shape, cells)!!)
				for (r in rots) {
					val (cx, cz) = if (info.shape == "1x1") SecretRooms.cellOrigin(cells[0]).let { (x, z) -> x + r.dx to z + r.dz }
					else SecretRooms.clayPos(info.shape, cells, r)!!
					val d = SecretRooms.Detected(info, r, cx, cz, cells, 0, "")

					for ((x, z) in listOf(0 to 0, 1 to 1, 2 to 3)) {
						val (rx, _, rz) = d.toReal(x, 70, z)
						assertTrue(cells.any { (i, j) -> rx in -200 + 32 * i..-200 + 32 * i + 31 && rz in -200 + 32 * j..-200 + 32 * j + 31 },
							"${info.name} $r $cells ($x, $z) -> ($rx, $rz)")
						checked++
					}
				}
			}
		}
		assertTrue(checked > 140 * 3, "checked $checked")
	}

	@Test
	fun bundledDataHasIdentityOnly() {
		val rooms = SecretRooms.rooms
		assertEquals(140, rooms.size)
		val json = SecretRooms::class.java.getResourceAsStream(SecretRooms.RESOURCE)!!.reader().readText()
		val root = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
		val allowed = setOf("name", "type", "shape", "cores", "maxSecrets", "crypts")
		for (r in root["rooms"] as kotlinx.serialization.json.JsonArray)
			assertTrue((r as kotlinx.serialization.json.JsonObject).keys.all { it in allowed }, "extra fields in $r")
		assertTrue("BSD-3-Clause" in root["_source"].toString())
		val wm = SecretRooms.byName["Withermancer"]!!
		assertEquals("L", wm.shape)
		assertEquals(34, SecretRooms.byName["Admin"]!!.crypts)
		assertEquals(6, SecretRooms.byName["Altar"]!!.maxSecrets)
		assertEquals(setOf("NORMAL", "PUZZLE", "RARE", "CHAMPION", "TRAP", "BLOOD", "ENTRANCE", "FAIRY"), rooms.map { it.type }.toSet())
	}

	@Test
	fun liveF6RoomsMatchTheLoggedClay() {
		data class Live(val name: String, val rot: Rotation, val clay: Pair<Int, Int>, val cell: Pair<Int, Int>)
		val live = listOf(
			Live("Duncan", Rotation.WEST, -74 to -40, 3 to 5),
			Live("Andesite", Rotation.EAST, -104 to -74, 3 to 3),
			Live("Cell", Rotation.SOUTH, -136 to -40, 2 to 5),
			Live("Water Board", Rotation.EAST, -40 to -42, 5 to 4),
			Live("Entrance", Rotation.EAST, -72 to -74, 4 to 3),
		)
		for (l in live) {
			SecretRooms.byName.getValue(l.name)
			assertEquals(l.clay, SecretRooms.cellOrigin(l.cell).let { (x, z) -> x + l.rot.dx to z + l.rot.dz }, l.name)
		}
	}
}
