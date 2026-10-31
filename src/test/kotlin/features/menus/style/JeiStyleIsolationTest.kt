// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.menus.style

import dev.ashtonite365.apex.init.MixinPlugin
import dev.ashtonite365.apex.test.testutil.RepoFile
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JeiStyleIsolationTest {
	private val tmp = File.createTempFile("jei-style", ".json").also { it.delete() }
	private val menuTmp = File.createTempFile("menu-style", ".json").also { it.delete() }

	@BeforeEach fun setUp() { JeiStyle.file = tmp; JeiStyle.resetForTests(); MenuStyle.file = menuTmp; MenuStyle.resetForTests() }
	@AfterEach fun tearDown() { tmp.delete(); menuTmp.delete(); JeiStyle.file = null; JeiStyle.resetForTests(); MenuStyle.file = null; MenuStyle.resetForTests() }

	@Test fun noJeiRowsWithoutJei() {
		CustomMenusPage.resetExtrasForTests()
		assertTrue(CustomMenusPage.rows.none { it is CustomMenusPage.Row.Module && it.title == "JEI" })
		assertTrue(CustomMenusPage.rows.none { it is CustomMenusPage.Row.Cycle && it.label.contains("JEI") })
	}

	@Test fun jeiMixinsDoNotApplyWithoutJei() {
		val absent = java.util.function.Predicate<String> { false }
		for (m in listOf("JeiStyleSpritePatch", "JeiGridLayoutRowsPatch", "JeiItemListGridTagPatch", "JeiLifecycleCapturePatch",
			"JeiRecipeSyncNoticePatch", "JeiRectangularLayoutRowsPatch")) {
			assertFalse(MixinPlugin.requiredModPresent("dev.ashtonite365.apex.mixins.compat.jei.$m", absent), m)
			assertTrue(MixinPlugin.requiredModPresent("dev.ashtonite365.apex.mixins.compat.jei.$m") { it == "jei" }, m)
		}
		assertTrue(MixinPlugin.requiredModPresent("dev.ashtonite365.apex.mixins.SomeCorePatch", absent))
	}

	@Test fun noJeiClassesOnTheAlwaysLoadedSide() {

		val code = RepoFile("src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
		assertEquals(emptyList<File>(), code.filter { it.readText().contains("mezz.jei") })
		assertEquals(listOf("MixinPlugin.java"), code.filter { it.readText().contains("compat.jei") }.map { it.name })

		assertNull(Class.forName("dev.ashtonite365.apex.features.menus.style.JeiStyle").declaredFields.firstOrNull { it.type.name.startsWith("mezz.") })

		val fmj = RepoFile("src/main/resources/fabric.mod.json").readText()
		assertTrue(fmj.contains("\"jei_mod_plugin\""))
		val panel = RepoFile("src/compat/jei/java/dev/ashtonite365/apex/compat/jei/ApexJeiPlugin.kt").readText()
		assertTrue(panel.contains("JeiStylePanel.install()"))
		assertTrue(RepoFile("src/compat/jei/java/dev/ashtonite365/apex/compat/jei/style/JeiStyleDraw.kt").readText().contains("ModSettingsPages.register(PAGE)"))
	}

	@Test fun jeiStyleIsIndependentOfMenuStyle() {
		MenuStyle.choose(StyleChoice.FORGE)
		assertEquals(JeiLook.VANILLA, JeiStyle.state.look)
		JeiStyle.choose(JeiLook.SKYGLASS)
		assertEquals(Style.FORGE, MenuStyle.state.style)
		MenuStyle.choose(StyleChoice.VANILLA)
		assertEquals(JeiLook.SKYGLASS, JeiStyle.state.look)
		assertFalse(menuTmp.readText().contains("look"))
		JeiStyle.resetForTests()
		assertEquals(JeiLook.SKYGLASS, JeiStyle.load().look)
	}

	@Test fun spriteMapping() {
		assertNull(JeiStyle.spriteFor("ingredient_list_background", JeiStyleState(JeiLook.VANILLA)))
		assertEquals("jei/skyglass/search_background", JeiStyle.spriteFor("search_background", JeiStyleState(JeiLook.SKYGLASS)))
		assertEquals("jei/forge_light/button_enabled", JeiStyle.spriteFor("button_enabled", JeiStyleState(JeiLook.FORGE, forgeDark = false)))
		assertNull(JeiStyle.spriteFor("icons/arrow_next", JeiStyleState(JeiLook.FORGE)))
		for (v in JeiStyle.VARIANTS) for (n in JeiStyle.THEMED)
			assertTrue(RepoFile("src/main/resources/assets/apex/textures/gui/sprites/jei/$v/$n.png").isFile, "$v/$n")
	}

	@Test fun codecRoundTrip() {
		val s = JeiStyleState(JeiLook.FORGE, forgeDark = false)
		assertEquals(s, JeiStyleCodec.decode(JeiStyleCodec.encode(s)))
		assertEquals(JeiStyleState(), JeiStyleCodec.decode("garbage"))
	}

	@Test fun skyglassJeiIsTranslucentAndFrostedForgeIsForged() {
		val dir = "src/main/resources/assets/apex/textures/gui/sprites/jei"
		for (name in JeiStyle.THEMED) for (v in JeiStyle.VARIANTS) {
			val f = dev.ashtonite365.apex.test.testutil.RepoFile("$dir/$v/$name.png")
			assertTrue(f.isFile, "$v/$name exists")
			if (v != "skyglass") continue
			val img = javax.imageio.ImageIO.read(f)
			val a = (img.getRGB(img.width / 2, img.height / 2) ushr 24) and 0xFF
			assertTrue(a < 0x80, "Skyglass $name fill is translucent (alpha $a)")
		}
		val sky = JeiStyleState(JeiLook.SKYGLASS); val forge = JeiStyleState(JeiLook.FORGE); val van = JeiStyleState(JeiLook.VANILLA)
		for (n in listOf("gui_background", "single_recipe_background", "recipe_preview_background", "button_enabled", "tab_selected", "search_background"))
			assertTrue(JeiStyle.frosted(n, sky), "$n blurred + frosted in Skyglass")
		assertFalse(JeiStyle.frosted("ingredient_list_slot_background", sky), "no blur per item slot")
		assertFalse(JeiStyle.frosted("gui_background", forge)); assertFalse(JeiStyle.frosted("gui_background", van))
		assertTrue(JeiStyle.forged("gui_background", 176, 166, forge)); assertTrue(JeiStyle.forged("single_recipe_background", 150, 80, forge))
		assertFalse(JeiStyle.forged("gui_background", 176, 166, sky)); assertFalse(JeiStyle.forged("button_enabled", 60, 30, forge))
		assertEquals("jei/forge_dark/tab_selected", JeiStyle.spriteFor("tab_selected", forge))
		assertNull(JeiStyle.spriteFor("tab_selected", van), "Vanilla JEI untouched")
		val tabs = dev.ashtonite365.apex.test.testutil.RepoFile("src/compat/jei/java/dev/ashtonite365/apex/mixins/compat/jei/JeiStyleFixedSpritePatch.java").readText()
		assertTrue(tabs.contains("mezz.jei.common.gui.elements.DrawableSprite") && tabs.contains("require = 0"))
	}
}
