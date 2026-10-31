// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object InventorySearch {
	@Config
	object TConfig : ManagedConfig("inventory-search", Category.CHAT) {
		val enabled by toggle("enabled") { true }
		val searchLore by toggle("search-lore") { true }
		val openSearch by keyBindingExact("open-search") { SavedKeyBinding.keyWithMods(InputConstants.KEY_F, InputModifiers.of(ctrl = true)) }
	}

	var query: String? = null
		private set

	@Subscribe
	fun onInit(event: dev.ashtonite365.apex.events.ClientInitEvent) {
		dev.ashtonite365.apex.util.InventoryKeyClose.registerTypingInput({ query != null }, ::onKey)
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		query = null
	}

	private fun charFor(keyCode: Int): Char? = when (keyCode) {
		in InputConstants.KEY_A..InputConstants.KEY_Z -> 'a' + (keyCode - InputConstants.KEY_A)
		in InputConstants.KEY_0..InputConstants.KEY_9 -> '0' + (keyCode - InputConstants.KEY_0)
		InputConstants.KEY_SPACE -> ' '
		InputConstants.KEY_MINUS -> '-'
		InputConstants.KEY_APOSTROPHE -> '\''
		InputConstants.KEY_PERIOD -> '.'
		else -> null
	}

	@Subscribe
	fun onKey(event: HandledScreenKeyPressedEvent) {
		if (!TConfig.enabled || event.screen !is AbstractContainerScreen<*>) return
		val current = query
		if (current == null) {
			if (event.matches(TConfig.openSearch)) {
				query = ""
				event.cancel()
			}
			return
		}
		val input = event.input as? GenericInputAction.KeyboardInput ?: return
		when (input.keyCode) {
			InputConstants.KEY_ESCAPE, InputConstants.KEY_RETURN -> query = null
			InputConstants.KEY_BACKSPACE -> query = current.dropLast(1)
			else -> {
				if (event.matches(TConfig.openSearch)) query = null
				else charFor(input.keyCode)?.let { if (current.length < 40) query = current + it }
			}
		}
		event.cancel()
	}

	fun matches(stack: net.minecraft.world.item.ItemStack, q: String): Boolean {
		if (stack.isEmpty) return false
		if (stack.hoverName.bareText.lowercase().contains(q)) return true
		return TConfig.searchLore && stack.view().loreAccordingToNbt.any { it.bareText.lowercase().contains(q) }
	}

	@Subscribe
	fun onSlotAfter(event: SlotRenderEvents.After) {
		val q = query?.trim()?.takeIf { it.isNotEmpty() } ?: return
		if (!matches(event.slot.item, q))
			event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, 0xC0101010.toInt())
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		val q = query ?: return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val acc = screen as AccessorHandledScreen
		val ctx = event.drawContext
		val label = tr("apex.inventory-search.prompt", "Search: ").string + q + "_"
		val w = maxOf(120, Client.font.width(label) + 8)
		val x = acc.x_Apex + acc.backgroundWidth_Apex / 2 - w / 2
		val y = acc.y_Apex - 16
		ctx.nextStratum()
		ctx.fill(x, y, x + w, y + 13, 0xE0000000.toInt())
		ctx.fill(x, y + 12, x + w, y + 13, 0xFFFFFF55.toInt())
		ctx.text(Client.font, label, x + 4, y + 3, 0xFFFFFFFF.toInt(), false)
	}
}
