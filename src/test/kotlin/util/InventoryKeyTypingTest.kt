// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import java.io.File
import com.mojang.blaze3d.platform.InputConstants
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.features.menus.MenuBrowseScreen
import dev.ashtonite365.apex.gui.EmptyComponent
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.InventoryKeyClose
import dev.ashtonite365.apex.util.TextInputFocus
import dev.ashtonite365.apex.test.util.InventoryKeyCloseTest.Companion.alloc
import dev.ashtonite365.apex.test.util.InventoryKeyCloseTest.Companion.key
import dev.ashtonite365.apex.test.util.InventoryKeyCloseTest.Companion.set

class FakeSettingsScreen : MoulConfigScreenComponent(Component.empty(), alloc(GuiContext::class.java), null) {
	var closes = 0

	var keys = mutableListOf<Int>()
	var chars = StringBuilder()
	override fun onClose() { closes++ }
	override fun keyPressed(input: KeyEvent): Boolean { keys += input.input(); return true }
	override fun charTyped(input: CharacterEvent): Boolean { chars.appendCodePoint(input.codepoint()); return true }
}

class FakeBrowseScreen : MenuBrowseScreen("t", FakeSearchView()) {
	var closes = 0
	override fun onClose() { closes++ }
}

class FakeKeyCapture : GuiComponent(), TextInputFocus {
	var editing = false
	override val textInputFocused get() = editing
	override fun getWidth() = 0
	override fun getHeight() = 0
	override fun render(context: io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext) {}
}

class InventoryKeyTypingTest {
	companion object {

		val published = mutableListOf<Int>()
		var spying = false

		var typedOpen = false
		val typed = StringBuilder()
		init {
			HandledScreenKeyPressedEvent.subscribe("test:spy") { e ->
				if (spying) (e.input as? GenericInputAction.KeyboardInput)?.let { published += it.keyCode }
			}
			InventoryKeyClose.registerTypingInput({ typedOpen }) { e ->
				(e.input as? GenericInputAction.KeyboardInput)?.let { typed.append(Char('a'.code + it.keyCode - InputConstants.KEY_A)) }
				e.cancel()
			}
		}
	}

	private val binding = KeyMapping("key.apex.test.inventory_typing", InputConstants.KEY_E, KeyMapping.Category.INVENTORY)
	private lateinit var saved: () -> KeyMapping?
	private lateinit var savedItemList: () -> Boolean

	@BeforeEach fun seam() {
		saved = InventoryKeyClose.inventoryKey; InventoryKeyClose.inventoryKey = { binding }
		savedItemList = InventoryKeyClose.itemListFocus; InventoryKeyClose.itemListFocus = { false }
		published.clear(); spying = true; typedOpen = false; typed.setLength(0)
	}
	@AfterEach fun restore() {
		InventoryKeyClose.inventoryKey = saved; InventoryKeyClose.itemListFocus = savedItemList
		spying = false; typedOpen = false
	}

	private fun press(s: Screen, code: Int, ch: Char) {
		if (InventoryKeyClose.handleScreenKey(s, key(code))) return
		s.keyPressed(key(code))
		s.charTyped(CharacterEvent(ch.code))
	}

	private fun settings(): Pair<FakeSettingsScreen, GuiContext> {
		val s = alloc(FakeSettingsScreen::class.java)
		val editor = alloc(MoulConfigEditor::class.java)
		val editorCtx = alloc(GuiContext::class.java)
		set(editor, MoulConfigEditor::class.java, "guiContext", editorCtx)
		val root = alloc(GuiElementComponent::class.java)
		set(root, GuiElementComponent::class.java, "element", editor)
		val screenCtx = alloc(GuiContext::class.java)
		set(screenCtx, GuiContext::class.java, "root", root)
		set(s, MoulConfigScreenComponent::class.java, "guiContext", screenCtx)
		s.keys = mutableListOf(); s.chars = StringBuilder()
		return s to editorCtx
	}
	private fun focus(ctx: GuiContext, c: GuiComponent?) = set(ctx, GuiContext::class.java, "focusedElement", c)

	@Test fun settingsSearchFocusedETypesAndDoesNotClose() {
		val (s, editorCtx) = settings()
		focus(editorCtx, alloc(TextFieldComponent::class.java))

		assertNull(s.guiContext.focusedElement)
		assertTrue(InventoryKeyClose.settingsEditorTyping(s))
		assertTrue(InventoryKeyClose.textInputFocused(s))
		for (ch in "speed") press(s, InputConstants.KEY_A + (ch - 'a'), ch)
		assertEquals(0, s.closes, "typing never closes the settings")
		assertEquals("speed", s.chars.toString(), "every letter, E included, reaches the search box")
		assertTrue(InputConstants.KEY_E in s.keys)
	}

	@Test fun settingsUnfocusedEClosesAsBefore() {
		val (s, editorCtx) = settings()
		focus(editorCtx, null)
		assertFalse(InventoryKeyClose.textInputFocused(s))
		press(s, InputConstants.KEY_E, 'e')
		assertEquals(1, s.closes)
		assertEquals("", s.chars.toString(), "closed: nothing typed")
	}

	@Test fun settingsClickedButtonIsNotTyping() {

		val (s, editorCtx) = settings()
		focus(editorCtx, alloc(ApexButton::class.java))
		assertFalse(InventoryKeyClose.textInputFocused(s))
		press(s, InputConstants.KEY_E, 'e')
		assertEquals(1, s.closes)
	}

	@Test fun settingsKeybindCaptureTakesE() {
		val (s, editorCtx) = settings()
		val capture = alloc(FakeKeyCapture::class.java).also { it.editing = true }
		focus(editorCtx, capture)
		press(s, InputConstants.KEY_E, 'e')
		assertEquals(0, s.closes, "waiting for a key: E is captured")
		capture.editing = false
		press(s, InputConstants.KEY_E, 'e')
		assertEquals(1, s.closes)

		val button = Class.forName("dev.ashtonite365.apex.gui.config.KeyBindingStateManager\$createButton\$1")
		assertTrue(TextInputFocus::class.java.isAssignableFrom(button))
	}

	@Test fun settingsReboundInventoryKeyAlsoTypes() {
		val (s, editorCtx) = settings()
		binding.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_I))
		focus(editorCtx, alloc(TextFieldComponent::class.java))
		press(s, InputConstants.KEY_I, 'i')
		assertEquals(0, s.closes); assertEquals("i", s.chars.toString())
		focus(editorCtx, null)
		press(s, InputConstants.KEY_I, 'i')
		assertEquals(1, s.closes)
	}

	@Test fun settingsEditorContextIsReadable() {

		val editor = alloc(MoulConfigEditor::class.java)
		val ctx = alloc(GuiContext::class.java)
		set(editor, MoulConfigEditor::class.java, "guiContext", ctx)
		assertSame(ctx, InventoryKeyClose.settingsEditorContext(editor))
	}

	@Test fun moulComponentTypingRule() {
		assertFalse(InventoryKeyClose.moulComponentTyping(null))
		assertTrue(InventoryKeyClose.moulComponentTyping(alloc(TextFieldComponent::class.java)))
		assertFalse(InventoryKeyClose.moulComponentTyping(alloc(EmptyComponent::class.java)))
		assertFalse(InventoryKeyClose.moulComponentTyping(alloc(ApexButton::class.java)))
	}

	@Test fun otherMoulConfigScreensUnchanged() {

		val s = alloc(FakeMoulScreen::class.java)
		val ctx = alloc(GuiContext::class.java)
		set(ctx, GuiContext::class.java, "root", alloc(EmptyComponent::class.java))
		set(s, MoulConfigScreenComponent::class.java, "guiContext", ctx)
		assertFalse(InventoryKeyClose.settingsEditorTyping(s))
		assertFalse(InventoryKeyClose.textInputFocused(s))
		set(ctx, GuiContext::class.java, "focusedElement", alloc(TextFieldComponent::class.java))
		assertTrue(InventoryKeyClose.textInputFocused(s))
	}

	@Test fun customMenuSearchTypesE() {
		val s = alloc(FakeBrowseScreen::class.java)
		val v = FakeSearchView().also { it.search.focused = true }
		set(s, MenuBrowseScreen::class.java, "view", v)
		for (ch in "berserk") press(s, InputConstants.KEY_A + (ch - 'a'), ch)
		assertEquals(0, s.closes)
		assertEquals("berserk", v.search.query)
		v.search.focused = false
		press(s, InputConstants.KEY_E, 'e')
		assertEquals(1, s.closes)
		assertEquals("berserk", v.search.query, "unfocused: nothing typed")
	}

	private fun ev(s: Screen, code: Int) = HandledScreenKeyPressedEvent(s, GenericInputAction.of(key(code)), InputModifiers.of(key(code)))

	@Test fun keyBindingsDoNotFireWhileTyping() {
		val s = alloc(FakeOwnInputScreen::class.java).also { it.typing = true }
		for (k in listOf(InputConstants.KEY_R, InputConstants.KEY_U, InputConstants.KEY_L, InputConstants.KEY_H, InputConstants.KEY_1))
			assertFalse(InventoryKeyClose.publishScreenKey(s, ev(s, k)))
		assertEquals(emptyList<Int>(), published, "typing: no key binding sees the key")

		InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_ESCAPE))
		assertEquals(listOf(InputConstants.KEY_ESCAPE), published)

		s.typing = false
		InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_R))
		assertEquals(listOf(InputConstants.KEY_ESCAPE, InputConstants.KEY_R), published)
	}

	@Test fun keyBindingsDoNotFireWhileSettingsSearchTyped() {
		val (s, editorCtx) = settings()
		focus(editorCtx, alloc(TextFieldComponent::class.java))
		InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_R))
		assertEquals(emptyList<Int>(), published)
	}

	@Test fun typedSearchGetsItsKeysAlone() {
		val s = alloc(FakeApexScreen::class.java)
		typedOpen = true
		assertTrue(InventoryKeyClose.textInputFocused(s), "an open typed search counts as focus for the close rule")
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertTrue(InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_E)), "consumed by the search")
		assertTrue(InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_R)))
		assertEquals("er", typed.toString())
		assertEquals(emptyList<Int>(), published, "no other key binding saw the letters")
		assertEquals(0, s.closes)
		typedOpen = false
		assertFalse(InventoryKeyClose.publishScreenKey(s, ev(s, InputConstants.KEY_R)))
		assertEquals(listOf(InputConstants.KEY_R), published)
	}

	private val projectRoot: File by lazy {
		generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { File(it, "settings.gradle.kts").isFile && File(it, "src/main/kotlin").isDirectory }
	}
	private fun text(rel: String) = File(projectRoot, rel).readText()

	@Test fun everyScreenKeyGoesThroughTheTypingGuard() {
		val mixin = text("src/main/java/dev/ashtonite365/apex/mixins/ScreenInputEvents.java")
		assertTrue(mixin.contains("var pressed = new HandledScreenKeyPressedEvent(apex\$screen(), GenericInputAction.of(key), InputModifiers.of(key));\n\t\tif (InventoryKeyClose.publishScreenKey(apex\$screen(), pressed))"))
		assertFalse(mixin.contains("HandledScreenKeyPressedEvent.Companion.publish(new HandledScreenKeyPressedEvent(\n\t\t\t(Screen) (Object) this,\n\t\t\tGenericInputAction.of(event)"))

		assertTrue(text("src/main/kotlin/features/items/wiki/ItemWiki.kt").contains("!dev.ashtonite365.apex.util.InventoryKeyClose.textInputFocused(screen)"))
		assertTrue(text("src/main/kotlin/features/items/recipes/ViewerTabs.kt").contains("InventoryKeyClose.textInputFocused(s)) return@AllowKeyPress true"))
		assertTrue(text("src/main/kotlin/features/inventory/InventorySearch.kt").contains("InventoryKeyClose.registerTypingInput({ query != null }, ::onKey)"))
	}
}
