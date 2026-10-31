package dev.ashtonite365.apex.keybindings

import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.util.TestUtil

object ApexKeyBindings {

	val cats = mutableMapOf<MainSection, KeyMapping.Category>()

	val keyBindings: MutableMap<KeyMapping, ManagedOption<SavedKeyBinding>> = mutableMapOf()

	fun groupFor(section: MainSection): KeyMapping.Category =
		cats.getOrPut(section) { KeyMapping.Category.register(Apex.identifier(section.id)) }

	fun sectionFor(option: ManagedOption<*>): MainSection = SettingsTabs.mainSectionOf(option)

	fun registerKeyBinding(name: String, config: ManagedOption<SavedKeyBinding>) {
		val category = groupFor(sectionFor(config))
		val mapping = KeyMapping(name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.value, category)

		if (!TestUtil.isInTest) KeyMappingHelper.registerKeyMapping(mapping)
		keyBindings[mapping] = config
	}
}
