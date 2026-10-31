// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

@Config
object MenuSections : ManagedConfig("custom-menu-sections", Category.CUSTOM_MENUS) {
	data class Section(val group: String, val label: String) {
		val switchLabel get() = "$label Apex Menus"
	}

	val SECTIONS = listOf(
		Section("general", "General"), Section("auction", "Auction House"), Section("bank", "Bank"), Section("bazaar", "Bazaar"),
		Section("bestiary", "Bestiary"), Section("collections", "Collections"), Section("crafting", "Crafting"),
		Section("enchant", "Enchanting"), Section("events", "Events"), Section("garden", "Garden"), Section("minions", "Minions"),
		Section("museum", "Museum"), Section("pets-wardrobe", "Pets and Wardrobe"), Section("quests", "Quests"),
		Section("recipe-book", "Recipe Book"), Section("skills", "Skills and Leveling"), Section("skyblock", "SkyBlock"),
	)

	val REUSED = mapOf("bazaar" to "bz-menu.overlay")

	private val switches = SECTIONS.filter { it.group !in REUSED }.associate { it.group to toggle(it.group) { true } }

	@Suppress("UNCHECKED_CAST")
	fun isOn(group: String): Boolean = when (group) {
		"bazaar" -> (dev.ashtonite365.apex.features.apexbzmenu.BzMenu.TConfig.allOptions["overlay"] as? dev.ashtonite365.apex.gui.config.ManagedOption<Boolean>)
			?.let { it._actualValue ?: it.default() } ?: true
		else -> switches[group]?.let { it._actualValue ?: it.default() } ?: true
	}

	@Suppress("UNCHECKED_CAST")
	fun set(group: String, on: Boolean) {
		val o = if (group == "bazaar") dev.ashtonite365.apex.features.apexbzmenu.BzMenu.TConfig.allOptions["overlay"] as dev.ashtonite365.apex.gui.config.ManagedOption<Boolean>
		else switches[group] ?: return
		o.set(on)
	}

	private val groupByHolder: Map<String, String> by lazy { groups(MigrationMap.bundled()) }

	fun groups(map: MigrationMap): Map<String, String> = map.entries
		.filter { it.newPath.main == MainSection.CUSTOM_MENUS && it.group.isNotEmpty() && it.oldConfig != name }
		.groupBy { it.oldConfig }.mapValues { (_, rows) -> rows.first().group }

	fun sectionOf(settingsName: String): String? = groupByHolder[settingsName]

	fun allows(menu: CustomMenu): Boolean = menu.section?.let(::isOn) ?: true
}
