// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import kotlinx.serialization.Serializable
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.features.menus.support.MenuSlots
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.bareText

@Serializable
data class SkillRank(val rank: Long, val total: Long, val topPercent: Double? = null) {

	val betterThan: Double? get() = when {
		total > 0 && rank in 1..total -> (total - rank) * 100.0 / total
		topPercent != null -> (100.0 - topPercent).coerceIn(0.0, 100.0)
		else -> null
	}
}

@Serializable
data class SkillRankData(var ranks: MutableMap<String, SkillRank> = mutableMapOf())

object SkillRankings {
	private val RANK = Regex("^#([\\d,]+) out of ([\\d,]+) players$", RegexOption.IGNORE_CASE)
	private val TOP = Regex("top ([\\d.]+)% of players", RegexOption.IGNORE_CASE)
	val BUTTON = Regex("^(Show|Hide) Skill Rankings$", RegexOption.IGNORE_CASE)

	fun parse(lore: List<String>): SkillRank? {
		val clean = lore.map(MenuText::clean)
		val m = clean.firstNotNullOfOrNull { RANK.matchEntire(it) } ?: return null
		val rank = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
		val total = m.groupValues[2].replace(",", "").toLongOrNull() ?: return null
		val top = clean.firstNotNullOfOrNull { TOP.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
		return SkillRank(rank, total, top)
	}

	fun fromPage(title: String, slots: List<SlotText>): Map<Skill, SkillRank> {
		val m = SkillsParser.parse(title, slots) ?: return emptyMap()
		val bySlot = slots.associateBy { it.slot }
		return m.skills.mapNotNull { e -> bySlot[e.slot]?.let { parse(it.lore) }?.let { e.skill to it } }.toMap()
	}

	fun update(d: SkillRankData, live: Map<Skill, SkillRank>): Boolean {
		var changed = false
		for ((s, r) in live) if (d.ranks[s.name] != r) { d.ranks[s.name] = r; changed = true }
		return changed
	}

	fun best(skill: Skill, live: Map<Skill, SkillRank>, cached: Map<String, SkillRank>): SkillRank? = live[skill] ?: cached[skill.name]

	private fun pct(d: Double) = "%.2f".format(java.util.Locale.ROOT, d)
	private fun num(n: Long) = "%,d".format(java.util.Locale.ROOT, n)

	fun tooltip(skills: List<Skill>, live: Map<Skill, SkillRank>, cached: Map<String, SkillRank>): List<String> = skills.map { s ->
		val r = best(s, live, cached)
		if (r == null) "§f${s.label}: §7unknown"
		else "§f${s.label}: §e#${num(r.rank)} §7of ${num(r.total)}" +
			(r.betterThan?.let { " §8· §abetter than ${pct(it)}%" } ?: "")
	}
}

@Config
object SkillRankCache : ProfileSpecificDataHolder<SkillRankData>(SkillRankData.serializer(), "skill-rankings-cache", ::SkillRankData) {
	fun remember(live: Map<Skill, SkillRank>) {
		if (live.isEmpty()) return
		val d = runCatching { data }.getOrNull() ?: return
		if (SkillRankings.update(d, live)) runCatching { markDirty() }
	}

	fun cached(): Map<String, SkillRank> = runCatching { data?.ranks?.toMap() }.getOrNull() ?: emptyMap()

	@Subscribe
	fun onChest(event: ChestInventoryUpdateEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val title = screen.title.bareText
		if (!SkillsParser.TITLE.matches(MenuText.clean(title))) return
		runCatching { remember(SkillRankings.fromPage(title, MenuSlots.snapshot(screen).first)) }
	}
}
