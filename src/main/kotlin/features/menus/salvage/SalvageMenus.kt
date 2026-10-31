// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.salvage

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.h6.HubLayout
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object SalvageLore {
	private val STATE = Regex("\\b(ON|OFF|Enabled|Disabled)\\b", RegexOption.IGNORE_CASE)

	fun toggleState(s: SlotText): Boolean? {
		val lines = s.cleanLore
		val pick = lines.firstOrNull { Regex("^(Currently|Status|State|Toggled)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) && STATE.containsMatchIn(it) }
			?: s.cleanName.takeIf { STATE.containsMatchIn(it) }
			?: lines.firstOrNull { STATE.containsMatchIn(it) && !it.startsWith("Click", true) }
			?: return null
		return STATE.find(pick)!!.value.lowercase().let { it == "on" || it == "enabled" }
	}

	fun isFiller(s: SlotText) = s.cleanName.isEmpty()
	fun clickable(s: SlotText) = s.cleanLore.any { it.startsWith("Click", true) || it.contains("click to", true) }
}

data class SalvageControls(val salvage: SlotText?, val assist: SlotText?, val adders: List<SlotText> = emptyList())

object SalvageParser {

	val TITLE = Regex("^Salvage Items?$")

	val BUTTON = Regex("^(?:Salvage Items?|Confirm Salvage)$", RegexOption.IGNORE_CASE)

	fun controls(slots: List<SlotText>) = SalvageControls(
		slots.firstOrNull { BUTTON.matches(it.cleanName) },
		slots.firstOrNull { it.cleanName.startsWith("Salvage Assist", true) },
		slots.filter { ADD.matches(it.cleanName) },
	)
	private val ADD = Regex("^Add .+ Items$", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val c = controls(slots)
		val salvage = c.salvage ?: return null
		val navSlots = HubText.navOf(slots).map { it.second }.toSet()
		val actions = listOfNotNull(

			HubTile(salvage.slot, salvage.cleanName, HubText.infoLines(salvage.lore).take(3), tooltip = HubText.clean(salvage.lore),
				highlight = salvage.cleanName.startsWith("Confirm", true)),
			c.assist?.let { a ->
				val on = SalvageLore.toggleState(a)

				HubTile(a.slot, a.cleanName, listOfNotNull(on?.let { if (it) "§aON" else "§cOFF" }), tooltip = HubText.clean(a.lore), highlight = on == true)
			}) +

			c.adders.map { HubTile(it.slot, it.cleanName, emptyList(), tooltip = HubText.clean(it.lore)) }
		val used = actions.map { it.slot }.toSet() + navSlots
		val rest = slots.filter { !SalvageLore.isFiller(it) && it.slot !in used }

		val (items, other) = rest.partition { HubText.rarity(it.lore) != null }
		return HubModel("Salvage Items", emptyList(),
			listOf(HubSection("Salvage", actions),
				HubSection("Items", items.map { HubTile(it.slot, it.cleanName, HubText.infoLines(it.lore).take(2), tooltip = HubText.clean(it.lore)) }),
				HubSection("Other", other.map { HubTile(it.slot, it.cleanName, emptyList(), tooltip = HubText.clean(it.lore), clickable = SalvageLore.clickable(it)) })).filter { it.tiles.isNotEmpty() },
			HubText.navOf(slots), emptyText = "Put dungeon items in to salvage")
	}
}

object MalikParser {
	val TITLE = Regex("^Dungeon Blacksmith$")

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val nav = HubText.navOf(slots); val navSlots = nav.map { it.second }.toSet()
		val filled = slots.filter { !SalvageLore.isFiller(it) && it.slot !in navSlots }
		if (filled.isEmpty()) return null
		val (actions, info) = filled.partition(SalvageLore::clickable)
		fun tile(s: SlotText) = HubTile(s.slot, s.cleanName, HubText.infoLines(s.lore).take(2), tooltip = HubText.clean(s.lore), clickable = SalvageLore.clickable(s))
		return HubModel("Dungeon Blacksmith", emptyList(), listOf(HubSection("Services", actions.map(::tile)), HubSection("Info", info.map(::tile))).filter { it.tiles.isNotEmpty() }, nav)
	}
}

object SalvageButtonLayout {
	const val GAP = 4
	const val MAX_W = 48
	const val PAD = 3
	const val ICON = 16
	const val LINE = 9

	data class Box(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<String>)

	fun isButton(t: HubTile) = SalvageParser.BUTTON.matches(t.name)

	fun clickAt(box: Box, t: HubTile, mx: Double, my: Double, packetButton: Int): Pair<Int, Int>? =
		if (t.locked || !t.clickable || mx < box.x || mx >= box.x + box.w || my < box.y || my >= box.y + box.h) null
		else t.slot to (if (packetButton == 1) 1 else t.button)

	fun box(x: Int, y: Int, w: Int, h: Int, label: String, measure: (String) -> Int, pad: Int = 6): Box? {
		val (ix, iy) = HubLayout.inventoryOrigin(x, y, w, h)
		val right = ix - GAP
		val bw = minOf(MAX_W, right - (x + pad))
		val inner = bw - 2 * PAD
		if (inner < ICON) return null

		if (label.split(' ').any { measure(it) > inner }) return null
		val lines = dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText(label, inner, measure)
		val bh = PAD + ICON + 2 + lines.size * LINE + PAD - 1
		if (bh > InventoryPanel.HEIGHT - 10) return null
		return Box(right - bw, iy + 10, bw, bh, lines)
	}

	fun withoutTile(m: HubModel, slot: Int) =
		m.copy(sections = m.sections.map { s -> s.copy(tiles = s.tiles.filter { it.slot != slot }) }.filter { it.tiles.isNotEmpty() })
}

@Config
object SalvageSettings : MenuSettings("salvage") {
	val reset by button("reset") { resetToDefaults() }
}

@Config
object MalikSettings : MenuSettings("malik") {
	val reset by button("reset") { resetToDefaults() }
}

object SalvageMenu : CustomMenu {
	override val id = "salvage"
	override val displayName = "Salvage Items"
	override val title = SalvageParser.TITLE
	override val settings get() = SalvageSettings

	override val defaultSize = 360 to 230 + InventoryPanel.RESERVED
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = SalvageView(screen)
}

object MalikMenu : CustomMenu {
	override val id = "malik"
	override val displayName = "Dungeon Blacksmith (Malik)"
	override val title = MalikParser.TITLE
	override val settings get() = MalikSettings
	override val defaultSize = 340 to 200
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = MalikView(screen)
}

class SalvageView(screen: AbstractContainerScreen<*>) : HubView(screen, { SalvageSettings }) {
	private var lastUnread: String? = null
	override fun parse(title: String, slots: List<SlotText>): HubModel? {
		val m = SalvageParser.parse(title, slots)

		if (m == null && SalvageParser.TITLE.matches(MenuText.clean(title))) {
			val why = "no salvage button (slot 40='${slots.firstOrNull { it.slot == 40 }?.cleanName.orEmpty()}')"
			if (why != lastUnread) { lastUnread = why; dev.ashtonite365.apex.Apex.logger.warn("[ApexSalvage] unreadable: $why") }
		} else lastUnread = null
		return m
	}
	override fun liveOnly(m: HubModel) = true

	override fun showsInventory(m: HubModel) = true

	override fun draw(ctx: GuiGraphicsExtractor, m: HubModel, mx: Int, my: Int) {
		val t = m.sections.firstOrNull()?.tiles?.firstOrNull(SalvageButtonLayout::isButton)
		val box = t?.let { SalvageButtonLayout.box(x, y, w, h, it.name, font::width) }
		if (t == null || box == null || drawingCached) return super.draw(ctx, m, mx, my)
		super.draw(ctx, SalvageButtonLayout.withoutTile(m, t.slot), mx, my)
		val hov = hovered(mx, my, box.x, box.y, box.w, box.h)
		MenuTheme.button(ctx, font, box.x, box.y, box.w, box.h, "", MenuTheme.state(hov, false, !t.locked), palette = palette())
		stacks[t.slot]?.let { ctx.item(it, box.x + (box.w - SalvageButtonLayout.ICON) / 2, box.y + SalvageButtonLayout.PAD) }
		var ly = box.y + SalvageButtonLayout.PAD + SalvageButtonLayout.ICON + 2
		for (l in box.lines) { ctx.text(font, l, box.x + (box.w - font.width(l)) / 2, ly, if (t.locked) MenuTheme.DIM else MenuTheme.WHITE); ly += SalvageButtonLayout.LINE }
		if (t.highlight) ctx.fill(box.x, box.y, box.x + 2, box.y + box.h, 0xFF55FF55.toInt())
		if (!t.locked && t.clickable) area(box.x, box.y, box.w, box.h) { b ->
			dev.ashtonite365.apex.Apex.logger.info("[ApexSalvage] box click '${t.name}' slot=${t.slot} button=$b container=${screen.menu.containerId} -> one click (ApexClickDelay)")
			press(m, t, b)
		}
		if (hov) ctx.setComponentTooltipForNextFrame(font, (listOf(t.name) + t.tooltip.ifEmpty { t.info }).map { Component.literal(it) }, mx, my)
	}
}

class MalikView(screen: AbstractContainerScreen<*>) : HubView(screen, { MalikSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = MalikParser.parse(title, slots)
}
