// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.workbench

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.workbench.AnvilMenu
import dev.ashtonite365.apex.features.menus.workbench.AnvilMenuSettings
import dev.ashtonite365.apex.features.menus.workbench.BrewingStandMenu
import dev.ashtonite365.apex.features.menus.workbench.BrewingStandMenuSettings
import dev.ashtonite365.apex.features.menus.workbench.SocketMemory
import dev.ashtonite365.apex.features.menus.workbench.WorkbenchClicks
import dev.ashtonite365.apex.features.menus.workbench.WorkbenchParser
import dev.ashtonite365.apex.features.menus.workbench.WorkbenchRole
import dev.ashtonite365.apex.features.menus.workbench.WorkbenchSlot
import dev.ashtonite365.apex.features.menus.workbench.WorkbenchTitles
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class WorkbenchMenusTest {
	private val anvil = RealDumpFixtures.load("anvil-empty")
	private val brew = RealDumpFixtures.load("brewing-stand-empty")
	private fun ws(slots: List<SlotText>) = slots.map { WorkbenchSlot.of(it) }
	private fun roles(d: RealDumpFixtures.Dump) = WorkbenchParser.roles(54, ws(d.slots))
	private fun of(r: Map<Int, WorkbenchRole>, role: WorkbenchRole) = r.filterValues { it == role }.keys

	@AfterEach
	fun cleanup() {
		MenuRegistry.clearForTests()

		AnvilMenuSettings.explicitDefaultLoad(); BrewingStandMenuSettings.explicitDefaultLoad()
		dev.ashtonite365.apex.features.menus.MenuSections.explicitDefaultLoad()
	}

	@Test
	fun anvilFillerAndHintsStripped() {
		val r = roles(anvil)
		assertEquals(setOf(29, 33), of(r, WorkbenchRole.SOCKET))
		assertEquals(setOf(13, 22, 49), of(r, WorkbenchRole.BUTTON))
		assertEquals(setOf(11, 12, 20, 14, 15, 24), of(r, WorkbenchRole.HINT))
		assertEquals(54 - 2 - 3 - 6, of(r, WorkbenchRole.FILLER).size)
		val m = WorkbenchParser.parse(anvil.title, 54, ws(anvil.slots))!!
		assertEquals(listOf(13, 22, 29, 33, 49), m.tiles.map { it.slot })
		assertEquals("Combine Items", m.tile(22)!!.name)
	}

	@Test
	fun brewingFillerAndHintsStripped() {
		val r = roles(brew)
		assertEquals(setOf(13, 38, 40, 42), of(r, WorkbenchRole.SOCKET))
		assertEquals(setOf(49), of(r, WorkbenchRole.BUTTON))
		assertEquals(setOf(20, 21, 22, 23, 24, 29, 31, 33), of(r, WorkbenchRole.HINT))
		val m = WorkbenchParser.parse(brew.title, 54, ws(brew.slots))!!
		assertEquals(listOf(13, 38, 40, 42, 49), m.tiles.map { it.slot })
		assertTrue(m.tiles.none { it.role == WorkbenchRole.FILLER || it.role == WorkbenchRole.HINT })
	}

	@Test
	fun liveFlagsDecideFillerGenerically() {
		val slots = listOf(
			WorkbenchSlot(0, " ", pane = true), WorkbenchSlot(1, "Some Hint", listOf("x"), pane = true),
			WorkbenchSlot(2, "Do Thing", listOf("Cost", "5 Exp Levels"), pane = false, realItem = false),
			WorkbenchSlot(3, "Hyperion", pane = false, realItem = true),
		) + (5 until 9).map { WorkbenchSlot(it, "", pane = true) }
		val r = WorkbenchParser.roles(9, slots)
		assertEquals(WorkbenchRole.FILLER, r[0]); assertEquals(WorkbenchRole.HINT, r[1])
		assertEquals(WorkbenchRole.BUTTON, r[2]); assertEquals(WorkbenchRole.ITEM, r[3]); assertEquals(WorkbenchRole.SOCKET, r[4])
	}

	@Test
	fun identicalRealItemsAreNotMistakenForHints() {

		val slots = brew.slots.map { WorkbenchSlot.of(it) } + listOf(38, 40, 42).map { WorkbenchSlot(it, "Water Bottle", realItem = true) }
		val r = WorkbenchParser.roles(54, slots)
		assertEquals(setOf(38, 40, 42), of(r, WorkbenchRole.ITEM))
		assertTrue(r.getValue(38).realSlot)
	}

	@Test
	fun socketMemoryKeepsFilledSockets() {
		val mem = SocketMemory()

		assertTrue(mem.update(7, 54, emptySet()).isEmpty())
		val full = anvil.slots.map { it.slot }.toSet()
		assertEquals(setOf(29, 33), mem.update(7, 54, full))

		assertEquals(setOf(29, 33), mem.update(7, 54, full + 29))
		val withItem = ws(anvil.slots) + WorkbenchSlot(29, "Aspect of the End", realItem = true)
		assertEquals(WorkbenchRole.SOCKET, WorkbenchParser.roles(54, withItem, mem.sockets)[29])

		assertTrue(mem.update(8, 54, emptySet()).isEmpty())
		assertFalse(SocketMemory.populated(54, 27)); assertTrue(SocketMemory.populated(54, 28))
	}

	@Test
	fun unreadablePagesFallBack() {
		assertNull(WorkbenchParser.parse("Anvil", 54, emptyList()))
		assertNull(WorkbenchParser.parse("Anvil", 54, (0 until 54).map { WorkbenchSlot(it, "") }))
		assertNull(WorkbenchParser.parse("Anvil", 50, ws(anvil.slots)))
	}

	@Test
	fun anvilKeepsHypixelArrangement() {
		val m = WorkbenchParser.parse(anvil.title, 54, ws(anvil.slots))!!
		assertEquals(5, m.rows); assertEquals(5, m.cols)
		fun pos(s: Int) = m.tile(s)!!.let { it.row to it.col }
		assertEquals(0 to 2, pos(13)); assertEquals(1 to 2, pos(22))
		assertEquals(2 to 0, pos(29)); assertEquals(2 to 4, pos(33)); assertEquals(4 to 2, pos(49))
		assertEquals(20, m.tile(29)!!.hint)
		assertEquals(24, m.tile(33)!!.hint)
		assertEquals(29, m.firstSocket!!.slot)
	}

	@Test
	fun brewingKeepsHypixelArrangement() {
		val m = WorkbenchParser.parse(brew.title, 54, ws(brew.slots))!!
		assertEquals(5, m.rows); assertEquals(5, m.cols)
		assertEquals(listOf(0 to 2, 3 to 0, 3 to 2, 3 to 4, 4 to 2), m.tiles.map { it.row to it.col })
		assertEquals(mapOf(13 to null, 38 to 29, 40 to 31, 42 to 33), m.sockets.associate { it.slot to it.hint })
	}

	@Test
	fun movedLayoutStillMapsRelatively() {

		val shifted = anvil.slots.filter { it.slot + 9 < 54 }.map { it.copy(slot = it.slot + 9) } + (0 until 9).map { SlotText(it, "") }
		val m = WorkbenchParser.parse("Anvil", 54, ws(shifted))!!
		assertEquals(setOf(38, 42), m.sockets.map { it.slot }.toSet())
		assertEquals(2 to 0, m.tile(38)!!.let { it.row to it.col })
		assertEquals(38, m.firstSocket!!.slot)
	}

	@Test
	fun inventorySlotsFollowTheChest() {
		assertEquals(54, WorkbenchClicks.inventorySlot(0, 0, 54))
		assertEquals(89, WorkbenchClicks.inventorySlot(3, 8, 54))
		assertEquals(27 + 9 + 4, WorkbenchClicks.inventorySlot(1, 4, 27))
	}

	@Test
	fun costLinesReadLive() {
		assertEquals(listOf("10 Exp Levels"), WorkbenchParser.costLines(listOf("§7Combine the items", "", "§7Cost", "§310 Exp Levels", "", "§eClick to combine!")))
		assertEquals(listOf("Cost: 1,000 Coins"), WorkbenchParser.costLines(listOf("Brew it", "Cost: 1,000 Coins")))
		assertTrue(WorkbenchParser.costLines(anvil.slots.first { it.slot == 22 }.lore).isEmpty())
	}

	@Test
	fun shiftClickFromInventoryIsAQuickMoveOnTheRealSlot() {
		val p = WorkbenchClicks.plan(slot = 60, packetButton = 0, shift = true, realSlot = true)!!
		assertEquals(60, p.slot); assertEquals(WorkbenchClicks.Input.QUICK_MOVE, p.input); assertFalse(p.silent)

		assertEquals(WorkbenchClicks.Input.QUICK_MOVE, WorkbenchClicks.plan(29, 0, true, true)!!.input)
	}

	@Test
	fun plainClicksPlaceAndTake() {
		val pick = WorkbenchClicks.plan(60, 0, false, true)!!
		assertEquals(WorkbenchClicks.Plan(60, 0, WorkbenchClicks.Input.PICKUP, false), pick)
		assertEquals(WorkbenchClicks.Plan(29, 1, WorkbenchClicks.Input.PICKUP, false), WorkbenchClicks.plan(29, 1, false, true))
		assertNull(WorkbenchClicks.plan(29, 2, false, true))
	}

	@Test
	fun buttonsUseTheSilentPathAndIgnoreShift() {
		assertEquals(WorkbenchClicks.Plan(22, 0, WorkbenchClicks.Input.PICKUP, true), WorkbenchClicks.plan(22, 0, true, false))
		assertEquals(WorkbenchClicks.Plan(22, 1, WorkbenchClicks.Input.PICKUP, true), WorkbenchClicks.plan(22, 1, false, false))
	}

	@Test
	fun titlesMatchRealDumpsAndVariants() {
		assertEquals("Anvil", anvil.title); assertEquals("Brewing Stand", brew.title)
		for (t in listOf("Anvil", "anvil", " Anvil ", "Anvil ➜ Combine", "Anvil (1/2)")) assertTrue(WorkbenchTitles.ANVIL.containsMatchIn(t), t)
		for (t in listOf("Brewing Stand", "BREWING STAND", "Brewing  Stand", "Brewing Stand #2", "Brewing Stand - Island")) assertTrue(WorkbenchTitles.BREWING.containsMatchIn(t), t)
		for (t in listOf("Reforge Anvil", "Reforge Item", "Anvils", "Anvil Upgrades", "Enchant Item")) assertFalse(WorkbenchTitles.ANVIL.containsMatchIn(t), t)
		for (t in listOf("Brewing Stands", "Brewing", "Potion Bag", "Brewing Stand Upgrades")) assertFalse(WorkbenchTitles.BREWING.containsMatchIn(t), t)
	}

	@Test
	fun registryRoutesTitlesToTheirOwnMenu() {
		MenuRegistry.clearForTests(); MenuRegistry.register(BrewingStandMenu); MenuRegistry.register(AnvilMenu)
		assertEquals("anvil", MenuRegistry.match("§8Anvil")?.id)
		assertEquals("brewing-stand", MenuRegistry.match("Brewing Stand")?.id)
		assertNull(MenuRegistry.match("Reforge Anvil")); assertNull(MenuRegistry.match("Craft Item"))
	}

	@Test
	fun craftingSectionSwitchOffMeansVanilla() {

		dev.ashtonite365.apex.features.menus.MenuSections.explicitDefaultLoad()
		for ((s, menu) in listOf(AnvilMenuSettings to AnvilMenu, BrewingStandMenuSettings to BrewingStandMenu)) {
			assertFalse("apex-menus" in s.allOptions.keys)
			assertEquals("crafting", menu.section)
			assertTrue(menu.enabled)
			dev.ashtonite365.apex.features.menus.MenuSections.set("crafting", false)
			assertFalse(menu.enabled)
			MenuRegistry.clearForTests(); MenuRegistry.register(menu)
			val r = MenuRegistry.resolve(menu.displayName) { error("must not build a view while the section is off") }
			assertTrue(r is MenuRegistry.Resolution.Vanilla && r.sectionOff, "$r")
			dev.ashtonite365.apex.features.menus.MenuSections.set("crafting", true)
		}
		assertNull(translation("apex.config.custom-menu-anvil.apex-menus"))
		assertNull(translation("apex.config.custom-menu-brewing-stand.apex-menus"))
	}

	@Test
	fun failuresNeverNavigate() {

		assertFalse(dev.ashtonite365.apex.features.menus.support.ApexFallback.navigates(AnvilMenu.id))
		assertFalse(dev.ashtonite365.apex.features.menus.support.ApexFallback.navigates(BrewingStandMenu.id))
		assertFalse(dev.ashtonite365.apex.features.menus.support.ApexFallback.navigates("bank"))
	}

	private fun translation(key: String): String? {
		val f = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "translations/en_us.json") }.firstOrNull { it.exists() }
			?: return null
		return Regex("\"" + Regex.escape(key) + "\":\\s*\"([^\"]*)\"").find(f.readText())?.groupValues?.get(1)
	}
}
