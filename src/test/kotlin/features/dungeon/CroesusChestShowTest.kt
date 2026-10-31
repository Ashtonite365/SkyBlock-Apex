// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.dungeon.ChestLootPricing
import dev.ashtonite365.apex.features.dungeon.CroesusHelpers
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit
import dev.ashtonite365.apex.features.items.ItemCustomizer
import dev.ashtonite365.apex.features.kuudra.KuudraProfit
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class CroesusChestShowTest {
	@BeforeEach fun loadDefaults() {

		if (runCatching { ItemCustomizer.DConfig.data }.isFailure) ItemCustomizer.DConfig.explicitDefaultLoad()
		listOf(ItemCustomizer.TConfig, DungeonChestProfit.TConfig, KuudraProfit.TConfig)
			.forEach { c -> if (c.sortedOptions.any { it._actualValue == null }) c.explicitDefaultLoad() }
	}

	private fun stack(name: String, lore: List<String>) = ItemStack(Items.CHEST).apply {
		set(DataComponents.CUSTOM_NAME, Component.literal(name))
		set(DataComponents.LORE, ItemLore(lore.map { Component.literal(it) }))
	}

	private val note = listOf("NOTE: Coins are withdrawn from your", "bank if you don't have enough in", "your purse.")

	private fun floor3(): List<ItemStack> {
		val slots = MutableList(36) { ItemStack.EMPTY }
		slots[11] = stack("Wood", listOf("Contents", "Enchanted Book (Feather Falling VI)", "Undead Essence x12", "Wither Essence x12", "", "Cost", "FREE", "", "Click to open!"))
		slots[12] = stack("Gold", listOf("Contents", "Enchanted Book (Rejuvenate I)", "Enchanted Book (Infinite Quiver VI)", "Undead Essence x14", "Wither Essence x11", "", "Cost", "100,000 Coins", "") + note + listOf("", "Click to open!"))
		slots[14] = stack("Diamond", listOf("Contents", "Enchanted Book (Rejuvenate I)", "Enchanted Book (Combo I)", "Undead Essence x19", "Wither Essence x12", "", "Cost", "250,000 Coins", "") + note + listOf("", "Click to open!"))
		slots[15] = stack("Emerald", listOf("Contents", "Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12", "", "Cost", "500,000 Coins", "") + note + listOf("", "Click to open!"))
		slots[30] = stack("Go Back", listOf("To Croesus"))
		slots[31] = stack("Close", emptyList())
		slots[32] = stack("Chest Modifiers", listOf("These consumables can be used on", "your chests once per run!", "", "Available Modifiers:", " Kismet Feather", " Dungeon Chest Key"))
		return slots
	}

	private fun named(stacks: List<ItemStack>) = stacks.filter { !it.isEmpty }
		.map { it.hoverName.string to (it.get(DataComponents.LORE)?.lines ?: emptyList()).map { l -> l.string } }

	@Test fun realCroesusTitlesAreRecognised() {

		assertFalse(".+ Catacombs - Flo.*".toRegex().matches("Catacombs - Floor III"), "old pattern (root cause)")
		for (t in listOf("Catacombs - Floor III", "Catacombs - Floor IV", "Catacombs - Floor V", "Catacombs - Entrance",
		                 "The Catacombs - Floor VII", "Master Mode Catacombs - Floor III", "Master Mode The Catacombs - Floor VII",
		                 "Kuudra - Infernal Tier"))
			assertTrue(ChestLootPricing.isChestPreviewTitle(t), t)
		for (t in listOf("Croesus", "Catacombs Gate", "Emerald", "Wood", "Paid Chest", "Chest", "Large Chest"))
			assertFalse(ChestLootPricing.isChestPreviewTitle(t), t)
		assertTrue(ChestLootPricing.isCroesusPreview("Catacombs - Floor III", named(floor3())))
		assertFalse(ChestLootPricing.isCroesusPreview("Croesus", named(floor3())))
	}

	@Test fun renamedScreenFallsBackToGoBackToCroesus() {
		assertTrue(ChestLootPricing.isCroesusPreview("Some New Title - Floor III", named(floor3())))

		assertFalse(ChestLootPricing.isCroesusPreview("Something", listOf("Go Back" to listOf("To Croesus"))))
	}

	@Test fun masterModeTitles() {
		assertTrue(ChestLootPricing.isMasterMode("Master Mode Catacombs - Floor III"))
		assertTrue(ChestLootPricing.isMasterMode("Master Mode The Catacombs - Floor VII"))
		assertTrue(ChestLootPricing.isMasterMode("Master Catacombs - Floor II"))
		assertFalse(ChestLootPricing.isMasterMode("Catacombs - Floor III"))
		assertFalse(ChestLootPricing.isMasterMode("Kuudra - Infernal Tier"))
	}

	@Test fun floor3ScreenYieldsAllFourChests() {
		val chests = DungeonChestProfit.croesusChests(floor3(), masterMode = false)
		assertEquals(setOf("Wood", "Gold", "Diamond", "Emerald"), chests.map { it.name.string }.toSet())
		val bySlot = chests.associateBy { it.slot }
		assertEquals(setOf(11, 12, 14, 15), bySlot.keys)
		assertEquals(0.0, bySlot[11]!!.cost)
		assertEquals(100_000.0, bySlot[12]!!.cost)
		assertEquals(250_000.0, bySlot[14]!!.cost)
		assertEquals(500_000.0, bySlot[15]!!.cost)
		assertEquals(listOf("Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12"),
		             bySlot[15]!!.items.map { it.label.string })

		assertFalse(chests.any { it.name.string in setOf("Go Back", "Close", "Chest Modifiers") })
	}

	@Test fun floor4KeyChestCountsOneKeyAndKeepsOpenedChest() {
		val slots = MutableList(36) { ItemStack.EMPTY }
		slots[11] = stack("Wood", listOf("Contents", "Enchanted Book (Feather Falling VI)", "Undead Essence x17", "Wither Essence x12", "", "Already opened!"))
		slots[13] = stack("Gold", listOf("Contents", "Enchanted Book (Rejuvenate I)", "Enchanted Book (Bank I)", "Undead Essence x19", "Wither Essence x10", "", "Cost", "100,000 Coins", "Dungeon Chest Key", "") + note + listOf("", "Requires a Dungeon Chest Key"))
		slots[30] = stack("Go Back", listOf("To Croesus"))
		val chests = DungeonChestProfit.croesusChests(slots, masterMode = false)
		assertEquals(listOf(13), chests.map { it.slot })
		assertTrue(chests[0].cost >= 100_000.0)
	}

	@Test fun endOfRunEmeraldRewardChest() {

		val slots = MutableList(54) { ItemStack.EMPTY }
		slots[11] = stack("Adaptive Helmet", listOf("Gear Score: 920"))
		slots[12] = ItemStack(Items.ENCHANTED_BOOK).apply {
			set(DataComponents.CUSTOM_NAME, Component.literal("Enchanted Book"))
			set(DataComponents.LORE, ItemLore(listOf("Combinable in Anvil", "", "Feather Falling VI").map { Component.literal(it) }))
		}
		slots[14] = stack("Undead Essence x21", listOf("Undead Essence can be used to"))
		slots[15] = stack("Wither Essence x12", listOf("Wither Essence can be used to"))
		slots[31] = stack("Open Reward Chest", listOf("Contents", "Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12", "", "Cost", "500,000 Coins", "") + note + listOf("", "Click to open!"))
		slots[49] = stack("Close", emptyList())
		slots[50] = stack("Reroll Chest", listOf("Consume a Kismet Feather to reroll", "", "Bring a Kismet Feather"))
		val parsed = DungeonChestProfit.parseRewardChest(Component.literal("Emerald"), slots)
		assertNotNull(parsed)
		val (chest, kismet) = parsed!!
		assertEquals(500_000.0, chest.cost)
		assertEquals(0.0, kismet)
		assertEquals(listOf("Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12"),
		             chest.items.map { it.label.string })
	}

	@Test fun croesusRunListLoreStates() {
		val f3 = listOf("Floor III", "", "Players:", " Ashtonite365: Mage (20)", "", "Completed: 50 mins ago", "", "No chests opened yet!", "Chests expire in 2d 23h!")
		assertEquals(CroesusHelpers.RunState.UNOPENED, CroesusHelpers.runState(f3))
		val f4 = listOf("Floor IV", "", "Completed: 9 hours ago", "", "Opened Chest: Wood", "Chests expire in 2d 14h!")
		assertEquals(CroesusHelpers.RunState.PARTIAL, CroesusHelpers.runState(f4))
	}
}
