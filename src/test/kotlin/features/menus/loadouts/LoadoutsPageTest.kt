// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.loadouts

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.support.FallbackPlanner
import dev.ashtonite365.apex.features.menus.support.SlotDumpGate
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.wardrobe.*
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.test.features.menus.m2.M2

class LoadoutsPageTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun real() = RealDumpFixtures.load("loadouts-p1").let { LoadoutPageParser.parse(it.title, it.slots)!! to it }
	private fun synth() = RealDumpFixtures.load("loadouts-synthetic-active").let { LoadoutPageParser.parse(it.title, it.slots)!! to it }

	@Test fun cardCountEqualsLiveLoadoutSlots() {
		val (p, d) = real()
		val live = d.slots.count { it.slot in LoadoutKeybinds.Logic.loadoutSlots && it.cleanName.isNotBlank() }
		assertEquals(live, p.cards.size); assertEquals(12, p.cards.size)

		val fewer = LoadoutPageParser.parse(d.title, d.slots.filter { it.slot != 43 })!!
		assertEquals(11, fewer.cards.size)
	}

	@Test fun activeDetection() {
		assertNull(real().first.active)
		val (p, _) = synth()
		assertEquals(14, p.active!!.loadout.slot)
		assertEquals(1, p.cards.count { it.loadout.active })
		assertFalse(p.cards.first { it.loadout.slot == 15 }.loadout.active)
	}

	@Test fun lockedDetectionWithRequirementLore() {
		val (p, _) = real()
		val locked = p.cards.filter { it.loadout.locked }
		assertEquals(listOf(24, 25, 32, 33, 34, 41, 42, 43), locked.map { it.loadout.slot })
		assertTrue(locked.all { c -> c.requirement.any { it.contains("[MVP+]") } })
		assertTrue(p.cards.filter { !it.loadout.locked }.all { it.requirement.isEmpty() })
	}

	@Test fun clickForwardsExactlyOnePacketToRealSlotAndLeftRefreshesOnUpdate() {
		val sent = mutableListOf<Pair<Int, Int>>()
		val c = LoadoutController({ s, b -> sent += s to b })
		val d = RealDumpFixtures.load("loadouts-p1")
		c.update(d.title, d.slots)
		val lay = LoadoutLayout(LRect(0, 0, 420, 222))
		val r = lay.card(1)
		assertTrue(c.clickAt(lay, r.x + 2, r.y + 2))
		assertEquals(listOf(15 to 0), sent)

		assertNull(c.page!!.active); assertEquals("Molten Belt", c.page!!.cell(1, 2)!!.name)
		val s = RealDumpFixtures.load("loadouts-synthetic-active")
		c.update(s.title, s.slots)
		assertEquals(2, c.updates)
		assertEquals(14, c.page!!.active!!.loadout.slot)
		assertTrue(c.page!!.cell(1, 2)!!.empty)
		assertEquals(1, sent.size)
	}

	@Test fun splitLayoutStaysInsideFrame() {
		for ((w, h) in listOf(420 to 222, 300 to 200, 600 to 300)) {
			val f = LRect(10, 20, w, h); val lay = LoadoutLayout(f)
			for (col in 0..3) for (row in 0..3) assertTrue(lay.cell(col, row).inside(lay.left), "cell $col,$row in $w x $h")
			assertTrue(lay.left.inside(f)); assertTrue(lay.right.inside(f)); assertTrue(lay.details.inside(f)); assertTrue(lay.footer.inside(f))
			assertTrue(lay.left.x + lay.left.w <= lay.right.x)
			assertTrue(lay.card(0).inside(lay.cardsArea))
		}
	}

	@Test fun leftHalfFromLiveSlots3x4AndSlotIndexMapping() {
		val (p, d) = real()
		val by = d.slots.associateBy { it.slot }

		for (col in 0..2) for (row in 0..3) {
			val c = p.cell(col, row)!!
			assertEquals((row + 1) * 9 + col, c.slot)
			assertEquals(by[c.slot!!]!!.lore, c.lore)
		}
		assertEquals(listOf("Heart of the Forest Slot", "Heart of the Mountain Slot", "Power Stone", "Stats Tuning"), (0..3).map { p.cell(0, it)!!.name })
		assertEquals(listOf(LoadoutRole.NECKLACE, LoadoutRole.CLOAK, LoadoutRole.BELT, LoadoutRole.BRACELET), (0..3).map { p.cell(1, it)!!.role })
		assertEquals(listOf(LoadoutRole.HELMET, LoadoutRole.CHESTPLATE, LoadoutRole.LEGGINGS, LoadoutRole.BOOTS), (0..3).map { p.cell(2, it)!!.role })
		assertTrue(p.cells.all { it.typeOk })
		assertEquals(13, p.cells.size); assertTrue(p.missing.isEmpty())
	}

	@Test fun petRightOfChestplate() {
		val (p, _) = real()
		val pet = p.pet!!; val chest = p.cell(2, 1)!!
		assertEquals(LoadoutRole.CHESTPLATE, chest.role)
		assertEquals(chest.row, pet.row); assertEquals(chest.col + 1, pet.col); assertEquals(chest.slot!! + 1, pet.slot)
		assertEquals("[Lvl 100] Griffin", pet.name)
		val lay = LoadoutLayout(LRect(0, 0, 420, 222))
		val a = lay.cell(chest.col, chest.row); val b = lay.cell(pet.col, pet.row)
		assertEquals(a.y, b.y); assertTrue(b.x > a.x + a.w - 1)
	}

	@Test fun emptyAndMissingRolesArePlaceholders() {
		val (p, _) = synth()
		val belt = p.cell(1, 2)!!
		assertTrue(belt.empty); assertEquals("Belt", belt.name); assertNull(belt.summary)
		val d = RealDumpFixtures.load("loadouts-p1"); val logs = mutableListOf<String>()
		val q = LoadoutPageParser.parse(d.title, d.slots.filter { it.slot != 38 }) { logs += it }!!
		assertTrue(q.cell(2, 3)!!.empty); assertEquals("Boots", q.cell(2, 3)!!.name)
		assertEquals(listOf("Boots"), q.missing); assertTrue(logs.any { "Boots" in it })

		val swapped = d.slots.map { if (it.slot == 10) it.copy(lore = it.lore.dropLast(1) + "EPIC CLOAK") else it }
		val logs2 = mutableListOf<String>()
		val r = LoadoutPageParser.parse(d.title, swapped) { logs2 += it }!!
		assertEquals(LoadoutRole.NECKLACE, r.cell(1, 0)!!.role); assertFalse(r.cell(1, 0)!!.typeOk); assertTrue(logs2.isNotEmpty())
	}

	@Test fun summaryAbilityFirstThenStatAndTooltipIsLiveLore() {
		val lore = listOf("§7Damage: §c+100", "§7Strength: §c+50", "", "§6Ability: Instant Transmission §e§lRIGHT CLICK", "§9EPIC SWORD")
		val s = SlotText(11, "Aspect of the End", lore)
		assertEquals("Ability: Instant Transmission RIGHT CLICK", LoadoutSummary.line(s))
		assertEquals("Health: +30", LoadoutSummary.line(SlotText(10, "x", listOf("Health: +30", "Defense: +20", "EPIC NECKLACE"))))
		assertEquals("Item Ability: Leap", LoadoutSummary.line(SlotText(1, "y", listOf("Strength: +5", "Item Ability: Leap"))))
		val (p, d) = real()
		val cell = p.cell(2, 0)!!
		assertEquals(d.slots.first { it.slot == 11 }.lore, cell.lore)
		assertEquals("Health: +215 (+40)", cell.summary)
	}

	@Test fun bottomBarMirrorsLiveButtons() {
		val (p, _) = real()
		assertEquals(48, p.nav.back); assertEquals(49, p.nav.close); assertEquals(44, p.nav.next); assertTrue(p.extraButtons.isEmpty())
		val (s, _) = synth()
		assertEquals(listOf(50), s.extraButtons.map { it.slot })
	}

	@Test fun titlesRegistryAndFallback() {
		M2.reg(LoadoutMenu, true)
		for (t in listOf("(1/3) Loadouts", "(3/3) Loadouts", "Loadouts", "§a(2/3) Loadouts")) assertTrue(M2.attaches(t), t)
		assertNull(MenuRegistry.match("Loadout Editor")); assertNull(MenuRegistry.match("SkyBlock Menu"))
		assertTrue(LoadoutParser.parse("Loadouts", RealDumpFixtures.load("loadouts-p1").slots) != null)
		assertTrue(MenuRegistry.match("(1/3) Loadouts", emptyList())?.id == "loadouts")

		val d = RealDumpFixtures.load("loadouts-p1")
		val planner = FallbackPlanner({ it == "SkyBlock Menu" })
		assertEquals(FallbackPlanner.Decision.GoBack(48, "SkyBlock Menu"), planner.decide(d.title, d.slots, 0))
	}

	@Test fun slotDumpGateCapturesLoadoutsEveryUpdate() {
		assertTrue(SlotDumpGate.everyUpdate("(1/3) Loadouts")); assertTrue(SlotDumpGate.everyUpdate("Loadouts"))
		assertTrue(SlotDumpGate.everyUpdate("Museum Milestones")); assertFalse(SlotDumpGate.everyUpdate("SkyBlock Menu"))

		val g = SlotDumpGate()
		g.opened(900)
		assertFalse(g.offer(900, 1, 10, 0, everyUpdate = true)); assertTrue(g.offer(900, 1, 10, 400, everyUpdate = true))
		assertFalse(g.offer(900, 2, 10, 500, everyUpdate = true)); assertTrue(g.offer(900, 2, 10, 900, everyUpdate = true))
	}
}
