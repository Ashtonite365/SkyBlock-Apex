// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.pets

import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.rebuildComponent
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.PageNav
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.Tile
import dev.ashtonite365.apex.features.menus.support.TileGroup
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.menus.support.TileViewBase
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.util.data.Config

enum class PetGrouping : StringRepresentable { TYPE, RARITY, NONE; override fun getSerializedName() = name }

data class PetEntry(
	val slot: Int, val name: String, val level: Int?, val rarity: String, val type: String,
	val heldItem: String?, val progressPct: Double?, val xp: String?, val maxLevel: Boolean, val active: Boolean,
	val candy: Int? = null,
)

data class PetsModel(val pets: List<PetEntry>, val nav: PageNav, val buttons: List<SlotText> = emptyList(), val selectedName: String? = null, val header: SlotText? = null) {

	val active get() = pets.firstOrNull { it.active }
}

object PetsParser {

	val TITLE = MenuTitles.anyOf(Regex("^Pets(?: \\(\\d+/\\d+\\))?$", RegexOption.IGNORE_CASE), MenuTitles.cutPrefixOf("Pets"))
	const val CHEST_SIZE = 54
	private val LEVEL_NAME = Regex("^\\[Lvl (\\d+)]\\s*(?:\\[[^]]*]\\s*)?(.+)$")
	private val HELD = Regex("^Held Item: (.+)$")
	private val PROGRESS = Regex("^Progress to Level \\d+: ([\\d.]+)%$")
	private val XP = Regex("^[-\\s]*([\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?)$")
	private val TYPE = Regex("^(\\w+) (?:Pet|Mount)\\b", RegexOption.IGNORE_CASE)
	private val CANDY = Regex("^\\((\\d+)/\\d+\\) Pet Candy Used$")
	private val SELECTED = Regex("^Selected pet: (.+)$")
	private val CHOICE = Regex("^▶\\s*(.+)$")
	private val TOTAL_XP = Regex("^▸?\\s*([\\d,.]+ XP)$")
	private val RARITY_COLOURS = mapOf('f' to "Common", 'a' to "Uncommon", '9' to "Rare", '5' to "Epic", '6' to "Legendary", 'd' to "Mythic", 'b' to "Divine", 'c' to "Special")
	val RARITY_ORDER = listOf("Mythic", "Legendary", "Epic", "Rare", "Uncommon", "Common", "Divine", "Special", "Unknown")

	fun rarity(rawName: String): String {
		val after = rawName.substringAfter("]", rawName)
		val c = Regex("§([0-9a-fk-or])").findAll(after).map { it.groupValues[1][0] }.firstOrNull { it in RARITY_COLOURS }
		return c?.let { RARITY_COLOURS[it] } ?: "Unknown"
	}

	fun pet(s: SlotText): PetEntry? {
		val m = LEVEL_NAME.matchEntire(s.cleanName) ?: return null
		val lore = s.cleanLore
		val active = lore.any { it.contains("Click to despawn", true) || it.contains("currently summoned", true) }
		return PetEntry(
			s.slot, m.groupValues[2].trim(), m.groupValues[1].toIntOrNull(), rarity(s.name),
			lore.firstNotNullOfOrNull { TYPE.find(it)?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase) } ?: "Other",
			TileParse.lore(s, HELD), TileParse.lore(s, PROGRESS)?.toDoubleOrNull(), TileParse.lore(s, XP) ?: TileParse.lore(s, TOTAL_XP),
			lore.any { it.contains("MAX LEVEL", true) }, active, TileParse.lore(s, CANDY)?.toIntOrNull())
	}

	fun parse(title: String, slots: List<SlotText>): PetsModel? {
		val t = MenuText.clean(title)
		if (!TITLE.containsMatchIn(t)) return null
		val chest = slots.filter { it.slot < CHEST_SIZE }
		val pets = chest.mapNotNull(::pet)
		val nav = TileParse.nav(chest, CHEST_SIZE, TileParse.page(t))
		if (pets.isEmpty() && nav.close == null && nav.back == null) return null
		val petSlots = pets.map { it.slot }.toSet()
		val header = chest.firstOrNull { it.cleanName.equals("Pets", true) }
		val buttons = chest.filter { it.slot !in petSlots && !TileParse.isFiller(it) && it !== header }
		val selected = header?.let { TileParse.lore(it, SELECTED) }?.takeUnless { it.equals("None", true) }
		return PetsModel(pets, nav, buttons, selected, header)
	}

	fun group(pets: List<PetEntry>, g: PetGrouping): List<Pair<String, List<PetEntry>>> = when (g) {
		PetGrouping.NONE -> if (pets.isEmpty()) emptyList() else listOf("All pets" to pets)
		PetGrouping.RARITY -> pets.groupBy { it.rarity }.toList().sortedBy { RARITY_ORDER.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
		PetGrouping.TYPE -> pets.groupBy { it.type }.toList().sortedWith(compareBy({ it.first == "Other" }, { it.first }))
	}

	fun buttonTile(s: SlotText): Tile = s.cleanLore.firstNotNullOfOrNull { CHOICE.matchEntire(it)?.groupValues?.get(1) }
		?.let { Tile(s.slot, s.cleanName, listOf("§e▶ $it")) } ?: TileParse.buttonTile(s)

	fun sections(m: PetsModel, g: PetGrouping): List<TileGroup> = group(m.pets, g).map { (n, ps) ->
		TileGroup(n, ps.map { Tile(it.slot, (if (it.active) "§a" else "§f") + it.name, info(it), it.active) })
	}

	data class BarIcon(val slot: Int, val name: String, val lore: List<String>)

	fun bottomBar(m: PetsModel): List<BarIcon> {
		val nav = setOfNotNull(m.nav.prev, m.nav.next, m.nav.back, m.nav.close)
		return m.buttons.filter { it.slot !in nav }.sortedBy { it.slot }.map { BarIcon(it.slot, it.cleanName, it.cleanLore) }
	}

	fun subtitle(m: PetsModel): String = m.active?.let { "§aActive: §f${it.name} §7Lv ${it.level ?: "?"}" + (it.heldItem?.let { h -> " §8(§b$h§8)" } ?: "") }
		?: m.selectedName?.let { "§aActive: §f$it §8(other page)" } ?: "§8No active pet"

	fun info(p: PetEntry): List<String> = listOfNotNull(
		buildString {
			append("§7Lv ").append(p.level ?: "?")
			when {
				p.maxLevel -> append(" §6MAX")
				p.progressPct != null -> append(" §a").append(p.progressPct).append('%')
			}
			if (!p.maxLevel && p.xp != null) append(" §8").append(p.xp)
		},
		"§b" + (p.heldItem ?: "§8no item") + (p.candy?.takeIf { it > 0 }?.let { " §d${it}c" } ?: ""))
}

object PetTooltip {
	sealed interface Resolved<out T> {
		data class Show<T>(val item: T) : Resolved<T>
		data class Missing(val reason: String) : Resolved<Nothing>
	}

	fun <T : Any> resolve(slot: Int, itemAt: (Int) -> T?): Resolved<T> =
		itemAt(slot)?.let { Resolved.Show(it) } ?: Resolved.Missing("pet slot $slot is empty or missing")

	private var lastMissing: String? = null

	fun lines(screen: AbstractContainerScreen<*>, slot: Int): List<Component>? =
		when (val r = resolve(slot) { i -> screen.menu.slots.getOrNull(i)?.item?.takeIf { !it.isEmpty } }) {
			is Resolved.Missing -> {
				if (lastMissing != r.reason) runCatching { dev.ashtonite365.apex.Apex.logger.info("[ApexMenus] Pets tooltip: ${r.reason}, no tooltip shown") }
				lastMissing = r.reason; null
			}
			is Resolved.Show -> {
				lastMissing = null
				val font = Client.font
				val w = (screen.width / 2 - 16).coerceAtLeast(100)
				Screen.getTooltipFromItem(Client.game, r.item).flatMap { c ->
					font.splitter.splitLines(c, w, net.minecraft.network.chat.Style.EMPTY)
						.map { it.rebuildComponent() as Component }.ifEmpty { listOf(Component.empty()) }
				}
			}
		}
}

@Config
object PetsSettings : MenuSettings("pets") {
	val grouping by choice("grouping") { PetGrouping.TYPE }
	val showButtons by toggle("show-buttons") { true }
	val reset by button("reset") { resetToDefaults() }
}

object PetsMenu : CustomMenu {
	override val id = "pets"
	override val displayName = "Pets"
	override val title = PetsParser.TITLE
	override val settings get() = PetsSettings
	override val defaultSize = 540 to 300 + InventoryPanel.RESERVED

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = PetsView(screen)
}

class PetsView(screen: AbstractContainerScreen<*>) : TileViewBase<PetsModel>(screen) {
	override fun palette() = PetsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = PetsParser.parse(title, slots)
	override fun title(m: PetsModel) = "Pets"
	override fun nav(m: PetsModel) = m.nav
	override fun subtitle(m: PetsModel) = PetsParser.subtitle(m)

	override fun showsInventory(m: PetsModel) = true
	override fun groups(m: PetsModel) = PetsParser.sections(m, PetsSettings.grouping)

	override fun tileTooltip(t: Tile, group: String): List<Component>? = PetTooltip.lines(screen, t.slot)

	override fun draw(ctx: GuiGraphicsExtractor, m: PetsModel, mx: Int, my: Int) {
		super.draw(ctx, m, mx, my)
		if (PetsSettings.showButtons) drawBottomBar(ctx, m, mx, my)
	}

	private fun drawBottomBar(ctx: GuiGraphicsExtractor, m: PetsModel, mx: Int, my: Int) {
		val icons = PetsParser.bottomBar(m)
		if (icons.isEmpty()) return
		val p = palette()
		val size = 18
		val right = x + w - 4 - (if (m.nav.next != null) font.width("Next >") + 16 else 0)
		var bx = right - icons.size * (size + 2) + 2
		val by = y + h - 20
		var tip: List<Component>? = null
		for (ic in icons) {
			val hover = MenuTheme.inside(mx, my, bx, by, size, size)
			MenuTheme.frame(ctx, bx, by, bx + size, by + size, if (hover) p.fillHover else p.fill, if (hover) p.borderHover else p.border)
			val stack = stacks[ic.slot]
			if (stack != null) ctx.item(stack, bx + 1, by + 1) else ctx.text(font, ic.name.take(1), bx + 6, by + 5, MenuTheme.WHITE)
			if (hover) tip = stack?.let { Screen.getTooltipFromItem(Client.game, it) }
				?: (listOf(ic.name) + ic.lore).map { Component.literal(it) }
			val slot = ic.slot
			area(bx, by, size, size) { b -> clickSlot(slot, b) }
			bx += size + 2
		}
		tip?.let { ctx.setComponentTooltipForNextFrame(font, it, mx, my) }
	}
}
