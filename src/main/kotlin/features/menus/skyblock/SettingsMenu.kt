// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class SettingFlag(val name: String, val on: Boolean)
data class SettingEntry(val slot: Int, val name: String, val description: List<String>, val flags: List<SettingFlag>) {

	val summary get() = if (flags.isEmpty()) null else "${flags.count { it.on }}/${flags.size} on"
}
data class SettingsModel(val entries: List<SettingEntry>, val nav: Map<String, Int>)

object SettingsParser {

	val TITLE = Regex("^(?:SkyBlock )?Settings$", RegexOption.IGNORE_CASE)
	val NAV = listOf("Go Back", "Close")

	fun flags(lore: List<String>): List<SettingFlag> {
		val i = lore.indexOfFirst { it.trim().equals("Includes:", true) }
		if (i < 0) return emptyList()
		return lore.drop(i + 1).takeWhile { it.isNotBlank() }.mapNotNull { l ->
			val t = l.trim()
			when { t.startsWith("✔") -> SettingFlag(t.drop(1).trim(), true); t.startsWith("✖") -> SettingFlag(t.drop(1).trim(), false); else -> null }
		}
	}

	fun description(lore: List<String>): List<String> {
		val end = lore.indexOfFirst { it.trim().equals("Includes:", true) }.let { if (it < 0) lore.size else it }
		return lore.take(end).filter { it.isNotBlank() && !it.startsWith("Click", true) }
	}

	fun parse(title: String, slots: List<SlotText>): SettingsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		val back = slots.firstOrNull { it.cleanName.equals("Go Back", true) } ?: return null
		if (back.cleanLore.none { it.contains("SkyBlock Menu", true) }) return null
		val entries = slots.filter { it.cleanName.isNotBlank() && it.lore.isNotEmpty() && it.slot !in nav.values }
			.map { SettingEntry(it.slot, it.cleanName, description(it.cleanLore), flags(it.cleanLore)) }
		if (entries.isEmpty()) return null
		return SettingsModel(entries, nav)
	}
}

@Config
object SettingsMenuSettings : MenuSettings("sb-settings", panels = listOf("descriptions")) {

	val startExpanded by toggle("start-expanded") { false }
	val reset by button("reset") { resetToDefaults() }
}

object SettingsMenu : CustomMenu {
	override val id = "sb-settings"
	override val displayName = "SkyBlock Settings"
	override val title = SettingsParser.TITLE
	override val settings get() = SettingsMenuSettings
	override val defaultSize = 380 to 260

	val expanded = mutableSetOf<String>()

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView? = SettingsView(screen)

	fun isOpen(name: String, startExpanded: Boolean) = (name in expanded) != startExpanded
	fun toggle(name: String) { if (!expanded.remove(name)) expanded += name }
}

class SettingsView(screen: AbstractContainerScreen<*>) : ApexViewBase<SettingsModel>(screen) {
	override fun palette() = SettingsMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SettingsParser.parse(title, slots)

	private val scroller = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey() }
	override val scrollView get() = scroller

	override fun draw(ctx: GuiGraphicsExtractor, m: SettingsModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lSkyBlock Settings", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val top = y + 22; val bottom = y + h - 24
		val panels = m.entries.map { e ->
			val open = SettingsMenu.isOpen(e.name, SettingsMenuSettings.startExpanded)
			val desc = if (!open) emptyList() else
				(if (SettingsMenuSettings.shows("descriptions")) e.description.map { "§7$it" } else emptyList()) +
					e.flags.map { (if (it.on) "§a✔ " else "§c✖ ") + "§f" + it.name }
			Triple(e, open, desc)
		}
		val heights = panels.map { (_, _, desc) -> 18 + desc.size * 10 + if (desc.isNotEmpty()) 4 else 0 }
		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + 1, top, w - 2, bottom - top)
		scroller.layout(view, if (heights.isEmpty()) 0 else heights.sum() + 3 * (heights.size - 1))
		var yy = top - scroller.offset
		beginClip(ctx, view)
		panels.forEachIndexed { i, (e, open, desc) ->
			val ph = heights[i]
			if (yy + ph > top && yy < bottom) {
				MenuTheme.frame(ctx, x + 6, yy, x + w - 6, yy + ph, MenuTheme.FILL)

				area(x + 6, yy, w - 90, 18) { SettingsMenu.toggle(e.name) }
				ctx.text(font, if (open) "§6▼" else "§6▶", x + 10, yy + 5, MenuTheme.GOLD)
				stacks[e.slot]?.let { ctx.item(it, x + 20, yy + 1) }
				ctx.text(font, "§f" + MenuTheme.fit(e.name, w - 190, font::width), x + 40, yy + 5, MenuTheme.WHITE)
				e.summary?.let { ctx.text(font, "§7$it", x + w - 140, yy + 5, MenuTheme.GREY) }
				button(ctx, mx, my, x + w - 82, yy + 2, 72, 14, "Open") { b -> clickSlot(e.slot, b) }
				desc.forEachIndexed { j, l -> ctx.text(font, MenuTheme.fit(l, w - 30, font::width), x + 14, yy + 20 + j * 10, MenuTheme.GREY) }
			}
			yy += ph + 3
		}
		endClip(ctx)
		var bx = x + 6
		for ((n, s) in m.nav) { val bw = font.width(n) + 14; button(ctx, mx, my, bx, y + h - 20, bw, 14, n) { b -> clickSlot(s, b) }; bx += bw + 4 }
	}
}
