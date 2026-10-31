// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay.SlotBox
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay.LockKind
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay.LockState
import dev.ashtonite365.apex.features.inventory.GlobalSlotLocks
import dev.ashtonite365.apex.features.inventory.SlotLocking
import dev.ashtonite365.apex.features.inventory.SlotBinding
import dev.ashtonite365.apex.features.inventory.SlotBindings
import java.util.UUID
import dev.ashtonite365.apex.features.inventory.BindLinkStyle
import dev.ashtonite365.apex.features.menus.support.InventoryPanel

class SlotLinkOverlayTest {

	private val projectRoot: java.io.File by lazy {
		generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { java.io.File(it, "settings.gradle.kts").isFile && java.io.File(it, "src/main/kotlin").isDirectory }
	}

	private fun all36(m: Map<Int, SlotBox>) = assertEquals((0 until 36).toSet(), m.keys)

	@Test
	fun windowSlotMappingRoundTrips() {
		for (n in listOf(36 + 27, 36 + 54, 36 + 5, 36 + 3, 36 + 45)) {
			val c = n - 36
			assertNull(SlotLinkOverlay.inventoryIndexOfWindowSlot(0, n))
			assertNull(SlotLinkOverlay.inventoryIndexOfWindowSlot(c - 1, n))
			assertEquals(9, SlotLinkOverlay.inventoryIndexOfWindowSlot(c, n))
			assertEquals(35, SlotLinkOverlay.inventoryIndexOfWindowSlot(c + 26, n))
			assertEquals(0, SlotLinkOverlay.inventoryIndexOfWindowSlot(c + 27, n))
			assertEquals(8, SlotLinkOverlay.inventoryIndexOfWindowSlot(c + 35, n))
			assertNull(SlotLinkOverlay.inventoryIndexOfWindowSlot(c + 36, n))
			for (i in 0 until 36) assertEquals(i, SlotLinkOverlay.inventoryIndexOfWindowSlot(SlotLinkOverlay.windowSlotOfInventoryIndex(i, n)!!, n))
		}
		assertNull(SlotLinkOverlay.inventoryIndexOfWindowSlot(3, 20))
	}

	@Test
	fun panelCellsMatchInventoryPanelSlotIndex() {
		val n = 90
		for (row in 0..3) for (col in 0..8)
			assertEquals(SlotLinkOverlay.inventoryIndexOfCell(row, col),
				SlotLinkOverlay.inventoryIndexOfWindowSlot(InventoryPanel.slotIndex(row, col, InventoryPanel.chestSlots(n)), n))
	}

	@Test
	fun vanillaAndStorageOverlayRealSlots() {

		val m = SlotLinkOverlay.fromRealSlots(listOf(Triple(0, 8, 142), Triple(9, 8, 84), Triple(40, 77, 62), Triple(5, -100000, -100000)), 100, 50)
		assertEquals(mapOf(0 to SlotBox(108, 192), 9 to SlotBox(108, 134)), m)
	}

	@Test
	fun storageOverlayScreenPositions() {

		val m = SlotLinkOverlay.fromPositions { i -> if (i < 9) (10 + i * 18) to 200 else (10 + (i % 9) * 18) to (140 + (i / 9 - 1) * 18) }
		all36(m)
		assertEquals(SlotBox(10, 200, standIn = true), m[0]); assertEquals(SlotBox(154, 140, standIn = true), m[17]); assertEquals(SlotBox(10, 176, standIn = true), m[27])
	}

	@Test
	fun inventoryPanelMenus() {
		val origin = 40 to 120
		val m = SlotLinkOverlay.forMenuSession(origin, emptyMap(), 1f, emptyMap(), 90)
		all36(m)
		for (row in 0..3) for (col in 0..8) {
			val (x, y) = InventoryPanel.itemPos(row, col, origin.first, origin.second)
			assertEquals(SlotBox(x, y), m[SlotLinkOverlay.inventoryIndexOfCell(row, col)])
		}
		assertEquals(SlotBox(41, 131), m[9])
		assertEquals(SlotBox(41, 131 + 54 + 4), m[0])

		assertTrue(SlotLinkOverlay.forMenuSession(origin, emptyMap(), 1f, emptyMap(), 27).isEmpty())
	}

	@Test
	fun wardrobeScaledRealCells() {
		val n = 90; val c = 54; val s = 0.75f
		val cells = mutableMapOf<Int, Pair<Int, Int>>()
		for (row in 0..3) for (col in 0..8) {
			val (ax, ay) = InventoryPanel.itemPos(row, col, 0, 0)
			cells[InventoryPanel.slotIndex(row, col, c)] = (200 + (ax * s).toInt()) to (100 + (ay * s).toInt())
		}
		cells[3] = 5 to 5
		val m = SlotLinkOverlay.forMenuSession(null, cells, s, emptyMap(), n)
		all36(m)
		assertEquals(SlotBox(200, 100 + (11 * s).toInt(), s), m[9])
		assertEquals(12, m[9]!!.size)
		assertEquals(200 + 7 to 100 + (11 * s).toInt() + 12, m[9]!!.anchor(hotbar = false))
	}

	@Test
	fun realTileMenus() {
		val n = 90; val c = 54
		val cells = mapOf(c to (51 to 61), c + 35 to (195 to 121), 10 to (0 to 0))
		val m = SlotLinkOverlay.forMenuSession(null, cells, 1f, emptyMap(), n)
		assertEquals(mapOf(9 to SlotBox(51, 61), 8 to SlotBox(195, 121)), m)
	}

	@Test
	fun workbenchStandInTiles() {
		for (chest in listOf(5, 3)) {
			val n = chest + 36; val ix = 70; val iy = 150
			val stand = mutableMapOf<Int, Pair<Int, Int>>()
			for (row in 0..3) for (col in 0..8) {

				val slot = dev.ashtonite365.apex.features.menus.workbench.WorkbenchClicks.inventorySlot(row, col, chest)
				stand[slot] = (ix + col * 18 + 1) to (iy + row * 18 + (if (row == 3) 4 else 0) + 1)
			}
			val m = SlotLinkOverlay.forMenuSession(null, emptyMap(), 1f, stand, n)
			assertEquals(SlotLinkOverlay.fromTileGrid(ix, iy), m)
			assertEquals(SlotBox(71, 151, standIn = true), m[9]); assertEquals(SlotBox(71, 151 + 58, standIn = true), m[0])
		}
	}

	@Test
	fun ahCreateAuctionGrid() {
		val n = 90; val gx = 300; val gy = 40
		val ids = (54 until 90).toList()
		val stand = ids.withIndex().associate { (i, w) -> w to ((gx + (i % 9) * 18 + 1) to (gy + (i / 9) * 18 + (if (i >= 27) 4 else 0) + 1)) }
		val m = SlotLinkOverlay.forMenuSession(null, emptyMap(), 1f, stand, n)
		assertEquals(SlotLinkOverlay.fromTileGrid(gx, gy), m)
	}

	@Test
	fun menusWithoutInventoryDrawNothing() {
		assertTrue(SlotLinkOverlay.forMenuSession(null, mapOf(13 to (1 to 1)), 1f, emptyMap(), 90).isEmpty())
	}

	@Test
	fun standInWinsOverPanelForTheSameSlot() {
		val m = SlotLinkOverlay.forMenuSession(0 to 0, emptyMap(), 1f, mapOf(54 to (500 to 500)), 90)
		assertEquals(SlotBox(500, 500, standIn = true), m[9]); assertEquals(36, m.size)
	}

	@Test
	fun anchorsAreVanillaLineCentres() {
		val b = SlotBox(8, 84)
		assertEquals(17 to 84, b.anchor(hotbar = true))
		assertEquals(17 to 100, b.anchor(hotbar = false))
		assertEquals(16, b.size)
	}

	@Test
	fun planFollowsTheLinesSetting() {
		val boxes = SlotLinkOverlay.fromPanel(0 to 0)
		val pairs = listOf(0 to 27, 1 to 28, 5 to 99)
		val every = SlotLinkOverlay.plan(pairs, boxes, null, BindLinkStyle.EVERYTHING)
		assertEquals(2, every.size)
		assertTrue(every.all { it.line && !it.lineHighlit && !it.hotHighlit && !it.invHighlit })
		val boxesOnly = SlotLinkOverlay.plan(pairs, boxes, null, BindLinkStyle.ONLY_BOXES)
		assertEquals(2, boxesOnly.size); assertTrue(boxesOnly.none { it.line })
		assertTrue(SlotLinkOverlay.plan(pairs, boxes, null, BindLinkStyle.NOTHING).isEmpty())
		val hov = SlotLinkOverlay.plan(pairs, boxes, 28, BindLinkStyle.NOTHING).single()
		assertEquals(1 to 28, hov.hotbar to hov.inventory)
		assertTrue(hov.line && hov.lineHighlit && hov.hotHighlit && hov.invHighlit)
		val hovBoxes = SlotLinkOverlay.plan(pairs, boxes, 0, BindLinkStyle.ONLY_BOXES)
		assertTrue(hovBoxes[0].line); assertFalse(hovBoxes[1].line)
	}

	@Test
	fun hoverIsHitTestedOnTheDrawnBoxes() {
		val boxes = SlotLinkOverlay.fromTileGrid(0, 0)
		assertEquals(9, SlotLinkOverlay.hovered(boxes, 5.0, 5.0))
		assertEquals(0, SlotLinkOverlay.hovered(boxes, 5.0, 60.0))
		assertNull(SlotLinkOverlay.hovered(boxes, 500.0, 5.0))
	}

	@Test
	fun dragPreview() {
		val boxes = SlotLinkOverlay.fromPanel(0 to 0)
		assertNull(SlotLinkOverlay.planDrag(null, 3, boxes))
		val toMouse = SlotLinkOverlay.planDrag(2, null, boxes)!!
		assertNull(toMouse.to); assertTrue(toMouse.fromHotbar)
		assertNull(SlotLinkOverlay.planDrag(2, 4, boxes)!!.to)
		val toSlot = SlotLinkOverlay.planDrag(2, 20, boxes)!!
		assertEquals(boxes[20], toSlot.to); assertFalse(toSlot.toHotbar)
	}

	private fun mappings(): Map<String, Map<Int, SlotBox>> {
		val n = 90
		val wardrobe = mutableMapOf<Int, Pair<Int, Int>>()
		val realTiles = mutableMapOf<Int, Pair<Int, Int>>()
		for (row in 0..3) for (col in 0..8) {
			val (ax, ay) = InventoryPanel.itemPos(row, col, 0, 0)
			wardrobe[InventoryPanel.slotIndex(row, col, 54)] = (200 + (ax * 0.75f).toInt()) to (100 + (ay * 0.75f).toInt())
			realTiles[InventoryPanel.slotIndex(row, col, 54)] = (10 + col * 20) to (10 + row * 20)
		}
		val ahIds = (54 until 90).toList()
		return mapOf(
			"vanilla inventory / chest" to SlotLinkOverlay.fromRealSlots((0 until 36).map { Triple(it, 8 + (it % 9) * 18, if (it < 9) 142 else 84 + (it / 9 - 1) * 18) }, 100, 50),
			"storage overlay screen" to SlotLinkOverlay.fromPositions { i -> (10 + (i % 9) * 18) to (if (i < 9) 200 else 140 + (i / 9 - 1) * 18) },
			"inventory panel menus" to SlotLinkOverlay.forMenuSession(40 to 120, emptyMap(), 1f, emptyMap(), n),
			"wardrobe (scaled)" to SlotLinkOverlay.forMenuSession(null, wardrobe, 0.75f, emptyMap(), n),
			"minion / craft / enchant real tiles" to SlotLinkOverlay.forMenuSession(null, realTiles, 1f, emptyMap(), n),
			"workbench (brewing / anvil)" to SlotLinkOverlay.fromTileGrid(70, 150),
			"ah create auction" to SlotLinkOverlay.forMenuSession(null, emptyMap(), 1f,
				ahIds.withIndex().associate { (i, w) -> w to ((300 + (i % 9) * 18 + 1) to (40 + (i / 9) * 18 + (if (i >= 27) 4 else 0) + 1)) }, n),
		)
	}

	private val uuidSword = UUID.fromString("00000000-0000-0000-0000-000000000007")
	private fun global() = GlobalSlotLocks(
		lockedSlots = mutableSetOf(3, 20),
		boundSlots = SlotBindings(mutableSetOf(SlotBinding(0, 27))),
		lockedUUIDs = mutableSetOf(uuidSword),
	)
	private val uuidAt: (Int) -> UUID? = { i -> if (i == 7) uuidSword else if (i == 11) UUID.randomUUID() else null }

	@Test
	fun hookDrawsOnlyLockedAndBoundSlotsInEveryMenuMapping() {
		val state = LockState.of(global(), uuidAt)
		for ((menu, boxes) in mappings()) {
			assertEquals(36, boxes.size, menu)
			val f = SlotLinkOverlay.planFrame(boxes, state, hovered = null, mode = BindLinkStyle.EVERYTHING)

			assertEquals(mapOf(3 to LockKind.SLOT, 7 to LockKind.UUID, 20 to LockKind.SLOT), f.locks.associate { it.index to it.kind }, menu)
			for (l in f.locks) assertEquals(boxes[l.index], l.box, menu)

			val link = f.links.single()
			assertEquals(0 to 27, link.hotbar to link.inventory, menu)
			assertEquals(boxes[0], link.hotBox, menu); assertEquals(boxes[27], link.invBox, menu)
			assertTrue(link.line, menu)

			for (i in listOf(1, 11, 35)) {
				assertTrue(f.locks.none { it.index == i }, "$menu: $i")
				assertTrue(f.links.none { it.hotbar == i || it.inventory == i }, "$menu: $i")
			}

			val none = SlotLinkOverlay.planFrame(boxes, LockState.of(GlobalSlotLocks(), uuidAt), null, BindLinkStyle.EVERYTHING)
			assertTrue(none.links.isEmpty() && none.locks.isEmpty(), menu)
		}
	}

	@Test
	fun padlocksDrawnByTheHookOnlyWhereNoRealSlotDrawsThem() {
		val state = LockState.of(global(), uuidAt)
		val m = mappings()
		fun standIns(menu: String) = SlotLinkOverlay.planFrame(m.getValue(menu), state, null, BindLinkStyle.EVERYTHING).locks.map { it.box.standIn }.toSet()

		for (menu in listOf("storage overlay screen", "workbench (brewing / anvil)", "ah create auction")) assertEquals(setOf(true), standIns(menu), menu)

		for (menu in listOf("vanilla inventory / chest", "inventory panel menus", "wardrobe (scaled)", "minion / craft / enchant real tiles")) assertEquals(setOf(false), standIns(menu), menu)
	}

	@Test
	fun globalLocksAreTheOnlySource() {

		val g = global()
		val s = LockState.of(g, uuidAt)
		assertEquals(LockKind.SLOT, s.lockAt(3)); assertEquals(LockKind.UUID, s.lockAt(7)); assertNull(s.lockAt(11)); assertNull(s.lockAt(4))
		assertEquals(listOf(0 to 27), s.boundPairs)

		assertEquals(LockKind.SLOT, LockState.of(GlobalSlotLocks(lockedSlots = mutableSetOf(7), lockedUUIDs = mutableSetOf(uuidSword)), uuidAt).lockAt(7))

		assertEquals(LockKind.UUID, LockState.of(g) { i -> if (i == 30) uuidSword else null }.lockAt(30))

		val src = java.io.File(projectRoot, "src/main/kotlin/features/inventory/SlotLinkOverlay.kt").readText()
		val live = src.substringAfter("fun liveState()").substringBefore("\n\t}")
		assertTrue("LockState.of(SlotLocking.globalData)" in live, live)
		assertFalse(Regex("""overworld|rift|DimensionData""").containsMatchIn(src))
		val locking = java.io.File(projectRoot, "src/main/kotlin/features/inventory/SlotLocking.kt").readText()
		assertTrue("val locks = globalData" in locking && "globalData.lockedUUIDs" in locking && "globalData.boundSlots" in locking, "slot locking reads the global set")
		assertFalse(Regex("""overworld|rift|DimensionData""").containsMatchIn(locking))
	}
}
