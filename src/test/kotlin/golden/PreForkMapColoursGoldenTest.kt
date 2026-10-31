// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.world.level.material.MapColor
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class PreForkMapColoursGoldenTest {
	@Test fun roomAndDoorColoursMatchThePreForkMap() {
		val size = DungeonMap.SIZE
		var rooms = 0; var doors = 0
		for (m in Batch10Maps.maps) {
			val c = m.colors
			val g = DungeonMap.detectGrid(c) ?: continue
			fun packed(x: Int, y: Int) = if (x in 0 until size && y in 0 until size) c[x + y * size].toInt() and 0xFF else 0
			fun colourOf(p: Int) = if (p / 4 == 0) 0 else MapColor.getColorFromPackedId(p)
			val out = DungeonMap.layoutPixels(c, g, false)
			fun at(x: Int, y: Int, want: Int) = if (x in 0 until size && y in 0 until size) out[x + y * size] else want
			var gy = g.y
			while (gy < size) {
				var gx = g.x
				while (gx < size) {
					val room = packed(gx + 1, gy + 1)
					if (room / 4 != 0 && gx + 1 < size && gy + 1 < size) {
						assertEquals(colourOf(room), at(gx, gy, colourOf(room)), "room at $gx,$gy grid $g"); rooms++
						val right = packed(gx + g.room + 1, gy + g.room / 2)
						if (right / 4 != 0 && packed(gx + g.room + 1, gy + 1) != room && gx + g.room + 3 < size) {
							for (dy in -2..1) for (dx in 0 until 4) assertEquals(colourOf(right), at(gx + g.room + dx, gy + g.room / 2 + dy, colourOf(right)), "east door $gx,$gy +$dx,$dy grid $g"); doors++
						}
						val below = packed(gx + g.room / 2, gy + g.room + 1)
						if (below / 4 != 0 && packed(gx + 1, gy + g.room + 1) != room && gy + g.room + 3 < size) {
							for (dy in 0 until 4) for (dx in -2..1) assertEquals(colourOf(below), at(gx + g.room / 2 + dx, gy + g.room + dy, colourOf(below)), "south door $gx,$gy +$dx,$dy grid $g"); doors++
						}
					}
					gx += g.step
				}
				gy += g.step
			}
		}
		assertTrue(rooms > 500, "rooms=$rooms")
		assertTrue(doors > 100, "doors=$doors")
	}
}
