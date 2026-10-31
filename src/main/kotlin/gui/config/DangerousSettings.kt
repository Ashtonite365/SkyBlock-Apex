// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object DangerousSettings {
	const val CONFIG = "dangerous"
	const val OPTION = "allow-borderline"
	const val TITLE = "Allow borderline bannable features"
	const val CONFIRM_ON = "Show and allow borderline bannable features? Using them is at your own risk."
	const val CONFIRM_OFF = "Hide and switch off the borderline bannable features? Their saved settings are kept."

	@Config
	object TConfig : ManagedConfig(CONFIG, Category.MISC) {
		val allowBorderline by toggle(OPTION) { false }

		override fun onChange(option: ManagedOption<*>) = listeners.forEach { it() }
	}

	val listeners = mutableListOf<() -> Unit>()

	val allowed: Boolean
		get() = runCatching { TConfig.allowBorderline }.getOrDefault(false)

	var confirmer: (target: Boolean, answer: (Boolean) -> Unit) -> Unit = { target, answer ->
		val parent = Client.screen
		ScreenUtil.setScreenLater(object : ConfirmScreen(
			{ yes ->
				Client.screen = parent
				answer(yes)

				if (yes && parent != null) ScreenUtil.setScreenLater(AllConfigsGui.makeScreen())
			},
			Component.literal(TITLE).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			Component.literal(if (target) CONFIRM_ON else CONFIRM_OFF),
			Component.literal("Yes"),
			Component.literal("No"),
		) {
			override fun onClose() { Client.screen = parent; answer(false) }
		})
	}

	private var confirmedTarget: Boolean? = null

	var save: (ManagedOption<Boolean>) -> Unit = { it.element.markDirty() }

	fun requestFlip(option: ManagedOption<Boolean>, target: Boolean): Boolean {
		if (confirmedTarget == target) { confirmedTarget = null; return true }
		confirmer(target) { yes ->
			if (!yes) return@confirmer
			confirmedTarget = target
			option.value = target
			save(option)
		}
		return false
	}

	fun isSwitch(o: ManagedOption<*>) = o.element.name == CONFIG && o.propertyName == OPTION
}
