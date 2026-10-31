// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.pets

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.DynamicPage
import dev.ashtonite365.apex.features.menus.support.DynamicParse
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.Tile
import dev.ashtonite365.apex.features.menus.support.TileGroup
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.support.PurseReadout
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object PetCareParser {
	val SITTER = Regex("^Pet Sitter$", RegexOption.IGNORE_CASE)
	val OFFER = Regex("^Offer Pets$", RegexOption.IGNORE_CASE)
	val TRAINING = Regex("^Pet Training$", RegexOption.IGNORE_CASE)
	const val PETS = "Pets"
	const val SLOTS = "Slots"
	const val LOCKED = "Locked"
	const val INFO = "Info"

	const val PREVIEW = "Fann's Pet (preview)"

	fun chestSize(windowSlots: Int) = (windowSlots - 36).coerceAtLeast(9)

	fun parse(title: String, slots: List<SlotText>, regex: Regex, chest: Int) = DynamicParse.parse(title, slots, regex, chest)

	fun status(s: SlotText): String {
		val first = s.cleanLore.firstOrNull { it.isNotBlank() } ?: ""
		return when {
			PetsParser.pet(s) != null && TileParse.loreHas(s, "This is a preview") -> PREVIEW
			PetsParser.pet(s) != null -> PETS
			first.equals("Locked", true) || TileParse.loreHas(s, "Unlock this slot") -> LOCKED
			s.cleanName.contains("Slot", true) -> SLOTS
			else -> INFO
		}
	}

	private val TIME = Regex("(\\d+[dhms](?:\\s*\\d+[dhms])*)")
	fun info(s: SlotText): List<String> {
		PetsParser.pet(s)?.let { return PetsParser.info(it) }
		val first = s.cleanLore.firstOrNull { it.isNotBlank() }
		val extra = s.cleanLore.firstOrNull { it.contains("Progress", true) || it.contains("Time", true) && TIME.containsMatchIn(it) || it.contains(": ") && !it.startsWith("/") }
		return listOfNotNull(first?.let { "§7$it" }, extra?.takeIf { it != first }?.let { "§e$it" })
	}

	fun placer(p: DynamicPage) = (listOfNotNull(p.header) + p.items).firstOrNull { TileParse.loreHas(it, "Place a pet above") }

	fun petSlot(p: DynamicPage) = placer(p)?.slot?.minus(9)?.takeIf { it >= 0 }

	fun groups(p: DynamicPage): List<TileGroup> {
		val placer = placer(p)
		val slot = petSlot(p)
		val placed = p.items.firstOrNull { it.slot == slot }
		val pet = slot?.let { listOf(TileGroup("Place pet", listOf(
			if (placed != null) Tile(slot, placed.cleanName, info(placed), active = true)
			else Tile(slot, "Pet slot", listOf("§7Click with a pet"))))) } ?: emptyList()
		return pet + rest(p.copy(items = p.items.filter { it.slot != slot && it !== placer }))
	}

	private fun rest(p: DynamicPage): List<TileGroup> {
		val order = listOf(SLOTS, PETS, PREVIEW, INFO, LOCKED)
		return DynamicParse.groupBy(p.items, ::status).sortedBy { order.indexOf(it.first) }
			.map { (n, l) -> TileGroup(n, l.map { Tile(it.slot, it.cleanName, info(it), active = n == SLOTS && !TileParse.loreHas(it, "Empty")) }) }
	}
}

enum class CellKind { INPUT, BUTTON, PREVIEW }
data class PetCareCell(val slot: Int, val row: Int, val col: Int, val kind: CellKind, val item: SlotText?)

data class PetCareModel(val page: DynamicPage, val slots: List<SlotText>, val chest: Int)

object PetCareLayout {
	fun isFiller(s: SlotText) = s.cleanName.isBlank()

	fun cells(m: PetCareModel): List<PetCareCell> {
		val input = PetCareParser.petSlot(m.page)
		val bySlot = m.slots.filter { it.slot < m.chest }.associateBy { it.slot }
		val out = mutableListOf<PetCareCell>()
		for (slot in 0 until m.chest) {
			val it = bySlot[slot]
			val kind = when {
				slot == input -> CellKind.INPUT
				it == null || isFiller(it) -> continue
				PetCareParser.status(it) == PetCareParser.PREVIEW -> CellKind.PREVIEW
				else -> CellKind.BUTTON
			}
			out += PetCareCell(slot, slot / 9, slot % 9, kind, it?.takeUnless { s -> isFiller(s) })
		}
		return out
	}

	fun rows(chest: Int) = (chest + 8) / 9
}

@Config
object PetCareSettings : MenuSettings("pet-care") {
	val reset by button("reset") { resetToDefaults() }
}

abstract class PetCareMenu(override val id: String, override val displayName: String, override val title: Regex) : CustomMenu {
	override val settings get() = PetCareSettings

	override val defaultSize = 220 to 22 + PurseReadout.LINE_H + 2 + 6 * 18 + 4 + InventoryPanel.HEIGHT + 8
	override fun create(screen: AbstractContainerScreen<*>): MenuView = PetCareView(screen, title, displayName)
}

object PetSitterMenu : PetCareMenu("pet-sitter", "Pet Sitter (Kat)", PetCareParser.SITTER) {
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
}
object OfferPetsMenu : PetCareMenu("offer-pets", "Offer Pets (George)", PetCareParser.OFFER) {
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
}
object PetTrainingMenu : PetCareMenu("pet-training", "Pet Training", PetCareParser.TRAINING) {
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
}

class PetCareView(screen: AbstractContainerScreen<*>, private val regex: Regex, private val name: String) : ApexViewBase<PetCareModel>(screen) {
	override fun palette() = PetCareSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>): PetCareModel? {
		val chest = PetCareParser.chestSize(screen.menu.slots.size)
		val page = PetCareParser.parse(title, slots, regex, chest) ?: return null
		return PetCareModel(page, slots, chest)
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: PetCareModel, mx: Int, my: Int) {
		val p = palette()
		ctx.text(font, "§l$name", x + 6, y + 7, p.accent)
		val vw = font.width("Vanilla") + 10

		var tip: List<Component>? = null
		val gx = x + (w - 9 * 18) / 2

		val purseH = PurseReadout.draw(ctx, font, x + 6, y + 22, w - 12)
		val gy = y + 22 + if (purseH > 0) purseH + 2 else 0
		for (c in PetCareLayout.cells(m)) {
			val cx = gx + c.col * 18; val cy = gy + c.row * 18
			val hov = MenuTheme.inside(mx, my, cx, cy, 18, 18)
			val accent = c.kind == CellKind.INPUT
			MenuTheme.frame(ctx, cx, cy, cx + 18, cy + 18, if (hov) p.fillHover else p.fill, if (accent) p.accent else if (hov) p.borderHover else p.border)
			val st = screen.menu.slots.getOrNull(c.slot)?.item?.takeUnless { it.isEmpty }
			val slot = c.slot
			if (c.kind == CellKind.INPUT) {

				realSlotCell(slot, cx, cy)
				if (hov && st == null) tip = listOf(Component.literal("§7Place a pet here"))
				continue
			}
			st?.let { ctx.item(it, cx + 1, cy + 1); ctx.itemDecorations(font, it, cx + 1, cy + 1) }
			if (hov) {
				val base = st?.let { Screen.getTooltipFromItem(Client.game, it) }
				tip = if (c.kind == CellKind.PREVIEW) listOf(Component.literal("§e${PetCareParser.PREVIEW} §8(not your pet)")) + (base ?: emptyList()) else base
			}
			area(cx, cy, 18, 18) { b -> clickSlot(slot, b) }
		}
		val iy = gy + PetCareLayout.rows(m.chest) * 18 + 4
		tip?.let { if (screen.menu.carried.isEmpty) ctx.setComponentTooltipForNextFrame(font, it, mx, my) }

		inventoryPanel(ctx, mx, my, gx, iy)
	}
}
