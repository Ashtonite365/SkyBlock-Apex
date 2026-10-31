// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.rng

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.PageNav
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.Tile
import dev.ashtonite365.apex.features.menus.support.TileGroup
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.menus.support.TileViewBase
import dev.ashtonite365.apex.features.skills.SlayerOverlay
import dev.ashtonite365.apex.util.data.Config

data class RngDrop(val slot: Int, val name: String, val tier: String, val chancePct: Double?, val xpLabel: String, val xp: Long?, val xpNeeded: Long?, val selected: Boolean) {
	val progress: Double? get() = if (xp != null && xpNeeded != null && xpNeeded > 0) (xp.toDouble() / xpNeeded).coerceIn(0.0, 1.0) else null
}
data class RngModel(val title: String, val drops: List<RngDrop>, val stored: String?, val buttons: List<SlotText>, val nav: PageNav, val header: SlotText?) {
	val selected get() = drops.firstOrNull { it.selected }
}

object RngMeterParser {
	const val CHEST_SIZE = 54
	const val MIN_DROPS = 3
	private val ODDS = Regex("^Odds: (.+?)(?: \\(([\\d.]+)%\\))?$")
	private val XP = Regex("^(.+? XP): ([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)$")
	private val STORED = Regex("^(Stored .+?): ([\\d,.]+[kKmMbB]?)$")

	fun drop(s: SlotText): RngDrop? {
		val l = s.cleanLore
		val odds = l.firstNotNullOfOrNull { ODDS.matchEntire(it) } ?: return null
		val xp = l.firstNotNullOfOrNull { XP.matchEntire(it) } ?: return null
		val selected = l.any { it.equals("SELECTED", true) || it.startsWith("Click to deselect", true) || it.contains("currently selected", true) }
		return RngDrop(s.slot, s.cleanName, odds.groupValues[1].trim(), odds.groupValues[2].toDoubleOrNull(), xp.groupValues[1],
			amount(xp.groupValues[2]), amount(xp.groupValues[3]), selected)
	}

	fun amount(s: String): Long? {
		val m = Regex("^([\\d,.]+)([kKmMbB]?)$").matchEntire(s.trim()) ?: return null
		val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		val mult = when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
		return kotlin.math.floor(base * mult).toLong()
	}

	fun isRngMeter(slots: List<SlotText>) = slots.count { it.slot < CHEST_SIZE && drop(it) != null } >= MIN_DROPS

	fun parse(title: String, slots: List<SlotText>): RngModel? {
		val chest = slots.filter { it.slot < CHEST_SIZE }
		if (!isRngMeter(chest)) return null
		val t = MenuText.clean(title)
		val drops = chest.mapNotNull(::drop)
		val header = chest.firstOrNull { it.cleanName.equals(t, true) } ?: chest.firstOrNull { it.cleanName.contains("RNG Meter", true) && drop(it) == null }
		val stored = header?.cleanLore?.firstNotNullOfOrNull { STORED.matchEntire(it)?.let { m -> "${m.groupValues[1]}: ${m.groupValues[2]}" } }
		val nav = TileParse.nav(chest, CHEST_SIZE, TileParse.page(t))
		val dropSlots = drops.map { it.slot }.toSet()
		val skip = setOfNotNull(header?.slot, nav.back, nav.close, nav.prev, nav.next)
		val buttons = chest.filter { it.slot !in dropSlots && it.slot !in skip && !TileParse.isFiller(it) }
		return RngModel(t, drops, stored, buttons, nav, header)
	}

	fun info(d: RngDrop): List<String> = listOfNotNull(
		d.progress?.let { "§a${"%.1f".format(it * 100)}% §8${MenuText.coins((d.xp ?: 0).toDouble())}/${MenuText.coins((d.xpNeeded ?: 0).toDouble())}" },
		d.chancePct?.let { "§7${it}%" + if (d.selected) " §bSelected" else "" } ?: if (d.selected) "§bSelected" else null)

	fun groups(m: RngModel): List<TileGroup> =
		m.drops.groupBy { it.tier }.map { (tier, ds) -> TileGroup(tier, ds.map { Tile(it.slot, it.name, info(it), it.selected) }) } +
			if (m.buttons.isEmpty()) emptyList() else listOf(TileGroup("Menu", m.buttons.map { TileParse.buttonTile(it) }))
}

@Config
object RngMeterSettings : MenuSettings("rng-meter") {
	val reset by button("reset") { resetToDefaults() }
}

object RngMeterMenu : CustomMenu {
	override val id = "rng-meter"
	override val displayName = "RNG Meter"

	override val title = Regex("(?!)")
	override val slotPredicate: (String, List<SlotText>) -> Boolean = { _, slots -> RngMeterParser.isRngMeter(slots) }
	override val settings get() = RngMeterSettings
	override val defaultSize = 520 to 280
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = RngMeterView(screen)
}

class RngMeterView(screen: AbstractContainerScreen<*>) : TileViewBase<RngModel>(screen) {
	override fun palette() = RngMeterSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = RngMeterParser.parse(title, slots)
	override fun title(m: RngModel) = m.title
	override fun nav(m: RngModel) = m.nav
	override fun subtitle(m: RngModel) = listOfNotNull(
		m.selected?.let { "§bSelected: §f${it.name}" } ?: "§8No drop selected",
		m.stored?.let { "§7$it" },
		SlayerOverlay.rngMeterLine?.takeIf { m.drops.any { d -> d.xpLabel.contains("Slayer", true) } }?.let { "§d$it" }).joinToString("  ")
	override fun groups(m: RngModel) = RngMeterParser.groups(m)
}
