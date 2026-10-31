package dev.ashtonite365.apex.test.features.menus.hunting

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.hunting.AttributeMenuMenu
import dev.ashtonite365.apex.features.menus.hunting.AttributeMenuParser
import dev.ashtonite365.apex.features.menus.hunting.HuntingBoxMenu
import dev.ashtonite365.apex.features.menus.hunting.HuntingBoxParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class HuntingMenusTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun huntingBox() {
		val d = RealDumpFixtures.load("hunting-box")
		val m = HuntingBoxParser.parse(d.title, d.slots)!!
		assertEquals("2", m.total)
		assertEquals(listOf("Chill" to 10, "Hummingbird" to 11), m.shards.map { it.name to it.slot })
		val chill = m.shards[0]
		assertEquals("Skeletal Ruler", chill.attribute!!.name); assertEquals("Combat", chill.attribute!!.category)
		assertEquals(1, chill.owned); assertEquals(1, chill.toNext)
		assertEquals("Bird Family", m.shards[1].family); assertEquals("Foraging", m.shards[1].attribute!!.category)
		assertEquals(setOf("Combat", "Foraging"), m.byCategory.keys)
		assertEquals("ID (Lowest to Highest)", m.sort!!.selected); assertEquals(50, m.sort!!.slot)
		assertEquals(51, m.massSyphon!!.first); assertEquals(4, m.swapSlot)
		assertEquals(listOf("Search" to 46, "Hunting Toolkit" to 52), m.extras)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
	}

	@Test fun huntingBoxSecondCapture() {
		val d = RealDumpFixtures.load("hunting-box-2")
		val m = HuntingBoxParser.parse(d.title, d.slots)!!
		assertEquals("1", m.total); assertEquals(listOf("Hummingbird"), m.shards.map { it.name })
		assertTrue(m.massSyphon!!.second.contains("nothing to syphon"))

		assertNull(HuntingBoxParser.parse(d.title, d.slots.filter { it.slot != 4 }))
	}

	@Test fun attributeMenuEmpty() {
		val d = RealDumpFixtures.load("attribute-menu")
		val m = AttributeMenuParser.parse(d.title, d.slots)!!
		assertTrue(m.attributes.isEmpty())
		assertEquals("0/30", m.unique)
		assertEquals("None", m.filter!!.selected); assertEquals("ID (Lowest to Highest)", m.sort!!.selected)
		assertEquals(4, m.swapSlot)
		assertEquals(listOf("Search" to 46, "Attribute Transfer" to 51, "Advanced Mode" to 52), m.extras)
		assertNull(AttributeMenuParser.parse(d.title, d.slots.filter { it.slot < 4 }))
	}

	@Test fun attributeTileDerived() {
		val d = RealDumpFixtures.load("attribute-menu")
		val chill = RealDumpFixtures.load("hunting-box").slots.first { it.slot == 10 }
		val m = AttributeMenuParser.parse(d.title, d.slots + SlotText(10, "Skeletal Ruler II", chill.lore))!!
		val a = m.attributes.single()
		assertEquals("Skeletal Ruler", a.name); assertEquals(2, a.level); assertEquals("Combat", a.category); assertEquals(1, a.owned)
	}

	@Test fun registryAndToggle() {
		MenuRegistry.register(HuntingBoxMenu); MenuRegistry.register(object : CustomMenu by AttributeMenuMenu { override val enabled = false })
		assertEquals("hunting-box", MenuRegistry.match("Hunting Box")?.id)
		assertNull(MenuRegistry.match("Hunting Recipes"))
		assertTrue(MenuRegistry.resolve("Attribute Menu") { null } is MenuRegistry.Resolution.Vanilla)
	}
}
