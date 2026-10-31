package dev.ashtonite365.apex.test.features.menus.levelling

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.levelling.StageState
import dev.ashtonite365.apex.features.menus.levelling.XpGuideMenu
import dev.ashtonite365.apex.features.menus.levelling.XpGuideParser
import dev.ashtonite365.apex.features.menus.skills.DungeoneeringParser
import dev.ashtonite365.apex.features.menus.skills.SkillsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class XpGuideTasksTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun d(n: String) = RealDumpFixtures.load(n)
	private fun p(n: String) = d(n).let { XpGuideParser.parse(it.title, it.slots) }

	private fun taskSlots(n: String) = d(n).slots.filter { s -> s.slot >= 9 && XpGuideParser.task(s) != null }.map { it.slot }

	@Test fun stagePages() {
		val m = p("xpguide-starter-1046")!!
		assertEquals("Guide", m.parent); assertEquals("Starter", m.leaf); assertNull(m.page)
		assertEquals(7, m.stages.size)
		assertEquals(StageState.SELECTED, m.stages.first { it.name == "Starter" }.state)
		assertEquals("Reach 50% completion of Intermediate to unlock this stage!", m.stages.first { it.name == "Skilled" }.unlockHint)
		assertEquals(taskSlots("xpguide-starter-1046"), m.tasks.map { it.slot })
		val inter = p("xpguide-intermediate-p1-1046")!!
		assertEquals(1 to 3, inter.page); assertEquals("Intermediate", inter.leaf); assertNotNull(inter.nav["Next Page"])
		assertEquals(StageState.AVAILABLE, inter.stages.first { it.name == "Starter" }.state)
		assertNotNull(p("xpguide-amateur-1046"))
	}

	@Test fun categoryPages() {
		val acc = p("xpguide-starter-accessories-1046")!!
		assertEquals("Accessories", acc.header!!.name); assertEquals(14, acc.header!!.count)
		assertEquals(taskSlots("xpguide-starter-accessories-1046"), acc.tasks.map { it.slot })
		assertEquals(acc.tasks.count { it.done }, d("xpguide-starter-accessories-1046").slots.count { it.cleanName.startsWith("✔") && it.slot >= 9 })
		assertEquals("Default", acc.sort!!.selected)
		assertEquals(1 to 3, p("xpguide-starter-museum-p1-1046")!!.page)
		assertEquals(20, p("xpguide-starter-skills-1046")!!.tasks.first().worthXp)
	}

	@Test fun waysToLevelUpAndCategoryHubs() {
		val w = p("ways-to-level-up-1049")!!
		val cats = d("ways-to-level-up-1049").slots.filter { s -> s.cleanLore.firstOrNull()?.matches(Regex("\\d+ Tasks")) == true }
		assertEquals(cats.map { it.cleanName }, w.tasks.map { it.name })
		assertTrue(w.tasks.first { it.name == "Dungeon Tasks" }.subtasks.map { it.first }.contains("Complete Dungeons"))
		assertFalse(w.extras.any { it.first.startsWith("Recently Viewed #") })
		val skill = p("tasks-skill-related-1055")!!
		assertEquals(d("tasks-skill-related-1055").slots.count { it.cleanName.endsWith(" Tasks") }, skill.tasks.size)
	}

	@Test fun taskPagesAnyDepth() {
		for (f in listOf("tasks-core-1049", "tasks-core-bank-1049", "tasks-core-fast-travel-1049", "tasks-dungeon-1050", "tasks-complete-dungeons-1050",
				"tasks-complete-th-1050", "tasks-complete-ma-1050", "tasks-essence-1050", "tasks-slaying-1052", "tasks-slay-dragons-1052",
				"tasks-defeat-slayers-1052", "tasks-kuudra-1052", "tasks-arachne-1052", "tasks-mining-1055", "tasks-rock-milestones-1055", "tasks-farming-1055", "tasks-fishing-1055",
				"tasks-fishing-trophy-fish-1055", "tasks-fishing-dolphin-milestones-1055", "tasks-fishing-ship-parts-1055", "tasks-fishing-ship-crew-1055",
				"tasks-foraging-1055", "tasks-safari-milestones-1055", "tasks-safari-cavern-1055", "tasks-safari-haunted-1055", "tasks-safari-icy-1055")) {
			val m = p(f); assertNotNull(m, f); m!!
			assertEquals(taskSlots(f), m.tasks.map { it.slot }, f)
		}
		val bank = p("tasks-core-bank-1049")!!
		assertEquals("Bank Upgrades", bank.header!!.name)
		assertEquals(3, bank.done.size); assertEquals(listOf(20, 25, 30, 35, 40, 50), bank.tasks.map { it.worthXp })
	}

	@Test fun truncatedTitlesNeverGuessed() {

		assertEquals("Complete Th", p("tasks-complete-th-1050")!!.leaf)
		assertEquals("Complete Ma", p("tasks-complete-ma-1050")!!.leaf)

		val essence = listOf("dragon", "ice", "spider", "undead", "wither", "crimson", "gold", "diamond", "forest", "fossil", "safari")
		for (n in essence) {
			val x = d("tasks-essence-$n-1050"); val m = XpGuideParser.parse(x.title, x.slots); assertNotNull(m, n); m!!
			assertEquals(x.slots.first { it.slot == 4 }.cleanName, m.leaf, n)
			assertTrue(m.tasks.isNotEmpty(), n)
		}
		for (n in listOf("revenan", "tarantu", "sven_pa", "voidglo", "riftsta", "inferno")) assertNotNull(p("tasks-slayer-$n-1052"), n)
		assertTrue(p("tasks-slayer-inferno-1052")!!.locked.size == 4)
	}

	@Test fun heartAndCoreOfTheMountainBothPresent() {
		val m = p("tasks-mining-1055")!!
		val names = m.tasks.map { it.slot to it.name }
		assertTrue(19 to "Heart of the Mountain" in names); assertTrue(22 to "Core of the Mountain" in names)
	}

	@Test fun heartTilesLinkToH5Menus() {
		val mining = p("tasks-mining-1055")!!; val foraging = p("tasks-foraging-1055")!!
		assertTrue(mining.tasks.first { it.name == "Heart of the Mountain" }.clickable)
		assertTrue(mining.tasks.first { it.name == "Core of the Mountain" }.clickable)
		assertTrue(foraging.tasks.first { it.name == "Heart of the Forest" }.clickable)
		val h = d("hotm-1107-44")
		assertNull(XpGuideParser.parse(h.title, h.slots))
		for (t in listOf("Heart of the Mountain", "Heart of the Forest", "Core of the Mountain")) assertFalse(XpGuideParser.TITLE.matches(t), t)
	}

	@Test fun milestonePages() {
		val c = p("tasks-commission-milestones-1055")!!
		assertTrue(c.done.isNotEmpty()); assertTrue(c.open.isNotEmpty())
		assertEquals(325.0, c.open.first().progress!!.current)
		assertNotNull(p("tasks-frozen-corpse-1055"))

		val mu = p("tasks-mutation-analysis-1055")!!
		assertEquals("Mutation Analysis Rewards", mu.leaf)
		assertEquals(d("tasks-mutation-analysis-1055").slots.count { it.cleanName.startsWith("DNA Analysis Milestone") }, mu.tasks.size)
		val fig = p("tasks-fig-tree-milestones-1055")!!
		assertEquals(4.0, fig.tasks.first().progress!!.current)

		assertNull(p("tasks-swoop-1055")); assertNull(p("tasks-tree-gifts-1055"))
	}

	@Test fun infoOnlyTiles() {
		for (f in listOf("tasks-farming-1055", "tasks-foraging-1055", "tasks-mining-1055", "tasks-core-1049", "tasks-slaying-1052")) {
			val m = p(f)!!
			val expected = d(f).slots.filter { s -> s.slot >= 9 && XpGuideParser.task(s) != null }.associate { s -> s.slot to s.cleanLore.any { it.startsWith("Click", true) } }
			assertEquals(expected, m.tasks.associate { it.slot to it.clickable }, f)
		}
		val farming = p("tasks-farming-1055")!!
		assertFalse(farming.tasks.first { it.name == "Garden Level" }.clickable)
		assertTrue(farming.tasks.first { it.name == "DNA Analysis Milestones" }.clickable)
		assertTrue(farming.tasks.any { !it.clickable } && farming.tasks.any { it.clickable })
		assertTrue(p("tasks-foraging-1055")!!.tasks.all { it.clickable })

		assertFalse(p("tasks-mining-1055")!!.tasks.first { it.name == "Fossil Research" }.clickable)
	}

	@Test fun trophyFrogPages() {
		val dir = java.io.File(javaClass.getResource("/apex-fixtures/xpguide-starter-1046.txt")!!.toURI()).parentFile
		val pages = dir.listFiles()!!.filter { it.name.startsWith("tasks-frog-trophy-frogs-to-") }.map { it.nameWithoutExtension }
		assertEquals(12, pages.size)
		for (f in pages) { val m = p(f); assertNotNull(m, f); assertEquals("Trophy Frogs", m!!.parent, f); assertEquals(taskSlots(f), m.tasks.map { it.slot }, f) }
		val hub = p("tasks-frog-fishing-to-trophy-frogs-1058")!!
		assertEquals("Trophy Frogs", hub.leaf); assertEquals(taskSlots("tasks-frog-fishing-to-trophy-frogs-1058"), hub.tasks.map { it.slot })
	}

	@Test fun trophyFishPages() {
		val dir = java.io.File(javaClass.getResource("/apex-fixtures/xpguide-starter-1046.txt")!!.toURI()).parentFile
		val pages = dir.listFiles()!!.filter { it.name.startsWith("tasks-trophy-") }.map { it.nameWithoutExtension }
		assertEquals(18, pages.size)
		for (f in pages) {
			val m = p(f); assertNotNull(m, f); m!!
			assertEquals("Trophy Fish", m.parent, f)
			assertEquals(taskSlots(f), m.tasks.map { it.slot }, f)
			assertTrue(m.tasks.isNotEmpty(), f)
		}
	}

	@Test fun noStealing() {
		for (f in listOf("tasks-your-skills-1049", "tasks-pets-1049", "tasks-collections-1049", "tasks-dungeoneering-1050"))
			assertNull(p(f), f)

		MenuRegistry.register(dev.ashtonite365.apex.features.menus.museum.MilestonesMenu); MenuRegistry.register(XpGuideMenu)
		assertEquals(dev.ashtonite365.apex.features.menus.museum.MilestonesMenu.id, d("tasks-museum-milestones-1049").let { MenuRegistry.match(it.title, it.slots)?.id })
		MenuRegistry.clearForTests()
		assertNotNull(d("tasks-your-skills-1049").let { SkillsParser.parse(it.title, it.slots) })
		assertNotNull(d("tasks-dungeoneering-1050").let { DungeoneeringParser.parse(it.title, it.slots) })
		val dir = java.io.File(javaClass.getResource("/apex-fixtures/xpguide-starter-1046.txt")!!.toURI()).parentFile
		val others = dir.listFiles()!!.filter { f -> f.isFile && f.readLines().first().contains("\\u279C") && !f.name.startsWith("tasks-") && !f.name.startsWith("xpguide-") }
		assertTrue(others.isNotEmpty())

		MenuRegistry.register(dev.ashtonite365.apex.features.menus.bestiary.BestiaryAreaMenu)
		for (f in others) RealDumpFixtures.load(f.nameWithoutExtension).let {
			if (MenuRegistry.match(it.title) == null) assertNull(XpGuideParser.parse(it.title, it.slots), f.name)
			else assertEquals(MenuRegistry.match(it.title)?.id, MenuRegistry.match(it.title, it.slots)?.id, f.name)
		}
		MenuRegistry.clearForTests()
		MenuRegistry.register(XpGuideMenu)
		assertEquals("xp-guide", d("tasks-core-1049").let { MenuRegistry.match(it.title, it.slots)?.id })
	}
}
