// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry

class SlotLinkEveryMenuTest {

	private val projectRoot: java.io.File by lazy {
		generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { java.io.File(it, "settings.gradle.kts").isFile && java.io.File(it, "src/main/kotlin").isDirectory }
	}
	private val main by lazy { File(projectRoot, "src/main/kotlin") }
	private val sources by lazy { main.walkTopDown().filter { it.extension == "kt" }.toList() }
	private fun text(rel: String) = File(projectRoot, rel).readText()

	@AfterEach
	fun cleanup() = MenuRegistry.clearForTests()

	private data class Decl(val name: String, val isObject: Boolean, val supers: List<String>, val file: File, val pkg: String, val outer: List<String>)

	private fun declarations(): List<Decl> = sources.flatMap { f ->
		val src = f.readText()
		val pkg = Regex("""(?m)^package\s+([\w.]+)""").find(src)?.groupValues?.get(1) ?: ""
		val tops = Regex("""(?m)^(?:internal |private )?object\s+(\w+)""").findAll(src).map { it.groupValues[1] }.toList()
		Regex("""(?m)^\s*(?:(?:private|internal|public|abstract|open|sealed|data)\s+)*(object|class)\s+(\w+)(?:<[^>]*>)?\s*(?:\((?:[^()]|\([^()]*\))*\))?\s*:\s*([^{\n]+)""").findAll(src).map { m ->
			val supers = m.groupValues[3].split(',').map { it.trim().substringBefore('(').substringBefore('<').substringAfterLast('.').trim() }
			Decl(m.groupValues[2], m.groupValues[1] == "object", supers, f, pkg, tops)
		}.toList()
	}

	private fun customMenuObjects(): List<Decl> {
		val decls = declarations()
		val menuTypes = mutableSetOf("CustomMenu")
		var grew = true
		while (grew) { grew = false; for (d in decls) if (d.name !in menuTypes && d.supers.any { it in menuTypes }) { menuTypes += d.name; grew = true } }
		return decls.filter { it.isObject && it.name in menuTypes }.distinctBy { it.pkg + "." + it.name }
	}

	private fun load(d: Decl): CustomMenu {
		val names = listOf("${d.pkg}.${d.name}") + d.outer.filter { it != d.name }.map { "${d.pkg}.$it\$${d.name}" }
		val cls = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() } ?: error("cannot load ${d.name} (${names})")
		return cls.getField("INSTANCE").get(null) as CustomMenu
	}

	@Test
	fun everyRegisteredCustomMenuGoesThroughTheOverlay() {
		val objects = customMenuObjects()
		MenuRegistry.clearForTests()
		val files = mutableMapOf<String, File>()
		for (d in objects) { val m = load(d); MenuRegistry.register(m); files[m.id] = d.file }
		val all = MenuRegistry.all
		assertTrue(all.size >= 70, "expected every Apex custom menu, walked only ${all.size}")

		val registeredNames = sources.flatMap { f ->
			Regex("""MenuRegistry\.(?:register|registerFirst)\((\w+)\)""").findAll(f.readText()).map { it.groupValues[1] }.filter { it != "this" }.toList()
		}.toSet()
		val walkedNames = objects.map { it.name }.toSet()
		assertTrue(walkedNames.containsAll(registeredNames), "not walked: ${registeredNames - walkedNames}")

		for (n in listOf("BrewingStandMenu", "AnvilMenu", "GardenFarmingToolkitMenu", "WardrobeMenu", "AhMenu", "BazaarMenu", "PlayerProfileMenu"))
			assertTrue(n in walkedNames, "not walked: $n")

		val hostAttach = Regex("""MenuHost\.attach\(""")
		val uncovered = all.filter { m ->

			m.selfHosted && !hostAttach.containsMatchIn(files.getValue(m.id).readText())
		}.map { it.id }
		assertEquals(emptyList<String>(), uncovered, "custom menus not hosted through MenuFront (no slot binding overlay)")
		println("SlotLinkEveryMenuTest: ${all.size} custom menus covered: " + all.joinToString { it.id })
	}

	@Test
	fun theHostPathEndsInTheOverlay() {
		val registry = text("src/main/kotlin/features/menus/MenuRegistry.kt")
		val attach = registry.substringAfter("fun attach(screen: AbstractContainerScreen<*>, menu: CustomMenu").substringBefore("\n\t}")
		assertTrue("MenuHost.attach(" in attach, "MenuRegistry.attach must host the view through MenuHost")
		val host = text("src/main/kotlin/features/menus/MenuHost.kt")
		assertTrue("screen.customGui = if (v) null else MenuFront(this)" in host, "the menu front is MenuFront")
		val front = host.substringAfter("class MenuFront(")
		assertTrue("override fun slotLinkBoxes(" in front && "SlotLinkOverlay.forMenuSession(" in front, "MenuFront supplies the overlay positions")
		assertTrue("session.standInCells.clear()" in front, "stand-ins are re-registered each frame")

		val mixin = text("src/main/java/dev/ashtonite365/apex/mixins/customgui/PatchGenericScreen.java")
		assertTrue("if (gui != null) CustomGuiHost.drawFront((Screen) (Object) this, gui, g, mx, my, partial);" in mixin)
		val render = text("src/main/kotlin/util/customgui/CustomGuiHost.kt").substringAfter("fun drawFront(").substringBefore("\n\t}")
		assertTrue(render.indexOf("gui.render(") in 0 until render.indexOf("SlotLinkOverlay.afterCustomGui("),
			"the overlay is drawn for every CustomGui right after its render")
	}

	@Test
	fun noMenuInstallsAnotherFrontScreen() {

		val bad = sources.filter { it.path.contains("features/menus/") || it.path.contains("features/apexbzmenu/") }.flatMap { f ->
			Regex("""customGui\s*=\s*([^\n=][^\n]*)""").findAll(f.readText()).map { it.groupValues[1].trim() }
				.filter { v -> !(v.startsWith("null") || v.startsWith("if (v) null else MenuFront(")) }.map { "${f.name}: $it" }.toList()
		}
		assertEquals(emptyList<String>(), bad)
	}

	@Test
	fun viewsThatDrawTheirOwnInventoryTilesRegisterThem() {

		val drawsOwn = sources.filter { it.path.contains("features/menus/") }.filter { f ->
			val s = f.readText(); Regex("""container is (?:net\.minecraft\.world\.entity\.player\.)?Inventory""").containsMatchIn(s) && ".item(" in s
		}
		assertTrue(drawsOwn.isNotEmpty())
		val missing = drawsOwn.filter { f -> f.readText().let { "standInSlotCell(" !in it && "standInCells" !in it } }.map { it.name }
		assertEquals(emptyList<String>(), missing)
		assertTrue("standInSlotCell(" in text("src/main/kotlin/features/menus/workbench/WorkbenchMenus.kt"))
		assertTrue("standInCells" in text("src/main/kotlin/features/menus/ah/AhMenu.kt"))
	}
}
