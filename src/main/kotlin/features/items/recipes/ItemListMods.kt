// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes

import net.fabricmc.loader.api.FabricLoader

enum class ItemListMods(val modId: String) {
	JEI("jei"),
	REI("roughlyenoughitems"),
	EMI("emi"),
	;

	fun installed(): Boolean = runCatching { FabricLoader.getInstance().isModLoaded(modId) }.getOrDefault(false)

	fun installedVersion(): String? = runCatching {
		FabricLoader.getInstance().getModContainer(modId).map { it.metadata.version.friendlyString }.orElse(null)
	}.getOrNull()

	fun usable(): Boolean = installedVersion()?.let { supports(it) } ?: false

	fun supports(version: String): Boolean = supportedVersion(this, version)

	companion object {

		fun anyUsable(): Boolean = entries.any { it.usable() }

		fun numericPrefix(version: String): List<Int> =
			version.trim().takeWhile { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }

		fun supportedVersion(mod: ItemListMods, version: String): Boolean {
			val v = numericPrefix(version)
			return when (mod) {
				JEI -> true
				REI -> (v.getOrNull(0) ?: 0) == 26
				EMI -> v.getOrNull(0) == 1 && (v.getOrNull(1) ?: -1) >= 1
			}
		}
	}
}
