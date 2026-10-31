package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.gui.config.KeyBindingHandler
import dev.ashtonite365.apex.keybindings.ApexKeyBindings
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.ManagedConfig

class SettingsTabsTest {
	private val configs by lazy { AllRegisteredConfigs.configs }

	@Test
	fun allConfigsLoaded() {
		assertTrue(AllRegisteredConfigs.loadFailures.isEmpty(), "Configs that could not be loaded: ${AllRegisteredConfigs.loadFailures}")
		assertTrue(configs.size > 150, "Only ${configs.size} configs found; enumeration is broken")
	}

	private val pages by lazy { SettingsTabs.pages(configs) }

	@Test
	fun everyOptionIsInExactlyOneSubsection() {
		val seen = mutableMapOf<String, MutableList<String>>()
		for (page in pages) for (view in page.subsections) for (o in view.items)
			seen.getOrPut(SettingsTabs.optionKey(o)) { mutableListOf() }.add(view.subsection.path)

		val all = configs.flatMap { c -> dev.ashtonite365.apex.gui.config.FeatureRisk.visibleOptions(c).map { SettingsTabs.optionKey(it) } }
		assertEquals(all.toSet(), seen.keys, "options missing from the settings screen")
		val twice = seen.filterValues { it.size != 1 }
		assertTrue(twice.isEmpty(), "options shown more than once: $twice")
	}

	@Test
	fun mainSectionsInSpecOrderMiscLast() {
		val mains = pages.map { it.main }.distinct()
		assertEquals(MainSection.entries.toList(), mains)
		assertEquals(MainSection.MISC, pages.last().main)
		val timerSubs = pages.filter { it.main == MainSection.TIMERS }.flatMap { it.subsections }.map { it.subsection.id }

		assertEquals(listOf("board-1", "board-2", "inventory-board"), timerSubs.take(3), "Timers subsections: $timerSubs")
		assertTrue(timerSubs.containsAll(listOf("fishing", "boss-timers", "run-timers", "event")), "Timers subsections: $timerSubs")
		assertTrue(pages.filter { it.group.isNotEmpty() }.all { it.subsections.isNotEmpty() }, "empty group page")
	}

	@Test
	fun mappedOptionsFollowTheMigrationMap() {
		for (o in configs.flatMap { it.sortedOptions }) {
			val e = SettingsTabs.map[SettingsTabs.optionKey(o)] ?: continue
			assertEquals(e.newPath, SettingsTabs.placementOf(o).path, SettingsTabs.optionKey(o))
		}
	}

	@Test
	fun linksAreShortcutsNotCopies() {
		val linked = pages.flatMap { it.links }
		assertTrue(linked.isNotEmpty())
		val homes = pages.flatMap { p -> p.subsections.map { it.subsection } }.toSet()
		for (l in linked) {
			assertTrue(l in homes, "link to ${l.path} without a home")
			assertTrue(pages.none { p -> p.main != l.main && p.subsections.any { it.subsection == l } }, "${l.path} copied")
		}
	}

	@Test
	fun keybindsAreGroupedByMainSection() {
		val keys = configs.flatMap { it.sortedOptions }.filter { it.handler is KeyBindingHandler }
		assertTrue(keys.size > 50, "only ${keys.size} keybinds")
		for (k in keys) assertEquals(SettingsTabs.mainSectionOf(k), ApexKeyBindings.sectionFor(k), SettingsTabs.optionKey(k))
	}

	private fun isCustomMenusConfig(c: ManagedConfig) =
		c is MenuSettings || c.name == "custom-menus" || c.name.startsWith(SettingsTabs.CUSTOM_MENU_PREFIX) ||
			c.category == ManagedConfig.Category.CUSTOM_MENUS || c.name == "bz-menu"

	private val menuAutoClickAllowList: Set<ManagedOption<*>>
		get() = emptySet()

	@Test
	fun riskyOptionsKeepTheirMarkingAndDefault() {
		configs
		val allow = menuAutoClickAllowList
		val risky = configs.flatMap { it.sortedOptions }.filter { it.riskLevel != RiskLevel.NONE }
		assertTrue(risky.isNotEmpty())
		for (o in risky) {

			assertEquals(o.shownRisk.isRisky, o.labelText.string.startsWith("⚠"), SettingsTabs.optionKey(o))
			if (o in allow) continue

			val parent = o.parentToggle
			if (parent != null && parent.riskLevel.isRisky && parent.default() == false) continue
			assertEquals(false, o.default(), "${SettingsTabs.optionKey(o)}: risky options outside Custom Menus stay OFF")
		}
	}

	@Test
	fun customMenusAutoClickOptionsAreOnAndKeepWarning() {
		configs
		val allow = menuAutoClickAllowList

		assertTrue(allow.isEmpty(), allow.map { SettingsTabs.optionKey(it) }.toString())
		assertFalse(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/MenuOptions.kt").exists())
		for (o in allow) {
			assertEquals(MainSection.CUSTOM_MENUS, SettingsTabs.mainSectionOf(o), "default-ON ⚠ ${SettingsTabs.optionKey(o)} must be shown in Custom Menus")
			assertEquals(true, o.default(), SettingsTabs.optionKey(o))
			assertTrue(o.riskLevel.isRisky && o.labelText.string.startsWith("⚠"), SettingsTabs.optionKey(o))
		}
	}

	@Test
	fun silentAutoClickOptionRemoved() {
		assertTrue(configs.flatMap { it.sortedOptions }.none { SettingsTabs.optionKey(it).contains("silent-auto-click") })
	}

	private val hidden by lazy { configs.flatMap { it.sortedOptions }.filterNot(dev.ashtonite365.apex.gui.config.FeatureRisk::visibleInMenus).map { SettingsTabs.optionKey(it) }.toSet() }

	@Test
	fun dungeonsSolversComeFirstAndKeysUnchanged() {
		val page = pages.single { it.main == MainSection.DUNGEONS && it.group.isEmpty() }
		val shown = page.subsections.flatMap { v -> v.items.map { SettingsTabs.optionKey(it) } }

		val subs = page.subsections.map { it.subsection.id }
		assertEquals(listOf("terminal-solver", "f7-devices"), subs.take(2), "Dungeons subsections: $subs")
		val puzzles = subs.drop(2).takeWhile { it.startsWith("puzzle-") }
		assertTrue(puzzles.size >= 12, "puzzle subsections after the solvers: $subs")
		val solverConfigs = listOf("terminal-solver", "f7-devices")
		val solvers = configs.filter { it.name in solverConfigs }.sortedBy { solverConfigs.indexOf(it.name) }
			.flatMap { c -> dev.ashtonite365.apex.gui.config.FeatureRisk.visibleOptions(c).map { SettingsTabs.optionKey(it) } }
		assertTrue(solvers.size >= 15, "solvers: $solvers")
		assertEquals(solvers.toSet(), shown.take(solvers.size).toSet(), "Dungeons tab must start with the solver block")
		val puzzleKeys = dev.ashtonite365.apex.settings.SettingsLayout.PUZZLE_SOLVER_KEYS.filter { "puzzle-solvers.$it" !in hidden }.map { "puzzle-solvers.$it" }
		val puzzleShown = page.subsections.filter { it.subsection.id in puzzles }.flatMap { v -> v.items.map { SettingsTabs.optionKey(it) } }
		val onPage = puzzleKeys.filter { it in shown }
		assertTrue(onPage.size > 20, "puzzle solver keys on the Dungeons page: $onPage")
		assertTrue(puzzleShown.containsAll(onPage), "puzzle solver keys outside the puzzle subsections: ${onPage - puzzleShown.toSet()}")
		val expected = configs.flatMap { c -> dev.ashtonite365.apex.gui.config.FeatureRisk.visibleOptions(c).filter { SettingsTabs.placementOf(it).path.main == MainSection.DUNGEONS && SettingsTabs.placementOf(it).group.isEmpty() }.map { SettingsTabs.optionKey(it) } }
		assertEquals(expected.toSet(), shown.toSet(), "Dungeons key set changed")
		assertEquals(shown.size, shown.toSet().size)
	}
}
