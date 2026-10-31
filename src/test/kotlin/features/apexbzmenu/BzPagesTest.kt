package dev.ashtonite365.apex.test.features.apexbzmenu

import dev.ashtonite365.apex.features.apexbzmenu.BzColor
import dev.ashtonite365.apex.features.apexbzmenu.BzButton
import dev.ashtonite365.apex.features.apexbzmenu.BzScroll
import dev.ashtonite365.apex.features.apexbzmenu.BzPrices
import dev.ashtonite365.apex.features.apexbzmenu.BzHeld
import dev.ashtonite365.apex.features.apexbzmenu.BzLook
import dev.ashtonite365.apex.features.apexbzmenu.BzPageView
import dev.ashtonite365.apex.features.apexbzmenu.BzMenu
import dev.ashtonite365.apex.features.apexbzmenu.BzPage
import dev.ashtonite365.apex.features.apexbzmenu.BzPageKind
import dev.ashtonite365.apex.features.apexbzmenu.BzPageLayout
import dev.ashtonite365.apex.features.apexbzmenu.BzPages
import dev.ashtonite365.apex.features.apexbzmenu.BzPlaced
import dev.ashtonite365.apex.features.apexbzmenu.BzRole
import dev.ashtonite365.apex.features.menus.style.StyleChoice
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.test.testutil.RepoFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BzPagesTest {
	private val fixtures = mapOf(
		"bazaar-oddities-1019" to BzPageKind.CATEGORY,
		"bazaar-oddities-cookie-1431" to BzPageKind.CATEGORY,
		"bazaar-oddities-nocookie-1410" to BzPageKind.CATEGORY,
		"bz-instant-buy-acacia-2147" to BzPageKind.INSTANT_BUY,
		"bz-instant-buy-dragon-claw-1428" to BzPageKind.INSTANT_BUY,
		"bz-instant-sell-booster-cookie-1428" to BzPageKind.INSTANT_SELL,
		"bz-confirm-instant-buy-acacia-2147" to BzPageKind.CONFIRM_BUY,
		"bz-buy-order-price-2106" to BzPageKind.BUY_ORDER_PRICE,
		"bz-orders-after-claim-1435" to BzPageKind.ORDERS,
		"bz-orders-claimable-1435" to BzPageKind.ORDERS,
		"bz-orders-filled-sell-1435" to BzPageKind.ORDERS,
		"bz-order-options-1435" to BzPageKind.ORDER_OPTIONS,
	)
	private fun size(slots: List<SlotText>) = (slots.maxOf { it.slot } / 9 + 1) * 9
	private fun page(name: String): Pair<BzPage, List<SlotText>> {
		val d = RealDumpFixtures.load(name)
		return (BzPages.parse(d.title, d.slots, size(d.slots)) ?: error("$name did not parse")) to d.slots
	}
	private val sizes = listOf(176 to 166, 320 to 220, 480 to 300, 640 to 360)

	@Test fun everyFixtureParsesToItsKind() {
		for ((n, k) in fixtures) {
			val d = RealDumpFixtures.load(n)
			assertEquals(k, BzPages.kindOf(d.title), n)
			val (p, slots) = page(n)
			assertEquals(k, p.kind, n)
			assertTrue(p.cards.isNotEmpty() || p.kind == BzPageKind.ORDERS, "$n has cards")

			assertEquals(p.all.size, p.all.map { it.slot }.toSet().size, "$n one slot per button")
			for (b in p.all) assertEquals(b.name, slots.single { it.slot == b.slot }.cleanName, "$n slot ${b.slot}")
		}
	}

	@Test fun categoryRailAndBottomRow() {
		val (p, _) = page("bazaar-oddities-1019")
		assertTrue(p.rail.size >= 2 && p.rail.all { it.slot % 9 == 0 && it.role == BzRole.CATEGORY })
		assertEquals(1, p.rail.count { it.active }, "one category is 'Currently viewing!'")
		assertTrue(p.nav.all { it.slot >= 45 }, "nav comes from Hypixel's bottom row")
		assertTrue(p.cards.all { it.role == BzRole.TILE })
	}

	@Test fun changedOrMissingSlotFallsBack() {
		for (n in fixtures.keys) {
			val d = RealDumpFixtures.load(n)
			val p = BzPages.parse(d.title, d.slots, size(d.slots))!!

			val noCards = d.slots.filter { s -> p.cards.none { it.slot == s.slot } }
			if (p.kind != BzPageKind.ORDERS) assertNull(BzPages.parse(d.title, noCards, size(d.slots)), "$n without cards")
		}
		val d = RealDumpFixtures.load("bz-instant-buy-acacia-2147")
		assertNull(BzPages.parse(d.title, d.slots.filterNot { it.cleanName == "Go Back" }, size(d.slots)))
		assertNull(BzPages.kindOf("Some Other Menu"))
	}

	@Test fun clickReChecksTheItemName() {
		val (p, _) = page("bz-confirm-instant-buy-acacia-2147")
		val b = p.cards.first()
		assertEquals(b.slot, BzPages.clickTarget(b, b.name))
		assertEquals(b.slot, BzPages.clickTarget(b, "§a${b.name}"))
		assertNull(BzPages.clickTarget(b, "Something else"))
		assertNull(BzPages.clickTarget(b, null))
	}

	@Test fun noOverlapAndInsideForBothLooks() {
		for (n in fixtures.keys) for (look in BzLook.entries) for ((w, h) in sizes) {
			val (p, _) = page(n)
			val r = BzPageLayout.place(p, look, 10, 10, w, h)
			val shown = r.shown()
			for ((i, a) in shown.withIndex()) {
				assertTrue(a.inside(10, 10, w, h), "$n $look ${w}x$h ${a.button.name} inside")
				assertTrue(a.w > 0 && a.h > 0)
				for (b in shown.drop(i + 1)) assertFalse(a.overlaps(b), "$n $look ${w}x$h ${a.button.name} / ${b.button.name}")
			}
			assertEquals(r.placed.size, r.placed.map { it.button.slot }.toSet().size, "$n each slot placed once")
		}
	}

	@Test fun everyStyleHasNoCloseAndACentredTextBack() {
		for (look in BzLook.entries) for (n in fixtures.keys) {
			val (p, slots) = page(n)
			val r = BzPageLayout.place(p, look, 0, 0, 480, 300)
			assertTrue(r.placed.none { it.button.role == BzRole.CLOSE }, "$n $look no Close")
			val back = r.placed.filter { it.button.role == BzRole.BACK }
			val goBack = slots.firstOrNull { it.cleanName == "Go Back" }
			if (goBack == null) assertTrue(back.isEmpty(), "$n $look hides Back without a Go Back slot")
			else {
				val b = back.single()
				assertEquals(goBack.slot, b.button.slot, "$n $look Back is the Go Back slot")
				assertFalse(b.icon, "$n $look Back is text only")
				assertEquals(480 - (b.x + b.w), b.x, "$n $look Back centred")
				assertTrue(b.y + b.h > 300 - 30, "$n $look Back at the bottom")
			}
		}
		for (look in BzLook.entries) {
			val d = RealDumpFixtures.load("bz-orders-claimable-1435")
			val p = BzPages.parse(d.title, d.slots.filterNot { it.cleanName == "Go Back" }, 36)
			if (p != null) assertTrue(BzPageLayout.place(p, look, 0, 0, 480, 300).placed.none { it.button.role == BzRole.BACK || it.button.role == BzRole.CLOSE })
		}
	}

	@Test fun skyglassIconsOnEveryOtherButton() {
		for (n in fixtures.keys) {
			val (p, _) = page(n)
			val r = BzPageLayout.place(p, BzLook.SKYGLASS, 0, 0, 480, 300)
			assertTrue(r.placed.filter { it.button.role != BzRole.BACK }.all { it.icon }, "$n every Skyglass button but Back shows its item")
		}
	}

	@Test fun confirmShowsHypixelsRealConfirmSlot() {
		val (p, slots) = page("bz-confirm-instant-buy-acacia-2147")
		val confirm = p.cards.single()
		val real = slots.single { s -> s.cleanLore.any { it == "Click to buy now!" } }
		assertEquals(real.slot, confirm.slot, "the card is Hypixel's own confirm item")
		assertEquals("Click to buy now!", BzPageView.actionLine(confirm))
		val placed = BzPageLayout.place(p, BzLook.SKYGLASS, 0, 0, 480, 300).placed.single { it.button.slot == real.slot }
		assertEquals(real.slot, BzPages.clickTarget(placed.button, real.name), "one click on that slot")
	}

	@Test fun sellValueFromHypixelsLore() {
		val (p, _) = page("bazaar-oddities-1019")
		val inv = p.nav.single { it.name == "Sell Inventory Now" }
		assertEquals("952.2 coins" to BzPages.SellSource.YOU_EARN_LINE, BzPages.sellValue(inv))
		val sacks = p.nav.single { it.name == "Sell Sacks Now" }
		assertEquals(BzPages.SellSource.YOU_EARN_LINE, BzPages.sellValue(sacks)?.second)

		val noTotal = inv.copy(lore = inv.lore.filterNot { it.startsWith("You earn") })
		assertEquals("963 coins" to BzPages.SellSource.SUM_OF_ITEM_LINES, BzPages.sellValue(noTotal))
		assertNull(BzPages.sellValue(inv.copy(lore = listOf("Nothing to sell"))))
		assertNull(BzPages.sellValue(p.nav.first { it.name == "Search" }))

		assertEquals(BzPageLayout.NAV_H_VALUE, BzPageLayout.navH(p))
	}

	@Test fun historyAndSettingsAtTheBottomFullLabels() {
		for (n in fixtures.keys) for (look in BzLook.entries) for ((w, h) in listOf(320 to 220, 480 to 300, 640 to 360)) {
			val (p, slots) = page(n)
			assertTrue(p.nav.none { it.name in BzPages.FOOT_NAMES }, "$n not in the top bar")
			assertEquals(slots.filter { it.cleanName in BzPages.FOOT_NAMES && it.slot < size(slots) }.map { it.slot }.toSet(), p.foot.map { it.slot }.toSet())
			val r = BzPageLayout.place(p, look, 0, 0, w, h)
			val foot = r.placed.filter { it.button.role == BzRole.FOOT }
			val back = r.placed.singleOrNull { it.button.role == BzRole.BACK }
			for (f in foot) {
				assertTrue(BzPageLayout.approx(f.button.name) + (if (f.icon) 18 else 0) <= f.w - 8, "$n $look ${w}: ${f.button.name} full label")
				assertTrue(f.y + f.h > h - 60, "$n $look bottom")
				back?.let { assertFalse(f.overlaps(it), "$n $look ${w}: ${f.button.name} vs Back") }
				assertEquals(look == BzLook.SKYGLASS, f.icon)
			}
		}
	}

	@Test fun cardTextBesideTheIconAndInsideTheTile() {
		for (n in fixtures.keys) for (look in BzLook.entries) {
			val (p, _) = page(n)
			for (pl in BzPageLayout.place(p, look, 0, 0, 480, 300).placed.filter { it.button.role in setOf(BzRole.TILE, BzRole.OPTION, BzRole.ORDER) }) {
				val t = BzPageLayout.cardText(pl, BzPageView.actionLine(pl.button) != null)
				if (pl.icon) assertTrue(t.detailX >= pl.x + 2 + 16 + 2 && t.nameX >= pl.x + 20, "$n text right of the icon")
				assertTrue(t.detailY >= t.nameY + 12, "$n detail a line below the name with a gap")
				assertTrue(t.detailY + 9 <= pl.y + pl.h - 1, "$n first detail line inside ${pl.button.name} (${pl.h} high)")
				t.actionY?.let { assertTrue(it + 9 <= pl.y + pl.h && it >= t.detailY + 9, "$n action line inside, below the details") }
			}
		}
		assertEquals(0xFF102030.toInt(), BzColor.mix(0xFF102030.toInt(), 0xFFFFFFFF.toInt(), 0f))
		assertEquals(0xFFFFFFFF.toInt(), BzColor.mix(0xFF102030.toInt(), 0xFFFFFFFF.toInt(), 1f))
		assertEquals(0xFF7F7F7F.toInt(), BzColor.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
	}

	@Test fun inventoryTabIsApexStateAndRealSlotsOnly() {
		val (p, _) = page("bazaar-oddities-1019")
		for (look in BzLook.entries) {
			val off = BzPageLayout.place(p, look, 0, 0, 480, 300, inv = false)
			val tab = off.placed.single { it.button.role == BzRole.INV_TAB }
			assertEquals(-1, tab.button.slot)
			assertNull(BzPages.clickTarget(tab.button, "Inventory"), "the tab can never become a slot click")
			assertNull(off.list)

			val rail = off.placed.filter { it.button.role == BzRole.CATEGORY || it.button.role == BzRole.INV_TAB }
			assertEquals(BzRole.INV_TAB, rail.minBy { it.y }.button.role)
			assertTrue(rail.all { it.x == tab.x && it.w == tab.w }, "styled/aligned like the other sidebar entries")
			assertEquals(p.rail.count { it.active }, off.placed.count { it.button.role == BzRole.CATEGORY && it.button.active }, "off: Hypixel's category highlighted")
			assertFalse(tab.button.active)
			val on = BzPageLayout.place(p, look, 0, 0, 480, 300, inv = true)
			assertTrue(on.placed.none { it.button.role == BzRole.TILE }, "no product tiles (no clicks) under the list")
			val l = on.list!!; val c = on.content
			assertTrue(l[0] >= c[0] && l[1] >= c[1] && l[0] + l[2] <= c[0] + c[2] && l[1] + l[3] <= c[1] + c[3])
			for (pl in on.placed) assertFalse(pl.overlaps(BzPlaced(pl.button, l[0], l[1], l[2], l[3], false)), "${pl.button.name} vs list")

			for (sc in listOf(0, 7, 50)) {
				val rr = BzHeld.rowRects(l, 40, sc)
				assertTrue(rr.isNotEmpty())

				for ((_, q) in rr) assertTrue(q[1] < l[1] + l[3] && q[1] + q[3] > l[1] + BzHeld.HEADER_H)
				rr.zipWithNext().forEach { (a, b) -> assertTrue(a.second[1] + a.second[3] <= b.second[1]) }
			}
			assertTrue(BzHeld.EACH_W + BzHeld.TOTAL_W + 21 + 60 < l[2], "name column keeps room")
			assertTrue(on.placed.single { it.button.role == BzRole.INV_TAB }.button.active)
			assertTrue(on.placed.none { it.button.role == BzRole.CATEGORY && it.button.active }, "on: only Inventory highlighted")
			for ((i, a) in on.placed.withIndex()) for (b in on.placed.drop(i + 1)) assertFalse(a.overlaps(b))
		}

		val (o, _) = page("bz-instant-buy-acacia-2147")
		assertTrue(BzPageLayout.place(o, BzLook.SKYGLASS, 0, 0, 480, 300).placed.none { it.button.role == BzRole.INV_TAB })

		assertNull(BzHeld.bazaarItem("HYPERION", "Hyperion", 1, false, null, 0))
		val rows = BzHeld.bazaarRows(listOf(
			BzHeld.bazaarItem("COAL", "Coal", 64, true, 2.0, 0), BzHeld.bazaarItem("SWORD", "Sword", 1, false, null, 1),
			BzHeld.bazaarItem("COAL", "Coal", 10, true, 2.0, 2), BzHeld.bazaarItem("ENCHANTED_DIAMOND", "Enchanted Diamond", 3, true, 170.0, 3)))
		assertEquals(listOf("Enchanted Diamond" to 3, "Coal" to 74), rows.map { it.name to it.count })
		assertEquals(510.0 + 148.0, BzHeld.grandTotal(rows))
		assertEquals("x74 (2 stacks)", BzHeld.countText(rows[1]))
		assertEquals("2" to "148", BzHeld.unitText(rows[1]) to BzHeld.totalText(rows[1]))
		assertTrue(rows.none { BzHeld.unitText(it) == "—" || BzHeld.totalText(it) == "—" })
		assertEquals("No Bazaar items in your inventory", BzHeld.EMPTY_TEXT)

		assertTrue(BzPageView.openState(false, 0L, 10_000L))
		assertFalse(BzPageView.openState(false, 10_000L, 10_050L))
		assertTrue(BzPageView.leavesInventory(BzButton(3, "Farming", emptyList(), BzRole.CATEGORY, false)))
		assertFalse(BzPageView.leavesInventory(BzPageLayout.invTab(false)))
		assertEquals("950.5", BzHeld.coins(950.5)); assertEquals("12,400", BzHeld.coins(12400.0)); assertEquals("1.25M", BzHeld.coins(1.25e6))
		val view = RepoFile("src/main/kotlin/features/apexbzmenu/BzMenu.kt").readText()
		val held = view.substringAfter("private fun drawHeld(").substringBefore("private fun drawButton(")

		assertEquals(1, Regex("area\\(").findAll(held).count())
		assertTrue(held.contains("if (row.menuSlot != null) area(rr[0], rr[1], rr[2], rr[3]) { b -> clickHeld(row, b) }"))
		assertFalse(held.contains("clickSlot") || held.contains("inventoryPanel"))
		val ch = view.substringAfter("private fun clickHeld(").substringBefore("\n\t}")
		assertEquals(1, Regex("BzHeld\\.sendRowClick\\(").findAll(ch).count(), "exactly one click sent")
		assertFalse(ch.contains("clickLater") || ch.contains("sendCommand") || ch.contains("Sign"))
		val src = RepoFile("src/main/kotlin/features/apexbzmenu/BzMenu.kt").readText()
		assertTrue(src.contains("inventoryTabOn = true; scroll = 0 }"))
		assertFalse(src.contains("lowestBin"), "no auction prices in the Bazaar list")
	}

	@Test fun scrolledContentCropsAndIndicatorOnlyWhenScrollable() {

		val l = intArrayOf(0, 0, 300, BzHeld.HEADER_H + 50)
		val body = BzHeld.body(l)
		for (sc in 0..30) {
			val rr = BzHeld.rowRects(l, 20, sc)
			assertTrue(rr.all { (_, q) -> q[1] < body[1] + body[3] && q[1] + q[3] > body[1] })
			val covered = rr.sumOf { (_, q) -> minOf(q[1] + q[3], body[1] + body[3]) - maxOf(q[1], body[1]) }
			assertTrue(covered >= body[3] - (BzHeld.listHeight(20) / 20), "scroll $sc: the body stays filled (no popped-out gap)")
			assertTrue(rr.all { (_, q) -> q[0] + q[2] <= l[0] + l[2] - BzScroll.RESERVE }, "rows leave room for the indicator")
		}

		assertNull(BzScroll.bar(intArrayOf(0, 0, 100, 100), 100, 0))
		assertNull(BzScroll.bar(intArrayOf(0, 0, 100, 100), 40, 0))
		val (t0, k0) = BzScroll.bar(intArrayOf(0, 0, 100, 100), 400, 0)!!
		val (_, k1) = BzScroll.bar(intArrayOf(0, 0, 100, 100), 400, 300)!!
		assertEquals(97, t0[0]); assertEquals(0, k0[1]); assertEquals(100, k1[1] + k1[3])
		assertEquals(l[2], BzHeld.rowRects(l, 1, 0).single().second[2], "no reserve when nothing scrolls")

		val (p, _) = page("bazaar-oddities-1019")
		for (sc in listOf(0, 13, 37)) {
			val r = BzPageLayout.place(p, BzLook.SKYGLASS, 0, 0, 480, 160, sc, inv = false)
			val v = r.view!!
			val cards = r.placed.filter { it.scrolled }
			assertTrue(cards.all { it.button.role == BzRole.TILE && it.y < v[1] + v[3] && it.y + it.h > v[1] })
			if (r.contentHeight > v[3]) assertTrue(cards.all { it.x + it.w <= v[0] + v[2] - BzScroll.RESERVE })
			assertTrue(r.placed.filter { !it.scrolled }.none { it.button.role == BzRole.TILE })
		}
		val view = RepoFile("src/main/kotlin/features/apexbzmenu/BzMenu.kt").readText()
		assertTrue(view.contains("beginClip(ctx") && view.contains("endClip(ctx)"), "scrolled content drawn under a scissor")
	}

	@Test fun balancedCentredGrids() {
		assertEquals(listOf(3, 2), BzPageLayout.balancedRows(5, 3))
		assertEquals(listOf(4, 3), BzPageLayout.balancedRows(7, 4))
		assertEquals(listOf(2, 2), BzPageLayout.balancedRows(4, 3))
		assertEquals(listOf(4), BzPageLayout.balancedRows(4, 4))
		assertEquals(listOf(3, 3, 2), BzPageLayout.balancedRows(8, 3))
		assertEquals(emptyList<Int>(), BzPageLayout.balancedRows(0, 3))
		val cells = BzPageLayout.gridCells(listOf(3, 2), 3, 400, 120)
		val gap = BzPageLayout.GAP
		val row0 = cells.filter { it.second == 0 }.map { it.first }; val row1 = cells.filter { it.second == 1 }.map { it.first }
		assertEquals(400 - (row0.last() + 120), row0.first(), "top row centred")
		assertEquals(400 - (row1.last() + 120), row1.first(), "partial row centred, not left-aligned")
		assertEquals(listOf(120 + gap), row1.zipWithNext { a, b -> b - a }, "even spacing")
	}

	@Test fun productCardsShowFourPrices() {
		val lore = listOf("Buy price: 1,234.5 coins", "Sell price: 1,100 coins", "Click to view details!")
		val q = BzPrices.of(lore, 9.0, 8.0)!!
		assertEquals(BzPrices.Source.LORE, q.source)
		assertEquals(listOf(1100.0, 1234.5, 1234.5, 1100.0), q.values())
		val d = BzPrices.of(listOf("Rare commodity", "Click to view details!"), 50.0, 40.0)!!
		assertEquals(BzPrices.Source.BAZAAR_DATA, d.source)
		assertEquals(listOf(40.0, 50.0, 50.0, 40.0), d.values(), "buy order≈instant sell, instant buy=lowest offer, …")
		assertEquals(listOf("Buy Order", "Instant Buy", "Sell Order", "Instant Sell"), BzPrices.lines(d).map { it.first })
		assertNull(BzPrices.of(emptyList(), null, null))
		assertEquals(emptyList<String>(), BzPrices.otherLines(listOf("Rare commodity", "Buy price: 5 coins")))

		val pl = BzPlaced(BzButton(10, "Fine Jade Gemstone", emptyList(), BzRole.TILE), 0, 0, 120, BzPrices.CARD_H, true)
		val t = BzPageLayout.cardText(pl, false)
		assertTrue(t.detailY + 3 * t.step + 9 <= t.detailBottom, "4 price lines fit")
	}

	@Test fun inventoryRowsMapToOneRealSlotEach() {
		val ci = net.minecraft.world.inventory.ContainerInput::class.java
		assertEquals(54 until 90, BzHeld.playerSlots(90))
		assertEquals(18 until 54, BzHeld.playerSlots(54))
		val coal1 = BzHeld.Item("COAL", "Coal", 64, 2.0, BzHeld.Source.BAZAAR, menuSlot = 60)
		val coal2 = BzHeld.Item("COAL", "Coal", 10, 2.0, BzHeld.Source.BAZAAR, menuSlot = 81)
		val helm = BzHeld.Item("ENCHANTED_DIAMOND", "Enchanted Diamond", 1, 170.0, BzHeld.Source.BAZAAR, menuSlot = null)
		val rows = BzHeld.slotRows(listOf(coal2, null, coal1, helm))
		assertEquals(listOf(170.0, 128.0, 20.0), rows.map { it.total }, "one row per slot, by value")
		assertEquals(listOf(null, 60, 81), rows.map { it.menuSlot })

		assertEquals(Triple(60, 0, net.minecraft.world.inventory.ContainerInput.PICKUP), BzHeld.clickFor(coal1, "COAL", 0, false))
		assertEquals(Triple(81, 1, net.minecraft.world.inventory.ContainerInput.PICKUP), BzHeld.clickFor(coal2, "COAL", 1, false))
		assertEquals(Triple(60, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE), BzHeld.clickFor(coal1, "COAL", 0, true))
		assertNull(BzHeld.clickFor(helm, "ENCHANTED_DIAMOND", 0, false), "armour / offhand: view only")
		assertNull(BzHeld.clickFor(coal1, "WHEAT", 0, false), "slot changed: nothing sent")
		assertNull(BzHeld.clickFor(coal1, null, 0, false))
		assertTrue(ci.isEnum)
	}

	@Test fun productPageItemTopActionsMiddleExtrasBottom() {
		val slots = listOf(SlotText(10, "Buy Instantly"), SlotText(11, "Sell Instantly"), SlotText(13, "Lily Pad Minion Upgrade"),
			SlotText(15, "Create Sell Offer"), SlotText(16, "Create Buy Order"), SlotText(30, "Go Back", listOf("To Minion Upgrades")),
			SlotText(31, "Manage Orders"), SlotText(32, "View Graphs"), SlotText(33, "Instasell Ignore"), SlotText(29, "Go Back"))
		val p = BzPages.parse("Minion Upgrades ➜ Lily Pad Mini", slots, 36)!!
		assertEquals(BzPageKind.PRODUCT, p.kind)
		assertEquals(listOf("Lily Pad Minion Upgrade", "Buy Instantly", "Sell Instantly", "Create Buy Order", "Create Sell Offer"), p.cards.map { it.name })
		assertEquals(listOf("Manage Orders", "View Graphs", "Instasell Ignore"), p.foot.map { it.name })
		assertTrue(p.foot.all { it.role == BzRole.FOOT })
		assertTrue(p.back != null && p.cards.none { it.name == "Go Back" }, "text-only Back, no Go Back card")
		assertTrue(BzPageView.isProductItem(p.kind, p.cards[0]))
		assertFalse(BzPageView.isProductItem(p.kind, p.cards[1]))
		for (look in BzLook.entries) for ((w, h) in listOf(480 to 300, 640 to 360)) {
			val r = BzPageLayout.place(p, look, 0, 0, w, h)
			val sh = r.shown()
			val cards = sh.filter { it.button.role == BzRole.OPTION }
			val item = cards.first { it.button.name == "Lily Pad Minion Upgrade" }
			val acts = cards.filter { it.button.name in BzPages.PRODUCT_ACTIONS }
			assertTrue(acts.all { it.y > item.y }, "item on top")
			val vc = r.view!!; val mid = vc[0] + vc[2] / 2
			assertTrue(kotlin.math.abs(item.x + item.w / 2 - mid) <= 2, "$look $w item centred")

			for ((_, row) in acts.groupBy { it.y }) assertTrue(kotlin.math.abs((row.minOf { it.x } + row.maxOf { it.x + it.w }) / 2 - mid) <= 3)
			assertEquals(BzPages.PRODUCT_ORDER, acts.sortedWith(compareBy({ it.y }, { it.x })).map { it.button.name })
			val foot = sh.filter { it.button.role == BzRole.FOOT }
			assertEquals(3, foot.size); assertTrue(foot.all { f -> acts.all { it.y + it.h <= f.y } }, "extras along the bottom")
			for ((i, a) in sh.withIndex()) for (b in sh.drop(i + 1)) assertFalse(a.overlaps(b), "${a.button.name} / ${b.button.name}")
		}
	}

	private fun gemGroup(): List<SlotText> {
		val gems = listOf("Ruby", "Amber", "Topaz", "Sapphire", "Amethyst", "Jasper", "Jade", "Opal", "Aquamarine", "Citrine", "Onyx", "Peridot")
		return gems.mapIndexed { i, g -> SlotText(10 + i + (i / 7) * 2, "§a$g Gemstones", listOf("§7Rough, Flawed, Fine...", "", "§eClick to view products!")) } +
			listOf(SlotText(48, "Go Back", listOf("To Bazaar")), SlotText(49, "Close"), SlotText(50, "Sell Inventory Now", listOf("You earn: 1,200 coins")),
				SlotText(51, "Bazaar History"), SlotText(52, "Bazaar Settings"))
	}
	private fun product(): List<SlotText> = listOf(
		SlotText(10, "Buy Instantly", listOf("Price per unit: 1,234 coins")), SlotText(11, "Sell Instantly", listOf("Price per unit: 1,100 coins")),
		SlotText(13, "Fine Ruby Gemstone"), SlotText(15, "Create Buy Order"), SlotText(16, "Create Sell Offer"), SlotText(31, "Go Back", listOf("To Ruby Gemstones")))

	@Test fun subcategoryAndProductPagesGetTheCardLayout() {
		val g = BzPages.parse("Mining ➜ Gemstones", gemGroup(), 54)!!
		assertEquals(BzPageKind.PRODUCT_GROUP, g.kind)
		assertEquals(12, g.cards.size); assertTrue(g.cards.all { it.role == BzRole.TILE })
		assertEquals(setOf("Bazaar History", "Bazaar Settings"), g.foot.map { it.name }.toSet())
		assertEquals(listOf("Sell Inventory Now"), g.nav.map { it.name })
		assertEquals(48, g.back?.slot)

		val small = gemGroup().map { if (it.slot >= 45) it.copy(slot = it.slot - 18) else it }.filter { it.slot < 36 }
		assertEquals(BzPageKind.PRODUCT_GROUP, BzPages.parse("Mining ➜ Gemstones", small, 36)?.kind)
		val pr = BzPages.parse("Ruby Gemstones ➜ Fine Ruby Gemstone", product(), 36)!!
		assertEquals(BzPageKind.PRODUCT, pr.kind)
		assertTrue(pr.cards.map { it.name }.containsAll(BzPages.PRODUCT_ACTIONS))
		for (p in listOf(g, pr)) for (look in BzLook.entries) for ((w, h) in listOf(320 to 220, 480 to 300, 640 to 360)) {
			val r = BzPageLayout.place(p, look, 0, 0, w, h)
			assertTrue(r.placed.any { it.button.role == BzRole.TILE || it.button.role == BzRole.OPTION }, "${p.kind} cards drawn")
			val shown = r.shown()
			for ((i, a) in shown.withIndex()) {
				assertTrue(a.x >= 0 && a.y >= 0 && a.x + a.w <= w && a.y + a.h <= h)
				for (b in shown.drop(i + 1)) assertFalse(a.overlaps(b), "${p.kind} $look ${w}: ${a.button.name} / ${b.button.name}")
			}
		}

		val pred = BzMenu.BazaarMenu.slotPredicate!!
		assertTrue(pred("Mining ➜ Gemstones", gemGroup())); assertTrue(pred("Ruby Gemstones ➜ Fine Ruby Gemstone", product()))
		assertFalse(pred("SkyBlock Menu ➜ Skills", listOf(SlotText(10, "Mining Skill"), SlotText(49, "Close"))))
		assertNull(BzPages.parse("SkyBlock Menu ➜ Skills", listOf(SlotText(10, "Mining Skill"), SlotText(48, "Go Back")), 54))
		assertFalse(pred("Bazaar ➜ Oddities", page("bazaar-oddities-1019").second), "title-matched pages are not slot-matched twice")

		for ((n, k) in fixtures) assertEquals(k, BzPages.kindOf(RealDumpFixtures.load(n).title, page(n).second))
	}

	@Test fun noPlayerInventoryOnAnyBazaarPage() {

		val view = RepoFile("src/main/kotlin/features/apexbzmenu/BzMenu.kt").readText()
		assertFalse(view.contains("inventoryPanel("))

		assertTrue(dev.ashtonite365.apex.features.menus.style.StyledChestLayout.usesInventory("Mining ➜ Gemstones"))
		assertTrue(BzPages.isBazaarChest("Mining ➜ Gemstones", gemGroup()))
		assertTrue(BzPages.isBazaarChest("Bazaar ➜ Mining", emptyList()), "a category page even before its items arrive")
		for (n in fixtures.keys) assertTrue(BzPages.isBazaarChest(RealDumpFixtures.load(n).title, page(n).second), n)
		assertFalse(BzPages.isBazaarChest("Large Chest", listOf(SlotText(0, "Dirt"))))
		val gen = RepoFile("src/main/kotlin/features/menus/generic/GenericStyledView.kt").readText()
		assertTrue(gen.contains("usesInventory(screen.title.string) && !bazaarNow()"))
		assertTrue(gen.contains("if (withInventory) layout.inventory?.let"))
	}

	@Test fun fitKeepsTextInItsBox() {
		val m: (String) -> Int = { it.length * 6 }
		assertEquals("Short", BzPageLayout.fit("Short", 60, m))
		for (w in listOf(0, 6, 13, 40, 100)) assertTrue(m(BzPageLayout.fit("Enchanted Acacia Log Instant Buy", w, m)) <= w, "w=$w")
	}

	@Test fun noCommandsSignsOrExtraClicks() {
		for (f in listOf("BzMenu.kt", "BzPages.kt")) {
			val t = RepoFile("src/main/kotlin/features/apexbzmenu/$f").readText()
			for (bad in listOf("sendCommand", "runCommand", "SignText", "setMessage(", "ServerboundSignUpdate", "MenuChain", "ScreenEvents"))
				assertFalse(t.contains(bad), "$f contains $bad")
		}
		val view = RepoFile("src/main/kotlin/features/apexbzmenu/BzMenu.kt").readText()
		assertEquals(1, Regex("""clickSlot\(""").findAll(view).count(), "one click call, inside click()")
		assertTrue(view.substringAfter("private fun click(").contains("BzPages.clickTarget("))
	}

	@Test fun vanillaJustShowsHypixelsChest() {

		assertFalse(BzMenu.BazaarMenu.selfHosted)
		val s = StyleChoice.apply(StyleState(), StyleChoice.VANILLA)
		assertFalse(s.customMenus)
		assertEquals(StyleChoice.VANILLA, StyleChoice.of(s))
		val reg = RepoFile("src/main/kotlin/features/menus/MenuRegistry.kt").readText()
		assertTrue(reg.contains("if (!dev.ashtonite365.apex.features.menus.style.MenuStyle.state.customMenus) return@AfterInit"))
		for (t in fixtures.keys.map { RealDumpFixtures.load(it).title }) assertTrue(BzMenu.BazaarMenu.title.containsMatchIn(t.replace(Regex("§."), "")), t)
	}

	@Test fun retiredKeysDropped() {
		val saved = JsonObject(mapOf("overlay" to JsonPrimitive(true), "show-on-open" to JsonPrimitive(true), "toggle-key" to JsonPrimitive(5)))
		assertEquals(setOf("overlay"), BzMenu.dropRetired(saved).keys)
		val clean = JsonObject(mapOf("overlay" to JsonPrimitive(false)))
		assertTrue(BzMenu.dropRetired(clean) === clean)
	}
}
