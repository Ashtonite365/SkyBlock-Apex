package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.TestUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder

object RiskDisclaimer {
	@Serializable
	data class Data(var accepted: Boolean = false, var menuShown: Boolean = false)

	const val MENU_TEXT = "some of these modifications are potentially bannable or moderator dependent, so use at your own risk."

	fun onApexMenuOpen(data: Data, save: () -> Unit, show: (text: String, ok: () -> Unit) -> Unit): Boolean {
		if (data.menuShown) return false
		show(MENU_TEXT) {
			data.menuShown = true
			data.accepted = true
			save()
		}
		return true
	}

	fun wrapApexMenu(then: () -> net.minecraft.client.gui.screens.Screen): net.minecraft.client.gui.screens.Screen {
		var out: net.minecraft.client.gui.screens.Screen? = null
		val data = runCatching { DConfig.data }.getOrNull() ?: return then()
		val shown = onApexMenuOpen(data, { DConfig.markDirty() }) { text, ok ->
			out = net.minecraft.client.gui.screens.AlertScreen(
				{ ok(); ScreenUtil.setScreenLater(then()) },
				Component.literal("SkyBlock Apex").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.literal(text),
				Component.literal("OK"),
				false,
			)
		}
		return if (shown) out!! else then()
	}

	@Config
	object DConfig : DataHolder<Data>(serializer(), "risk-disclaimer", ::Data)

	const val WARNING_KEY = "apex.config.risky.warning"
	val warningText: Component
		get() = Component.translatable(WARNING_KEY).withStyle(ChatFormatting.RED)

	val isAccepted: Boolean
		get() = runCatching { DConfig.data.accepted || DConfig.data.menuShown }.getOrDefault(false)

	fun requestEnable(option: ManagedOption<Boolean>): Boolean {
		if (isAccepted || TestUtil.isInTest) return true
		val parent = Client.screen
		ScreenUtil.setScreenLater(
			ConfirmScreen(
				{ accepted ->
					if (accepted) {
						DConfig.data.accepted = true
						DConfig.markDirty()
						option.value = true
						option.element.markDirty()
					}
					Client.screen = parent
				},
				Component.translatable("apex.risk-disclaimer.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("apex.risk-disclaimer.message", option.labelText),
				Component.translatable("apex.risk-disclaimer.accept"),
				Component.translatable("apex.risk-disclaimer.decline"),
			)
		)
		return false
	}
}
