// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankAmounts
import dev.ashtonite365.apex.features.menus.bank.BankCap
import dev.ashtonite365.apex.features.menus.bank.BankChains
import dev.ashtonite365.apex.features.menus.bank.BankLayout
import dev.ashtonite365.apex.features.menus.bank.BankPage
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.bank.BankProfileCache
import dev.ashtonite365.apex.features.menus.bank.BankProfileData
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumRemoveGuard
import dev.ashtonite365.apex.features.menus.museum.MuseumTabs
import dev.ashtonite365.apex.features.museum.MuseumHelper

class RealDumpTest {
	private val museum = RealDumpFixtures.load("museum-main")
	private val special = RealDumpFixtures.load("museum-special")
	private val bank = RealDumpFixtures.load("bank-main")
	private val withdraw = RealDumpFixtures.load("bank-withdraw")

	@Test
	fun museumCountsFromRealLore() {
		val m = MuseumParser.parse(museum.title, museum.slots)!!
		val want = mapOf("Combat" to (20 to 33 to 245), "Farming" to (21 to 9 to 101), "Mining" to (22 to 9 to 74), "Fishing" to (23 to 8 to 66),
			"Foraging" to (24 to 5 to 27), "Dungeoneering" to (30 to 22 to 98), "Hunting" to (31 to 0 to 25))
		for ((n, v) in want) {
			val c = m.categories.single { it.name == n }
			assertEquals(v.first.first, c.slot, n); assertEquals(v.first.second, c.donated, n); assertEquals(v.second, c.total, n)
		}
		val sp = m.categories.single { it.name == "Special Items" }
		assertEquals(32, sp.slot); assertEquals(1, sp.donated); assertNull(sp.total)
		assertEquals("§a1", MuseumHelper.MuseumCounts.label(MuseumHelper.MuseumCounts.Shown(1, null, false)))
		assertTrue(m.categories.none { it.name == "Close" || it.slot == 49 })
		assertTrue(m.categories.none { it.name == "Museum" })
	}

	@Test
	fun museumHeaderFromSlots4And51() {
		val st = MuseumParser.parse(museum.title, museum.slots)!!.stats!!
		assertEquals(4, st.slot); assertEquals(86, st.donated); assertEquals(636, st.total)
		val all = st.lines.joinToString("\n")
		assertTrue(all.contains("134,101,143"), all)
		assertTrue(all.contains("135,875"), all)
	}

	@Test
	fun museumLayoutFromSlotOrder() {
		val tabs = MuseumTabs.of(MuseumParser.parse(museum.title, museum.slots)!!.categories)
		assertEquals(listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting", "Special Items"), tabs.main.map { it.name })
		assertEquals(listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering"), tabs.carousel(0).map { it.name })
		assertEquals(listOf("Hunting", "Special Items"), tabs.carousel(1).map { it.name })
		assertEquals(32, tabs.special!!.slot); assertEquals(1, tabs.main.count { it.slot == 32 })

		val r = dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.rects(6, 228, 328, tabs.more.size)
		assertEquals(1, r.map { it.w }.toSet().size)
		val w = { s: String -> s.length * 6 }
		assertTrue(dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.fits(tabs.more.map { it.name }, r, w,
			dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.scaleToFit(tabs.more.map { it.name }, r, w)))
		val more = tabs.more.map { it.slot }
		assertTrue(more.containsAll(listOf(48, 50)), "$more"); assertFalse(51 in more)
	}

	@Test
	fun milestonesLore() {
		val l = museum.slots.single { it.slot == 48 }.cleanLore
		assertEquals(3 to 40, MuseumParser.loreCount(l))
	}

	@Test
	fun specialItemsPage() {
		val m = MuseumParser.parse(special.title, special.slots)!!
		assertEquals("Special Items", m.category)
		assertEquals(listOf(10), m.entries.map { it.slot })
		val e = m.entries.single()
		assertTrue(e.removable)
		val d = e.details!!
		assertEquals("SPECIAL", d.rarity); assertEquals(1, d.atriumSlot)
		assertTrue(d.value!!.contains("150,000")); assertTrue(d.cleanValue!!.contains("127,000"))
		assertEquals(47, m.extraDetailsSlot); assertEquals(true, m.extraDetailsOn)
		assertEquals(51, m.sortControl!!.slot); assertEquals("Ascending XP", m.sortControl!!.selected)
		assertEquals(1, m.countOnly)
	}

	@Test
	fun removableNeedsConfirmAndNoChainTargetsIt() {
		val m = MuseumParser.parse(special.title, special.slots)!!
		val g = MuseumRemoveGuard()
		assertNull(g.click(m.entries.single()))
		assertEquals("Remove Shrimp the Fish from museum?", g.prompt())
		assertEquals(10, g.confirm()); assertNull(g.prompt())
		assertTrue(MuseumRemoveGuard.isProtected(m, 10)); assertTrue(MuseumRemoveGuard.isProtected(m, 11))
	}

	@Test
	fun countsCachePerProfileAndRoundTrip() {
		val a = MuseumHelper.Data(); val b = MuseumHelper.Data()
		assertTrue(MuseumHelper.MuseumCounts.record(a, "Combat", 33, 245, 1))
		assertFalse(MuseumHelper.MuseumCounts.record(a, "§aCombat", 33, 245, 2))
		assertEquals(MuseumHelper.MuseumCounts.Shown(33, 245, true), MuseumHelper.MuseumCounts.shown("Combat", null, null, a))
		assertNull(MuseumHelper.MuseumCounts.shown("Combat", null, null, b))
		assertEquals(MuseumHelper.MuseumCounts.Shown(34, 245, false), MuseumHelper.MuseumCounts.shown("Combat", 34, 245, a))
		val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
		val back = json.decodeFromString(MuseumHelper.Data.serializer(), json.encodeToString(MuseumHelper.Data.serializer(), a))
		assertEquals(a.counts, back.counts)
		assertTrue(json.decodeFromString(MuseumHelper.Data.serializer(), "{}").counts.isEmpty())
	}

	@Test
	fun bankMainFromRealDump() {
		val m = BankParser.parse(bank.title, bank.slots)!!
		assertEquals(BankPage.MAIN, m.page)
		assertEquals(373_567_481.8, m.balance!!, 0.01)
		assertNull(m.purse)
		assertEquals(11 * 3600L, m.interestInSeconds)
		assertEquals("Super Deluxe", m.tier)
		assertEquals(500_000_000.0, m.bankLimit); assertEquals(500_000_000.0, m.balanceLimit)
		assertEquals(413_400.0, m.lastInterest); assertEquals(413_400.0, m.projectedInterest); assertEquals("0.111%", m.projectedPct)
		assertEquals(10, m.transactions.size)
		val t0 = m.transactions[0]
		assertEquals(1, t0.sign); assertEquals(2_019_827.0, t0.amount); assertEquals("27 mins ago", t0.ageText); assertEquals(27 * 60L, t0.ageSeconds)
		assertEquals(-1, m.transactions[2].sign); assertEquals(37_150_653.0, m.transactions[2].amount)
		assertEquals("3 days ago", m.transactions[9].ageText)
	}

	@Test
	fun capSourcesAndCache() {
		val m = BankParser.parse(bank.title, bank.slots)!!
		assertEquals(500_000_000.0, m.cap)
		val only32 = BankParser.parse(bank.title, bank.slots.filter { it.slot != 35 })!!
		assertNull(only32.bankLimit); assertEquals(500_000_000.0, only32.cap)
		val noBankLimit = bank.slots.map { if (it.slot == 35) it.copy(lore = it.lore.map { l -> l.replace("500,000,000", "1,000,000,000") }) else it }
		assertEquals(1e9, BankParser.parse(bank.title, noBankLimit)!!.cap)
		val d = BankProfileData()
		assertTrue(BankProfileCache.update(d, m)); assertEquals(5e8, d.cap); assertEquals("Super Deluxe", d.tier)
		assertFalse(BankProfileCache.update(d, m))
		assertFalse(BankProfileCache.update(BankProfileData(), BankParser.parse(withdraw.title, withdraw.slots)!!))
		assertEquals("373.57M / 500M", BankCap.text(373_567_481.8, 5e8))
		assertEquals(0.747, BankCap.fraction(373_567_481.8, 5e8)!!, 0.001)
	}

	@Test
	fun withdrawPageByName() {
		val m = BankParser.parse(withdraw.title, withdraw.slots)!!
		assertEquals(BankPage.WITHDRAW, m.page)
		assertEquals(10, m.button(BankAction.WITHDRAW_ALL)!!.slot)
		assertEquals(12, m.button(BankAction.WITHDRAW_HALF)!!.slot)
		assertEquals(14, m.button(BankAction.WITHDRAW_20)!!.slot)
		assertEquals(16, m.button(BankAction.WITHDRAW_CUSTOM)!!.slot)
		assertEquals(31, m.button(BankAction.BACK)!!.slot)

		assertEquals(74_713_496.0, BankAmounts.shown(BankAction.WITHDRAW_20, m, null, 1.0))
		assertEquals(BankAmounts.AmountLine("-74M", BankAmounts.RED), BankAmounts.amountLine(BankAction.WITHDRAW_20, BankAmounts.shown(BankAction.WITHDRAW_20, m, null, 0.0)))
		assertEquals(20.0, BankAmounts.shown(BankAction.WITHDRAW_20, null, null, 100.0))
		assertEquals(BankAmounts.AmountLine("+5", BankAmounts.GREEN), BankAmounts.amountLine(BankAction.DEPOSIT_ALL, 5.0))
		assertNull(BankAmounts.amountLine(BankAction.DEPOSIT_CUSTOM, null))
	}

	@Test
	fun fullLabelsFitAndAmountLinesDontOverlap() {
		val w = { s: String -> s.length * 6 }
		val cardW = (340 - 12 - 6) / 2 - 12
		for (labels in listOf(listOf("All", "Half", "Custom"), listOf("All", "Half", "20%", "Custom"))) {
			val r = BankLayout.fitPills(0, 22, cardW, labels, w)!!
			labels.indices.forEach { assertTrue(r[it].w >= w(labels[it]) + 4, labels[it]) }
			val amounts = r.map { BankLayout.amountRect(it) }
			for (a in amounts) for (p in r) assertFalse(BankLayout.intersects(a, p))
			for (i in amounts.indices) for (j in amounts.indices) if (i != j) assertFalse(BankLayout.intersects(amounts[i], amounts[j]))
			assertTrue(amounts.all { it.y + it.h <= 58 })
		}
	}

	@Test
	fun mainPagePresetOnlyOpensTheSubPage() {
		val m = BankParser.parse(bank.title, bank.slots)!!

		assertEquals(13, BankChains.press(m, BankAction.WITHDRAW_HALF).single)
		assertEquals(m.button(BankAction.OPEN_DEPOSIT)!!.slot, BankChains.press(m, BankAction.DEPOSIT_CUSTOM).single)
	}

}
