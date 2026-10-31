// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.iris

import net.fabricmc.loader.api.FabricLoader
import dev.ashtonite365.apex.util.compatloader.CompatEntry
import dev.ashtonite365.apex.util.compatloader.CompatSwitch

@CompatEntry
object Compat : CompatSwitch {
	private const val REQUIRED_MOD = "iris"
	override fun enabledHere() = FabricLoader.getInstance().isModLoaded(REQUIRED_MOD)
}
