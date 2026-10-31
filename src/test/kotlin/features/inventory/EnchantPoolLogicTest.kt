package dev.ashtonite365.apex.test.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.EnchantPoolLogic
import dev.ashtonite365.apex.features.menus.enchant.EnchantConflicts

class EnchantPoolLogicTest {

	val available = mapOf(
		"SWORD" to listOf("sharpness", "smite", "bane_of_arthropods", "ultimate_wise", "ultimate_soul_eater", "ultimate_one_for_all", "looting"),
		"FISHING ROD" to listOf("angler", "blessing", "caster"),
		"LONGSWORD" to listOf("sharpness"),
		"HOE" to listOf("replenish", "harvesting"),
		"HELMET" to listOf("protection", "blast_protection", "fire_protection"),
	)
	val pools = listOf(
		listOf("ultimate_wise", "ultimate_soul_eater", "ultimate_one_for_all"),
		listOf("protection", "blast_protection", "fire_protection", "projectile_protection"),
		listOf("ultimate_one_for_all", "sharpness"), listOf("ultimate_one_for_all", "smite"),
	)

	@Test fun poolLookupKeepsSpacedKeys() {
		assertEquals(available["FISHING ROD"], EnchantPoolLogic.poolFor("FISHING ROD", available))
		assertEquals(available["FISHING ROD"], EnchantPoolLogic.poolFor("DUNGEON FISHING ROD", available))
		assertEquals(available["SWORD"], EnchantPoolLogic.poolFor("DUNGEON SWORD", available))
	}

	@Test fun itemTypeMapping() {
		assertEquals(available["LONGSWORD"], EnchantPoolLogic.poolFor("LONG SWORD", available))
		assertEquals(available["FISHING ROD"], EnchantPoolLogic.poolFor("ROD", available))
		assertEquals(available["HOE"], EnchantPoolLogic.poolFor("HOE", available))
		assertEquals(null, EnchantPoolLogic.poolFor("PET", available))
	}

	@Test fun missingDropsConflictsOnlyOneUltimate() {

		val split = EnchantConflicts.splitMissing(available["SWORD"]!!, setOf("sharpness", "ultimate_wise"), pools)
		assertEquals(listOf("smite", "bane_of_arthropods", "looting"), split.missing)
		val all = split.missing + split.conflicts.map { it.first }
		assertTrue("ultimate_soul_eater" in all && "ultimate_one_for_all" in all)
	}

	@Test fun protectionTypesConflict() {
		assertEquals(emptyList<String>(), EnchantConflicts.splitMissing(available["HELMET"]!!, setOf("protection"), pools).missing)
	}

	@Test fun levelMaths() {
		assertEquals(6, EnchantPoolLogic.combinedLevel(5, 5, 7))
		assertEquals(7, EnchantPoolLogic.combinedLevel(7, 7, 7))
		assertEquals(5, EnchantPoolLogic.combinedLevel(5, 3, 7))
		assertEquals(4, EnchantPoolLogic.combinedLevel(3, 3, null))
	}

	@Test fun combineCompatibility() {
		assertTrue(EnchantPoolLogic.canCombine(mapOf("sharpness" to 5), mapOf("looting" to 3), available["SWORD"], pools))
		assertFalse(EnchantPoolLogic.canCombine(mapOf("ultimate_wise" to 1), mapOf("ultimate_soul_eater" to 1), available["SWORD"], pools))
		assertFalse(EnchantPoolLogic.canCombine(mapOf(), mapOf("angler" to 1), available["SWORD"], pools))
		assertFalse(EnchantPoolLogic.canCombine(mapOf(), mapOf(), available["SWORD"], pools))
	}
}

class EnchantNameTest {
	val wise = listOf("§8Combinable in Anvil", "", "§d§lUltimate Wise I")
	val sharp5 = listOf("§8Combinable in Anvil", "", "§9Sharpness V")

	@Test fun knownId() {
		assertEquals("Sharpness", EnchantPoolLogic.enchantName("sharpness", null, listOf("§8Combinable in Anvil", "", "§9Sharpness I")))
		assertEquals("Sharpness V", EnchantPoolLogic.enchantName("sharpness", 5, sharp5))
	}
	@Test fun ultimate() = assertEquals("Ultimate Wise", EnchantPoolLogic.enchantName("ultimate_wise", 1, wise))
	@Test fun unknownTitleCased() = assertEquals("Ultimate Fancy Thing III", EnchantPoolLogic.enchantName("ultimate_fancy_thing", 3, null))
	@Test fun numerals() {
		assertEquals("VII", EnchantPoolLogic.roman(7)); assertEquals("X", EnchantPoolLogic.roman(10)); assertEquals("IV", EnchantPoolLogic.roman(4))
	}
	@Test fun neverBlank() {
		assertTrue(EnchantPoolLogic.enchantName("x", null, listOf("", "§8Combinable in Anvil")).isNotBlank())
		assertTrue(EnchantPoolLogic.enchantName("_", null, emptyList()).isNotBlank())
	}
}

class EnchantRepoDerivedTest {
	@Test fun noAliasTableSuffixMatch() {

		assertEquals("SWORD", EnchantPoolLogic.poolKey("LONG SWORD", listOf("SWORD", "BOW")))

		assertEquals(null, EnchantPoolLogic.poolKey("ROD", listOf("FISHING ROD", "LAVA ROD")))
		assertEquals("FISHING ROD", EnchantPoolLogic.poolKey("DUNGEON ROD", listOf("FISHING ROD", "SWORD")))
	}

	@Test fun maxLevelsFromRepoItemIds() {
		val ids = listOf("SHARPNESS;1", "SHARPNESS;7", "SHARPNESS;5", "EXPERIENCE;5", "ULTIMATE_WISE;5", "ASPECT_OF_THE_END", "PET;3")
		val known = setOf("sharpness", "experience", "ultimate_wise", "scavenger")
		val m = EnchantPoolLogic.maxLevels(ids, known, mapOf("scavenger" to 6, "sharpness" to 5))
		assertEquals(7, m["sharpness"])
		assertEquals(5, m["experience"]); assertEquals(5, m["ultimate_wise"]); assertEquals(6, m["scavenger"])
		assertEquals(null, m["pet"])
	}

	@Test fun namesFromBookCache() {
		val m = EnchantPoolLogic.namesToIds(mapOf("Sharpness VII" to "SHARPNESS;7", "Ultimate Wise V" to "ULTIMATE_WISE;5"))
		assertEquals("sharpness", m["Sharpness"]); assertEquals("ultimate_wise", m["Ultimate Wise"])
	}

	@Test fun romanParsing() {
		assertEquals(4, EnchantPoolLogic.parseLevel("IV")); assertEquals(10, EnchantPoolLogic.parseLevel("X"))
		assertEquals(24, EnchantPoolLogic.parseLevel("XXIV")); assertEquals(3, EnchantPoolLogic.parseLevel("3")); assertEquals(0, EnchantPoolLogic.parseLevel("Q"))
	}
}
