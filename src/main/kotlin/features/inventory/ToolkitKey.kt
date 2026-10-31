// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hypixelIdText
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.tr

object ToolkitKey {
	@Config
	object TConfig : ManagedConfig("toolkit-key", Category.INVENTORY) {
		val key by keyBindingWithDefaultUnbound("key")
	}

	sealed interface Step {
		data class Select(val slot: Int) : Step
		data object Use : Step
		data object NoToolkit : Step
		data object Nothing : Step
	}

	interface Actions {
		fun select(slot: Int)
		fun use()
		fun noToolkit()
	}

	object Logic {
		const val HOTBAR_SLOTS = 9

		val TOOLKIT_IDS = setOf("HUNTING_TOOLKIT", "FARMING_TOOLKIT")

		val NOT_TOOLKITS = setOf("HARMONIOUS_SURGERY_TOOLKIT")

		const val TOOL_KIT_KEY = "tool_kit"

		fun isToolkit(id: String?, hasToolKitData: Boolean, name: String?): Boolean {
			if (id != null && id in NOT_TOOLKITS) return false
			if (hasToolKitData) return true
			if (id != null && (id in TOOLKIT_IDS || id.endsWith("_TOOLKIT"))) return true
			val trimmed = name?.trim() ?: return false
			return trimmed.endsWith(" Toolkit") && trimmed != "Harmonious Surgery Toolkit"
		}

		fun nextToolkitSlot(current: Int, toolkit: List<Boolean>): Int? {
			val size = toolkit.size
			if (size == 0) return null
			for (step in 1..size) {
				val slot = Math.floorMod(current + step, size)
				if (toolkit[slot]) return slot
			}
			return null
		}

		fun step(isRepeat: Boolean, current: Int, toolkit: List<Boolean>): Step = when {
			isRepeat -> Step.Nothing
			toolkit.getOrElse(current) { false } -> Step.Use
			else -> nextToolkitSlot(current, toolkit)?.let { Step.Select(it) } ?: Step.NoToolkit
		}
	}

	fun press(isRepeat: Boolean, current: Int, toolkit: List<Boolean>, actions: Actions): Step {
		val step = Logic.step(isRepeat, current, toolkit)
		when (step) {
			is Step.Select -> actions.select(step.slot)
			Step.Use -> actions.use()
			Step.NoToolkit -> actions.noToolkit()
			Step.Nothing -> {}
		}
		return step
	}

	fun isToolkit(stack: ItemStack): Boolean {
		if (stack.isEmpty) return false
		val view = stack.view()
		return Logic.isToolkit(view.hypixelIdText, view.customDataTag.contains(Logic.TOOL_KIT_KEY), stack.hoverName.bareText)
	}

	private object GameActions : Actions {
		override fun select(slot: Int) {
			Client.player?.inventory?.selectedSlot = slot
		}

		override fun use() {
			val player = Client.player ?: return
			if (player.isUsingItem) return
			Client.game.startUseItem()
		}

		override fun noToolkit() {
			Client.showChat(tr("apex.toolkit-key.missing", "§cNo toolkit in your hotbar."))
		}
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (Client.screen != null || !event.matches(TConfig.key)) return
		event.cancel()
		val player = Client.player ?: return
		if (player.isSpectator) return
		val inventory = player.inventory
		val toolkit = List(Logic.HOTBAR_SLOTS) { isToolkit(inventory.getItem(it)) }
		press(isRepeat(event), inventory.selectedSlot, toolkit, GameActions)
	}

	private fun isRepeat(event: WorldKeyboardEvent): Boolean =
		event.action is GenericInputAction.KeyboardInput && HandledScreenKeyPressedEvent.internalPollAction() == InputConstants.REPEAT
}
