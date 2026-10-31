// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import java.io.File
import java.lang.reflect.Modifier
import net.minecraft.client.gui.screens.Screen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.InventoryKeyClose

class InventoryKeyCloseEveryScreenTest {

	private val projectRoot: File by lazy {
		generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { File(it, "settings.gradle.kts").isFile && File(it, "src/main/kotlin").isDirectory }
	}
	private val sources by lazy {
		listOf("src/main/kotlin", "src/main/java").map { File(projectRoot, it) }.filter { it.isDirectory }
			.flatMap { d -> d.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList() }
	}
	private fun text(rel: String) = File(projectRoot, rel).readText()
	private fun rel(f: File) = f.relativeTo(projectRoot).path

	@Test fun noHardCodedInventoryKey() {
		val banned = listOf(Regex("""\bGLFW_KEY_E\b"""), Regex("""\bKEY_E\b"""), Regex(""""key\.keyboard\.e""""), Regex("""GLFW\.GLFW_KEY_E"""))
		val hits = sources.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (banned.any { it.containsMatchIn(l) }) "${rel(f)}:${i + 1}: ${l.trim()}" else null } }
		assertEquals(emptyList<String>(), hits, "closing must use the player's bound inventory key, never a fixed E")

		val src = text("src/main/kotlin/util/InventoryKeyClose.kt")
		assertTrue(src.contains("options?.keyInventory"))
		assertTrue(src.contains(".matches(key)")); assertTrue(src.contains(".matchesMouse(click)"))
		assertFalse(Regex("""KeyEvent\(\s*\d""").containsMatchIn(src), "no fixed key codes")
	}

	@Test fun everyApexScreenClassGoesThroughTheRule() {
		val roots = listOf("build/classes/kotlin/main", "build/classes/java/main").map { File(projectRoot, it) }.filter { it.isDirectory }
		assertTrue(roots.isNotEmpty())
		val loader = InventoryKeyClose::class.java.classLoader
		val screens = roots.flatMap { root ->
			root.walkTopDown().filter { it.extension == "class" }.map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
				.filter { it.startsWith("dev.ashtonite365.apex.") && !it.startsWith("dev.ashtonite365.apex.mixins.") && !it.contains(".compat.") }
				.mapNotNull { runCatching { Class.forName(it, false, loader) }.getOrNull() }
				.filter { Screen::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
				.toList()
		}
		val names = screens.map { it.name }

		for (expected in listOf(
			"dev.ashtonite365.apex.features.menus.MenuBrowseScreen",
			"dev.ashtonite365.apex.features.auction.SignSearchScreen",
			"dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen",
			"dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewScreen",
		)) assertTrue(expected in names, "$expected found among $names")
		assertTrue(names.any { it.startsWith("dev.ashtonite365.apex.util.GuiXml") }, "settings screens (MoulConfig wrapper)")
		assertTrue(screens.size >= 15, "found ${screens.size} screens: $names")
		val outside = screens.filterNot { InventoryKeyClose.isApexScreenClass(it) }
		assertEquals(emptyList<Class<*>>(), outside)

		val src = text("src/main/kotlin/util/InventoryKeyClose.kt")
		assertTrue(src.contains("ScreenEvents.AFTER_INIT.register"))
		assertTrue(src.contains("@Subscribe\n\tfun onInit(event: ClientInitEvent)"))
	}

	@Test fun chestBackedMenusUseTheRuleWithEscapeUnchanged() {
		val host = text("src/main/kotlin/features/menus/MenuHost.kt")
		assertTrue(host.contains("InventoryKeyClose.handleMenuKey(screen, key,"))
		assertTrue(host.contains("InventoryKeyClose.handleMenuClick(screen, click,"))

		assertTrue(host.indexOf("InventoryKeyClose.handleMenuKey") < host.indexOf("view.keyPressed(key)"))

		assertTrue(host.contains("if (key.input() == InputConstants.KEY_ESCAPE) onEscape()\n\t\t\tkey.input() == InputConstants.KEY_ESCAPE"))

		assertTrue(text("src/main/kotlin/util/customgui/CustomGui.kt").contains("TextInputFocus"))
		assertTrue(host.contains("override val textInputFocused get() = session.guard(\"textFocused\", true) { view.textFocused }"))
	}

	@Test fun delegatingMenuViewsForwardFocusAndCloseClaims() {
		val delegate = Regex("""override fun keyPressed\([^)]*\)\s*=\s*(\w+)\.keyPressed\(""")
		val found = sources.filter { it.extension == "kt" }.flatMap { f -> delegate.findAll(f.readText()).map { f to it.groupValues[1] }.toList() }
			.filter { (f, _) -> f.readText().contains(": MenuView") }
		assertTrue(found.isNotEmpty())
		for ((f, inner) in found) {
			val s = f.readText()
			assertTrue(s.contains("override val textFocused get() = $inner.textFocused"), "${rel(f)} forwards textFocused")
			assertTrue(s.contains("override fun interceptClose() = $inner.interceptClose()"), "${rel(f)} forwards interceptClose")
		}
	}

	@Test fun everyTextInputOwnerReportsFocus() {

		val input = Regex("""\b(EditBox|MultiLineEditBox|MenuSearch)\(""")
		val bad = sources.filter { it.extension == "kt" && input.containsMatchIn(it.readText()) }
			.filterNot { it.name in setOf("MenuSearch.kt", "InventoryKeyClose.kt", "ChatSearch.kt") }
			.filterNot { f -> f.readText().let { s -> s.contains("addRenderableWidget") || s.contains("addWidget") || s.contains("override val textFocused") || s.contains("TextInputFocus") } }
			.map { rel(it) }
		assertEquals(emptyList<String>(), bad)

		val moul = sources.filter { it.readText().contains("TextFieldComponent(") }.map { it.name }.toSet()
		assertTrue(moul.isNotEmpty())
		assertTrue(text("src/main/kotlin/util/InventoryKeyClose.kt").contains("MoulConfigScreenComponent)?.guiContext?.focusedElement"))

		for ((file, owner) in listOf(
			"src/main/kotlin/features/menus/MenuBrowseScreen.kt" to "view.textFocused",
			"src/main/kotlin/util/PopupHostScreen.kt" to "popup != null",
			"src/main/kotlin/features/inventory/storageoverlay/StorageOverlayScreen.kt" to "guiContext.focusedElement",
			"src/main/kotlin/features/inventory/storageoverlay/StorageOverlayCustom.kt" to "overview.textInputFocused",
		)) assertTrue(text(file).contains(owner) && text(file).contains("textInputFocused"), "$file reports $owner")

		assertTrue(text("src/main/kotlin/features/inventory/InventorySearch.kt").contains("InventoryKeyClose.registerTypingInput({ query != null }, ::onKey)"))
		assertTrue(text("src/main/kotlin/util/InventoryKeyClose.kt").contains("ItemListInputBridge.itemListHasFocus()") && text("src/main/kotlin/util/InventoryKeyClose.kt").contains("|| itemListFocus()"))

		for (f in listOf("features/auction/SignSearchScreen.kt", "features/storage/StorageSearch.kt",
			"features/inventory/buttons/IconPicker.kt", "features/items/ItemCustomizer.kt"))
			assertTrue(text("src/main/kotlin/$f").contains("setInitialFocus"), f)
	}
}
