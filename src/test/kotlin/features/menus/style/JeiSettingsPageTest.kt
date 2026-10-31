// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.menus.style

import dev.ashtonite365.apex.test.testutil.RepoFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JeiSettingsPageTest {
	private val tmp = java.io.File.createTempFile("jei-style", ".json").also { it.delete() }
	@BeforeEach fun setUp() { ModSettingsPages.resetForTests(); JeiStyle.file = tmp; JeiStyle.resetForTests() }
	@AfterEach fun tearDown() { ModSettingsPages.resetForTests(); tmp.delete(); JeiStyle.file = null; JeiStyle.resetForTests() }

	private fun fakeJeiPage() = ModSettingsPages.Page("jei", "JEI") {
		listOf(ModSettingsPages.Row.Module("Panel style"),
			ModSettingsPages.Row.Cycle("JEI style", "look", { JeiStyle.state.look.label }) { JeiStyle.choose(JeiLook.FORGE) },
			ModSettingsPages.Row.Option("repo.enable-rei"), ModSettingsPages.Row.Option("fixes.hide-recipe-book"))
	}

	@Test fun missingWithoutJei() {

		assertTrue(ModSettingsPages.all.isEmpty())
		assertNull(ModSettingsPages.byId("jei"))
		assertTrue(ModSettingsPages.movedKeys().isEmpty(), "options stay on their usual pages")

		CustomMenusPage.resetExtrasForTests()
		assertTrue(CustomMenusPage.rows.none { it is CustomMenusPage.Row.Module && it.title == "JEI" })
		assertTrue(CustomMenusPage.rows.none { it is CustomMenusPage.Row.Cycle && it.label.contains("JEI") })
	}

	@Test fun presentWithJei() {
		ModSettingsPages.register(fakeJeiPage()); ModSettingsPages.register(fakeJeiPage())
		assertEquals(listOf("JEI"), ModSettingsPages.all.map { it.title }, "one page, registered once")
		assertEquals(setOf("repo.enable-rei", "fixes.hide-recipe-book"), ModSettingsPages.movedKeys())
		val p = ModSettingsPages.byId("jei")!!
		assertEquals(listOf("JEI style"), ModSettingsPages.search(p, "style") { null }.map { it.label })
		assertEquals(1, ModSettingsPages.search(p, "recipe book") { k -> if (k == "fixes.hide-recipe-book") "Hide recipe book" to "" else null }.size)
		(p.rows()[1] as ModSettingsPages.Row.Cycle).step(1)
		assertEquals(JeiLook.FORGE, JeiStyle.state.look, "a change applies at once")
	}

	@Test fun registeredOnlyFromTheJeiCompatCode() {
		val main = RepoFile("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }
		assertTrue(main.none { it.readText().contains("ModSettingsPages.register(") }, "core never registers a mod page")
		val jei = RepoFile("src/compat/jei/java/dev/ashtonite365/apex/compat/jei/style/JeiStyleDraw.kt").readText()
		assertTrue(jei.contains("ModSettingsPages.Page(\"jei\", \"JEI\")") && jei.contains("ModSettingsPages.register(PAGE)"))
		for (k in listOf("JEI style", "JEI Forge: dark mode", "repo.enable-rei", "Fill the grid above the tabs", "Hide the recipe sync notice", "fixes.hide-recipe-book"))
			assertTrue(jei.contains("\"$k\""), k)
		val screen = RepoFile("src/main/kotlin/gui/config/styled/StyledSettingsScreen.kt").readText()
		assertTrue(screen.contains("ModSettingsPages.all") && screen.contains("ModSettingsPages.movedKeys()"))
	}

	@Test fun newSettingsKeepOldFiles() {
		assertEquals(JeiStyleState(JeiLook.SKYGLASS), JeiStyleCodec.decode("""{"look":"skyglass","forge-dark":true}"""))
		val s = JeiStyleState(JeiLook.FORGE, false, gridFill = false, hideSyncNotice = false)
		assertEquals(s, JeiStyleCodec.decode(JeiStyleCodec.encode(s)))
		JeiStyle.toggleGridFill(); assertFalse(JeiStyle.state.gridFill)
		JeiStyle.resetForTests(); assertFalse(JeiStyle.load().gridFill)
	}
}
