/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): tooltip background/frame per SkyBlock rarity, plus a safe fallback for
 * tooltip_style sprites that are not in the GUI atlas (e.g. hypixel_skyblock:* while Hypixel's pack is skipped,
 * which rendered as the purple-black missing texture). Original implementation.
 */
package dev.ashtonite365.apex.features.inventory

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.StylePack
import dev.ashtonite365.apex.util.skyblock.Rarity

object RarityTooltips {
	@Config
	object TConfig : ManagedConfig("rarity-tooltips", Category.INVENTORY) {
		val enabled by toggle("enabled") { true }
		val overrideHypixel by toggle("override-hypixel") { true }
	}

	fun styleFor(rarity: Rarity): Identifier = Apex.identifier("rarity/" + rarity.name.lowercase())

	fun backgroundSprite(style: Identifier): Identifier = style.withPath { "tooltip/${it}_background" }
	fun frameSprite(style: Identifier): Identifier = style.withPath { "tooltip/${it}_frame" }

	fun resolve(style: Identifier?, rarity: Rarity?, enabled: Boolean, overrideHypixel: Boolean, available: (Identifier) -> Boolean): Identifier? {
		if (style != null && style.namespace == Apex.MOD_ID && style.path.startsWith("rarity/")) return style
		if (enabled && rarity != null && rarity != Rarity.UNKNOWN && (style == null || overrideHypixel || !available(backgroundSprite(style)))) {
			val ours = styleFor(rarity)
			if (available(backgroundSprite(ours)) && available(frameSprite(ours))) return ours
		}
		if (style != null && !(available(backgroundSprite(style)) && available(frameSprite(style)))) return null
		return style
	}

	@JvmStatic
	fun resolveForLines(style: Identifier?, lines: List<Component>?): Identifier? = runCatching {
		val rarity = if (TConfig.enabled && lines != null) Rarity.fromLore(lines) else null
		resolve(style, rarity, TConfig.enabled, TConfig.overrideHypixel, StylePack::isAvailable)
	}.getOrDefault(style)
}
