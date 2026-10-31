// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.dungeon.ChestLootPricing
import dev.ashtonite365.apex.features.dungeon.ChestLootPricing.Line
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit
import dev.ashtonite365.apex.repo.EnchantedBookNames
import dev.ashtonite365.apex.repo.ItemNameMatch
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class DungeonChestProfitTest {
	private data class Item(val id: String, val name: String, val lore: List<String> = emptyList())

	private val books = listOf(
		Item("FEATHER_FALLING;6", "§9Enchanted Book", listOf("§8Combinable in Anvil", "", "§9Feather Falling VI§r", "§7Increases how high you can fall")),
		Item("ULTIMATE_WISE;1", "§fEnchanted Book", listOf("§8Combinable in Anvil", "", "§d§lUltimate Wise I§r", "§7Reduces the ability mana cost of this")),

		Item("ANGLER;2", "§fEnchanted Book", listOf("§8Combinable in Anvil", "", "§9Angler I§r", "§7Grants +1α Sea Creature Chance.")),
		Item("AIMING;1", "§fEnchanted Book", listOf("§8Combinable in Anvil", "", "§9Dragon Tracer I§r", "§7Arrows home towards dragons if")),
		Item("ULTIMATE_LEGION;1", "§fEnchanted Book", listOf("§8Combinable in Anvil", "", "§d§lLegion I§r", "§7Increases all Combat stats and ✯")),

		Item("TURBO_CANE;1", "§fEnchanted Book", listOf("§7Use this on an item in an Anvil to", "§7apply it!", "", "§f§lCOMMON")),
		Item("ULTIMATE_BANK;2", "§fEnchanted Book", listOf("§7Use this on an item in an Anvil to", "§7apply it!", "", "§f§lCOMMON")),
		Item("ENCHANTED_BOOK_BUNDLE_CHIMERA", "§fEnchanted Book Bundle", listOf("§8Consumable", "")),
	)

	private val items = listOf(
		Item("STARRED_ADAPTIVE_HELMET", "§5Adaptive Helmet"),
		Item("ADAPTIVE_HELMET", "§5Adaptive Helmet"),
		Item("STARRED_BONZO_STAFF", "§9Bonzo's Staff"),
		Item("BONZO_STAFF", "§9Bonzo's Staff"),
		Item("SPIRIT_BOW", "§6Spirit Bow"),
		Item("HOT_POTATO_BOOK", "§5Hot Potato Book", listOf("§8Combinable in Anvil", "", "§7When applied to armor, grants +2❈")),
		Item("FUMING_POTATO_BOOK", "§5Fuming Potato Book", listOf("§8Combinable in Anvil", "", "§7When applied to armor, grants +2❈")),
		Item("POTATO_ITEM", "§fPotato"),
		Item("RECOMBOBULATOR_3000", "§6Recombobulator 3000"),
	) + books

	private fun namesOf(i: Item) = setOfNotNull(i.name, EnchantedBookNames.bookName(i.id, i.name, i.lore))

	private inner class TableLookup(rows: List<Item>) : ChestLootPricing.Lookup {
		private val byId = rows.associateBy { it.id }
		override fun byName(name: String) =
			ItemNameMatch.bestMatch(ItemNameMatch.preferredOrder(byId.values) { it.id }, name, false, ::namesOf)?.id
		override fun bookByName(enchantName: String) =
			byId.values.sortedBy { it.id }.firstOrNull { EnchantedBookNames.bookName(it.id, it.name, it.lore) == enchantName }?.id
		override fun exists(id: String) = id in byId
	}

	private val lookup = TableLookup(items)
	private val reversedLookup = TableLookup(items.reversed())

	private val prices = mapOf(
		"ADAPTIVE_HELMET" to 150_000.0, "STARRED_ADAPTIVE_HELMET" to 2_000_000.0,
		"BONZO_STAFF" to 90_000.0, "STARRED_BONZO_STAFF" to 1_200_000.0,
		"FEATHER_FALLING;6" to 300_000.0, "ULTIMATE_WISE;1" to 80_000.0,
		"ESSENCE_UNDEAD" to 1_000.0, "ESSENCE_WITHER" to 3_000.0,
		"HOT_POTATO_BOOK" to 70_000.0, "FUMING_POTATO_BOOK" to 1_100_000.0,
	)

	private fun value(lines: List<String>, master: Boolean, l: ChestLootPricing.Lookup = lookup) =
		lines.map { ChestLootPricing.value(ChestLootPricing.parseLine(it, master, l)!!, prices::get) }

	@Test fun bookNamesComeFromIdNotLoreOrder() {
		fun name(id: String) = books.first { it.id == id }.let { EnchantedBookNames.bookName(it.id, it.name, it.lore) }
		assertEquals("Feather Falling VI", name("FEATHER_FALLING;6"))
		assertEquals("Ultimate Wise I", name("ULTIMATE_WISE;1"))
		assertEquals("Angler II", name("ANGLER;2"))
		assertEquals("Dragon Tracer I", name("AIMING;1"))
		assertEquals("Legion I", name("ULTIMATE_LEGION;1"))
		assertEquals("Turbo-Cane I", name("TURBO_CANE;1"))
		assertEquals("Bank II", name("ULTIMATE_BANK;2"))
		assertNull(name("ENCHANTED_BOOK_BUNDLE_CHIMERA"))
		assertNull(EnchantedBookNames.bookName("HOT_POTATO_BOOK", "§5Hot Potato Book", listOf("§8Combinable in Anvil")))
		assertFalse(namesOf(books[0]).contains("Combinable in Anvil"))
	}

	@Test fun bookIdHelpers() {
		assertEquals("Soul Eater", EnchantedBookNames.baseNameFromId("ULTIMATE_SOUL_EATER"))
		assertEquals("Bane of Arthropods", EnchantedBookNames.baseNameFromId("BANE_OF_ARTHROPODS"))
		assertEquals("One For All", EnchantedBookNames.baseNameFromId("ULTIMATE_ONE_FOR_ALL"))
		assertEquals("Ultimate Jerry", EnchantedBookNames.baseNameFromId("ULTIMATE_JERRY"))
		assertEquals(listOf("I", "IV", "IX", "X", "XIV"), listOf(1, 4, 9, 10, 14).map(EnchantedBookNames::roman))
		assertEquals("ENCHANTMENT_FEATHER_FALLING_6", EnchantedBookNames.bazaarStockFor("FEATHER_FALLING;6"))
		assertEquals("FEATHER_FALLING;6", EnchantedBookNames.repoIdFor("feather_falling", 6))
		assertNull(EnchantedBookNames.bazaarStockFor("HOT_POTATO_BOOK"))
	}

	@Test fun enchantedBookLinePrices() {
		assertEquals(Line.Item("FEATHER_FALLING;6"), ChestLootPricing.parseLine("Enchanted Book (Feather Falling VI)", false, lookup))
		assertEquals(Line.Item("ULTIMATE_WISE;1"), ChestLootPricing.parseLine("Enchanted Book (Ultimate Wise I)", false, lookup))
		assertEquals(listOf(300_000.0, 80_000.0), value(listOf("Enchanted Book (Feather Falling VI)", "Enchanted Book (Ultimate Wise I)"), false))
	}

	private val emeraldLore = listOf(
		"Contents", "Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12",
		"", "Cost", "500,000 Coins", "", "Click to open!",
	)

	@Test fun f3EmeraldChest() {
		val (lines, costAt) = ChestLootPricing.croesusSplit(emeraldLore)!!
		val texts = lines.map { emeraldLore[it] }
		assertEquals(listOf("Adaptive Helmet", "Enchanted Book (Feather Falling VI)", "Undead Essence x21", "Wither Essence x12"), texts)
		assertFalse(ChestLootPricing.isMasterMode("The Catacombs - Floor III"))
		val parsed = texts.map { ChestLootPricing.parseLine(it, false, lookup) }
		assertEquals(Line.Item("ADAPTIVE_HELMET"), parsed[0])
		assertEquals(Line.Item("FEATHER_FALLING;6"), parsed[1])
		assertEquals(Line.Essence("Undead", 21), parsed[2])
		assertEquals(Line.Essence("Wither", 12), parsed[3])
		val values = value(texts, false)
		assertEquals(listOf(150_000.0, 300_000.0, 21_000.0, 36_000.0), values)
		val cost = ChestLootPricing.coinCost(emeraldLore.drop(costAt))
		assertEquals(500_000.0, cost)
		assertEquals(7_000.0, values.sumOf { it!! } - cost)
		assertEquals(values, value(texts, false, reversedLookup))
	}

	@Test fun masterModeChestPicksStarred() {
		assertTrue(ChestLootPricing.isMasterMode("Master Mode The Catacombs - Floor III"))
		assertTrue(ChestLootPricing.isMasterMode("M3 Emerald Chest"))
		assertFalse(ChestLootPricing.isMasterMode("Emerald Chest"))
		for (l in listOf(lookup, reversedLookup)) {
			assertEquals(Line.Item("STARRED_ADAPTIVE_HELMET"), ChestLootPricing.parseLine("Adaptive Helmet", true, l))
			assertEquals(Line.Item("STARRED_BONZO_STAFF"), ChestLootPricing.parseLine("Bonzo's Staff", true, l))
			assertEquals(Line.Item("BONZO_STAFF"), ChestLootPricing.parseLine("Bonzo's Staff", false, l))
			assertEquals(Line.Item("SPIRIT_BOW"), ChestLootPricing.parseLine("Spirit Bow", true, l))
		}
		assertEquals(listOf(2_000_000.0, 300_000.0), value(listOf("Adaptive Helmet", "Enchanted Book (Feather Falling VI)"), true))
	}

	@Test fun lookupOrderIsDeterministic() {
		val ids = listOf("STARRED_ADAPTIVE_HELMET", "ADAPTIVE_HELMET", "B", "A")
		assertEquals(listOf("A", "ADAPTIVE_HELMET", "B", "STARRED_ADAPTIVE_HELMET"), ItemNameMatch.preferredOrder(ids) { it })
		assertEquals(ItemNameMatch.preferredOrder(ids) { it }, ItemNameMatch.preferredOrder(ids.reversed()) { it })
		val exists = setOf("ADAPTIVE_HELMET", "STARRED_ADAPTIVE_HELMET", "SPIRIT_BOW")::contains
		assertEquals("STARRED_ADAPTIVE_HELMET", ItemNameMatch.dungeonVariant("ADAPTIVE_HELMET", true, exists))
		assertEquals("ADAPTIVE_HELMET", ItemNameMatch.dungeonVariant("STARRED_ADAPTIVE_HELMET", false, exists))
		assertEquals("SPIRIT_BOW", ItemNameMatch.dungeonVariant("SPIRIT_BOW", true, exists))
	}

	@Test fun potatoBooksKeepTheirName() {
		assertEquals(Line.Item("HOT_POTATO_BOOK"), ChestLootPricing.parseLine("Hot Potato Book", false, lookup))
		assertEquals(Line.Item("FUMING_POTATO_BOOK"), ChestLootPricing.parseLine("Fuming Potato Book", true, lookup))
		assertEquals(listOf(70_000.0, 1_100_000.0), value(listOf("Hot Potato Book", "Fuming Potato Book"), false))
		assertEquals(Line.Item("RECOMBOBULATOR_3000"), ChestLootPricing.parseLine("Recombobulator 3000", false, lookup))
		assertEquals(Line.Item(null), ChestLootPricing.parseLine("Mystery Thing", false, lookup))
	}

	private fun stack(name: String, nbt: CompoundTag?) = ItemStack(Items.ENCHANTED_BOOK).apply {
		set(DataComponents.CUSTOM_NAME, Component.literal(name))
		nbt?.let { set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
	}

	@Test fun rewardSlotWithEnchantments() {
		val book = stack("Enchanted Book", CompoundTag().apply {
			putString("id", "ENCHANTED_BOOK")
			put("enchantments", CompoundTag().apply { putInt("feather_falling", 6) })
		})
		assertEquals("FEATHER_FALLING;6", DungeonChestProfit.rewardStackId(book))
		val starred = stack("Adaptive Helmet", CompoundTag().apply { putString("id", "STARRED_ADAPTIVE_HELMET") })
		assertEquals("STARRED_ADAPTIVE_HELMET", DungeonChestProfit.rewardStackId(starred))
		val plain = stack("Adaptive Helmet", CompoundTag().apply { putString("id", "ADAPTIVE_HELMET") })
		assertEquals("ADAPTIVE_HELMET", DungeonChestProfit.rewardStackId(plain))
		assertNull(DungeonChestProfit.rewardStackId(stack("Enchanted Book", null)))
		val twoEnchants = stack("Enchanted Book", CompoundTag().apply {
			putString("id", "ENCHANTED_BOOK")
			put("enchantments", CompoundTag().apply { putInt("protection", 6); putInt("growth", 6) })
		})
		assertNull(DungeonChestProfit.rewardStackId(twoEnchants))
	}

	@Test fun rewardChestContentsFallback() {
		val openLore = listOf("Contents", "Enchanted Book (Feather Falling VI)", "Hot Potato Book", "Undead Essence x21", "", "Cost", "500,000 Coins")
		val books = ChestLootPricing.contentsBookLines(openLore)
		assertEquals(listOf("Enchanted Book (Feather Falling VI)"), books)
		assertEquals(Line.Item("FEATHER_FALLING;6"), ChestLootPricing.parseLine(books[0], false, lookup))
		assertEquals(12, ChestLootPricing.amountFromName("Wither Essence x12", 1))
		assertEquals(1, ChestLootPricing.amountFromName("Adaptive Helmet", 1))
	}
}
