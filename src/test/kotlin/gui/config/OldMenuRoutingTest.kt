package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.SettingsMenuPlan
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsScreen
import dev.ashtonite365.apex.test.testutil.RepoFile

class OldMenuRoutingTest {
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val pages by lazy { SettingsTabs.pages(configs) }
	private fun src(rel: String) = RepoFile(rel).readText()

	@Test fun everyEntryPointOpensTheApexMenu() {
		val gui = src("src/main/kotlin/gui/config/AllConfigsGui.kt")
		assertTrue(gui.contains("StyledSettingsScreen(search, parent, focus)"), "AllConfigsGui builds the Apex menu")
		assertTrue(gui.contains("RiskDisclaimer.wrapApexMenu { makeMenuScreen(search, parent) }"))
		assertTrue(gui.contains("onRun { setScreenLater(makeScreen(this[text])) }"), "/apex search")
		val rome = src("src/main/kotlin/commands/rome.kt")
		assertTrue(Regex("""onRun \{ AllConfigsGui\.showAllGuis\(\) \}""").findAll(rome).count() >= 2, "/apex and /apex config")
		assertTrue(src("src/compat/modmenu/java/dev/ashtonite365/apex/compat/modmenu/ApexModMenuPlugin.kt").contains("AllConfigsGui.makeScreen(parent = parent)"), "Mod Menu")
		assertTrue(src("src/main/kotlin/features/menus/ApexInventoryButton.kt").contains("AllConfigsGui.showAllGuis()"), "inventory button")
		assertTrue(src("src/main/kotlin/gui/config/DangerousSettings.kt").contains("AllConfigsGui.makeScreen()"), "Dangerous switch")
		val mc = src("src/main/java/dev/ashtonite365/apex/util/data/ManagedConfig.kt")
		assertTrue(mc.contains("fun getConfigEditor(parent: Screen? = null): Screen = AllConfigsGui.makeScreenFor(this, parent)"), "per-config pages")
		assertTrue(mc.contains("ScreenUtil.setScreenLater(getConfigEditor(parent))"), "showConfigEditor")
		assertTrue(src("src/main/java/dev/ashtonite365/apex/mixins/ApexKeybindsInVanillaControlsPatch.java").contains("showConfigEditor("), "vanilla Controls button")
		assertTrue(src("src/main/kotlin/gui/config/ModulesScreen.kt").contains("showConfigEditor(this)"), "Modules gear")
		assertTrue(src("src/main/kotlin/features/credits/Credits.kt").contains("TConfig.getConfigEditor()"), "/apex credits")
		assertTrue(src("src/main/kotlin/gui/hud/editor/HudEditors.kt").contains("config.getConfigEditor(parent)"), "HUD editor search")
		val styled = src("src/main/kotlin/gui/config/styled/StyledSettingsScreen.kt")
		assertFalse(styled.contains("openClassic"), "no way back into the old menu")
		assertTrue(styled.contains("o.element.getOptionEditor(o, this)"), "Edit… opens the option's own editor")
	}

	@Test fun oldMenuIsArchivedNotCompiled() {
		for (gone in listOf("src/compat/moulconfig", "src/compat/yacl", "src/main/kotlin/gui/config/BuiltInConfigScreenProvider.kt",
			"src/main/resources/assets/apex/gui/config/main.xml", "src/main/resources/assets/apex/gui/config/category.xml"))
			assertFalse(RepoFile(gone).exists(), gone)
		if (RepoFile("archive/old-firmament-menu").isDirectory) for (kept in listOf("compat-moulconfig/java/MCConfigEditorIntegration.kt", "compat-yacl/java/YaclIntegration.kt",
			"src-main-kotlin-gui-config/BuiltInConfigScreenProvider.kt", "resources-gui-config/main.xml", "resources-gui-config/category.xml"))
			assertTrue(RepoFile("archive/old-firmament-menu/$kept").isFile, kept)
		val main = RepoFile("src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
		for (f in main) assertFalse(f.readText().contains("makeBuiltInScreen"), f.path)
	}

	@Test fun everyOldSettingKeyIsInTheApexMenu() {
		val inMenu = pages.flatMap { p -> SettingsMenuPlan.rows(p).mapNotNull { (it as? SettingsMenuPlan.Row.Entry)?.item?.let(SettingsTabs::optionKey) } }.toSet()
		val old = configs.flatMap { c -> FeatureRisk.visibleOptions(c).map(SettingsTabs::optionKey) }.toSet()
		assertTrue(old.size > 100, "found ${old.size} keys")
		assertEquals(emptyList<String>(), (old - inMenu).sorted(), "old settings missing from the Apex menu")
		assertEquals(setOf("configconfig.enable-yacl", "configconfig.enable-moulconfig", "configconfig.wide-moulconfig"), AllConfigsGui.RETIRED_KEYS)
		assertTrue(AllConfigsGui.RETIRED_KEYS.all { it in old }, "retired keys stay registered so saved values load")
	}

	@Test fun perConfigRoutesLandOnThatConfigsPage() {
		for (c in configs) {
			if (FeatureRisk.visibleOptions(c).isEmpty()) continue
			val p = StyledSettingsScreen.pageFor(pages, c)
			assertNotNull(p, c.name)
			assertTrue(SettingsMenuPlan.rows(p!!).any { (it as? SettingsMenuPlan.Row.Entry)?.item?.element === c }, c.name)
		}
	}
}
