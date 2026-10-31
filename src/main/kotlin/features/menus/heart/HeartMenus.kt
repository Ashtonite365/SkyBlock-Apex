// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.heart

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.foraging.GalateaHelpers
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
import dev.ashtonite365.apex.features.mining.HotmCostTooltip
import dev.ashtonite365.apex.features.mining.HotmPresets
import dev.ashtonite365.apex.features.mining.MiningExtras
import dev.ashtonite365.apex.features.mining.MiningSolvers
import dev.ashtonite365.apex.features.plus.foraging.WhispersPerHotfPerk
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.customgui.customGui
import kotlin.time.Duration.Companion.seconds

enum class PerkState { MAXED, ENABLED, DISABLED, UNLOCKABLE, LOCKED, ABILITY, OTHER }
enum class TierState { UNLOCKED, PROGRESS, LOCKED }

data class HeartPerk(
	val slot: Int, val name: String, val level: Int?, val maxLevel: Int?, val state: PerkState,
	val cost: String?, val affordable: Boolean, val requires: String?, val selected: Boolean,
)
data class HeartTier(val slot: Int, val name: String, val state: TierState, val progress: String?, val perks: List<HeartPerk>, val requires: String? = null)
data class HeartModel(val title: String, val tiers: List<HeartTier>, val resources: List<Pair<String, String>>, val buttons: List<SlotText>, val nav: PageNav)

object HeartParser {
	const val CHEST_SIZE = 54
	const val ROWS = 5
	private val LEVEL = Regex("^Level (\\d+)(?:/(\\d+))?$")
	private val RESOURCE = Regex("^([A-Z][A-Za-z ]+?): ([\\d,]+)$")
	private val PROGRESS = Regex("^Progress: ([\\d.]+%)$")
	private val FRACTION = Regex("^([\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?)$")

	fun level(lore: List<String>): Pair<Int, Int?>? {
		for (l in lore) {
			if (l.contains("UPGRADE")) return null
			LEVEL.matchEntire(l)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toIntOrNull() }
		}
		return null
	}

	fun cost(lore: List<String>): String? {
		val i = lore.indexOfFirst { it == "Cost" }.takeIf { it >= 0 } ?: return null
		return lore.drop(i + 1).takeWhile { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotEmpty() }
	}

	fun perk(s: SlotText): HeartPerk {
		val l = s.cleanLore
		val lv = level(l)
		val requires = l.firstOrNull { it.startsWith("Requires ", true) }
		val ability = l.firstOrNull()?.let { Regex("^\\w+ Ability: ").containsMatchIn(it) } == true
		val selected = l.any { it.equals("SELECTED", true) || it.contains("currently selected", true) }
		val c = cost(l)
		val affordable = l.any { it.startsWith("Left-click to upgrade", true) || it.startsWith("Click to unlock", true) || it.startsWith("Click to upgrade", true) }
		val state = when {
			requires != null -> PerkState.LOCKED
			l.any { it == "DISABLED" } || l.any { it.startsWith("Click", true) && it.endsWith("to enable!") } -> PerkState.DISABLED
			ability -> PerkState.ABILITY
			lv != null && lv.second != null && lv.first >= lv.second!! || l.any { it.contains("MAXED", true) } -> PerkState.MAXED
			l.any { it == "ENABLED" } -> PerkState.ENABLED
			c != null -> PerkState.UNLOCKABLE
			lv != null -> PerkState.ENABLED
			else -> PerkState.OTHER
		}
		return HeartPerk(s.slot, s.cleanName, lv?.first, lv?.second, state, c, affordable, requires?.removePrefix("Requires "), selected)
	}

	fun tier(s: SlotText, perks: List<HeartPerk>): HeartTier {
		val l = s.cleanLore
		val state = when {
			l.any { it == "UNLOCKED" } -> TierState.UNLOCKED
			l.any { PROGRESS.matches(it) } -> TierState.PROGRESS
			else -> TierState.LOCKED
		}
		val progress = l.firstNotNullOfOrNull { PROGRESS.matchEntire(it)?.groupValues?.get(1) }?.let { p ->
			p + (l.firstNotNullOfOrNull { FRACTION.matchEntire(it)?.groupValues?.get(1) }?.let { " $it" } ?: "") }
		val requires = if (state == TierState.LOCKED) l.firstOrNull { it.startsWith("Requires ", true) }?.substring(9)?.trim() else null
		return HeartTier(s.slot, s.cleanName, state, progress, perks, requires)
	}

	fun parse(title: String, slots: List<SlotText>, titleRegex: Regex): HeartModel? {
		val t = MenuText.clean(title)
		if (!titleRegex.containsMatchIn(t)) return null
		val by = slots.filter { it.slot < CHEST_SIZE }.associateBy { it.slot }
		fun item(i: Int) = by[i]?.takeUnless { it.cleanName.isBlank() }
		val tiers = (0 until ROWS).mapNotNull { r ->
			val perks = (1..7).mapNotNull { c -> item(r * 9 + c) }.map(::perk)
			val label = item(r * 9) ?: return@mapNotNull if (perks.isEmpty()) null else HeartTier(r * 9, "", TierState.LOCKED, null, perks)
			tier(label, perks)
		}
		if (tiers.isEmpty()) return null
		val header = by.values.firstOrNull { it.slot >= ROWS * 9 && it.cleanName.equals(t, true) }
		val resources = header?.cleanLore?.mapNotNull { RESOURCE.matchEntire(it)?.let { m -> m.groupValues[1] to m.groupValues[2] } } ?: emptyList()
		val scrollUp = by.values.firstOrNull { it.cleanName.equals("Scroll Up", true) }?.slot
		val scrollDown = by.values.firstOrNull { it.cleanName.equals("Scroll Down", true) }?.slot
		val nav = TileParse.nav(slots, CHEST_SIZE).copy(prev = scrollUp, next = scrollDown)
		val skip = setOfNotNull(scrollUp, scrollDown, nav.back, nav.close, header?.slot)
		val buttons = (ROWS * 9 until CHEST_SIZE).mapNotNull { item(it) }.filter { it.slot !in skip }
		return HeartModel(t, tiers, resources, buttons, nav)
	}

	fun stateLabel(p: HeartPerk) = when (p.state) {
		PerkState.MAXED -> "§aMaxed"; PerkState.ENABLED -> "§aEnabled"; PerkState.DISABLED -> "§cDisabled"
		PerkState.UNLOCKABLE -> if (p.affordable) "§eUnlock" else "§6Locked"; PerkState.LOCKED -> "§8Needs ${p.requires}"
		PerkState.ABILITY -> if (p.selected) "§bSelected" else "§bAbility"; PerkState.OTHER -> ""
	}

	fun info(p: HeartPerk, showPct: Boolean, extra: String? = null): List<String> {
		val lv = p.level?.let { l -> "§7Lv §f$l" + (p.maxLevel?.let { m -> "§7/$m" + if (showPct && m > 0) " §8${l * 100 / m}%" else "" } ?: "") }
		val first = listOfNotNull(lv, stateLabel(p).takeIf { it.isNotEmpty() }).joinToString(" ")
		val second = extra ?: p.cost?.takeIf { p.state != PerkState.MAXED }?.let { (if (p.affordable) "§b" else "§8") + it }
		return listOfNotNull(first.takeIf { it.isNotEmpty() }, second)
	}

	fun tierName(t: HeartTier) = when (t.state) {
		TierState.UNLOCKED -> "${t.name} §a✔"
		TierState.PROGRESS -> "${t.name} §e${t.progress ?: ""}"
		TierState.LOCKED -> "${t.name} §8locked" + (t.requires?.let { " §7(needs $it)" } ?: "")
	}.trim()

	fun groups(m: HeartModel, showPct: Boolean, highlighted: Set<String> = emptySet(), extra: (HeartPerk) -> String? = { null }): List<TileGroup> =
		m.tiers.map { t ->
			TileGroup(tierName(t).ifEmpty { "Perks" }, t.perks.map { p ->
				val colour = when (p.state) { PerkState.DISABLED -> "§c"; PerkState.LOCKED -> "§8"; PerkState.MAXED -> "§a"; else -> "§f" }
				Tile(p.slot, colour + p.name, info(p, showPct, extra(p)), active = p.name in highlighted || p.selected)
			})
		} + if (m.buttons.isEmpty()) emptyList() else listOf(TileGroup("Menu", m.buttons.map { TileParse.buttonTile(it) }))
}

@Config
object HotmMenuSettings : MenuSettings("hotm") {
	val showCostToMax by toggle("show-cost-to-max") { true }
	val reset by button("reset") { resetToDefaults() }
}

@Config
object HotfMenuSettings : MenuSettings("hotf") {
	val showWhispers by toggle("show-whispers") { true }
	val reset by button("reset") { resetToDefaults() }
}

object HotmMenu : CustomMenu {
	override val id = "hotm"
	override val displayName = "Heart of the Mountain"
	override val title = MenuTitles.anyOf(Regex("^Heart of the Mountain$"))
	override val settings get() = HotmMenuSettings
	override val defaultSize = 560 to 300
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (HeartBridge.presetsActive(screen)) null else HeartView(screen, title, HotmMenuSettings, mountain = true)
}

object HotfMenu : CustomMenu {
	override val id = "hotf"
	override val displayName = "Heart of the Forest"
	override val title = MenuTitles.anyOf(Regex("^Heart of the Forest$"))
	override val settings get() = HotfMenuSettings
	override val defaultSize = 560 to 300
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = HeartView(screen, title, HotfMenuSettings, mountain = false)
}

object HeartBridge {

	fun presetsActive(screen: AbstractContainerScreen<*>): Boolean =
		screen.customGui is HotmPresets.HotmScrollPrompt || HotmPresets.hotmCommandSent.passedTime() < 5.seconds

	fun hotmCostToMax(p: HeartPerk): String? {
		if (!HotmMenuSettings.showCostToMax || !MiningSolvers.TConfig.hotmCosts) return null
		val level = p.level ?: return null
		val layout = RepoConstants.get("hotmlayout") ?: return null
		val perk = HotmCostTooltip.perks(layout)[p.name] ?: return null
		if (level >= perk.maxLevel) return null
		val cost = HotmCostTooltip.costToMax(HotmCostTooltip.prelude(layout), perk, level) ?: return null
		return "§7Max: §b${ApexFormatters.compactNumber(cost.toDouble())} ${perk.powder.lowercase().replaceFirstChar(Char::uppercase)}"
	}

	fun hotfWhispers(p: HeartPerk): String? {
		if (!HotfMenuSettings.showWhispers || !WhispersPerHotfPerk.TConfig.whispersSpent) return null
		val level = p.level ?: return null
		val perk = WhispersPerHotfPerk.Perk.byName(p.name) ?: return null
		val spent = perk.totalCost(level); val max = perk.totalCostMax
		return if (max <= 0) null else "§7Spent §e${ApexFormatters.compactNumber(spent.toDouble())} §8(${"%.0f".format(spent * 100.0 / max)}%)"
	}
}

class HeartView(screen: AbstractContainerScreen<*>, private val titleRegex: Regex, private val s: MenuSettings, private val mountain: Boolean) : TileViewBase<HeartModel>(screen) {
	override fun palette() = s.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = HeartParser.parse(title, slots, titleRegex)
	override fun title(m: HeartModel) = m.title
	override fun nav(m: HeartModel) = m.nav
	override fun subtitle(m: HeartModel) = m.resources.joinToString("  ") { (k, v) -> "§7$k §f$v" }
	override fun groups(m: HeartModel): List<TileGroup> {
		val pct = !mountain && GalateaHelpers.TConfig.hotfTotalProgress
		val highlighted = if (mountain) HotmPresets.highlightedPerks else emptySet()
		val g = HeartParser.groups(m, pct || mountain, highlighted) { p -> if (mountain) HeartBridge.hotmCostToMax(p) else HeartBridge.hotfWhispers(p) }

		return if (mountain && !MiningExtras.TConfig.disabledPerks) g.map { gr -> gr.copy(tiles = gr.tiles.map { it.copy(label = it.label.replace("§c", "§f")) }) } else g
	}
}
