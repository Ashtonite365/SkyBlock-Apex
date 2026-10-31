// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.settings.LayoutItem
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.Placement
import dev.ashtonite365.apex.settings.SectionPage
import dev.ashtonite365.apex.settings.SettingsLayout
import dev.ashtonite365.apex.settings.SettingsPlacement
import dev.ashtonite365.apex.settings.Subsection
import dev.ashtonite365.apex.util.data.ManagedConfig

object SettingsTabs {
	const val CUSTOM_MENU_PREFIX = SettingsPlacement.CUSTOM_MENU_PREFIX

	val map: MigrationMap by lazy { MigrationMap.bundled() }
	private val siblings by lazy { SettingsPlacement.siblings(map) }

	fun optionKey(option: ManagedOption<*>) = "${option.element.name}.${option.propertyName}"

	fun placementOf(option: ManagedOption<*>): Placement =
		SettingsPlacement.place(option.element.name, option.propertyName, option.element.category.name, map) { siblings[it] }

	fun mainSectionOf(option: ManagedOption<*>): MainSection = placementOf(option).path.main

	fun mainSectionOf(config: ManagedConfig): MainSection =
		config.sortedOptions.firstOrNull()?.let(::mainSectionOf)
			?: SettingsPlacement.place(config.name, "", config.category.name, map) { siblings[it] }.path.main

	fun items(configs: List<ManagedConfig>): List<LayoutItem<ManagedOption<*>>> = configs.flatMap { c ->

		c.sortedOptions.filter(FeatureRisk::visibleInMenus).map { LayoutItem(c.name, it.propertyName, c.category.name, it as ManagedOption<*>) }
	}

	fun pages(configs: List<ManagedConfig> = ManagedConfig.everyHolder.getAll()): List<SectionPage<ManagedOption<*>>> =
		SettingsLayout.pages(items(configs), map)

	private fun translated(key: String): Component? =
		if (Language.getInstance().has(key)) Component.translatable(key) else null

	fun label(main: MainSection): Component = translated(main.translationKey) ?: Component.literal(SettingsLayout.pretty(main.id.replace('_', '-')))

	fun label(page: SectionPage<*>): Component =
		if (page.group.isEmpty()) label(page.main)
		else translated(page.translationKey) ?: translated(page.fallbackTranslationKey) ?: Component.literal(SettingsLayout.pretty(page.group))

	fun label(sub: Subsection): Component =
		SettingsLayout.labelKeys(sub).firstNotNullOfOrNull(::translated) ?: Component.literal(SettingsLayout.pretty(sub.id))

	fun configsIn(main: MainSection, configs: List<ManagedConfig> = ManagedConfig.everyHolder.getAll()) =
		configs.filter { c -> FeatureRisk.visibleOptions(c).any { mainSectionOf(it) == main } || (c.sortedOptions.isEmpty() && mainSectionOf(c) == main) }
}
