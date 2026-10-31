// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexbzmenu.BzMenu
import dev.ashtonite365.apex.features.enchanting.ExperimentSolvers
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSections
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs

class MenuSectionsTest {
	private class View : MenuView {
		override var x = 0; override var y = 0; override var w = 0; override var h = 0
		override fun layout(x: Int, y: Int, w: Int, h: Int) {}
		override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
		override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = true
		override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = true
		override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = false
		override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = false
	}

	private class Menu(override val settings: MenuSettings) : CustomMenu {
		override val id = settings.menuId
		override val displayName = id
		override val title = Regex("^Test Menu$")
	}

	private val map = MigrationMap.bundled()

	private val outsideCustomMenus by lazy {
		map.entries.groupBy { it.oldConfig }.filterValues { rows -> rows.none { it.newPath.main == MainSection.CUSTOM_MENUS } }.keys
	}
	private val holders by lazy { AllRegisteredConfigs.configs.filterIsInstance<MenuSettings>().filter { it.name !in outsideCustomMenus } }

	@Test fun onlyTheExpTableMenuLivesOutsideCustomMenus() {
		assertEquals(listOf("custom-menu-exptable"), AllRegisteredConfigs.configs.filterIsInstance<MenuSettings>().map { it.name }.filter { it in outsideCustomMenus })
	}

	@BeforeEach fun load() { MenuSections.explicitDefaultLoad(); BzMenu.TConfig.explicitDefaultLoad() }
	@AfterEach fun cleanup() { MenuRegistry.clearForTests(); MenuSections.explicitDefaultLoad(); BzMenu.TConfig.explicitDefaultLoad() }

	@Test fun defaultIsOn() {
		for (s in MenuSections.SECTIONS) assertTrue(MenuSections.isOn(s.group), s.switchLabel)
		assertEquals(17, MenuSections.SECTIONS.size)
		assertEquals("Pets and Wardrobe Apex Menus", MenuSections.SECTIONS.first { it.group == "pets-wardrobe" }.switchLabel)
	}

	@Test fun everyMenuIsInASection() {
		val groups = MenuSections.SECTIONS.map { it.group }.toSet()
		val missing = holders.filter { MenuSections.sectionOf(it.name) !in groups }.map { it.name }
		assertTrue(missing.isEmpty(), "menus outside every section: $missing")
	}

	@Test fun sectionOffIsVanillaForEveryMenuAndOnGivesTheSubTabSettingsBack() {
		for (s in holders) {
			s.explicitDefaultLoad()
			val g = MenuSections.sectionOf(s.name)!!
			MenuRegistry.clearForTests(); MenuRegistry.register(Menu(s))
			MenuSections.set(g, false)
			val off = MenuRegistry.resolve("Test Menu") { View() }
			assertEquals(MenuRegistry.Resolution.Vanilla::class, off::class, s.name)
			assertTrue((off as MenuRegistry.Resolution.Vanilla).sectionOff, "${s.name}: deliberate, not a fallback")
			assertEquals("${s.menuId} (section off)", MenuRegistry.describe(off))
			MenuSections.set(g, true)
			val on = MenuRegistry.resolve("Test Menu") { View() }
			if (s.apex) assertTrue(on is MenuRegistry.Resolution.Attach, "${s.name}: Apex again")
			else assertTrue(on is MenuRegistry.Resolution.Vanilla && !on.sectionOff, "${s.name}: its own Normal setting again")
		}
	}

	@Test fun bazaarReusesItsOverlayToggle() {
		assertTrue(MenuSections.isOn("bazaar"))
		MenuSections.set("bazaar", false)
		assertFalse(BzMenu.TConfig.overlay)
		assertEquals("custom_menus.bazaar-apex-menus.bz-menu.overlay", map["bz-menu.overlay"]!!.newPath.toString())
	}

	@Test fun oldConfigsLoadWithMissingSwitchesOn() {
		MenuSections.loadFrom(Unit, JsonObject(emptyMap()))
		for (s in MenuSections.SECTIONS) assertTrue(MenuSections.isOn(s.group))
		MenuSections.loadFrom(Unit, JsonObject(mapOf("custom-menu-sections" to JsonObject(mapOf("garden" to JsonPrimitive(false))))))
		assertFalse(MenuSections.isOn("garden")); assertTrue(MenuSections.isOn("auction"))

		val old = Json.parseToJsonElement("""{"custom-menu-garden":{"mode":"apex"},"bz-menu":{"overlay":true}}""").jsonObject
		val tree = ConfigMigrator(map).migrate(old).tree
		assertEquals(JsonPrimitive(true), ConfigMigrator(map).lookup(tree, SettingPath.parse("custom_menus.bazaar-apex-menus.bz-menu.overlay")))
	}

	@Test fun experimentationSplitMigratesWithoutChangingValues() {
		val old = Json.parseToJsonElement("""{"experimentation-table":{"enabled":true,"block-wrong-clicks":true,
			"chronomatron-show-next":false,"superpairs-reveal":true,"ultrasequencer-numbers":false}}""").jsonObject
		val m = ConfigMigrator(map)
		val tree = m.migrate(old).tree
		fun at(p: String) = m.lookup(tree, SettingPath.parse(p))
		assertEquals(JsonPrimitive(true), at("experimentation.experimentation-table.enabled"))
		assertEquals(JsonPrimitive(true), at("experimentation.experimentation-table.block-wrong-clicks"))
		assertEquals(JsonPrimitive(false), at("experimentation.chronomatron.show-next"))
		assertEquals(JsonPrimitive(true), at("experimentation.superpairs.reveal"))
		assertEquals(JsonPrimitive(false), at("experimentation.ultrasequencer.numbers"))
		val subs = map.entries.filter { it.oldConfig == "experimentation-table" }.map { it.newPath.subsection }.toSet()
		assertEquals(setOf("experimentation-table", "chronomatron", "ultrasequencer", "superpairs"), subs)
		for (g in listOf("chronomatron", "ultrasequencer", "superpairs"))
			assertTrue(map.entries.any { it.newPath.main == MainSection.EXPERIMENTATION && it.newPath.subsection == g && it.newPath.key == "apex-menus" }, g)

		assertEquals(ExperimentSolvers.Solver.NONE, ExperimentSolvers.gate(ExperimentSolvers.Solver.SUPERPAIRS, true, false))
		assertEquals(ExperimentSolvers.Solver.ULTRASEQUENCER, ExperimentSolvers.gate(ExperimentSolvers.Solver.ULTRASEQUENCER, true, false))
	}

	@Test fun noVisibleLabelCarriesASourceTag() {

		val f = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.map { File(it, "translations/en_us.json") }.first { it.isFile }
		val tr = Json.parseToJsonElement(f.readText()).jsonObject
		val tag = Regex("""[\[(](Odin|SkyHanni|Apex|Firmament|NEU|NotEnoughUpdates|SBA|Skytils|Elite|Skyblocker|SkyblockAddons)(?:[- ][^\])]*)?[\])]""")
		val bad = tr.filter { (k, v) -> !k.contains(".description") && !k.contains(".tooltip") &&
			(k.startsWith("apex.config.") || k.startsWith("apex.settings.")) && tag.containsMatchIn((v as JsonPrimitive).content) }
		assertTrue(bad.isEmpty(), "labels with a source tag: ${bad.keys}")
	}
}
