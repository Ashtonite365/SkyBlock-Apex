// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.MenuChainRunner
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.SlotText

class SectionSwitchGateTest {
	private class Probe(override val id: String, pattern: String, var on: Boolean, slotMatch: Boolean) : CustomMenu {
		var slotCalls = 0
		var creates = 0
		override val displayName = id
		override val title = Regex(pattern)
		override val enabled get() = on
		override val settings: MenuSettings get() = error("not needed")
		override val slotPredicate: ((String, List<SlotText>) -> Boolean)? =
			if (slotMatch) { _, _ -> slotCalls++; true } else null
		override fun create(screen: net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>): MenuView? = null
	}

	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	companion object {

		fun sections() = listOf(
			arrayOf("bazaar", "bazaar", "Bazaar ➜ Farming"),
			arrayOf("auction", "ah", "Auctions Browser"),
			arrayOf("bank", "bank", "Bank Account"),
			arrayOf("bestiary", "bestiary", "Bestiary"),
			arrayOf("collections", "collections", "Collections"),
			arrayOf("crafting", "craft", "Craft Item"),
			arrayOf("enchant", "enchant", "Enchant Item"),
			arrayOf("events", "spooky-rewards", "Spooky Festival Rewards"),
			arrayOf("garden", "sam", "SkyMart"),
			arrayOf("general", "npc-shop", "Adventurer"),
			arrayOf("minions", "minion", "Cobblestone Minion IX"),
			arrayOf("museum", "museum", "Museum"),
			arrayOf("pets-wardrobe", "pets", "Pets"),
			arrayOf("quests", "quests", "Quest Log"),
			arrayOf("recipe-book", "recipe-book", "Recipe Book"),
			arrayOf("skills", "skills", "Your Skills"),
			arrayOf("skyblock", "skyblock-menu", "SkyBlock Menu"),
		)
	}

	private val slots = listOf(SlotText(13, "Some Item", listOf("lore")))

	@TestFactory
	fun offIsPureVanilla() = sections().map { (s, i, t) -> DynamicTest.dynamicTest("$s OFF: pure vanilla") { MenuRegistry.clearForTests(); off(s, i, t) } }

	@TestFactory
	fun onStillMatches() = sections().map { (s, i, t) -> DynamicTest.dynamicTest("$s ON: still matches") { MenuRegistry.clearForTests(); on(s, i, t) } }

	private fun off(section: String, id: String, title: String) {
		val byTitle = Probe(id, "^" + Regex.escape(title) + "$", on = false, slotMatch = false)
		val bySlots = Probe("$id-slots", "^\\uFFFF$", on = false, slotMatch = true)
		MenuRegistry.register(byTitle); MenuRegistry.register(bySlots)

		Assertions.assertTrue(MenuRegistry.switchedOff("§6$title"), section)
		val r = MenuRegistry.resolve(title, slots) { byTitle.creates++; null }
		Assertions.assertTrue(r is MenuRegistry.Resolution.Vanilla, "$section: $r")

		Assertions.assertEquals(MenuRegistry.Resolution.None, MenuRegistry.resolve("Unrelated Title", slots) { it as Probe; it.creates++; null })
		Assertions.assertFalse(MenuRegistry.hasSlotMatchers())
		Assertions.assertEquals(0, bySlots.slotCalls, "$section slot matcher called while OFF")
		Assertions.assertEquals(0, byTitle.creates + bySlots.creates, "$section reader/overlay created while OFF")

		Assertions.assertTrue(MenuRegistry.ownerSwitchedOff(id))
		Assertions.assertTrue(MenuRegistry.ownerSwitchedOff("$id-refresh"))
		val owner = object : MenuChainRunner.Owner { override val id = id }
		Assertions.assertFalse(MenuChainRunner.start(MenuChain(listOf(MenuChain.Step(Regex("x"), MenuChain.Target("a")))), owner))
		Assertions.assertFalse(MenuChainRunner.active)
	}

	private fun on(section: String, id: String, title: String) {
		val byTitle = Probe(id, "^" + Regex.escape(title) + "$", on = true, slotMatch = false)
		val bySlots = Probe("$id-slots", "^\\uFFFF$", on = true, slotMatch = true)
		MenuRegistry.register(byTitle); MenuRegistry.register(bySlots)
		Assertions.assertFalse(MenuRegistry.switchedOff(title))
		Assertions.assertSame(byTitle, MenuRegistry.match(title))
		Assertions.assertTrue(MenuRegistry.resolve(title) { (it as Probe).creates++; null } is MenuRegistry.Resolution.Fallback)
		Assertions.assertEquals(1, byTitle.creates)
		Assertions.assertSame(bySlots, MenuRegistry.match("Unrelated Title", slots))
		Assertions.assertEquals(1, bySlots.slotCalls)
		Assertions.assertFalse(MenuRegistry.ownerSwitchedOff(id))
	}
}
