/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): solid textured menus (style idea only, see CREDITS.md). Original implementation.
 */
package dev.ashtonite365.apex.features.misc

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.SidePanel

object SolidMenus {
	enum class Theme(val id: String) : StringRepresentable {
		LIGHT("light"), DARK("dark");
		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig("solid-menus", Category.MISC) {
		val hideFiller by toggle("hide-filler-panes") { true }
		val hideColouredFiller by toggle("hide-coloured-filler") { true }
		val solidPanels by toggle("solid-panels") { true }
		val panelTheme by choice("panel-theme") { Theme.LIGHT }
		val hideTimersInMenus by toggle("hide-timers-in-menus") { true }

		val staticAfterClick by toggle("static-after-click") { true }
	}

	enum class PaneKind { NONE, NEUTRAL, COLOURED }

	private val neutral = setOf(DyeColor.BLACK, DyeColor.GRAY, DyeColor.LIGHT_GRAY)

	val FILLER_NAMES = setOf("filler", "-", ".", "_", "|")

	val DENY_TITLES = listOf(
		"experimentation table", "experiment", "experiments", "chronomatron", "ultrasequencer", "superpairs",
		"harp", "melody",
		"bank",

		"click in order", "correct all the panes", "change all to same color", "select all the",
		"what starts with", "click the button on time", "navigate the maze", "terminal",

		"fossil excavator", "fossil excavation", "rubix", "simon says",
	)
	private val DENY_PHRASES = dev.ashtonite365.apex.features.menus.MenuTitles.PhraseList(DENY_TITLES)

	private val CLICK_HINTS = listOf("click", "▶", "➜", "toggle", "select")

	fun strip(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").replace("§", "")

	fun allowedIn(title: String?): Boolean {
		val t = strip(title ?: return false).lowercase()
		return !DENY_PHRASES.matches(t)
	}

	fun hasClickHint(text: String) = strip(text).lowercase().let { t -> CLICK_HINTS.any { it in t } }

	fun isFiller(kind: PaneKind, name: String, lore: List<String>, hideColoured: Boolean): Boolean {
		if (kind == PaneKind.NONE) return false
		if (kind == PaneKind.COLOURED && !hideColoured) return false
		if (hasClickHint(name)) return false
		val n = strip(name).trim().lowercase()
		if (n.isNotEmpty() && n !in FILLER_NAMES) return false
		return lore.all { strip(it).isBlank() }
	}

	fun paneKind(stack: ItemStack): PaneKind {
		if (stack.isEmpty) return PaneKind.NONE
		if (stack.`is`(Items.GLASS_PANE)) return PaneKind.NEUTRAL
		for (c in DyeColor.entries)
			if (stack.`is`(Items.STAINED_GLASS_PANE.pick(c))) return if (c in neutral) PaneKind.NEUTRAL else PaneKind.COLOURED
		return PaneKind.NONE
	}

	@JvmStatic
	fun shouldHide(slot: Slot): Boolean {
		if (!TConfig.hideFiller || !SkyBlockSession.inSkyBlock) return false
		if (slot.container is Inventory) return false
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return false
		if (!allowedIn(screen.title.string)) return false
		val stack = slot.item
		val kind = paneKind(stack)
		if (kind == PaneKind.NONE) return false
		return isFiller(kind, stack.hoverName.string, stack.view().loreAccordingToNbt.map { it.string }, TConfig.hideColouredFiller)
	}

	@JvmStatic
	fun drawHidden(graphics: GuiGraphicsExtractor, slot: Slot) {
		if (!TConfig.solidPanels) return
		graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, SidePanel.palette.fill)
	}
}
