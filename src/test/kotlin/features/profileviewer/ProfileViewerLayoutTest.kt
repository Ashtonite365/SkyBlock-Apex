// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.profileviewer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.profileviewer.ProfileViewerPages
import dev.ashtonite365.apex.features.profileviewer.ProfileViewerScreen
import dev.ashtonite365.apex.features.profileviewer.ProfileViewerScreen.InventoryKind

class ProfileViewerLayoutTest {
	private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

	@Test fun twoColumnsForPagedInventories() {
		val rows = ProfileViewerScreen.twoColumnBlockRows(InventoryKind.ENDER_CHEST)
		assertEquals(5, rows)
		assertEquals(0 to 0, ProfileViewerScreen.gridCell(0, rows))
		assertEquals(8 to 4, ProfileViewerScreen.gridCell(44, rows))
		assertEquals(9 to 0, ProfileViewerScreen.gridCell(45, rows))
		assertEquals(0 to 5, ProfileViewerScreen.gridCell(90, rows))
		assertEquals(25, ProfileViewerScreen.gridRows(9 * 45, rows))
		assertEquals(6, ProfileViewerScreen.twoColumnBlockRows(InventoryKind.BACKPACKS))
		assertEquals(5, ProfileViewerScreen.twoColumnBlockRows(InventoryKind.ACCESSORIES))
		assertEquals(null, ProfileViewerScreen.twoColumnBlockRows(InventoryKind.INVENTORY))
		assertEquals(3 to 2, ProfileViewerScreen.gridCell(21, null))
	}

	@Test fun heartOfTheForestFromSkillTree() {
		val m = json("""{"skill_tree":{"experience":{"mining":5,"foraging":12345},"tokens_spent":{"foraging":7},"nodes":{"foraging":{"a":1,"b":0,"toggle_a":1}}}}""")
		val lines = ProfileViewerPages.heartOfTheForest(m)
		assertTrue(lines[0].contains("Heart of the Forest") && lines[0].contains("12,345"), lines[0])
		assertTrue(lines.any { it.endsWith("§f7") }); assertTrue(lines.any { it.endsWith("unlocked: §f1") })
		assertEquals(listOf<String>(), ProfileViewerPages.heartOfTheForest(json("{}")))
	}

	@Test fun cropUpgradesCapped() {
		val ups = (1..20).joinToString(",") { "\"CROP_$it\":$it" }
		val lines = ProfileViewerPages.garden(json("""{"garden":{"crop_upgrade_levels":{$ups}}}"""), false)
		val i = lines.indexOf("§eCrop upgrades:")
		assertEquals(ProfileViewerPages.MAX_CROP_UPGRADES, lines.size - i - 1)
		assertTrue(lines.last().contains("+11 more"))
	}

	@Test fun bestiaryStatsOnSeparateLines() {
		val lines = ProfileViewerPages.bestiary(json("""{"bestiary":{"kills":{"zombie_1":3,"zombie_2":4},"deaths":{"zombie_1":2},"milestone":{"last_claimed_milestone":9}}}"""))
		assertEquals("§7Total kills: §f7", lines[1]); assertEquals("§7Deaths: §f2", lines[2]); assertEquals("§7Mob types: §f2", lines[3])
		assertTrue(lines.all { !it.contains("|") })
	}
}
