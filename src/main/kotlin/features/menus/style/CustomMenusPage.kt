// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

object CustomMenusPage {
	sealed interface Row {
		data class Module(val title: String) : Row

		data class Toggle(val label: String, val desc: String, val on: (StyleState) -> Boolean, val flip: () -> Unit) : Row

		data class Cycle(val label: String, val desc: String, val value: (StyleState) -> String, val step: (Int) -> Unit) : Row
	}

	private fun <E> next(all: List<E>, cur: E, dir: Int) = all[(all.indexOf(cur) + dir + all.size) % all.size]

	private val base: List<Row> = listOf(
		Row.Module("Custom Menus"),
		Row.Toggle("Custom Menus", "Master switch: off shows every SkyBlock menu and the inventory as vanilla (same as Vanilla in a style dropdown).",
			{ it.customMenus }) { MenuStyle.setCustomMenus(!MenuStyle.state.customMenus) },
		Row.Toggle("Style every chest menu", "Menus without their own Apex layout get a styled grid (off: Hypixel's chest).",
			{ it.wrapAll && it.customMenus }) { MenuStyle.setWrapAll(!(MenuStyle.state.wrapAll && MenuStyle.state.customMenus)) },
		Row.Module("Menu Style"),
		Row.Cycle("Style", "Vanilla, Skyglass or Forge: the same choice as the dropdown in every menu's top bar.",
			{ StyleChoice.of(it).label }) { d -> MenuStyle.choose(next(StyleChoice.entries, StyleChoice.of(MenuStyle.state), d)) },
		Row.Toggle("Forge: dark mode", "Forge has a light and a dark look (the same switch is in every Forge menu's top bar).",
			{ it.forgeMode == ForgeMode.DARK }) { MenuStyle.toggleForgeMode() },
		Row.Module("Menu Animations"),
		Row.Toggle("Open / close animations", "Animate menus as they open and close.", { it.animate }) { MenuStyle.update { it.copy(animate = !it.animate) } },
		Row.Cycle("Animation", "Fade, a circle that opens outward and closes in, or glitch.", { it.anim.label }) { d ->
			MenuStyle.update { it.copy(anim = next(MenuAnim.entries, it.anim, d)) } },
		Row.Toggle("Reduce motion", "A short fade in and an instant close, whatever the animation.", { it.reduceMotion }) { MenuStyle.update { it.copy(reduceMotion = !it.reduceMotion) } },
		Row.Module("Inventory"),
		Row.Toggle("Apex button in the inventory", "A logo button next to the recipe book that opens /apex (client-side only).",
			{ it.inventoryButton }) { MenuStyle.update { it.copy(inventoryButton = !it.inventoryButton) } },
	)

	private val extras = mutableListOf<() -> List<Row>>()
	fun contribute(rows: () -> List<Row>) { extras.add(rows) }
	fun resetExtrasForTests() { extras.clear() }

	val rows: List<Row> get() = base + extras.flatMap { it() }
}
