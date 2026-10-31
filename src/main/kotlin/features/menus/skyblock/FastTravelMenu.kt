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
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.misc.WarpMenu
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.network.chat.Component

data class TravelTile(val slot: Int, val island: String, val warps: List<String>, val locked: Boolean, val here: Boolean, val info: String?,
                      val lore: List<String> = emptyList(), val opens: Boolean = false)
data class FastTravelModel(val tiles: List<TravelTile>, val nav: Map<String, Int>)

object FastTravelParser {

	val TITLE = MenuTitles.anyOf(Regex("^Fast Travel$", RegexOption.IGNORE_CASE), MenuTitles.endsWith("Fast Travel", prefix = "^.*?➜\\s*"))
	private val WARP = Regex("^/warp\\s+([a-z0-9_]+)$", RegexOption.IGNORE_CASE)
	val NAV = listOf("Island Browser", "Go Back", "Close", "Advanced Mode")
	private val LOCKED = listOf("Warp not unlocked", "Explore more of SkyBlock", "Island is closed")

	fun parse(title: String, slots: List<SlotText>): FastTravelModel? {
		if (!TITLE.containsMatchIn(MenuText.clean(title))) return null
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		val tiles = slots.filter { it.slot !in nav.values && it.cleanName.isNotBlank() }.mapNotNull { s ->
			val lore = s.cleanLore
			val warps = lore.mapNotNull { WARP.matchEntire(it.trim())?.groupValues?.get(1)?.lowercase() }
			val name = s.cleanName
			val undiscovered = name.equals("Undiscovered Island", true)
			if (warps.isEmpty() && !undiscovered && !name.startsWith("Warp to:", true)) return@mapNotNull null
			val locked = undiscovered || lore.any { l -> LOCKED.any { l.contains(it, true) } }
			val skill = lore.firstOrNull { it.startsWith("Main skill:", true) }?.substringAfter(":")?.trim()
			val tier = lore.firstOrNull { it.startsWith("Island tier:", true) }?.substringAfter(":")?.trim()
			val info = listOfNotNull(skill, tier?.let { "Tier $it" }).joinToString(" ").ifEmpty { null }
			TravelTile(s.slot, name.removePrefix("Warp to:").trim(), warps, locked, lore.any { it.equals("You are here!", true) }, info,
				lore, lore.any { it.contains("to open", true) })
		}
		if (tiles.none { it.warps.isNotEmpty() }) return null
		return FastTravelModel(tiles, nav)
	}

	fun repoWarps(shown: List<String>, entries: List<WarpMenu.Entry>): List<WarpMenu.Entry> {
		val modes = entries.filter { it.warp in shown }.map { it.mode }.toSet()
		return entries.filter { it.mode in modes && it.warp !in shown }
	}
}

@Config
object FastTravelSettings : MenuSettings("fast-travel", panels = listOf("repo-warps")) {
	val reset by button("reset") { resetToDefaults() }
}

@Config
object FancyWarpSettings : ManagedConfig("custom-menu-fancy-warp", Category.CUSTOM_MENUS) {

	val fancyMap by toggle("fancy-map") { true }
}

object FastTravelMenu : CustomMenu {
	override val id = "fast-travel"
	override val displayName = "Fast Travel"
	override val title = FastTravelParser.TITLE
	override val settings get() = FastTravelSettings
	override val defaultSize = 520 to 330

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView? = FastTravelView(screen)

	private val loggedUnmarked = mutableSetOf<String>()
	internal fun logUnmarked(warps: List<String>) {
		for (w in warps) if (loggedUnmarked.add(w)) dev.ashtonite365.apex.Apex.logger.info("[Apex] fancy warp: no portal marker for warp '$w' (listed only)")
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {

		event.register(ManagedConfig.Category.MISC, "fancy", "Open the fancy warp map (Fast Travel)") {
			onRun { Client.runCommand("warp") }
		}
	}
}

class FastTravelView(screen: AbstractContainerScreen<*>) : ApexViewBase<FastTravelModel>(screen) {
	override fun palette() = FastTravelSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = FastTravelParser.parse(title, slots)
	private val repo by lazy { runCatching { WarpMenu.entries() }.getOrDefault(emptyList()) }

	override fun draw(ctx: GuiGraphicsExtractor, m: FastTravelModel, mx: Int, my: Int) {
		if (FancyWarpSettings.fancyMap) drawFancy(ctx, m, mx, my) else drawGrid(ctx, m, mx, my)
	}

	private val mapTex = net.minecraft.resources.Identifier.fromNamespaceAndPath("apex", FastTravelMap.TEXTURE_PATH)
	private val texOk by lazy {
		FastTravelMap.Texture.available({ Client.resources.getResource(mapTex).isPresent }) { dev.ashtonite365.apex.Apex.logger.warn(it) }
	}

	private val chatSink = CommandSink { Client.runCommand(it.removePrefix("/")) }

	private fun drawFancy(ctx: GuiGraphicsExtractor, m: FastTravelModel, mx: Int, my: Int) {
		val islands = FancyWarp.build(m, if (FastTravelSettings.shows("repo-warps")) repo else emptyList())
		FastTravelMenu.logUnmarked(FancyWarp.unmarked(islands))
		ctx.text(font, "§6§lFast Travel", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val lay = FastTravelMap.layout(x, y, w, h, islands, font::width)
		val img = lay.image

		if (texOk) ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, mapTex, img.x, img.y, 0f, 0f, img.w, img.h, 1280, 720, 1280, 720)
		else ctx.fill(img.x, img.y, img.x + img.w, img.y + img.h, FastTravelMap.Texture.NAVY)
		val rad = FancyWarp.radius(img.w)
		val sel = FancyWarp.nearest(lay.portals, { it.x to it.y }, mx, my, rad)
		val s0 = FastTravelMap.MARKER
		for (p in lay.portals) {
			val locked = p.live?.locked == true || !p.row.verified
			val s = if (p === sel) s0 + 1 else s0
			ctx.fill(p.x - s - 1, p.y - s - 1, p.x + s + 1, p.y + s + 1, if (!locked) 0xFF3A0060.toInt() else 0xFF2A2A2A.toInt())
			ctx.fill(p.x - s, p.y - s, p.x + s, p.y + s, if (locked) PORTAL_LOCKED else if (p === sel) PORTAL_HOVER else PORTAL)
		}
		if (sel != null) {
			area(mx - rad, my - rad, rad * 2, rad * 2) { FastTravelMap.click(sel, chatSink) }
			val lines = mutableListOf("§d${sel.row.island}", "§7${sel.row.command}")
			sel.live?.takeIf { it.locked }?.let { e -> lines += "§cLocked"; lines += e.lore.map { "§7$it" } }
			if (!sel.row.verified) lines += "§8No known /warp id: disabled (warp from the island's own menu)"
			else lines += "§eClick: ${sel.row.command}"
			ctx.setComponentTooltipForNextFrame(font, lines.map { Component.literal(it) }, mx, my)
		}
		for ((i, l) in lay.listedLines.withIndex()) ctx.text(font, "§7$l", x + 6, lay.listY + i * 9, MenuTheme.GREY)
		var nx = x + 6
		for ((n, s) in m.nav) { val bw = font.width(n) + 14; button(ctx, mx, my, nx, lay.navY, bw, 14, n) { b -> clickSlot(s, b) }; nx += bw + 4 }
	}

	companion object {
		const val PORTAL = 0xFFAA33FF.toInt()
		const val PORTAL_HOVER = 0xFFDD88FF.toInt()
		const val PORTAL_LOCKED = 0xFF554466.toInt()
		const val SEA = 0xFF101820.toInt()
	}

	private fun drawGrid(ctx: GuiGraphicsExtractor, m: FastTravelModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lFast Travel", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val showRepo = FastTravelSettings.shows("repo-warps")
		val tw = 104; val th = if (showRepo) 52 else 36; val gap = 4
		val cols = ((w - 12 + gap) / (tw + gap)).coerceAtLeast(1)
		for ((i, t) in m.tiles.withIndex()) {
			val tx = x + 6 + (i % cols) * (tw + gap); val ty = y + 22 + (i / cols) * (th + gap)
			if (ty + th > y + h - 24) break
			button(ctx, mx, my, tx, ty, tw, 20, "", enabled = !t.locked) { b -> clickSlot(t.slot, b) }
			stacks[t.slot]?.let { ctx.item(it, tx + 2, ty + 2) }
			ctx.text(font, (if (t.locked) "§8" else "§f") + MenuTheme.fit(t.island, tw - 24, font::width), tx + 20, ty + 6, MenuTheme.WHITE)
			ctx.text(font, (if (t.here) "§aYou are here " else "§7") + MenuTheme.fit(t.info ?: t.warps.joinToString(" ") { "/$it" }, tw - 4, font::width), tx + 2, ty + 23, MenuTheme.GREY)
			if (showRepo && !t.locked) {

				val extra = FastTravelParser.repoWarps(t.warps, repo)
				var cx = tx
				for (e in extra.take(3)) {
					val label = if (WarpMenu.TConfig.showAliases && e.aliases.isNotEmpty()) e.aliases.first() else e.warp
					val cw = (font.width(label) + 6).coerceAtMost(tw - (cx - tx)); if (cw < 14) break
					button(ctx, mx, my, cx, ty + 34, cw, 14, label) { Client.runCommand("warp ${e.warp}") }
					cx += cw + 2
				}
			}
		}
		var bx = x + 6
		for ((n, s) in m.nav) { val bw = font.width(n) + 14; button(ctx, mx, my, bx, y + h - 20, bw, 14, n) { b -> clickSlot(s, b) }; bx += bw + 4 }
	}
}
