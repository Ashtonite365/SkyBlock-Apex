/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H3): what the client-side copy of Hypixel's SkyBlock pack may contain, and what a change of the
 * "Skip Hypixel Resource Pack" setting must do. Pure logic, unit tested.
 *
 * No jar bundles the pack any more (it used to ship as "Hypixel SkyBlock (built-in copy)"). Every build downloads
 * Hypixel's pack, verifies Hypixel's SHA-1 and caches it (HypixelPackDownload.kt); [serves] is the keep-list applied
 * when the cached zip is extracted, and DownloadedHypixelPack offers that extracted copy to the client pack list in the
 * same place the built-in copy had, so the skip toggles below behave exactly as before.
 */
package dev.ashtonite365.apex.features.misc

object BuiltinHypixelPack {
	private val KEEP = listOf(
		Regex("^assets/[^/]+/font/.+\\.json$"),
		Regex("^assets/hypixel_skyblock/textures/gui/(stats|skills|icons|mobs|weather)\\.png$"),
		Regex("^assets/hypixel_skyblock/textures/gui/icons/[^/]+\\.png$"),
		Regex("^assets/[^/]+/textures/gui/sprites/tooltip/[^/]+\\.png(\\.mcmeta)?$"),
		Regex("^assets/minecraft/shaders/core/text\\.(vsh|fsh)$"),
		Regex("^assets/minecraft/shaders/include/[^/]+\\.glsl$"),
	)
	private val META = setOf("pack.mcmeta", "pack.png", "LICENSE", "README-APEX.txt", "APEX-FIXES.txt")

	fun isGlyphOrTooltip(path: String): Boolean = KEEP.any { it.matches(path) }

	fun serves(path: String, skipOn: Boolean): Boolean =
		path in META || isGlyphOrTooltip(path)

	enum class ToggleAction { NONE, POP_SERVER_PACK, RELOAD, REAPPLY_SERVER_PACK }

	fun onToggle(prev: Boolean?, now: Boolean, onHypixel: Boolean, serverPackApplied: Boolean): ToggleAction = when {
		prev == null || prev == now -> ToggleAction.NONE
		now && serverPackApplied -> ToggleAction.POP_SERVER_PACK
		now -> ToggleAction.RELOAD
		onHypixel -> ToggleAction.REAPPLY_SERVER_PACK
		else -> ToggleAction.RELOAD
	}
}
