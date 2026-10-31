package dev.ashtonite365.apex.test.features.menus.enchant

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.enchant.EnchantLevel
import dev.ashtonite365.apex.features.menus.enchant.EnchantLevelMenu
import dev.ashtonite365.apex.features.menus.enchant.EnchantLevelParser
import dev.ashtonite365.apex.features.menus.enchant.EnchantLevelView
import dev.ashtonite365.apex.features.menus.enchant.EnchantMenu
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class EnchantLevelMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun parse(name: String) = RealDumpFixtures.load(name).let { EnchantLevelParser.parse(it.title, it.slots)!! }

	@Test fun titles() {
		for (t in listOf("Enchant Item ➜ Sharpness", "Enchant Item ➜ Smite", "Enchant Item ➜ Bane of Arthropo", "Enchant Item ➜ Fire Aspect"))
			assertTrue(EnchantLevelParser.TITLE.matches(t), t)
		assertFalse(EnchantLevelParser.TITLE.containsMatchIn("Enchant Item"))
		assertFalse(EnchantLevelParser.TITLE.containsMatchIn("Enchantment Guide"))
		assertFalse(EnchantMenu.title.containsMatchIn("Enchant Item ➜ Sharpness"))
		MenuRegistry.register(EnchantMenu); MenuRegistry.register(EnchantLevelMenu)
		assertSame(EnchantLevelMenu, MenuRegistry.match("Enchant Item ➜ Sharpness"))
		assertSame(EnchantLevelMenu, MenuRegistry.match("Enchant Item ➜ Bane of Arthropo"))
		assertSame(EnchantMenu, MenuRegistry.match("Enchant Item"))
		assertFalse(MenuRegistry.denied("Enchant Item ➜ Sharpness"))
	}

	@Test fun pickerAndOtherPagesAreNotLevelPages() {
		for (f in listOf("enchant-1447-picker-48677", "enchant-item", "enchant-empty")) RealDumpFixtures.load(f).let {
			assertNull(EnchantLevelParser.parse(it.title, it.slots), f) }

		val picker = RealDumpFixtures.load("enchant-1447-picker-48677")
		assertNull(EnchantLevelParser.parse("Enchant Item ➜ Sharpness", picker.slots))

		val sharp = RealDumpFixtures.load("enchant-item-sharpness-1447")
		assertNull(EnchantLevelParser.parse("Enchant Item ➜ Smite", sharp.slots))
	}

	@Test fun sharpnessNotOnItem() {
		val m = parse("enchant-item-sharpness-1447")
		assertEquals("Sharpness", m.enchant)
		assertEquals(listOf(22, 23, 24, 31, 32), m.levels.map { it.slot })
		assertEquals((1..5).toList(), m.levels.map { it.level })
		assertEquals(listOf(10 to 8, 15 to 12, 20 to 15, 25 to 19, 30 to 23), m.levels.map { it.fullCost to it.cost })
		assertEquals("Increases melee damage dealt by 30%.", m.levels.last().description)
		assertEquals(listOf("Click to enchant!"), m.levels.last().notes)
		assertNull(m.current); assertFalse(m.complete)
		assertTrue(m.levels.all { it.state == EnchantLevel.State.AVAILABLE && it.affordable == true })
		assertEquals(19, m.itemSlot); assertEquals("Fabled Hyperion ✪✪✪✪✪", m.itemName)
	}

	@Test fun buttonsByName() {
		val m = parse("enchant-level-1447-smite-48736")
		assertEquals(50, m.guideSlot); assertEquals(45, m.backSlot); assertEquals("To Enchant Item", m.backLore)
		assertEquals(5, m.maxLevel)

		val d = m.levels[1].description
		assertTrue(d.startsWith("Increases damage dealt to") && d.contains("Wither,") && d.endsWith("Skeletal mobs by 10%."), d)
	}

	@Test fun twoAndThreeLevelEnchants() {
		assertEquals(listOf(22, 23), parse("enchant-level-1447-knockback-48910").levels.map { it.slot })
		val fa = parse("enchant-level-1447-fireaspect-49027")
		assertEquals(listOf("Fire Aspect I", "Fire Aspect II"), fa.levels.map { it.name })
		assertEquals("Ignites your enemies for 4s dealing 6% of your damage per second.", fa.levels[1].description)
		val xp = parse("enchant-level-1447-experience-49202")
		assertEquals(listOf(21, 22, 24), xp.levels.map { it.slot })
		assertEquals(listOf(true, false, false), xp.levels.map { it.affordable })
		assertEquals(listOf("You don't have enough Experience Levels!"), xp.levels[2].notes)
		assertEquals(listOf(8, 15, 23), parse("enchant-level-1447-scavenger-50216").levels.map { it.cost })
	}

	@Test fun cutTitleBane() {
		val m = parse("enchant-item-bane-1447")
		assertEquals("Bane of Arthropods", m.enchant)
		assertEquals(5, m.levels.size)
		assertEquals(listOf(true, true, true, false, false), m.levels.map { it.affordable })
	}

	@Test fun lootingBeforeAndAtMax() {
		val before = parse("enchant-item-looting-1447")
		assertNull(before.current); assertFalse(before.complete)
		val max = parse("enchant-level-1447-looting-max-50392")
		assertEquals(3, max.current); assertTrue(max.complete)
		assertEquals(listOf(EnchantLevel.State.LOWER, EnchantLevel.State.LOWER, EnchantLevel.State.CURRENT), max.levels.map { it.state })
		assertEquals(listOf("This enchantment is already present and can be removed.", "Click to remove!"), max.levels[2].notes)
		assertEquals("§a✔ Current · complete", EnchantLevelView.tag(max, max.levels[2]))
		assertEquals("§8lower level", EnchantLevelView.tag(max, max.levels[0]))
		assertNull(EnchantLevelView.tag(before, before.levels[0]))
	}

	@Test fun currentFromItemLoreWhenNoBookSaysSo() {

		val d = RealDumpFixtures.load("enchant-item-sharpness-1447")
		val slots = d.slots.map { if (it.slot == 19) it.copy(lore = listOf("Gemstones: [] [⚔]", "", "Sharpness III", "Smite V", "")) else it }
		val m = EnchantLevelParser.parse(d.title, slots)!!
		assertEquals(3, m.current); assertFalse(m.complete)
		assertEquals(listOf(EnchantLevel.State.LOWER, EnchantLevel.State.LOWER, EnchantLevel.State.CURRENT, EnchantLevel.State.AVAILABLE, EnchantLevel.State.AVAILABLE),
			m.levels.map { it.state })
		assertEquals("§a● Current", EnchantLevelView.tag(m, m.levels[2]))
		assertEquals(5, EnchantLevelParser.itemLevel("Sharpness", listOf("Bane of Arthropods V, Sharpness V")))
		assertNull(EnchantLevelParser.itemLevel("Smite", listOf("Sharpness V")))
	}

	@Test fun costText() {
		val l = parse("enchant-level-1447-experience-49202").levels
		assertEquals("§a§m15§r§a 12 Exp Levels ✔", EnchantLevelView.costText(l[0]))
		assertEquals("§c§m45§r§c 34 Exp Levels ✖", EnchantLevelView.costText(l[2]))
	}
}
