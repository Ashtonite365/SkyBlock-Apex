// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.bestiary

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.collections.LoreProgress
import dev.ashtonite365.apex.features.menus.collections.TileMenuView
import dev.ashtonite365.apex.features.menus.collections.TileSpec
import dev.ashtonite365.apex.features.menus.support.SlotText
import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

@Config
object BestiaryMenuSettings : MenuSettings("bestiary") {
	val reset by button("reset") { resetToDefaults() }
}

@Config
object BestiaryAreaMenuSettings : MenuSettings("bestiary-areas") {

	val grouped by toggle("grouped") { true }

	val showLocked by toggle("show-locked") { true }
	val reset by button("reset") { resetToDefaults() }
}

@Config
object BestiaryMilestonesMenuSettings : MenuSettings("bestiary-milestones") {

	val remember by toggle("remember") { true }
	val reset by button("reset") { resetToDefaults() }
}

object BestiaryMenu : CustomMenu {
	override val id = "bestiary"
	override val displayName = "Bestiary"
	override val title = BestiaryParser.MAIN
	override val settings get() = BestiaryMenuSettings
	override val defaultSize = 420 to 240
	@Subscribe
	fun onInit(event: ClientInitEvent) {
		MenuRegistry.register(this); MenuRegistry.register(BestiaryAreaMenu); MenuRegistry.register(BestiaryMilestonesMenu)
	}
	override fun create(screen: AbstractContainerScreen<*>): MenuView = BestiaryView(screen, BestiaryMenuSettings)
}

object BestiaryAreaMenu : CustomMenu {
	override val id = "bestiary-areas"
	override val displayName = "Bestiary Areas"
	override val title = BestiaryParser.AREA

	override val slotPredicate: ((String, List<SlotText>) -> Boolean) get() = BestiaryParser::isSubAreaPage
	override val settings get() = BestiaryAreaMenuSettings
	override val defaultSize = 470 to 280
	override fun create(screen: AbstractContainerScreen<*>): MenuView = BestiaryView(screen, BestiaryAreaMenuSettings)
}

object BestiaryMilestonesMenu : CustomMenu {
	override val id = "bestiary-milestones"
	override val displayName = "Bestiary Milestones"
	override val title = BestiaryParser.MILESTONES
	override val settings get() = BestiaryMilestonesMenuSettings
	override val defaultSize = 470 to 260
	override fun create(screen: AbstractContainerScreen<*>): MenuView = BestiaryView(screen, BestiaryMilestonesMenuSettings)
}

@Serializable
data class BestiaryMilestoneData(val seen: MutableMap<Int, BeastMilestone> = mutableMapOf())

@Config
object BestiaryMilestoneMemory : ProfileSpecificDataHolder<BestiaryMilestoneData>(BestiaryMilestoneData.serializer(), "bestiary-milestones", ::BestiaryMilestoneData) {
	var seen: Map<Int, BeastMilestone>
		get() = runCatching { data.seen }.getOrNull().orEmpty()
		set(v) { runCatching { val d = data; if (d.seen != v) { d.seen.clear(); d.seen.putAll(v); markDirty() } } }
}

class BestiaryView(screen: AbstractContainerScreen<*>, private val s: MenuSettings) : TileMenuView<BestiaryPage>(screen) {
	override fun palette() = s.customisation.palette
	override fun parse(title: String, slots: List<SlotText>): BestiaryPage? {
		val p = BestiaryParser.parse(title, slots, screen.menu.slots.size - 36) ?: return null
		if (p is BestiaryPage.Milestones) BestiaryMilestoneMemory.seen = BestiaryParser.remember(BestiaryMilestoneMemory.seen, p.tiles)
		return p
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: BestiaryPage, mx: Int, my: Int) {
		val bottom = y + h - 24
		when (m) {
			is BestiaryPage.Main -> {
				val top = header(ctx, mx, my, "Bestiary", m.found, listOfNotNull(m.completed?.let(::fracLine),
					m.milestone?.let { "§7Milestone §b${it.level}" + (it.totalTiers?.let { t -> " §8· §7${fmtNum(t)} tiers" } ?: "") }).joinToString(" §8· "))
				groups(ctx, mx, my, top, bottom, listOf("Areas" to m.areas.map(::areaTile)) + milestoneGroup(m.milestone))
				navRow(ctx, mx, my, navOf(m.nav, m.toggle))
			}
			is BestiaryPage.Area -> {
				val suffix = m.page?.let { " §8(${it.first}/${if (it.second > 0) it.second else "?"})" } ?: ""
				val top = header(ctx, mx, my, m.area + suffix, m.found, listOfNotNull(m.completed?.let(::fracLine),
					if (m.families.isEmpty()) null else "§a${m.unlocked}§7 found on this page §8· §6${m.maxed}§7 maxed").joinToString(" §8· "))
				val fams = m.families.filter { BestiaryAreaMenuSettings.showLocked || !it.locked }
				val g = mutableListOf<Pair<String, List<TileSpec>>>()
				if (m.subAreas.isNotEmpty()) g += "Areas" to m.subAreas.map(::areaTile)
				if (BestiaryAreaMenuSettings.grouped) {
					g += "In progress" to fams.filter { !it.locked && !it.maxed }.map(::familyTile)
					g += "Maxed" to fams.filter { it.maxed }.map(::familyTile)
					g += "Not found" to fams.filter { it.locked }.map(::familyTile)
				} else g += "Families" to fams.map(::familyTile)
				groups(ctx, mx, my, top, bottom, g.filter { it.second.isNotEmpty() } + milestoneGroup(m.milestone))
				navRow(ctx, mx, my, navOf(m.nav, m.toggle))
			}
			is BestiaryPage.Milestones -> {
				val live = m.tiles.associateBy { it.level }
				val all = (if (BestiaryMilestonesMenuSettings.remember) BestiaryMilestoneMemory.seen + live else live).values.sortedBy { it.level }
				val prog = m.next ?: m.inProgress?.progress?.let { (c, mx2) -> LoreProgress("Milestone ${m.inProgress!!.level}", null, c, mx2) }
				val seen = BestiaryParser.ranges(all.map { it.level }).joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
				val top = header(ctx, mx, my, "Bestiary Milestones", prog, listOfNotNull(
					m.current?.let { "§7Unlocked up to §a$it" }, m.tiles.firstOrNull()?.let { "§7window §f${it.level}-${m.tiles.last().level}" },
					"§7seen §f$seen").joinToString(" §8· "))
				groups(ctx, mx, my, top, bottom, listOf("Milestones" to all.map { t ->
					val isLive = live[t.level]?.slot == t.slot && t.level in live
					TileSpec(if (isLive) t.slot else -1, "Milestone ${t.level}", when (t.state) {
						BeastState.UNLOCKED -> "§aunlocked"; BeastState.LOCKED -> "§clocked"
						BeastState.IN_PROGRESS -> "§e" + (t.progress?.let { (a, b) -> "${fmtNum(a)}/${fmtNum(b)}" } ?: "in progress")
					} + if (isLive) "" else " §8(scroll)", t.progress?.let { (a, b) -> if (b > 0) a / b else null } ?: if (t.state == BeastState.UNLOCKED) 1.0 else null,
						MenuTheme.GREY, listOf("§fBestiary Milestone ${t.level}") + t.rewards.map { "§a$it" } + (if (isLive) emptyList() else listOf("", "§8Not in Hypixel's window: scroll to it")))
				}))
				navRow(ctx, mx, my, navOf(m.nav, m.reminders))
			}
		}
		tooltip(ctx, mx, my)
	}

	private fun fracLine(p: LoreProgress) = "§7${p.label} §f${p.current?.let(::fmtNum) ?: "?"}/${p.max?.let(::fmtNum) ?: "?"}"

	private fun navOf(nav: List<BeastButton>, t: BeastToggle?) = nav.map { b ->
		b.slot to when {
			t != null && b.slot == t.slot -> t.name.removePrefix("Toggle ") + (t.state?.let { ": $it" } ?: "")
			b.name.equals("Scroll Left", true) -> "« Scroll"
			b.name.equals("Scroll Right", true) -> "Scroll »"
			else -> b.name
		}
	}

	private fun milestoneGroup(e: BeastMilestoneEntry?) = if (e == null) emptyList() else listOf("Milestones" to listOf(
		TileSpec(e.slot, "Milestone ${e.level}", e.next?.let { p -> "§e→ ${e.nextLevel ?: "?"}: ${p.current?.let(::fmtNum) ?: "?"}/${p.max?.let(::fmtNum) ?: "?"}" },
			e.next?.fraction, MenuTheme.GREY)))

	private fun areaTile(a: BeastArea) = TileSpec(a.slot, a.name,
		a.found?.let { f -> "${f.current?.let(::fmtNum) ?: "?"}/${f.max?.let(::fmtNum) ?: "?"} found" + (a.completed?.current?.let { " · ${fmtNum(it)} done" } ?: "") },
		a.found?.fraction, if (a.completed?.done == true) MenuTheme.GOLD else MenuTheme.GREY)

	private fun familyTile(f: BeastFamily): TileSpec {
		val sub = when {
			f.locked -> "§cnot found yet"
			f.maxed -> "§6maxed" + (f.kills?.let { " · ${fmtNum(it)} kills" } ?: "")
			else -> "§eT${f.nextTier ?: "?"} " + (f.next?.let { p -> "${p.current?.let(::fmtNum) ?: "?"}/${p.max?.let(::fmtNum) ?: "?"}" } ?: "")
		}
		val tip = listOf("§f${f.name}") + (if (f.locked) listOf("§cNot found yet") else listOfNotNull(
			f.mobTypes.takeIf { it.isNotEmpty() }?.let { "§7${it.joinToString(", ")}" },
			f.kills?.let { "§7Kills §f${fmtNum(it)}" + (f.deaths?.let { d -> " §8· §7Deaths §f${fmtNum(d)}" } ?: "") },
			f.next?.let { p -> "§7Tier ${f.nextTier ?: "?"}: §f${p.current?.let(::fmtNum) ?: "?"}/${p.max?.let(::fmtNum) ?: "?"}" },
			f.overall?.let { p -> "§7Overall: §f${p.pct?.let(::fmtPct) ?: "?"}" + (if (f.maxed) " §6MAX" else "") },
			f.cap?.let { "§7Capped at tier §f$it" }) + f.bonuses.map { "§a$it" })
		return TileSpec(f.slot, f.name, sub, if (f.maxed) 1.0 else f.next?.fraction, if (f.locked) 0xFFFF5555.toInt() else MenuTheme.GREY, tip)
	}
}
