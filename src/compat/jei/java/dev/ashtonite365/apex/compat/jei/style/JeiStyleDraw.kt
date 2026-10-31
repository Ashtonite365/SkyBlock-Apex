// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jei.style

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.features.items.recipes.TabRect
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.features.menus.style.ModSettingsPages
import dev.ashtonite365.apex.features.menus.DropdownInput
import dev.ashtonite365.apex.features.items.recipes.ViewerGridRows
import dev.ashtonite365.apex.features.menus.style.JeiLook
import dev.ashtonite365.apex.features.menus.style.JeiStyle
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.GuiCanvas
import dev.ashtonite365.apex.features.menus.VanillaStyleDropdown
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults

object JeiStyleDraw {
	@JvmStatic
	fun drawThemed(g: GuiGraphicsExtractor, sprite: Identifier?, x: Int, y: Int, w: Int, h: Int): Boolean {
		if (sprite == null || w <= 0 || h <= 0) return false
		val ours = JeiStyle.spriteFor(sprite.path) ?: return false
		return runCatching {

			if (JeiStyle.frosted(sprite.path)) GuiCanvas(g, Client.font).frost(x, y, w, h, 2, 0.6)
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("apex", ours), x, y, w, h)

			if (JeiStyle.forged(sprite.path, w, h)) JeiStyle.paletteState()?.let {
				dev.ashtonite365.apex.features.menus.style.ForgeMetal.plate(GuiCanvas(g, Client.font), StylePalette.of(it), x, y, w, h)
			}
			true
		}.getOrDefault(false)
	}

	@JvmStatic
	fun drawThemedSprite(g: GuiGraphicsExtractor, sprite: net.minecraft.client.renderer.texture.TextureAtlasSprite?, x: Int, y: Int, w: Int, h: Int): Boolean =
		drawThemed(g, runCatching { sprite?.contents()?.name() }.getOrNull(), x, y, w, h)

	private fun tabColours(): IntArray? = when (JeiStyle.state.variant) {
		"skyglass" -> intArrayOf(0x38CDE1F5, 0x54EBF5FF, 0x96E1F0FF.toInt(), 0xFFFFFFFF.toInt())
		"forge_dark" -> intArrayOf(0xFF2A2C30.toInt(), 0xFF3A3D42.toInt(), 0xFFE8862A.toInt(), 0xFFF0E6DA.toInt())
		"forge_light" -> intArrayOf(0xFFC9CCD1.toInt(), 0xFFE2E4E8.toInt(), 0xFFD9761C.toInt(), 0xFF2B2B2B.toInt())
		else -> null
	}

	fun drawTab(ctx: GuiGraphicsExtractor, b: TabRect, selected: Boolean, hover: Boolean, label: Component): Boolean {
		val c = tabColours() ?: return false
		if (JeiStyle.state.look == JeiLook.SKYGLASS) GuiCanvas(ctx, Client.font).frost(b.x, b.y, b.w, b.h, 2, 0.6)
		ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, c[2])
		ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - (if (selected) 0 else 1), if (selected || hover) c[1] else c[0])
		if (selected) ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2, c[2])
		ctx.centeredText(Client.font, label, b.x + b.w / 2, b.y + (b.h - 8) / 2, c[3])
		return true
	}
}

object JeiStylePanel {
	const val W = 66
	const val H = 12
	private var installed = false
	private var open = false

	fun head(panelRight: Int): IntArray = intArrayOf(panelRight - W - 1, 1, W, H)

	fun row(panelRight: Int, i: Int): IntArray = head(panelRight).let { intArrayOf(it[0], it[1] + H * (i + 1), W, H) }

	private fun inside(r: IntArray, mx: Double, my: Double) = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]

	private fun panelRight(s: Screen): Int? {
		val host = ViewerTabs.host ?: return null
		if (!host.showsPanel(s)) return null
		return host.panelRect(s)?.right ?: s.width
	}

	val PAGE = ModSettingsPages.Page("jei", "JEI") {
		listOf(
			ModSettingsPages.Row.Module("Panel style"),
			ModSettingsPages.Row.Cycle("JEI style", "The item list panel's own look: Vanilla JEI, Skyglass or Forge (separate from the menu style).",
				{ JeiStyle.state.look.label }) { d ->
				val all = JeiLook.entries
				JeiStyle.choose(all[(all.indexOf(JeiStyle.state.look) + d + all.size) % all.size])
			},
			ModSettingsPages.Row.Toggle("JEI Forge: dark mode", "Forge panel in dark or light.", { JeiStyle.state.forgeDark }) { JeiStyle.toggleForgeMode() },
			ModSettingsPages.Row.Module("Item list"),
			ModSettingsPages.Row.Option("repo.enable-rei"),
			ModSettingsPages.Row.Toggle("Fill the grid above the tabs", "While the Apex tabs show, the item list uses every row that fits above them, " +
				"past JEI's own max rows setting (JEI's config file is never changed).", { JeiStyle.state.gridFill }) { JeiStyle.toggleGridFill() },
			ModSettingsPages.Row.Toggle("Hide the recipe sync notice", "No \"server does not provide recipes\" chat line when JEI starts (takes effect at the next start).",
				{ JeiStyle.state.hideSyncNotice }) { JeiStyle.toggleSyncNotice() },
			ModSettingsPages.Row.Option("fixes.hide-recipe-book"),
		)
	}

	fun rows(panelRight: Int): List<IntArray> = JeiLook.entries.indices.map { row(panelRight, it) }

	fun install() {
		if (installed) return
		installed = true
		ModSettingsPages.register(PAGE)
		ViewerGridRows.fillSetting = { JeiStyle.state.gridFill }
		ViewerTabs.skin = { ctx, b, sel, hov, label -> JeiStyleDraw.drawTab(ctx, b, sel, hov, label) }

		DropdownInput.register(DropdownInput.Claimer { s, mx, my, button ->
			val right = panelRight(s) ?: return@Claimer false.also { open = false }
			val o = DropdownInput.decide(open, head(right), rows(right), mx, my, button, InputConstants.MOUSE_BUTTON_LEFT)
			open = o.open
			o.pick?.let { JeiStyle.choose(JeiLook.entries[it]) }
			o.consumed
		})
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
			open = false
			ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { s, ctx, mx, my, _ ->
				Faults.attempt("Item list style dropdown") { render(s, ctx, mx, my) }
			})
		})
	}

	private fun render(s: Screen, ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		val right = panelRight(s) ?: return

		val p = JeiStyle.paletteState()?.let { StylePalette.of(it) }
		val c = GuiCanvas(ctx, Client.font)
		val h = head(right)
		VanillaStyleDropdown.paintHead(c, p, JeiStyle.state.look.label, h[0], h[1], W, H, inside(h, mx.toDouble(), my.toDouble()), open)
		if (!open) return
		val first = row(right, 0)
		VanillaStyleDropdown.paintRows(c, p, intArrayOf(first[0], first[1] - 1, W, JeiLook.entries.size * H + 2),
			JeiLook.entries.map { it.label }, JeiLook.entries.indexOf(JeiStyle.state.look), mx, my, H)
	}
}
