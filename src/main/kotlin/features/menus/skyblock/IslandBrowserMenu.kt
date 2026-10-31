// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
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

data class IslandTile(val slot: Int, val name: String, val tags: List<String>, val players: List<String>, val popularity: Int?, val votes: Int?)
data class IslandBrowserModel(val tiles: List<IslandTile>, val page: Int?, val pages: Int?, val tags: String?, val nav: Map<String, Int>)

object IslandBrowserParser {
	val TITLE = Regex("^Island Browser$", RegexOption.IGNORE_CASE)
	val NAV = listOf("Previous Page", "Next Page", "Island Category", "Go Back", "Close")
	private val PAGE = Regex("\\((\\d+)/(\\d+)\\)")

	fun tile(s: SlotText): IslandTile? {
		val lore = s.cleanLore
		val pi = lore.indexOfFirst { it.equals("Players:", true) }
		if (pi < 0 && lore.none { it.contains("Click to visit", true) }) return null
		val tags = (if (pi < 0) emptyList() else lore.take(pi)).flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
		val players = if (pi < 0) emptyList() else lore.drop(pi + 1).takeWhile { it.startsWith("-") }.map { it.removePrefix("-").trim() }
		val pop = lore.indexOfFirst { it.startsWith("Popularity Lvl:", true) }
		val level = lore.getOrNull(pop)?.substringAfter(":")?.trim()?.toIntOrNull()
		val votes = lore.getOrNull(pop + 1)?.takeIf { pop >= 0 }?.let { Regex("^(\\d+)").find(it.trim())?.value?.toIntOrNull() }
		return IslandTile(s.slot, s.cleanName, tags, players, level, votes)
	}

	fun filter(tiles: List<IslandTile>, q: String): List<IslandTile> {
		val t = q.trim().lowercase()
		if (t.isEmpty()) return tiles
		return tiles.filter { i -> i.name.lowercase().contains(t) || i.tags.any { it.lowercase().contains(t) } || i.players.any { it.lowercase().contains(t) } }
	}

	fun parse(title: String, slots: List<SlotText>): IslandBrowserModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		val tiles = slots.filter { it.slot !in nav.values }.mapNotNull(::tile)
		if (tiles.isEmpty()) return null
		val pageLine = listOf("Next Page", "Previous Page").firstNotNullOfOrNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.cleanLore?.firstNotNullOfOrNull { PAGE.find(it) } }
		val tags = slots.firstOrNull { it.cleanName.equals("Island Category", true) }?.cleanLore?.firstOrNull { it.startsWith("Tags:", true) }?.substringAfter(":")?.trim()

		val page = pageLine?.groupValues?.get(1)?.toInt()
		return IslandBrowserModel(tiles, page, pageLine?.groupValues?.get(2)?.toInt(), tags, nav)
	}
}

@Config
object IslandBrowserSettings : MenuSettings("island-browser", panels = listOf("players", "search")) {
	val reset by button("reset") { resetToDefaults() }
}

object IslandBrowserMenu : CustomMenu {
	override val id = "island-browser"
	override val displayName = "Island Browser"
	override val title = IslandBrowserParser.TITLE
	override val settings get() = IslandBrowserSettings
	override val defaultSize = 470 to 280

	val search = dev.ashtonite365.apex.features.menus.support.MenuSearch()
	var query: String get() = search.query; set(v) { search.query = v }

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = IslandBrowserView(screen)
}

class IslandBrowserView(screen: AbstractContainerScreen<*>) : ApexViewBase<IslandBrowserModel>(screen) {
	override fun palette() = IslandBrowserSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = IslandBrowserParser.parse(title, slots)
	private val search get() = IslandBrowserMenu.search
	init { search.focused = false }
	override val textFocused get() = search.focused

	override fun keyPressed(input: KeyEvent): Boolean = search.keyPressed(input) || super.keyPressed(input)

	override fun charTyped(input: CharacterEvent): Boolean { search.charTyped(input); return true }

	override fun draw(ctx: GuiGraphicsExtractor, m: IslandBrowserModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lIsland Browser", x + 6, y + 7, MenuTheme.GOLD)
		m.tags?.let { ctx.text(font, "§7Tags: §f$it", x + 100, y + 7, MenuTheme.GREY) }
		val vw = font.width("Vanilla") + 10

		var top = y + 22
		if (IslandBrowserSettings.shows("search")) {
			top += searchBox(ctx, mx, my, search, x + 6, top, 160, "Search islands / players…") + 4
		}
		val players = IslandBrowserSettings.shows("players")
		val shown = IslandBrowserParser.filter(m.tiles, IslandBrowserMenu.query)
		val tw = 112; val th = if (players) 54 else 32; val gap = 4
		val cols = ((w - 12 + gap) / (tw + gap)).coerceAtLeast(1)
		for ((i, t) in shown.withIndex()) {
			val tx = x + 6 + (i % cols) * (tw + gap); val ty = top + (i / cols) * (th + gap)
			if (ty + th > y + h - 24) break
			button(ctx, mx, my, tx, ty, tw, th, "") { b -> clickSlot(t.slot, b) }
			stacks[t.slot]?.let { ctx.item(it, tx + 3, ty + 3) }
			ctx.text(font, "§f" + MenuTheme.fit(t.name, tw - 24, font::width), tx + 21, ty + 4, MenuTheme.WHITE)
			ctx.text(font, "§e" + listOfNotNull(t.popularity?.let { "Lvl $it" }, t.votes?.let { "$it✌" }).joinToString(" "), tx + 21, ty + 13, MenuTheme.WHITE)
			if (t.tags.isNotEmpty()) ctx.text(font, "§b" + MenuTheme.fit(t.tags.joinToString(", "), tw - 6, font::width), tx + 3, ty + 22, MenuTheme.WHITE)
			if (players) t.players.take(2).forEachIndexed { k, p -> ctx.text(font, "§7" + MenuTheme.fit(p, tw - 6, font::width), tx + 3, ty + 32 + k * 9, MenuTheme.GREY) }
		}
		if (shown.isEmpty()) ctx.text(font, "§7No island on this page matches.", x + 8, top + 4, MenuTheme.GREY)

		val by = y + h - 20
		m.nav["Previous Page"]?.let { s -> button(ctx, mx, my, x + 6, by, 50, 14, "< Prev") { b -> clickSlot(s, b) } }
		val pg = if (m.page != null && m.pages != null) "Page ${m.page}/${m.pages}" else ""
		ctx.text(font, "§7$pg", x + 62, by + 3, MenuTheme.GREY)
		m.nav["Next Page"]?.let { s -> button(ctx, mx, my, x + 130, by, 50, 14, "Next >") { b -> clickSlot(s, b) } }
		var bx = x + w - 6
		for (n in listOf("Close", "Go Back", "Island Category")) m.nav[n]?.let { s ->
			val bw = font.width(n) + 14; bx -= bw; button(ctx, mx, my, bx, by, bw, 14, n) { b -> clickSlot(s, b) }; bx -= 4 }
	}
}
