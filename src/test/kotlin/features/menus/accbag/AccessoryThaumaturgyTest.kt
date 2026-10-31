// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.accbag

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.accbag.AccessoryThaumaturgy
import dev.ashtonite365.apex.features.menus.accbag.AccessoryThaumaturgyMenu
import dev.ashtonite365.apex.features.menus.accbag.OneClickGate
import dev.ashtonite365.apex.features.menus.h6.BagsMenu
import dev.ashtonite365.apex.features.menus.h6.HubTileText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class AccessoryThaumaturgyTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val dump = RealDumpFixtures.load("accessory-thaumaturgy-2212-14")
	private fun model() = AccessoryThaumaturgy.parse(dump.title, dump.slots)!!

	private fun plain(l: List<String>) = l.map { it.replace(Regex("[\\uE000-\\uF8FF]"), "").replace(Regex(" +"), " ") }

	@Test fun titleRouting() {
		assertEquals("Accessory Bag Thaumaturgy", dump.title)
		MenuRegistry.register(BagsMenu); MenuRegistry.register(AccessoryThaumaturgyMenu)
		assertSame(AccessoryThaumaturgyMenu, MenuRegistry.match(dump.title))
		assertFalse(MenuRegistry.denied(dump.title))

		assertSame(BagsMenu, MenuRegistry.match("Accessory Bag (1/2)"))
		for (t in listOf("Accessory Bag Upgrades", "Accessory Bag", "Thaumaturgy")) assertFalse(MenuRegistry.match(t) === AccessoryThaumaturgyMenu, t)
	}

	@Test fun everyPowerReadLive() {
		val powers = dump.slots.mapNotNull(AccessoryThaumaturgy::power)
		assertEquals(listOf(10 to "No Power", 11 to "Adept", 12 to "Bloody", 13 to "Frozen", 14 to "Itchy", 15 to "Sighted", 16 to "Silky",
			19 to "Commando", 20 to "Disciplined", 21 to "Inspired", 22 to "Ominous", 23 to "Prepared", 24 to "Fortuitous", 25 to "Pretty",
			28 to "Protected", 29 to "Simple", 30 to "Warrior"), powers.map { it.slot to it.name })
		val adept = powers.first { it.name == "Adept" }
		assertEquals("Advanced Stone Power", adept.tier)
		assertEquals(listOf("+168.41 Health", "+96.23 Defense", "+36.09 Intelligence"), plain(adept.stats))
		assertEquals("+168.41\uE010 Health", adept.stats[0])
		assertEquals(listOf("+100 Health", "+50 Defense"), plain(adept.bonus))
		assertNull(adept.description); assertNull(adept.requirement)
		val ominous = powers.first { it.name == "Ominous" }
		assertEquals(listOf("+50.52 Health", "+36.09 Strength", "+14.43 Crit Chance", "+36.09 Crit Damage", "+14.43 Attack Speed",
			"+43.3 Intelligence", "+9.62 Speed"), plain(ominous.stats))
		assertTrue(ominous.bonus.isEmpty())
		val none = powers.first { it.slot == 10 }
		assertEquals("Starter Power", none.tier); assertTrue(none.stats.isEmpty())
		assertEquals("Decline to use any power as you choose to remain humble and avoid corrupting influences from more stats.", none.description)
	}

	@Test fun selectedPowerFromLore() {
		val powers = dump.slots.mapNotNull(AccessoryThaumaturgy::power)
		assertEquals(listOf("Itchy"), powers.filter { it.selected }.map { it.name })
		val m = model()
		val itchy = m.allTiles.first { it.slot == 14 }
		assertTrue(itchy.highlight); assertEquals("§a§l✔ SELECTED", itchy.info.first())
		assertEquals(1, m.allTiles.count { it.highlight })
		assertTrue(("Selected Power" to "Itchy") in m.chips)

		val moved = dump.slots.map { s -> when (s.slot) {
			14 -> s.copy(lore = s.lore.map { if (it == "Power is selected!") "Click to select power!" else it })
			12 -> s.copy(lore = s.lore.map { if (it == "Click to select power!") "Power is selected!" else it })
			else -> s } }
		val m2 = AccessoryThaumaturgy.parse(dump.title, moved)!!
		assertEquals(listOf(12), m2.allTiles.filter { it.highlight }.map { it.slot })
		assertTrue(("Selected Power" to "Bloody") in m2.chips)
	}

	@Test fun sectionsChipsAndNav() {
		val m = model()
		assertEquals(listOf("Starter Power (6)", "Advanced Stone Power (4)", "Intermediate Stone Power (2)", "Intermediate Power (5)", "Accessory Bag"),
			m.sections.map { it.title })
		assertEquals(listOf("Accessory Power" to "260", "Selected Power" to "Itchy", "Tuning Points" to "26", "Powers" to "17"), m.chips)
		assertEquals(listOf("Close" to 49), m.nav)
		val more = m.sections.last().tiles
		assertEquals(listOf(47 to "Accessory Bag Shortcut", 48 to "Accessories Breakdown", 50 to "Learn Power From Stones", 51 to "Stats Tuning"), more.map { it.slot to it.name })
		assertEquals(listOf(true, false, true, true), more.map { it.clickable })
		assertTrue(more.none { it.delayed })
		assertTrue(more.first { it.slot == 48 }.info.containsAll(listOf("8 MP x 13 Accs. = 104 MP", "Total: 260 Accessory Power")))
	}

	@Test fun powerClicksAreOneDelayedClickEach() {
		val tiles = model().sections.dropLast(1).flatMap { it.tiles }
		assertEquals(17, tiles.size)
		assertTrue(tiles.all { it.delayed && it.clickable && !it.locked && it.button == 0 && it.shiftButton == null })
		val g = OneClickGate(250)
		assertTrue(g.tryAcquire(1_000)); assertFalse(g.tryAcquire(1_100)); assertFalse(g.tryAcquire(1_249)); assertTrue(g.tryAcquire(1_250))
	}

	@Test fun textWrappedNeverCut() {
		val itchy = model().allTiles.first { it.slot == 14 }
		assertEquals(listOf("§a§l✔ SELECTED", "§8Advanced Stone Power", "§a+72.17 Strength", "§a+84.2 Crit Damage", "§a+21.65 Attack Speed",
			"§a+6.01 Speed", "§6Unique Power Bonus:", "§e+15 Strength", "§e+15 Crit Damage"), plain(itchy.info))
		val measure = { s: String -> s.replace(Regex("§."), "").length * 6 }
		val (name, info) = HubTileText.wrapAll(itchy.name, itchy.info, 78, measure)
		val words = { l: List<String> -> l.joinToString(" ").replace(Regex("§."), "").split(' ').filter { it.isNotEmpty() } }
		assertEquals(words(itchy.info), words(info))
		assertTrue(info.all { measure(it) <= 78 }); assertTrue(info.none { it.contains("…") }); assertEquals(listOf("§aItchy"), name)
	}

	@Test fun unreadableStaysVanilla() {
		assertNull(AccessoryThaumaturgy.parse(dump.title, dump.slots.filter { AccessoryThaumaturgy.power(it) == null }))
		assertNull(AccessoryThaumaturgy.parse("Accessory Bag Upgrades", dump.slots))
		assertNull(AccessoryThaumaturgy.power(SlotText(49, "Close", emptyList())))
		assertNull(AccessoryThaumaturgy.power(dump.slots.first { it.slot == 51 }))
	}

	@Test fun lockedPowerShownNotClicked() {
		val s = dump.slots.first { it.slot == 15 }
		val locked = s.copy(lore = s.lore.dropLast(1) + "Requires Combat Skill XV")
		val p = AccessoryThaumaturgy.power(locked)!!
		assertEquals("Requires Combat Skill XV", p.requirement)
		val t = AccessoryThaumaturgy.parse(dump.title, dump.slots.map { if (it.slot == 15) locked else it })!!.allTiles.first { it.slot == 15 }
		assertTrue(t.locked); assertTrue("§cRequires Combat Skill XV" in t.info)
	}
}
