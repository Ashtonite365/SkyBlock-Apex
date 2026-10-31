// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.dungeon

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.kit.dungeon.DungeonExtras.ClearMode
import dev.ashtonite365.apex.features.kit.dungeon.KeyHighlight
import dev.ashtonite365.apex.features.kit.dungeon.MageBeam
import dev.ashtonite365.apex.features.kit.dungeon.RoomClear

class KitDungeonTest {
	@Test
	fun cellMath() {
		val g = DungeonMap.Grid(5, 5, 16)

		assertEquals(0 to 0, RoomClear.cellOf(2 * 13 - 128, 2 * 13 - 128, g))
		assertEquals(1 to 2, RoomClear.cellOf(2 * 30 - 128, 2 * 50 - 128, g))
		assertNull(RoomClear.cellOf(2 * 23 - 128, 2 * 13 - 128, g))
		assertNull(RoomClear.cellOf(-128, -128, g))
	}

	@Test
	fun clearAlerts() {
		val W = DungeonMap.Tick.WHITE; val G = DungeonMap.Tick.GREEN
		assertEquals("§fRoom Cleared!", RoomClear.alertFor(null, W, ClearMode.BOTH))
		assertEquals("§aRoom Complete!", RoomClear.alertFor(W, G, ClearMode.BOTH))
		assertNull(RoomClear.alertFor(W, W, ClearMode.BOTH))
		assertNull(RoomClear.alertFor(null, W, ClearMode.GREEN))
		assertNull(RoomClear.alertFor(null, G, ClearMode.WHITE))
		assertNull(RoomClear.alertFor(G, W, ClearMode.BOTH))
	}

	@Test
	fun beamTracker() {
		val t = MageBeam.Tracker()
		for (i in 0..4) t.add(Vec3(i.toDouble(), 0.0, 0.0))
		t.add(Vec3(4.0, 5.0, 0.0))
		assertEquals(2, t.beams.size)
		val segs = t.segments(Vec3(-1.0, 0.0, 0.0), 3)
		assertEquals(listOf(Vec3(0.0, 0.0, 0.0) to Vec3(4.0, 0.0, 0.0)), segs)
		repeat(40) { t.advance(40) }
		assertEquals(0, t.beams.size)
	}

	@Test
	fun keys() {
		assertEquals(KeyHighlight.Key.BLOOD, KeyHighlight.keyOf("Blood Key"))
		assertNull(KeyHighlight.keyOf("Wither Key!"))
	}
}
