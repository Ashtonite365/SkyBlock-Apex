// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object GardenLevelsParser {
	val TITLE = Regex("^(?i)(?:\\(\\d+/\\d+\\) )?Garden Levels$")

	fun parse(title: String, slots: List<SlotText>): SkillPageModel? {
		val t = MenuText.clean(title).replace(Regex("^\\(\\d+/\\d+\\) "), "")
		if (!TITLE.matches(MenuText.clean(title))) return null
		return SkillPageParser.parseTrack(t.removeSuffix(" Levels"), t, slots)
	}
}

@Config
object GardenLevelsSettings : MenuSettings("garden-levels", panels = listOf("rewards", "locked")) {
	val reset by button("reset") { resetToDefaults() }
}

object GardenLevelsMenu : CustomMenu {
	override val id = "garden-levels"
	override val displayName = "Garden Levels"
	override val title = GardenLevelsParser.TITLE
	override val settings get() = GardenLevelsSettings
	override val defaultSize = 440 to 300
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = GardenLevelsView(screen)
}

class GardenLevelsView(screen: AbstractContainerScreen<*>) : SkillPageView(screen) {
	override fun palette() = GardenLevelsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = GardenLevelsParser.parse(title, slots)
	override fun shows(panel: String) = GardenLevelsSettings.shows(panel)
}
