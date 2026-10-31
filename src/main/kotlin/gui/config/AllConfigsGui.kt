package dev.ashtonite365.apex.gui.config

import net.minecraft.client.gui.screens.Screen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil.setScreenLater
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object AllConfigsGui {

	@Config
	object ConfigConfig : ManagedConfig("configconfig", Category.GENERAL) {

		val enableYacl: Boolean by toggle("enable-yacl") { false }
		val enableMoulConfig: Boolean by toggle("enable-moulconfig") { true }
		val enableWideMC: Boolean by toggle("wide-moulconfig") { false }

		val modules: Unit by button("modules") { setScreenLater(ModulesScreen(Client.screen)) }
	}

	val RETIRED_KEYS: Set<String> = setOf("configconfig.enable-yacl", "configconfig.enable-moulconfig", "configconfig.wide-moulconfig")

	private fun makeMenuScreen(search: String?, parent: Screen?, focus: ManagedConfig? = null): Screen =
		StyledSettingsScreen(search, parent, focus)

	fun makeScreen(search: String? = null, parent: Screen? = null): Screen =
		RiskDisclaimer.wrapApexMenu { makeMenuScreen(search, parent) }

	fun makeScreenFor(config: ManagedConfig, parent: Screen? = null): Screen =
		RiskDisclaimer.wrapApexMenu { makeMenuScreen(null, parent, config) }

	fun showAllGuis() {
		setScreenLater(makeScreen())
	}

	@Subscribe
	fun registerCommands(event: CommandEvent.SubCommand) {
		event.addSubcommand("search", ManagedConfig.Category.GENERAL) {
			onArgument("search", RestArgumentType) { text ->
				onRun { setScreenLater(makeScreen(this[text])) }
			}
		}
	}
}
