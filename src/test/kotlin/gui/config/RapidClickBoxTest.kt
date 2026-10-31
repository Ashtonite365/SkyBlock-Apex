package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.RapidClickBox

class RapidClickBoxTest {
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val allKeys by lazy { configs.flatMap { c -> c.sortedOptions.map { "${c.name}.${it.propertyName}" } }.toSet() }

	@Test
	fun exactBoxedList() {

		assertEquals(emptyList<String>(), RapidClickBox.BOXED)
		val live = configs.flatMap { it.sortedOptions }.filter { RapidClickBox.isBoxed(it) }.map { RapidClickBox.key(it) }
		assertTrue(live.isEmpty(), live.toString())
		for (k in listOf("custom-menus.automation", "custom-menu-bank.chain-presets", "auto-buy.enabled", "inventory-tweaks.wheel-single-item"))
			assertFalse(k in allKeys, "$k must be removed")
	}

	@Test
	fun everyBoxedOptionExists() {
		assertTrue(AllRegisteredConfigs.loadFailures.isEmpty(), "Configs that could not be loaded: ${AllRegisteredConfigs.loadFailures}")
		for (k in RapidClickBox.BOXED) assertTrue(k in allKeys, "boxed option $k is not registered")
	}

	@Test
	fun bazaarMirrorNotBoxed() {

		assertFalse("bz-menu.in-apex-orders" in allKeys)
		assertFalse(RapidClickBox.isBoxed("bz-menu.in-apex-orders"))
		assertFalse("bz-menu.in-apex-orders" in RapidClickBox.EXCLUDED)
		assertTrue(configs.flatMap { it.sortedOptions }.none { RapidClickBox.isBoxed(it) && it.element.name == "bz-menu" })
	}

	@Test
	fun plainSolverTogglesNotBoxed() {
		val solvers = listOf(
			"experimentation-table.apex-menus", "experimentation-table.enabled", "experimentation-table.block-wrong-clicks",
			"experimentation-table.superpairs-apex-menus", "experimentation-table.superpairs-reveal",
			"experimentation-table.chronomatron-apex-menus", "experimentation-table.ultrasequencer-apex-menus",
			"terminal-solver.highlight", "terminal-solver.block-wrong-clicks", "terminal-solver.melody",
			"puzzle-solvers.three-weirdos", "puzzle-solvers.water-board-solver", "puzzle-solvers.quiz",
			"f7-devices.simon-says", "f7-devices.arrow-align", "fossil-excavator.solver", "mining-solvers.puzzler",
		)
		for (k in solvers) {
			assertTrue(k in allKeys, "solver toggle $k is not registered")
			assertFalse(RapidClickBox.isBoxed(k), "plain solver toggle $k must not be boxed")
		}
	}

	@Test
	fun nonClickingEspNotBoxed() {

		val byKey = configs.flatMap { it.sortedOptions }.associateBy { RapidClickBox.key(it) }
		for (k in listOf("shadow-assassin-finder.enabled", "dungeon-extras.nearest-door-highlight", "dungeon-extras.nearest-door-path")) {
			assertTrue(k in allKeys, "$k is not registered")
			assertFalse(RapidClickBox.isBoxed(k), "$k must not be boxed")
			assertFalse(RapidClickBox.isBoxed(byKey.getValue(k)), "$k must not be boxed on the screen")
			assertTrue(byKey.getValue(k).riskLevel.isRisky, "$k keeps its ⚠")
			assertEquals(false, byKey.getValue(k).default(), "$k stays off by default")
		}

		assertEquals("DANGER: clicks for you in a rapid chain, not one click per click you make.", RapidClickBox.HEADER_FALLBACK)
	}

	@Test
	fun wrapNeverTruncates() {
		val measure: (String) -> Int = { it.length * 6 }
		val text = RapidClickBox.HEADER_FALLBACK
		for (w in listOf(1, 6, 20, 50, 90, 200, 2000)) {
			val lines = RapidClickBox.wrap(text, w, measure)
			assertEquals(text.replace(" ", ""), lines.joinToString("").replace(" ", ""), "width $w lost text")
			for (l in lines) assertTrue(measure(l) <= maxOf(w, 6), "width $w: '$l' too wide")
		}
		assertEquals(listOf(text), RapidClickBox.wrap(text, 10_000, measure))
		assertTrue(RapidClickBox.wrap(text, 120, measure).size > 1)
	}

	@Test
	fun boxGeometry() {
		val lh = 9
		assertEquals(RapidClickBox.INSET + RapidClickBox.headerHeight(2, lh), RapidClickBox.innerOffsetY(2, lh))
		assertEquals(RapidClickBox.innerOffsetY(2, lh) + 45 + RapidClickBox.INSET, RapidClickBox.boxHeight(45, 2, lh))
		assertTrue(RapidClickBox.headerHeight(1, lh) >= RapidClickBox.SIGN_WIDTH)
		assertTrue(RapidClickBox.headerHeight(3, lh) > RapidClickBox.headerHeight(2, lh))
		assertTrue(RapidClickBox.textWidth(300) < 300 - 2 * RapidClickBox.INSET)
	}
}
