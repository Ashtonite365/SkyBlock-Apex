// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.skyblock

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.test.testutil.ItemResources
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.skyblock.AbilityUtils
import dev.ashtonite365.apex.util.skyblock.AbilityUtils.AbilityActivation
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.skyblock.SackUtil
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.skyblock.TabListAPI
import dev.ashtonite365.apex.util.skyblock.TabListAPI.WidgetName
import dev.ashtonite365.apex.util.timestamp
import dev.ashtonite365.apex.util.bareText

class SkyblockParsingTest {

	private data class Ability(
		val title: String,
		val scrolled: Boolean,
		val trigger: AbilityActivation,
		val mana: Int?,
		val cooldown: Duration?,
		val text: List<String>,
	)

	private fun abilitiesOf(sample: String): List<Ability> =
		AbilityUtils.abilitiesIn(ItemResources.loadItem(sample)).map { found ->
			Ability(found.name, found.hasPowerScroll, found.activation, found.manaCost, found.cooldown,
				found.descriptionLines.map(Component::bareText))
		}

	@TestFactory
	fun abilities(): List<DynamicTest> = mapOf(
		"titanium-drill" to listOf(
			Ability("Pickobulus", false, AbilityActivation.RIGHT_CLICK, null, 48.seconds,
				listOf("Throw your pickaxe to create an", "explosion mining all ores in a 3 block", "radius.")),
		),
		"diamond-pickaxe" to listOf(
			Ability("Mining Speed Boost", true, AbilityActivation.RIGHT_CLICK, null, 2.minutes,
				listOf("Grants +200% ⸕ Mining Speed for", "10s.")),
		),
		"aspect-of-the-void" to listOf(
			Ability("Instant Transmission", true, AbilityActivation.RIGHT_CLICK, 23, null,
				listOf("Teleport 12 blocks ahead of you and", "gain +50 ✦ Speed for 3 seconds.")),
			Ability("Ether Transmission", false, AbilityActivation.SNEAK_RIGHT_CLICK, 90, null,
				listOf("Teleport to your targeted block up", "to 61 blocks away.", "Soulflow Cost: 1")),
		),
	).map { (sample, expected) -> DynamicTest.dynamicTest("abilities of $sample") { assertEquals(expected, abilitiesOf(sample)) } }

	@TestFactory
	fun itemTypes(): List<DynamicTest> = listOf(
		Triple("pets/lion-item", ItemType.PET, "pet"),
		Triple("pets/rabbit-selected", ItemType.PET, "selected pet"),
		Triple("pets/mithril-golem-not-selected", ItemType.PET, "unselected pet"),
		Triple("aspect-of-the-void", ItemType.SWORD, "sword"),
		Triple("titanium-drill", ItemType.DRILL, "drill"),
		Triple("diamond-pickaxe", ItemType.PICKAXE, "pickaxe"),
		Triple("gemstone-gauntlet", ItemType.GAUNTLET, "gauntlet"),
	).map { (sample, type, what) ->
		DynamicTest.dynamicTest("$sample is a $what") { assertEquals(type, ItemType.fromItemStack(ItemResources.loadItem(sample))) }
	}

	@TestFactory
	fun sackMessages(): List<DynamicTest> = listOf("sacks/gain-rotten-flesh", "sacks/gain-and-lose-regular").map { sample ->
		DynamicTest.dynamicTest(sample) {
			assertEquals(listOf(SackUtil.SackUpdate(SkyBlockItems.ROTTEN_FLESH, "Rotten Flesh", 1)),
				SackUtil.getUpdatesFromMessage(ItemResources.loadText(sample)))
		}
	}

	@Test
	fun tabListWidgets() {
		val tab = ItemResources.loadTablist("dungeon_hub")
		fun lines(widget: WidgetName, withTitle: Boolean) =
			TabListAPI.getWidgetLines(widget, includeTitle = withTitle, from = tab).map(Component::getString)

		assertEquals(listOf("Profile: Strawberry", " SB Level: [210] 26/100 XP", " Bank: 1.4B", " Interest: 12 Hours (689.1k)"),
			lines(WidgetName.PROFILE, true))

		assertEquals(listOf(" Bonzo IV: 110/150", " Scarf II: 25/50", " The Professor IV: 141/150", " Thorn I: 29/50",
			" Livid II: 91/100", " Sadan V: 388/500", " Necron VI: 531/750"), lines(WidgetName.COLLECTION, false))
		assertEquals(listOf(" Undead: 1,907", " Wither: 318"), lines(WidgetName.ESSENCE, false))
	}

	@Test
	fun itemTimestamps() {

		assertEquals(Instant.ofEpochSecond(1_658_091_600), ItemResources.loadItem("hyperion").timestamp)

		val expected = ZonedDateTime.of(2021, 10, 11, 15, 39, 0, 0, SkyBlockSession.hypixelTimeZone).toInstant()
		assertEquals(expected, ItemResources.loadItem("backpack-in-menu").timestamp)
	}
}
