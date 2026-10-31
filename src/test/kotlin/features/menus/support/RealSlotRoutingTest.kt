package dev.ashtonite365.apex.test.features.menus.support

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.InventoryPanel

class RealSlotRoutingTest {
	private val window = 54 + 36
	private val left = 40; private val top = 30
	private val src = listOf("src/main/kotlin", "../src/main/kotlin", "../../src/main/kotlin").map { File(it).absoluteFile.normalize() }.first { File(it, "util/ApexClickDelay.kt").isFile }

	@Test fun registeredChestCellsSitUnderTheirFramesAndHitTest() {
		val (ix, iy) = InventoryPanel.cellItemPos(120, 80)
		assertEquals(121 to 81, ix to iy)
		val cells = mapOf(9 to (ix to iy))
		assertEquals((ix - left) to (iy - top), InventoryPanel.slotPlacement(9, window, null, left, top, cells))
		assertNull(InventoryPanel.slotPlacement(10, window, null, left, top, cells))
		assertTrue(InventoryPanel.hitsSlot(9, window, null, ix, iy, ix + 8.0, iy + 8.0, cells))
		assertFalse(InventoryPanel.hitsSlot(10, window, null, ix, iy, ix + 8.0, iy + 8.0, cells))
		assertEquals(9, InventoryPanel.cellAt(null, window, ix + 8.0, iy + 8.0, cells))
		assertNull(InventoryPanel.cellAt(null, window, ix + 30.0, iy + 8.0, cells))

		val origin = 100 to 200
		val (px, py) = InventoryPanel.itemPos(0, 0, origin.first, origin.second)
		assertEquals(54, InventoryPanel.cellAt(origin, window, px + 2.0, py + 2.0, cells))
	}

	@Test fun positionListLookupForStorage() {
		val pos = listOf(10 to 10, 28 to 10)
		assertEquals(0, InventoryPanel.cellAtPositions(pos, 9.0, 9.0))
		assertEquals(1, InventoryPanel.cellAtPositions(pos, 30.0, 20.0))
		assertNull(InventoryPanel.cellAtPositions(pos, 50.0, 10.0))
	}

	@Test fun noH5MenuForwardsSlotClicksItself() {
		val menus = listOf("features/menus/wardrobe", "features/menus/pets", "features/menus/h6", "features/menus/support",
			"features/inventory/storageoverlay").map { File(src, it) }
		assertTrue(menus.all { it.isDirectory }, "source dirs: $src")
		val files = menus.flatMap { d -> d.walk().filter { it.isFile && it.name.endsWith(".kt") }.toList() }
		for (f in files) {
			val t = f.readText()
			assertFalse(t.contains("forwardSlot("), "${f.name} forwards slot clicks itself")
			assertFalse(Regex("InventoryPanel\\.route\\(").containsMatchIn(t) && f.name != "InventoryPanel.kt", "${f.name} routes panel clicks itself")
		}
		val wardrobe = File(src, "features/menus/wardrobe/WardrobeMenu.kt").readText()
		assertTrue(wardrobe.contains("realSlotCell(slot, cx, cy)"))
		val petCare = File(src, "features/menus/pets/PetCareMenus.kt").readText()
		assertTrue(petCare.contains("realSlotCell(slot, cx, cy)"))
		val storage = File(src, "features/inventory/storageoverlay/StorageOverlayCustom.kt").readText()

		assertTrue(storage.contains("InventoryPanel.Gesture()") && storage.contains("gesture.scroll(overPlayerSlot(mouseX, mouseY))"))
		for (g in listOf("gesture.press(", "gesture.drag(", "gesture.release(")) assertFalse(storage.contains(g), g)

		val minionDir = File(src, "features/menus/minion")
		val minion = File(minionDir, "MinionMenu.kt").readText()
		val realTile = Regex("protected fun realTile\\([^)]*\\) \\{([\\s\\S]*?)\\n\\t}").find(minion)!!.groupValues[1]
		assertTrue(realTile.contains("realSlotCell(slot, tx, ty)")); assertFalse(realTile.contains("area(")); assertFalse(realTile.contains("forward("))
		assertTrue(minion.contains("for ((s, col, row) in cells) realTile("))
		assertTrue(Regex("fun inventory\\([^)]*\\) \\{[\\s\\S]*?realTile\\(ctx, mx, my, ix \\+ col \\* 18").containsMatchIn(minion))
		assertFalse(minion.contains("in cells) tile("))
		val chest = File(minionDir, "MinionChestMenu.kt").readText()
		assertTrue(chest.contains("for ((slot, col, row) in cells) realTile("))
		assertFalse(chest.contains("in cells) tile("))
		for (f in minionDir.walk().filter { it.isFile && it.name.endsWith(".kt") }) {
			val t = f.readText()
			assertFalse(t.contains("forwardSlot("), "${f.name} forwards slot clicks itself")
			assertFalse(t.contains("ctx.item(carried"), "${f.name} draws the carried stack over vanilla's")
		}

		assertTrue(File(src, "features/menus/support/TileGrid.kt").readText().contains("inventoryPanel("))
		assertTrue(File(src, "features/menus/h6/HubSupport.kt").readText().contains("inventoryPanel("))
	}
}
