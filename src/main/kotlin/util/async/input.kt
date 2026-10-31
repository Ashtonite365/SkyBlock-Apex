// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.async

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.component.CenterComponent
import io.github.notenoughupdates.moulconfig.gui.component.ColumnComponent
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import net.minecraft.client.gui.screens.Screen
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.ScreenUtil

private object KeyWaiters {
	private class Waiter(val binding: SavedKeyBinding, val wake: () -> Unit)

	private val parked = ArrayList<Waiter>()

	init {
		HandledScreenKeyPressedEvent.subscribe("Input:resumeAfterInput") { pressed ->
			val woken = synchronized(parked) {
				val hits = parked.filter { pressed.matches(it.binding) }
				parked.removeAll(hits.toSet())
				hits
			}
			for (waiter in woken) waiter.wake()
		}
	}

	fun park(binding: SavedKeyBinding, wake: () -> Unit): () -> Unit {
		val waiter = Waiter(binding, wake)
		synchronized(parked) { parked.add(waiter) }
		return { synchronized(parked) { parked.remove(waiter) } }
	}
}

suspend fun waitForInput(keybind: SavedKeyBinding): Unit = suspendCancellableCoroutine { continuation ->
	val cancel = KeyWaiters.park(keybind) { continuation.resume(Unit) }
	continuation.invokeOnCancellation { cancel() }
}

fun createPromptScreenGuiComponent(suggestion: String, prompt: String, action: Runnable): Pair<GuiContext, GetSetter<String>> {
	val typed = GetSetter.floating(suggestion)
	val field = TextFieldComponent(typed, 120)
	val button = ApexButton(TextComponent(prompt), action = action)
	return GuiContext(CenterComponent(PanelComponent(ColumnComponent(field, button)))) to typed
}

suspend fun waitForTextInput(suggestion: String, prompt: String): String = suspendCancellableCoroutine { continuation ->
	var shown: Screen? = null
	var field: GetSetter<String>? = null
	val finish = {
		if (Client.screen === shown) Client.screen = null
		continuation.resume(field!!.get())
	}
	val (gui, typed) = createPromptScreenGuiComponent(suggestion, prompt, finish)
	field = typed
	val screen = GuiXml.wrapScreen(gui, null, afterClose = finish)
	shown = screen
	ScreenUtil.setScreenLater(screen)
	continuation.invokeOnCancellation { finish() }
}
