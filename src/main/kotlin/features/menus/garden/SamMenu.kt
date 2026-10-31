// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.garden

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

enum class SamButton(val label: String, val defaultSlot: Int) { WARP("Warp to Garden", 13), CLOSE("Close", 31), MOVE("Move Sam", 35) }

data class SamModel(val slots: Map<SamButton, Int>)

object SamParser {

	val TITLE = Regex("^(?i)Sam the Garden Assistant$")
	const val CHEST_SIZE = 27

	fun parse(title: String, slots: List<SlotText>): SamModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val chest = slots.filter { it.slot < CHEST_SIZE + 9 }
		fun find(b: SamButton) = chest.firstOrNull { it.cleanName.equals(b.label, true) }?.slot
		val warp = find(SamButton.WARP) ?: return null
		return SamModel(mapOf(
			SamButton.WARP to warp,
			SamButton.CLOSE to (find(SamButton.CLOSE) ?: SamButton.CLOSE.defaultSlot),
			SamButton.MOVE to (find(SamButton.MOVE) ?: SamButton.MOVE.defaultSlot)))
	}
}

@Config
object SamSettings : MenuSettings("sam") {
	val reset by button("reset") { resetToDefaults() }
}

object SamMenu : CustomMenu {
	override val id = "sam"
	override val displayName = "Sam the Garden Assistant"
	override val title = SamParser.TITLE
	override val settings get() = SamSettings
	override val defaultSize = 220 to 150

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = SamView(screen)
}

class SamView(screen: AbstractContainerScreen<*>) : ApexViewBase<SamModel>(screen) {
	override fun palette() = SamSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SamParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: SamModel, mx: Int, my: Int) {
		ctx.text(font, "§a§lSam the Garden Assistant", x + 6, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val warp = m.slots.getValue(SamButton.WARP)
		val bw = 110; val bh = 40
		val bx = x + (w - bw) / 2; val by = y + (h - bh) / 2 - 6
		button(ctx, mx, my, bx, by, bw, bh, "") { b -> clickSlot(warp, b) }
		stacks[warp]?.let { ctx.item(it, bx + (bw - 16) / 2, by + 5) }
		val t = SamButton.WARP.label
		ctx.text(font, "§f$t", bx + (bw - font.width(t)) / 2, by + 26, MenuTheme.WHITE)

		val cw = font.width("Close") + 14
		val close = m.slots.getValue(SamButton.CLOSE)
		button(ctx, mx, my, x + (w - cw) / 2, y + h - 20, cw, 14, "Close") { b -> clickSlot(close, b) }
		val mw = font.width("Move Sam") + 10
		val move = m.slots.getValue(SamButton.MOVE)
		button(ctx, mx, my, x + w - mw - 4, y + h - 20, mw, 14, "Move Sam") { b -> clickSlot(move, b) }
	}
}
