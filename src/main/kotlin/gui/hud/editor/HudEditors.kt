// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.features.misc.Hud
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudMetaHandler
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.keybindings.ApexKeyBindings
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.util.TestUtil
import dev.ashtonite365.apex.util.data.LegacyDataMigration
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object HudEditors {

	const val OPEN_KEY_NAME = "key.apex.open-gui-editor"
	const val MOD_NAME = "SkyBlock Apex"

	private val configs: List<ManagedConfig> get() = ApexConfigLoader.allConfigs.filterIsInstance<ManagedConfig>()

	fun allHuds(from: List<ManagedConfig> = configs): List<HudMeta> =
		from.flatMap { c -> c.sortedOptions.mapNotNull { if (it.handler is HudMetaHandler) it.value as HudMeta else null } }

	val snapping = HudSnapping { Hud.TConfig.editorSnap }

	fun editor(returnTo: Screen?, huds: List<PlacedHud> = allHuds() + dev.ashtonite365.apex.features.timers.TimerBoards.ownHudElements()): HudEditorScreen = HudEditorScreen(returnTo, huds, snapping) { configs.forEach { it.markDirty() } }

	fun searchOptions(from: List<ManagedConfig> = configs): List<SearchableOption> = from.flatMap { config ->
		FeatureRisk.visibleOptions(config).map { option -> SearchableOption(option.labelText, open = { parent -> config.getConfigEditor(parent) }) }
	}

	fun optionSearch(returnTo: Screen?): OptionSearchScreen = OptionSearchScreen(returnTo, searchOptions(), MOD_NAME)

	private fun show(make: () -> Screen) { Minecraft.getInstance().let { mc -> mc.submit { mc.gui.setScreen(make()) } } }

	fun install() {
		val key = KeyMapping(OPEN_KEY_NAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, ApexKeyBindings.groupFor(MainSection.HUD))
		if (TestUtil.isInTest) return

		runCatching { LegacyDataMigration.migrateOptionsFile(FabricLoader.getInstance().gameDir) }
			.onFailure { Apex.logger.warn("Could not carry the HUD editor key binding over in options.txt", it) }
		KeyMappingHelper.registerKeyMapping(key)
		ClientTickEvents.END_CLIENT_TICK.register { while (key.consumeClick()) show { editor(null) } }
	}

	@Subscribe
	fun registerCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.HUD, "hudeditor", "Open the HUD editor with every HUD element (also Right Shift)") {
			onRun { show { editor(null) } }
		}
		event.register(ManagedConfig.Category.GENERAL, "options", "Search every config option by name") {
			onRun { show { optionSearch(null) } }
		}
	}
}
