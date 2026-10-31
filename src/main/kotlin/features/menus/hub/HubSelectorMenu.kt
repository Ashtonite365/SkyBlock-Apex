// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.hub

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class Hub(val number: Int, val name: String, val slot: Int, val players: Int, val capacity: Int, val server: String?, val joinable: Boolean) {
	val fill get() = if (capacity > 0) players.toDouble() / capacity else 1.0
}
data class HubSelectorModel(val title: String, val hubs: List<Hub>, val nav: Map<String, Int>) {
	val open get() = hubs.filter { it.joinable }
	val full get() = hubs.filter { !it.joinable }
}

object HubSelectorParser {

	val TITLE = Regex("^(?i)(.+ )?Hub Selector$")
	private val NAME = Regex("^(.+?) #(\\d+)$")
	private val PLAYERS = Regex("Players: (\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
	private val SERVER = Regex("Server: (\\S+)", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): HubSelectorModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val hubs = slots.mapNotNull { s ->
			val n = NAME.matchEntire(s.cleanName) ?: return@mapNotNull null
			val p = s.cleanLore.firstNotNullOfOrNull { PLAYERS.find(it) } ?: return@mapNotNull null
			val (a, b) = p.destructured
			val joinable = s.cleanLore.any { it.startsWith("Click to connect", true) } && b.toInt() > 0 && a.toInt() < b.toInt()
			Hub(n.groupValues[2].toInt(), n.groupValues[1], s.slot, a.toInt(), b.toInt(), s.cleanLore.firstNotNullOfOrNull { SERVER.find(it)?.groupValues?.get(1) }, joinable)
		}.sortedBy { it.number }
		if (hubs.isEmpty()) return null
		return HubSelectorModel(t, hubs, SkillLore.nav(slots))
	}
}

@Config
object HubSelectorSettings : MenuSettings("hub-selector", panels = listOf("full-hubs", "server-id")) {
	val reset by button("reset") { resetToDefaults() }
}

object HubSelectorMenu : CustomMenu {
	override val id = "hub-selector"
	override val displayName = "Hub Selector"
	override val title = HubSelectorParser.TITLE
	override val settings get() = HubSelectorSettings
	override val defaultSize = 440 to 280

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = HubSelectorView(screen)
}

class HubSelectorView(screen: AbstractContainerScreen<*>) : SkillViewBase<HubSelectorModel>(screen) {
	override fun palette() = HubSelectorSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = HubSelectorParser.parse(title, slots)

	private val scroller = ScrollView { scrollKey() }
	override val scrollView get() = scroller

	private fun groups(m: HubSelectorModel) =
		(listOf("Open" to m.open) + (if (HubSelectorSettings.shows("full-hubs")) listOf("Full" to m.full) else emptyList())).filter { it.second.isNotEmpty() }

	override fun draw(ctx: GuiGraphicsExtractor, m: HubSelectorModel, mx: Int, my: Int) {
		header(ctx, mx, my, m.title + "  §7${m.open.size}/${m.hubs.size} open")
		val p = palette()
		val cols = 5; val gap = 3
		val tw = (w - 16 - gap * (cols - 1)) / cols
		val viewTop = y + 22; val viewBottom = navTop() - ScrollClip.BAR_GAP
		val view = MenuBounds(x + 1, viewTop, w - 2, viewBottom - viewTop)
		fun layout(hub: Hub) = SelectorTileLayout.tile("§f#${hub.number}", hub.server?.takeIf { HubSelectorSettings.shows("server-id") }?.let { "§7$it" }, tw, font::width)

		val plan = groups(m).map { (label, list) ->
			val rows = list.chunked(cols).map { row -> row.map { it to layout(it) } }
			Triple(label, rows, 14 + rows.sumOf { r -> r.maxOf { it.second.height } + gap })
		}
		scroller.layout(view, (plan.sumOf { it.third + 4 } - if (plan.isEmpty()) 0 else 4).coerceAtLeast(0))
		beginClip(ctx, view)
		var gy = viewTop - scroller.offset
		for ((label, rows, gh) in plan) {
			val top = group(ctx, x + 4, gy, w - 8, gh, label)
			var ry = top
			for (row in rows) {
				val rh = row.maxOf { it.second.height }
				if (ry + rh > viewTop && ry < viewBottom) row.forEachIndexed { i, (hub, lt) ->
					val tx = x + 8 + i * (tw + gap)

					MenuTheme.button(ctx, font, tx, ry, tw, rh, "", MenuTheme.state(hovered(mx, my, tx, ry, tw, rh)), palette = p)
					stacks[hub.slot]?.let { ctx.item(it, tx + 3, ry + 3) }
					lt.title.forEachIndexed { j, l -> ctx.text(font, l, tx + SelectorTileLayout.TEXT_X, ry + lt.titleY[j], MenuTheme.WHITE) }
					lt.sub.forEachIndexed { j, l -> ctx.text(font, l, tx + SelectorTileLayout.TEXT_X, ry + lt.subY[j], MenuTheme.GREY) }
					val by = ry + rh - SelectorTileLayout.BAR_H - SelectorTileLayout.PAD_BOTTOM
					MenuTheme.progressBar(ctx, font, tx + 3, by, tw - 6, SelectorTileLayout.BAR_H, hub.players.toDouble(), hub.capacity.toDouble(),
						SelectorTileLayout.barLabel(hub.players, hub.capacity, tw - 6, font::width), palette = p)
					area(tx, ry, tw, rh) { b -> clickSlot(hub.slot, b) }
				}
				ry += rh + gap
			}
			gy += gh + 4
		}
		endClip(ctx)
		navRow(ctx, mx, my, m.nav)
	}
}
