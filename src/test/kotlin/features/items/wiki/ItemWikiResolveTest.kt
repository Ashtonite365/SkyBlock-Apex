// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.ItemRef
import dev.ashtonite365.apex.features.items.wiki.ItemWikiEnchants
import dev.ashtonite365.apex.features.items.wiki.ItemWikiResolver
import dev.ashtonite365.apex.features.items.wiki.WikiRepoView
import dev.ashtonite365.apex.features.items.wiki.WikiResolveNames
import dev.ashtonite365.apex.features.items.wiki.WikiTarget
import dev.ashtonite365.apex.features.items.wiki.WikiTitles

class ItemWikiResolveTest {
	private val W = "https://hypixelskyblock.minecraft.wiki/w/"
	private class Repo(val rows: Map<String, Pair<String?, String?>>) : WikiRepoView {
		override fun ids() = rows.keys
		override fun wikiUrl(id: String) = rows[id]?.second
		override fun displayName(id: String) = rows[id]?.first
	}
	private val repo = Repo(mapOf(
		"ASPECT_OF_THE_END" to ("Aspect of the End" to "${W}Aspect_of_the_End"),
		"ODGER_TOOTH" to ("Odger's Tooth" to "${W}Odger%27s_Tooth"),
		"TATER_HELMET" to ("Tater Helmet" to "${W}Tater_Armor"),
		"JERRY;1" to ("[Lvl {LVL}] Jerry" to null),
		"JERRY;4" to ("[Lvl {LVL}] Jerry" to "${W}Jerry_Pet"),
		"BAT;4" to ("[Lvl {LVL}] Bat" to null),
		"ENCHANTED_SHARK_FIN" to ("Enchanted Shark Fin" to "${W}Shark_Fin"),
		"SHARK_FIN" to ("Shark Fin" to "${W}Shark_Fin"),
		"ESSENCE_WITHER" to ("Wither Essence" to "${W}Wither_Essence"),
		"BLOSSOM_BELT" to ("Blossom Belt" to "${W}Blossom_Set"),
		"BLOSSOM_CLOAK" to ("Blossom Cloak" to "${W}Blossom_Set"),
	))
	private val r = ItemWikiResolver(repo, listOf("Heroic", "Withered", "Fabled", "Wise", "Ancient", "Spiritual"))
	private fun res(id: String?, name: String? = null, isPet: Boolean = false, isBook: Boolean = false, ench: List<String> = emptyList()) =
		r.resolve(ItemRef(id, name, ench, isPet, isBook))!!
	private fun page(t: WikiTarget) = t.title + (t.anchor?.let { "#$it" } ?: "")

	@Test fun sharpnessOneIsSharpnessSeven() {
		val one = res("SHARPNESS;1", isBook = true); val seven = res("SHARPNESS;7", isBook = true)
		assertEquals("Sharpness", one.title); assertEquals(one, seven); assertEquals(one.cacheKey, seven.cacheKey)
		assertEquals("Sharpness", res("ENCHANTMENT_SHARPNESS_5").title)
		assertEquals("Sharpness", res("ENCHANTED_BOOK", "Enchanted Book", isBook = true, ench = listOf("sharpness")).title)
		assertEquals(one.cacheKey, res("ENCHANTED_BOOK", isBook = true, ench = listOf("sharpness")).cacheKey)
	}

	@Test fun ultimateDungeonAndSpecialEnchantsStripTheLevel() {
		assertEquals("Ultimate Wise", res("ULTIMATE_WISE;5", isBook = true).title)
		assertEquals("Legion", res("ULTIMATE_LEGION;1", isBook = true).title)
		assertEquals(res("ULTIMATE_LEGION;1", isBook = true), res("ULTIMATE_LEGION;5", isBook = true))
		assertEquals("Wisdom (Enchantment)", res("ULTIMATE_WISDOM;3", isBook = true).title)
		assertEquals("Duplex", res("ENCHANTMENT_ULTIMATE_REITERATE_4").title)
		assertEquals("Turbo-Wheat", res("TURBO_WHEAT;5", isBook = true).title)
		assertEquals("Triple-Strike", res("TRIPLE_STRIKE;4", isBook = true).title)
		assertEquals("Feather Falling", res("FEATHER_FALLING;10", isBook = true).title)
		assertEquals("Dragon Tracer", ItemWikiEnchants.pageOf("aiming"))
		assertEquals("SHARPNESS", ItemWikiEnchants.keyOfId("ENCHANTMENT_SHARPNESS_5"))
		assertEquals("ULTIMATE_WISE", ItemWikiEnchants.keyOfId("ULTIMATE_WISE;5"))
		assertNull(ItemWikiEnchants.keyOfId("HYPERION"))
	}

	@Test fun multiEnchantBookUsesTheFirst() {
		assertEquals("Ultimate Wise", res("ENCHANTED_BOOK", isBook = true, ench = listOf("ultimate_wise", "sharpness")).title)
		assertEquals("Enchanted Book", res("ENCHANTED_BOOK", isBook = true).title, "no enchantment -> the book page")
		assertEquals("Sharpness", r.resolve(ItemRef(null, "Enchanted Book", listOf("sharpness"), isBook = true))!!.title)
	}

	@Test fun repoLinkIsPercentDecoded() {
		assertEquals("Aspect of the End", res("ASPECT_OF_THE_END", "Aspect of the End").title)
		assertEquals("Odger's Tooth", res("ODGER_TOOTH", "Odger's Tooth").title)
		assertEquals("Tater Armor", res("TATER_HELMET", "Tater Helmet").title)
		assertEquals("Aspect of the End", res("ASPECT_OF_THE_END", "§9Heroic Aspect of the End ✪✪✪").title)
	}

	@Test fun jerryBooksAreThePetAndSharkFinKeepsItsOwnPage() {
		for (n in 1..5) assertEquals("Jerry Pet", res("JERRY;$n", "Enchanted Book", isBook = true).title, "JERRY;$n is a pet, not a book")
		assertEquals("Enchanted Shark Fin", res("ENCHANTED_SHARK_FIN", "Enchanted Shark Fin").title)
		assertEquals("Shark Fin", res("SHARK_FIN", "Shark Fin").title)
	}

	@Test fun petsAlwaysUseThePetPage() {
		assertEquals("Bat Pet", res("BAT;4", "[Lvl 100] Bat", isPet = true).title)
		assertEquals("Bat Pet", res("BAT;0", "§7[Lvl 1] §fBat", isPet = true).title)
		assertEquals("Golden Dragon Pet", res("GOLDEN_DRAGON;4", "[Lvl 200] Golden Dragon", isPet = true).title)
	}

	@Test fun minionsIncludingAMalformedNumeral() {
		assertEquals("Birch Minion", res("BIRCH_GENERATOR_11", "Birch Minion XIi").title)
		assertEquals("Birch Minion", res("BIRCH_GENERATOR_1", "Birch Minion I").title)
		assertEquals("Wheat Minion", res("WHEAT_GENERATOR_12", null).title)
	}

	@Test fun armourSetsKuudraStarredAndPerfect() {
		assertEquals("Crimson Armor", res("HOT_CRIMSON_HELMET").title)
		assertEquals("Aurora Armor", res("INFERNAL_AURORA_CHESTPLATE").title)
		assertEquals("Terror Armor", res("TERROR_BOOTS").title)
		assertEquals("Fiery Scuttler", res("FIERY_SCUTTLER_SC", "Fiery Scuttler").title, "not an armour piece")
		assertTrue(res("INFERNAL_MOB_MONSTER", "Infernal (Mob) (Monster)").rule != "kuudra-armour")
		assertEquals("Aspect of the End", res("STARRED_ASPECT_OF_THE_END", "Aspect of the End").title)
		assertEquals("starred", res("STARRED_ASPECT_OF_THE_END", "Aspect of the End").rule)
		assertEquals("Perfect Armor#Tier I", page(res("PERFECT_HELMET_1")))
		assertEquals("Perfect Armor", page(res("PERFECT_CHESTPLATE_5")))
	}

	@Test fun gemstonesAndCrystals() {
		assertEquals("Gemstone", res("FINE_RUBY_GEM").title)
		assertEquals("Gemstone", res("PERFECT_JADE_GEM").title)
		assertEquals("Gemstone Crystals#Jade", page(res("JADE_CRYSTAL")))
	}

	@Test fun petItemsTrophyFishCampfireStarsKeysRunesPotions() {
		assertEquals("All Skills Exp Boost", res("PET_ITEM_ALL_SKILLS_BOOST_COMMON", "All Skills Exp Boost").title)
		assertEquals("Trophy Fish#Blobfish", page(res("BLOBFISH_BRONZE", "Blobfish")))
		assertEquals("Trophy Fish#Obfuscated 2", page(res("OBFUSCATED_FISH_2_GOLD")))
		assertEquals("Trophy Fish", page(res("STEAMING_HOT_FLOUNDER_DIAMOND", "Steaming-Hot Flounder")), "no anchor")
		assertEquals("Campfire Badge", res("CAMPFIRE_TALISMAN_13").title)
		assertEquals("Soul Campfire Badge", res("SOUL_CAMPFIRE_TALISMAN_4").title)
		assertEquals("Master Star", res("THIRD_MASTER_STAR").title)
		assertEquals("Kuudra Keys", res("KUUDRA_HOT_TIER_KEY").title)
		assertEquals("Kuudra Keys", res("KUUDRA_TIER_KEY").title)
		val rune = res("MUSIC_RUNE;2", "◆ Music Rune II")
		assertEquals("Music Rune", rune.title); assertEquals(listOf("Runes"), rune.alternates)
		assertEquals("Music Rune", res("MUSIC_RUNE;3", null).title)
		val potion = res("POTION", "Speed Potion VIII")
		assertEquals("Speed Potion", potion.title); assertTrue("Potions" in potion.alternates)
		assertEquals("Speed Potion", res("POTION", "Splash Speed Potion III").title)
	}

	@Test fun hubPages() {
		assertEquals("Abiphones", res("ABIPHONE_XII_MEGA", "Abiphone XII Amber").title)
		assertEquals("Backpack", res("SMALL_BACKPACK", "Small Backpack").title)
		assertEquals("Minion Skins#Chicken", page(res("CHICKEN_PERSONALITY", "Chicken Minion Skin")))
		assertEquals("Lamps", res("LAMP_BLACK", "Black Lamp").title)
		assertEquals("Barn Skins", res("BAMBOO_BARN_SKIN", "Bamboo Barn Skin").title)
		assertEquals("Enrichments", res("TALISMAN_ENRICHMENT_DEFENSE").title)
		assertEquals("Garden Chips", res("CROPSHOT_GARDEN_CHIP").title)
		assertEquals("Travel Scrolls#the Bayou", page(res("BAYOU_TRAVEL_SCROLL", "Travel Scroll to the Bayou")))
		assertEquals("Furniture", res("BEAR_BED", "Bear Bed").title)
		assertEquals("Accessory Bag", res("LARGE_TALISMAN_BAG").title)
		assertEquals("Archer", res("ARCHER_DUNGEON_ABILITY_3", "Healing Bow").title, "not the Healing Bow -> Ghosts redirect")
		assertEquals("Mithril Powder", res("MITHRIL_POWDER_TIER_1").title)
	}

	@Test fun ownPagesAreKept() {
		assertEquals("Wither Essence", res("ESSENCE_WITHER", "Wither Essence").title)
		assertEquals("Enchanted Diamond", res("ENCHANTED_DIAMOND", "Enchanted Diamond").title)
		assertEquals("Jade Dye", res("DYE_JADE", "Jade Dye").title)
		assertEquals("Hideonring Shard", res("ATTRIBUTE_SHARD_ACCESSORY_SIZE;1", "Hideonring Shard").title)
		val belt = res("BLOSSOM_BELT", "Blossom Belt")
		assertEquals("Blossom Belt", belt.title); assertEquals(listOf("Blossom Set"), belt.alternates, "shared set page as the fallback")
	}

	@Test fun malformedNamesGetFallbacks() {
		val skin = res("PET_SKIN_BLACK_CAT_IVORY", "IvoryBlack Cat")
		assertTrue("Ivory Black Cat Skin" in skin.alternates && "Pet Skins" in skin.alternates, skin.toString())
		val mob = res("ZOMBIE_TEST_MONSTER", "Zombie (Monster)")
		assertTrue("Zombie (Private Island)" in mob.alternates && "Mob" in mob.alternates, mob.toString())
		assertEquals("Mob", res("SILVERFISH_MONSTER", "Silverfish (Monster)").title)
		assertEquals("Admin-only", res("DUNGEON_NORMAL_KEY", "Key").title)
	}

	@Test fun nameFallbackAndCleanup() {
		assertEquals("Hyperion", r.resolve(ItemRef(null, "§dHeroic Hyperion §6✪✪✪✪§c➎"))!!.title)
		assertNull(r.resolve(ItemRef(null, "  ")))
		assertNull(r.resolve(ItemRef(null, null)))
		assertEquals("Golden Dragon", WikiResolveNames.baseName("§7[Lvl 100] §6Golden Dragon", emptyList()))
		assertEquals("Enchanted Diamond", WikiResolveNames.baseName("Enchanted Diamond x64", emptyList()))
		assertEquals("Odger's Tooth", WikiTitles.percentDecode("Odger%27s Tooth"))
		assertEquals("Trophy Fish" to "Blobfish", WikiTitles.ofUrl("${W}Trophy_Fish#Blobfish"))
		assertNull(WikiTitles.ofUrl("https://minecraft.wiki/w/Stone"), "only the wiki host")
		assertEquals("Necron's Handle", WikiTitles.ofUrl("${W}Necron%27s_Handle")!!.first)
	}
}
