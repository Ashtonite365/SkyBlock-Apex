package dev.ashtonite365.apex.test.features.menus.essence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.essence.EssenceLayout
import dev.ashtonite365.apex.features.menus.essence.EssenceMenu
import dev.ashtonite365.apex.features.menus.essence.EssenceNav
import dev.ashtonite365.apex.features.menus.essence.EssenceParser
import dev.ashtonite365.apex.features.menus.guides.DragonGuideMenu
import dev.ashtonite365.apex.features.menus.guides.ReforgeGuideMenu
import dev.ashtonite365.apex.features.menus.support.ApexFallback
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class EssenceMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val dump = RealDumpFixtures.load("your-essence-2146-14")

	private val measure: (String) -> Int = { s -> MenuText.clean(s).sumOf { c -> when (c) { 'i', 'l', '!', '.', ',', '\'', ':', '|' -> 2; 't', 'I', ' ' -> 4; else -> 6 } } }

	@Test fun titleMatchesExactlyAsDumped() {
		MenuRegistry.register(DragonGuideMenu); MenuRegistry.register(ReforgeGuideMenu); MenuRegistry.register(EssenceMenu)
		assertEquals("Your Essence", dump.title)
		assertSame(EssenceMenu, MenuRegistry.match(dump.title))
		assertSame(EssenceMenu, MenuRegistry.match("§8Your Essence"))
		for (t in listOf("Essence Shop", "Your Essence Shop", "Essence Shop ➜ Dragon Essence S", "Dungeoneering", "Dragon Guide")) assertFalse(MenuRegistry.match(t) === EssenceMenu, t)
		assertFalse(MenuRegistry.denied(dump.title))
		assertNull(EssenceParser.parse("Dungeoneering", dump.slots))
	}

	@Test fun parsesEveryEssenceFromTheRealDump() {
		val m = EssenceParser.parse(dump.title, dump.slots)!!
		assertEquals("Your Essence", m.heading)
		assertEquals(listOf("Wither", "Spider", "Undead", "Dragon", "Gold", "Diamond", "Ice", "Crimson", "Forest", "Fossil", "Safari"), m.entries.map { it.type })
		assertEquals(m.entries.map { it.type + " Essence" }, m.entries.map { it.name })
		assertEquals(listOf(19, 20, 21, 22, 23, 24, 25, 29, 30, 32, 33), m.entries.map { it.slot })
		assertEquals(listOf(119L, 38L, 1722L, 177L, 1975L, 1917L, 103L, 10L, 42L, 0L, 0L), m.entries.map { it.amount })
		assertEquals(listOf("119", "38", "1,722", "177", "1,975", "1,917", "103", "10", "42", "0", "0"), m.entries.map { it.amountText })

		assertEquals("Why are we still here? Just to suffer...", m.entries[0].info)

		assertTrue(m.entries.first { it.type == "Ice" }.info.endsWith("warm to the touch."))
		assertFalse(m.entries.any { it.info.contains("You currently own") })
		assertEquals(dump.slots.first { it.slot == 21 }.lore, m.entries[2].tooltip)

		assertFalse(m.entries.any { it.slot == 48 || it.slot == 49 || it.name == "Your Essence" })
	}

	@Test fun amountsAreReadLiveFromLore() {
		assertEquals("1,722" to 1722L, EssenceParser.amount(listOf("You currently own 1,722 Undead", "Essence.", "", "Is this thing rotten?")))
		assertEquals("103" to 103L, EssenceParser.amount(listOf("You currently own 103 Ice Essence.")))
		assertNull(EssenceParser.amount(listOf("Why are we still here? Just to", "suffer...")))

		val changed = dump.slots.map { if (it.slot == 19) it.copy(lore = it.lore.map { l -> l.replace("119", "12,345") }) else it }
		assertEquals(12345L, EssenceParser.parse(dump.title, changed)!!.entries.first { it.type == "Wither" }.amount)

		val extra = dump.slots.map { if (it.slot == 31) it.copy(name = "Rift Essence", lore = listOf("You currently own 7 Rift Essence.")) else it }
		assertEquals(7L, EssenceParser.parse(dump.title, extra)!!.entries.first { it.type == "Rift" }.amount)
	}

	@Test fun goBackAndCloseMirrorTheirRealSlots() {
		val m = EssenceParser.parse(dump.title, dump.slots)!!
		assertEquals(listOf(EssenceNav("Back (Dungeoneering)", 48), EssenceNav("Close", 49)), m.nav)
		assertEquals(48, m.back!!.slot); assertEquals(49, m.close!!.slot)
		assertEquals("Go Back", dump.slots.first { it.slot == m.back!!.slot }.name)
		assertEquals("Close", dump.slots.first { it.slot == m.close!!.slot }.name)
		val b = MenuBounds.centred(640, 360, EssenceMenu.defaultSize.first, EssenceMenu.defaultSize.second)
		val nav = EssenceLayout.nav(b.x, b.y, b.h, m.nav, measure)
		assertEquals(listOf(48, 49), nav.map { it.nav.slot })
		for (n in nav) {

			assertEquals(EssenceLayout.CELL, n.cell.w); assertEquals(EssenceLayout.CELL, n.cell.h)
			assertTrue(n.cell.x + n.cell.w <= n.label.x)
			assertTrue(n.frame.x == n.cell.x && n.frame.x + n.frame.w == n.label.x + n.label.w)
			assertTrue(n.frame.y + n.frame.h <= b.y + b.h && n.frame.x + n.frame.w <= b.x + b.w)
			assertEquals(measure(n.nav.label) + 10, n.label.w)
		}
		assertTrue(nav[0].frame.x + nav[0].frame.w < nav[1].frame.x)

		assertTrue(EssenceLayout.floor(b.y, b.h) < nav[0].frame.y)
	}

	@Test fun unreadablePageFallsBackToVanillaWithoutNavigating() {
		val blank = dump.slots.map { if (it.name.endsWith(" Essence")) it.copy(name = "", lore = emptyList()) else it }
		assertNull(EssenceParser.parse(dump.title, blank))
		assertNull(EssenceParser.parse(dump.title, emptyList()))
		MenuRegistry.register(EssenceMenu)

		val r = MenuRegistry.resolve(dump.title) { null }
		assertInstanceOf(MenuRegistry.Resolution.Fallback::class.java, r)
		assertSame(EssenceMenu, (r as MenuRegistry.Resolution.Fallback).menu)
		assertEquals("essence (fallback)", MenuRegistry.describe(r))

		assertFalse(ApexViewBase.shouldFallBack(true, 1000L, 0L, false))
		assertTrue(ApexViewBase.shouldFallBack(true, ContainerGate.GRACE_MS + 1, 0L, false))
		assertFalse(ApexFallback.navigates(EssenceMenu.id))
	}

	@Test fun centredGridWrapsAndFits() {
		val m = EssenceParser.parse(dump.title, dump.slots)!!
		for ((sw, sh) in listOf(640 to 360, 320 to 240)) {
			val b = MenuBounds.centred(sw, sh, EssenceMenu.defaultSize.first, EssenceMenu.defaultSize.second)
			val vw = measure("Vanilla") + 10
			val heading = BzMenuLayout.wrapText("§6§l" + m.heading, b.w - 2 * EssenceLayout.PAD - vw - 8, measure)
			val top = EssenceLayout.gridTop(b.y, heading.size); val floor = EssenceLayout.floor(b.y, b.h)
			val heights = m.entries.map { EssenceLayout.tileHeight(it, measure) }
			val g = EssenceLayout.grid(b.x, b.w, top, floor, heights)
			assertEquals(m.entries.size, g.tiles.size)
			val cols = EssenceLayout.cols(b.w, m.entries.size)
			g.tiles.chunked(cols).forEach { row ->
				val left = row.first().x - b.x; val right = b.x + b.w - (row.last().x + row.last().w)
				assertTrue(kotlin.math.abs(left - right) <= 1, "row centred: $left vs $right")
			}
			for (i in g.tiles.indices) for (j in g.tiles.indices) if (i < j) {
				val a = g.tiles[i]; val c = g.tiles[j]
				assertTrue(a.x + a.w <= c.x || c.x + c.w <= a.x || a.y + a.h <= c.y || c.y + c.h <= a.y, "tiles $i/$j overlap")
			}
			for ((i, e) in m.entries.withIndex()) {
				val (n, info) = EssenceLayout.tileText(e, measure)
				assertTrue((n + info).none { it.contains("…") })
				assertTrue((n + info).all { measure(it) <= EssenceLayout.TEXT_W })
				assertEquals(e.name, MenuText.clean(n.joinToString(" ")))
				assertEquals("Owned: ${e.amountText}", MenuText.clean(info.joinToString(" ")))
				assertTrue(g.tiles[i].h >= heights[i])
			}
			if (sw == 640) {
				assertEquals(4, cols)
				assertTrue(g.contentHeight <= floor - top)
				val nav = EssenceLayout.nav(b.x, b.y, b.h, m.nav, measure)

				println("PREVIEW screen=$sw,$sh menu=${b.x},${b.y},${b.w},${b.h} top=$top floor=$floor heading=${heading.joinToString("|") { MenuText.clean(it) }} " +
					"tiles=" + m.entries.indices.joinToString(";") { i -> val t = g.tiles[i]; val (n, info) = EssenceLayout.tileText(m.entries[i], measure)
						"${t.x},${t.y},${t.w},${t.h}~" + (n.map { MenuText.clean(it) }.joinToString("|")) + "~" + info.map { MenuText.clean(it) }.joinToString("|") } +
					" nav=" + nav.joinToString(";") { "${it.frame.x},${it.frame.y},${it.frame.w},${it.frame.h}~${it.nav.label}" })
			}
		}
	}
}
