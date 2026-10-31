// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.ah

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSession
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view

data class HubSlot(val index: Int, val name: String, val material: String)

enum class HubRole { BROWSE, BIDS, CREATE, CLOSE, EXTRA }

object AhHubModel {

	val TITLE = Regex("^(?i)(?:Co-?op\\s+)?Auction\\s+House$")

	private val BY_MATERIAL = mapOf(
		"gold_block" to HubRole.BROWSE,
		"golden_carrot" to HubRole.BIDS,
		"golden_axe" to HubRole.CREATE, "golden_hoe" to HubRole.CREATE, "gold_ingot" to HubRole.CREATE,
		"gold_nugget" to HubRole.CREATE, "golden_horse_armor" to HubRole.CREATE,
		"barrier" to HubRole.CLOSE,
	)

	fun roleOf(s: HubSlot): HubRole? {
		val label = MenuText.clean(s.name).lowercase()
		return when {
			"browse" in label -> HubRole.BROWSE
			"bids" in label -> HubRole.BIDS
			"create" in label || "manage auctions" in label -> HubRole.CREATE
			label == "close" -> HubRole.CLOSE
			else -> BY_MATERIAL[s.material]
		}
	}

	fun parse(title: String, slots: List<HubSlot>): Map<HubRole, HubSlot>? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val found = LinkedHashMap<HubRole, HubSlot>()
		val named = slots.filter { MenuText.clean(it.name).isNotEmpty() && !it.material.endsWith("stained_glass_pane") }
		for (slot in named.sortedBy { it.index }) {
			val role = roleOf(slot) ?: HubRole.EXTRA
			if (role !in found) found[role] = slot
		}
		return found.takeIf { HubRole.BROWSE in it }
	}
}

@Config
object AhHubSettings : MenuSettings("ah-hub") {
	val reset by button("reset") { resetToDefaults() }
}

object AhHubMenu : CustomMenu {
	override val id = "ah-hub"
	override val displayName = "Auction House (hub)"
	override val title = AhHubModel.TITLE
	override val settings get() = AhHubSettings
	override val defaultSize = 240 to 130

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = AhHubView(screen)
}

private class AhHubView(val screen: AbstractContainerScreen<*>) : MenuView {
	override var x = 0; override var y = 0; override var w = 0; override var h = 0
	private var session: MenuSession? = null
	private val openedAt = System.currentTimeMillis()

	private val hits = mutableListOf<Pair<IntArray, Int>>()

	override fun layout(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }

	override fun attached(session: MenuSession) { this.session = session }

	private fun chestSlots(): List<HubSlot> {
		val chestSize = (screen.menu.slots.size - Inventory.INVENTORY_SIZE).coerceAtLeast(0)
		return screen.menu.slots.take(chestSize).mapIndexedNotNull { i, slot ->
			val stack = slot.item
			if (stack.isEmpty) null else HubSlot(i, stack.hoverName.bareText, BuiltInRegistries.ITEM.getKey(stack.item).path)
		}
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		val font = Client.font
		val palette = AhHubSettings.customisation.palette
		MenuTheme.panel(ctx, x, y, w, h, palette)
		ctx.centeredText(font, "§6" + MenuText.clean(screen.title.string), x + w / 2, y + 6, MenuTheme.WHITE)
		hits.clear()
		val roles = AhHubModel.parse(screen.title.string, chestSlots())
		if (roles == null) {
			if (System.currentTimeMillis() - openedAt > ContainerGate.GRACE_MS) session?.fallback("auction house hub not recognised")
			else ctx.centeredText(font, "§7Loading…", x + w / 2, y + h / 2 - 4, MenuTheme.WHITE)
			return
		}
		val gap = 6
		val bigW = (w - gap * 4) / 3
		val bigH = (h - 18 - 22 - gap * 3).coerceAtLeast(36)
		val bigY = y + 18 + gap
		listOf(HubRole.BROWSE, HubRole.BIDS, HubRole.CREATE).forEachIndexed { i, role ->
			roles[role]?.let { tile(ctx, it, x + gap + i * (bigW + gap), bigY, bigW, bigH, mouseX, mouseY, big = true) }
		}
		val small = listOfNotNull(roles[HubRole.CLOSE], roles[HubRole.EXTRA])
		val smallW = 90
		val rowW = small.size * smallW + (small.size - 1).coerceAtLeast(0) * gap
		small.forEachIndexed { i, slot ->
			tile(ctx, slot, x + (w - rowW) / 2 + i * (smallW + gap), bigY + bigH + gap, smallW, 20, mouseX, mouseY, big = false)
		}
	}

	private fun tile(ctx: GuiGraphicsExtractor, slot: HubSlot, tx: Int, ty: Int, tw: Int, th: Int, mouseX: Int, mouseY: Int, big: Boolean) {
		val font = Client.font
		val palette = AhHubSettings.customisation.palette
		val over = MenuTheme.inside(mouseX, mouseY, tx, ty, tw, th)
		MenuTheme.button(ctx, font, tx, ty, tw, th, "", MenuTheme.state(over), palette = palette)
		val stack = screen.menu.slots[slot.index].item
		val name = MenuText.clean(slot.name)
		if (big) {
			ctx.item(stack, tx + tw / 2 - 8, ty + th / 2 - 14)
			MenuTheme.fittedLabel(ctx, font, name, tx + tw / 2, ty + th / 2 + 4, tw - 6, maxOf(9, th / 2 - 6), palette.text, centreY = false)
		} else {
			ctx.item(stack, tx + 2, ty + (th - 16) / 2)
			MenuTheme.fittedLabel(ctx, font, name, tx + 20 + (tw - 22) / 2, ty, tw - 22, th, palette.text, centreY = true)
		}
		hits += intArrayOf(tx, ty, tw, th) to slot.index
		if (over) {
			val lore = stack.view().loreAccordingToNbt
			ctx.setComponentTooltipForNextFrame(font, listOf(stack.hoverName) + lore, mouseX, mouseY)
		}
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return contains(click.x(), click.y())
		val hit = hits.firstOrNull { (box, _) -> MenuTheme.inside(click.x().toInt(), click.y().toInt(), box[0], box[1], box[2], box[3]) }
			?: return contains(click.x(), click.y())
		MenuClicks.click(screen.menu, screen.menu.slots[hit.second])
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = false
	override fun keyPressed(input: KeyEvent) = false
	override fun charTyped(input: CharacterEvent) = false
}
