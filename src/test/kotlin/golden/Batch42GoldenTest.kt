// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.SecretRooms
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch42GoldenTest {
	@Test
	fun rooms() {
		val S = Batch42Rooms
		val out = mutableListOf<Pair<String, String>>()
		out += "data" to "${SecretRooms.rooms.size} ${SecretRooms.rooms.hashCode()} ${SecretRooms.byName.keys.toList().hashCode()} ${SecretRooms.IGNORED_CORE} ${SecretRooms.RESOURCE}"
		for (room in SecretRooms.rooms) {
			out += "room|${room.name}" to "$room ${room.tileAmount}"
			for (c in room.cores) out += "info|$c" to "${SecretRooms.info(c)?.name}"
		}
		for (c in listOf(0, 1, -1, SecretRooms.IGNORED_CORE, Int.MAX_VALUE)) out += "info|$c" to "${SecretRooms.info(c)}"
		for (s in S.shapes) out += "tiles|$s" to "${SecretRooms.Info("n", "t", s, emptyList(), null, null).tileAmount}"
		for ((i, j) in S.jsons.withIndex()) out += "parse|$i" to Golden.run { SecretRooms.parse(j).toString() }
		for ((i, col) in S.columns.withIndex()) {
			val calls = mutableListOf<Int>()
			val res = SecretRooms.core { y -> calls += y; val (id, air) = col[140 - y]; SecretRooms.ColumnBlock(id, air) }
			out += "core|$i" to "$res ${calls.size} ${calls.lastOrNull()}"
		}
		out += "core|empty" to "${SecretRooms.core { SecretRooms.ColumnBlock("minecraft:air", true) }}"
		out += "bs" to SecretRooms.blockString("minecraft:stone")
		for ((i, g) in S.geomCases.withIndex()) {
			val (shape, cells) = g
			val rot = Golden.run { SecretRooms.geometryRotation(shape, cells).toString() }
			val clay = SecretRooms.Rotation.entries.joinToString(";") { r -> Golden.run { SecretRooms.clayPos(shape, cells, r).toString() } }
			out += "geom|$i" to "$rot $clay ${cells.firstOrNull()?.let { SecretRooms.cellOrigin(it) }}"
		}
		for (r in SecretRooms.Rotation.entries) for ((x, y, z) in S.coords) {
			out += "rot|$r|$x|$z" to "${SecretRooms.rotateAroundNorth(x, z, r)} ${SecretRooms.rotateToNorth(x, z, r)}"
			val d = SecretRooms.Detected(SecretRooms.rooms.first(), r, 17, -40, emptyList(), 0, "w")
			out += "det|$r|$x|$y|$z" to "${d.toReal(x, y, z)} ${d.toRelative(x, y, z)}"
		}
		val rooms = SecretRooms.rooms
		for ((i, c) in S.detCases.withIndex()) {
			val anchorRoom = c.choice.firstOrNull { it >= 0 }
			val readLog = mutableListOf<String>()
			val clayLog = mutableListOf<String>()
			val res = Golden.run {
				SecretRooms.detect(c.cells, { cell ->
					val idx = c.cells.indexOf(cell)
					val ch = c.choice[idx].let { if (c.sameRoom && it >= 0 && anchorRoom != null) anchorRoom else it }
					readLog += "$cell"
					when (ch) {
						-1 -> null
						-2 -> 123456 to c.highest
						-3 -> SecretRooms.IGNORED_CORE to c.highest
						else -> rooms[ch % rooms.size].let { it.cores.getOrElse(idx % maxOf(1, it.cores.size)) { 7 } } to c.highest
					}
				}, { x, y, z -> clayLog += "$x,$y,$z"; val bit = clayLog.size - 1; (c.clayMask shr bit) and 1 == 1 }).toString()
			}
			out += "detect|$i" to "$res ${readLog.joinToString(";")} ${clayLog.joinToString(";")}"
		}
		val n = Golden.verify("batch42-rooms", out, minCases = 8440)
		assertTrue(n >= 8440)
	}
}
