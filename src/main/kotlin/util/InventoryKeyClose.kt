// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.gui.component.ColorSelectComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.Event
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.customgui.HasCustomGui

interface TextInputFocus {
	val textInputFocused: Boolean
}

interface UserCloseHook {

	fun interceptUserClose(): Boolean = false

	fun beforeUserClose() {}
}

object InventoryKeyClose {

	val LATE_PHASE: Identifier = Identifier.fromNamespaceAndPath("apex", "inventory_key_close")

	@JvmStatic
	var inventoryKey: () -> KeyMapping? = { runCatching { Minecraft.getInstance()?.options?.keyInventory }.getOrNull() }

	@JvmStatic
	var itemListFocus: () -> Boolean = { runCatching { ItemListInputBridge.itemListHasFocus() }.getOrDefault(false) }

	private val probes = mutableListOf<(Screen) -> Boolean>()
	fun registerFocusProbe(probe: (Screen) -> Boolean) { probes += probe }

	private class TypingInput(val focused: (Screen) -> Boolean, val onKey: (HandledScreenKeyPressedEvent) -> Unit)
	private val typingInputs = mutableListOf<TypingInput>()
	fun registerTypingInput(focused: (Screen) -> Boolean, onKey: (HandledScreenKeyPressedEvent) -> Unit) {
		typingInputs += TypingInput(focused, onKey)
	}

	fun isInventoryKey(key: KeyEvent): Boolean = runCatching { inventoryKey()?.matches(key) == true }.getOrDefault(false)

	fun isInventoryButton(click: MouseButtonEvent): Boolean =
		click.button() != InputConstants.MOUSE_BUTTON_LEFT && click.button() != InputConstants.MOUSE_BUTTON_RIGHT &&
			runCatching { inventoryKey()?.matchesMouse(click) == true }.getOrDefault(false)

	fun shouldClose(inventoryKey: Boolean, textFocused: Boolean): Boolean = inventoryKey && !textFocused

	fun widgetTextFocused(w: GuiEventListener?, depth: Int = 0): Boolean {
		if (w == null || depth > 16) return false
		if (w is TextInputFocus && w.textInputFocused) return true
		return when (w) {
			is EditBox -> w.canConsumeInput()
			is MultiLineEditBox -> w.isFocused && w.visible
			is ContainerEventHandler -> w.focused.let { it !== w && widgetTextFocused(it, depth + 1) }
			else -> false
		}
	}

	fun moulConfigFocused(screen: Screen): Boolean =
		(screen as? MoulConfigScreenComponent)?.guiContext?.focusedElement != null || settingsEditorTyping(screen)

	fun moulComponentTyping(c: GuiComponent?): Boolean = when (c) {
		null -> false
		is TextInputFocus -> c.textInputFocused
		is TextFieldComponent, is ColorSelectComponent -> true
		else -> false
	}

	private val editorContextField by lazy {
		runCatching { MoulConfigEditor::class.java.getDeclaredField("guiContext").apply { isAccessible = true } }.getOrNull()
	}

	fun settingsEditorContext(editor: MoulConfigEditor<*>): GuiContext? =
		runCatching { editorContextField?.get(editor) as? GuiContext }.getOrNull()

	fun settingsEditorTyping(screen: Screen): Boolean {
		val root = runCatching { (screen as? MoulConfigScreenComponent)?.guiContext?.root }.getOrNull() ?: return false
		val editor = (root as? GuiElementComponent)?.element as? MoulConfigEditor<*> ?: return false
		val ctx = settingsEditorContext(editor) ?: return true
		return moulComponentTyping(ctx.focusedElement)
	}

	fun textInputFocused(screen: Screen): Boolean {
		val gui = (screen as? HasCustomGui)?.getCustomGui_Apex()
		return (screen is TextInputFocus && screen.textInputFocused)
			|| (gui?.textInputFocused == true)
			|| widgetTextFocused(screen.focused)
			|| moulConfigFocused(screen)
			|| itemListFocus()
			|| probes.any { runCatching { it(screen) }.getOrDefault(true) }
			|| typingInputs.any { runCatching { it.focused(screen) }.getOrDefault(true) }
	}

	@JvmStatic
	fun publishScreenKey(screen: Screen, event: HandledScreenKeyPressedEvent): Boolean {
		val key = event.input as? GenericInputAction.KeyboardInput
		if (key != null && key.keyCode != InputConstants.KEY_ESCAPE) {
			val owner = typingInputs.firstOrNull { runCatching { it.focused(screen) }.getOrDefault(false) }
			if (owner != null) {
				Faults.attempt("Typing input key") { owner.onKey(event) }
				return event.cancelled
			}
			if (runCatching { textInputFocused(screen) }.getOrDefault(false)) return false
		}
		return HandledScreenKeyPressedEvent.publish(event).cancelled
	}

	fun isApexScreen(screen: Screen): Boolean =
		(screen as? HasCustomGui)?.getCustomGui_Apex() != null || isApexScreenClass(screen.javaClass)

	fun isApexScreenClass(cls: Class<*>): Boolean = cls.name.startsWith("dev.ashtonite365.apex.")

	fun close(screen: Screen) {
		val hook = screen as? UserCloseHook
		if (hook?.interceptUserClose() == true) return
		hook?.beforeUserClose()
		screen.onClose()
	}

	fun handleScreenKey(screen: Screen, key: KeyEvent): Boolean {
		if (!isApexScreen(screen) || !shouldClose(isInventoryKey(key), textInputFocused(screen))) return false

		val claimed = runCatching {
			HandledScreenKeyPressedEvent.publish(HandledScreenKeyPressedEvent(screen, GenericInputAction.of(key), InputModifiers.of(key))).cancelled
		}.getOrDefault(false)
		if (!claimed) close(screen)
		return true
	}

	fun handleScreenClick(screen: Screen, click: MouseButtonEvent): Boolean {
		if (!isApexScreen(screen) || !shouldClose(isInventoryButton(click), textInputFocused(screen))) return false
		close(screen)
		return true
	}

	fun handleMenuKey(screen: Screen, key: KeyEvent, viewTextFocused: Boolean, interceptClose: () -> Boolean, beforeClose: () -> Unit): Boolean =
		menuDecision(screen, isInventoryKey(key), viewTextFocused, interceptClose, beforeClose)

	fun handleMenuClick(screen: Screen, click: MouseButtonEvent, viewTextFocused: Boolean, interceptClose: () -> Boolean, beforeClose: () -> Unit): Boolean =
		menuDecision(screen, isInventoryButton(click), viewTextFocused, interceptClose, beforeClose)

	private fun menuDecision(screen: Screen, isKey: Boolean, viewTextFocused: Boolean, interceptClose: () -> Boolean, beforeClose: () -> Unit): Boolean {
		if (!isKey) return false
		if (!shouldClose(true, viewTextFocused || textInputFocused(screen))) return false
		if (interceptClose()) return true
		beforeClose()
		close(screen)
		return true
	}

	fun install(screen: Screen) {
		val keys = ScreenKeyboardEvents.allowKeyPress(screen)
		keys.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
		keys.register(LATE_PHASE, ScreenKeyboardEvents.AllowKeyPress { s, key ->
			!Faults.attempt("Inventory key close") { handleScreenKey(s, key) }.orElse { false }
		})
		val clicks = ScreenMouseEvents.allowMouseClick(screen)
		clicks.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
		clicks.register(LATE_PHASE, ScreenMouseEvents.AllowMouseClick { s, click ->

			val handle = { !Faults.attempt("Inventory key close") { handleScreenClick(s, click) }.orElse { false } }
			if (isApexScreen(s)) dev.ashtonite365.apex.features.menus.MenuPress.press(handle) else handle()
		})
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {

		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ -> install(screen) })
	}
}
