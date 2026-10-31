package dev.ashtonite365.apex.compat.jei

import net.fabricmc.loader.api.FabricLoader
import dev.ashtonite365.apex.util.compatloader.CompatEntry
import dev.ashtonite365.apex.util.compatloader.CompatSwitch

@CompatEntry
object Compat : CompatSwitch {
	override fun enabledHere(): Boolean {
		return FabricLoader.getInstance().isModLoaded("jei")
	}
}
