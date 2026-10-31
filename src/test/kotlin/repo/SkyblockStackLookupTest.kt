// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.SkyblockStackLookup

class SkyblockStackLookupTest {

	private val reforges = listOf("Bountiful", "Blessed", "Green Thumb", "Deep Fried", "Heroic", "Strong", "Wise", "Bloody", "Fabled")
	private val items = mapOf(
		"Melon Dicer Mk. II" to "MELON_DICER_2", "Melon Dicer" to "MELON_DICER", "Hyperion" to "HYPERION",
		"Strong Dragon Chestplate" to "STRONG_DRAGON_CHESTPLATE", "Euclid's Wheat Hoe Tier 3" to "THEORETICAL_HOE_WHEAT_3",
		"Golden Fried Shark" to "GOLDEN_FRIED_SHARK",
	)
	private fun resolve(id: String?, name: String?) =
		SkyblockStackLookup.resolve(id, name, reforges, isKnown = { it in items.values }, byName = { items[it] })

	@Test fun bountifulMelonDicerById() = assertEquals("MELON_DICER_2", resolve("MELON_DICER_2", "§9Bountiful Melon Dicer Mk. II"))
	@Test fun bountifulMelonDicerByNameWithoutId() = assertEquals("MELON_DICER_2", resolve(null, "§9Bountiful Melon Dicer Mk. II"))
	@Test fun idWinsOverName() = assertEquals("MELON_DICER_2", resolve("MELON_DICER_2", "§9Bountiful Melon Dicer"))
	@Test fun unknownIdFallsBackToName() = assertEquals("MELON_DICER", resolve("NOT_IN_REPO", "§9Blessed Melon Dicer"))
	@Test fun multiWordReforge() = assertEquals("THEORETICAL_HOE_WHEAT_3", resolve(null, "§6Green Thumb Euclid's Wheat Hoe Tier 3"))
	@Test fun multiWordReforgeDeepFried() = assertEquals("GOLDEN_FRIED_SHARK", resolve(null, "§6Deep Fried Golden Fried Shark"))
	@Test fun starredAndMasterStarred() {
		assertEquals("HYPERION", resolve(null, "§dHeroic Hyperion §6✪✪✪✪✪§c➋"))
		assertEquals("HYPERION", resolve(null, "§d⚚ Fabled Hyperion ✪✪✪"))
	}
	@Test fun nameThatStartsWithAReforgeIsTriedAsIsFirst() {
		assertEquals("STRONG_DRAGON_CHESTPLATE", resolve(null, "§6Strong Dragon Chestplate"))
		assertEquals("STRONG_DRAGON_CHESTPLATE", resolve(null, "§6Wise Strong Dragon Chestplate ✪✪"))
	}
	@Test fun nothingMatches() = assertNull(resolve(null, "§fDirt"))
	@Test fun stripReforgeLongestFirst() =
		assertEquals("Euclid's Wheat Hoe Tier 3", SkyblockStackLookup.stripReforge("Green Thumb Euclid's Wheat Hoe Tier 3", reforges + "Green"))
}
