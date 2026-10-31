// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.pets

import dev.ashtonite365.apex.features.inventory.PetFeatures.TConfig
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.getLegacyFormatString
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

object PetNametag {

	private val TAG = Regex("^(§8\\[§7Lv(\\d+)§8]) (§.)([\\w\\s]+)(§. ✦)?$")
	private val MAX_LEVELS = setOf(100, 200)

	fun rewrite(legacy: String, hideLevel: Boolean, hideMax: Boolean): String? {
		val parts = TAG.matchEntire(legacy)?.groupValues ?: return null
		val level = parts[2].toInt()
		val strip = hideLevel || hideMax && level in MAX_LEVELS
		return if (strip) parts[3] + parts[4] + parts[5] else null
	}

	@JvmStatic
	fun replace(entity: Entity, name: Component?): Component? {
		if (name == null || entity !is ArmorStand) return null
		val hideLevel = TConfig.nametagHideLevel
		val hideMax = TConfig.nametagHideMaxLevel
		if (!hideLevel && !hideMax || !SkyBlockSession.inSkyBlock) return null
		val legacy = name.getLegacyFormatString(trimmed = true).replace("§r", "")
		return rewrite(legacy, hideLevel, hideMax)?.let { Component.literal(it) }
	}
}
