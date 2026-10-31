// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.ashtonite365.apex.gui.config.AllConfigsGui

class ApexModMenuPlugin : ModMenuApi {
	private val settings = ConfigScreenFactory { parent -> AllConfigsGui.makeScreen(parent = parent) }
	override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = settings
}
