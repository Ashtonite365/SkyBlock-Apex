// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class SkillEntry(val skill: Skill, val slot: Int, val level: Int?, val progress: Progress?, val rewards: List<String>)

data class SkillsModel(val skills: List<SkillEntry>, val nav: Map<String, Int>, val average: Double?, val extras: List<Pair<String, Int>>)

object SkillsParser {
	val TITLE = Regex("^(?i)Your Skills$")
	private val NAME = Regex("^(${Skill.NAMES})(?: ([IVXLCDM]+|\\d+))?$", RegexOption.IGNORE_CASE)

	const val MIN_SKILLS = 4
	private val AVG = Regex("([\\d.]+) Skill Avg", RegexOption.IGNORE_CASE)

	fun entry(s: SlotText): SkillEntry? {
		val m = NAME.matchEntire(s.cleanName) ?: return null
		val skill = Skill.byLabel(m.groupValues[1]) ?: return null
		val lore = s.cleanLore

		val p = SkillLore.progress(lore) ?: return null
		val lvl = SkillLore.level(m.groupValues[2]) ?: SkillLore.nextLevel(lore)?.minus(1) ?: (if (m.groupValues[2].isEmpty()) 0 else null)
		return SkillEntry(skill, s.slot, lvl, p, SkillLore.rewards(lore))
	}

	fun parse(title: String, slots: List<SlotText>): SkillsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val skills = slots.mapNotNull(::entry).distinctBy { it.skill }.sortedBy { it.skill.ordinal }
		if (skills.size < MIN_SKILLS) return null
		val avg = slots.firstNotNullOfOrNull { s -> s.cleanLore.firstNotNullOfOrNull { AVG.find(it)?.groupValues?.get(1)?.toDoubleOrNull() } }
			?: skills.filter { it.skill.group != SkillGroup.COSMETIC }.mapNotNull { it.level }.takeIf { it.isNotEmpty() }?.average()
		val header = slots.filter { TITLE.matches(it.cleanName) }.map { it.slot }
		return SkillsModel(skills, SkillLore.nav(slots), avg, SkillLore.extras(slots, skills.map { it.slot }.toSet() + header))
	}
}

@Config
object SkillsSettings : MenuSettings("skills", panels = listOf("rewards", "average")) {
	val reset by button("reset") { resetToDefaults() }
}

object SkillsMenu : CustomMenu {
	override val id = "skills"
	override val displayName = "Your Skills"
	override val title = SkillsParser.TITLE
	override val settings get() = SkillsSettings
	override val defaultSize = 440 to 320

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = SkillsView(screen)
}

class SkillsView(screen: AbstractContainerScreen<*>) : SkillViewBase<SkillsModel>(screen) {
	override fun palette() = SkillsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SkillsParser.parse(title, slots)?.also {
		live = SkillRankings.fromPage(title, slots); SkillRankCache.remember(live)
	}

	private var live: Map<Skill, SkillRank> = emptyMap()

	override fun draw(ctx: GuiGraphicsExtractor, m: SkillsModel, mx: Int, my: Int) {
		val avg = m.average?.takeIf { SkillsSettings.shows("average") }
		header(ctx, mx, my, "Your Skills" + (avg?.let { "  §7avg §f" + "%.2f".format(java.util.Locale.ROOT, it) } ?: ""))
		val rewards = SkillsSettings.shows("rewards")
		val groups = m.skills.groupBy { it.skill.group }.toSortedMap()
		val cols = 4; val gap = 4; val th = barTileH(32)
		val tw = (w - 16 - gap * (cols - 1)) / cols
		var gy = y + 22
		for ((g, list) in groups) {
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, g.label)
			list.forEachIndexed { i, e ->
				val tx = x + 8 + (i % cols) * (tw + gap); val ty = top + (i / cols) * (th + gap)
				val sub = if (rewards) e.rewards.firstOrNull() else null
				tile(ctx, mx, my, tx, ty, tw, th, e.slot, "§f${e.skill.label} §a${e.level ?: "?"}", sub, e.progress)
			}
			gy += gh + 4
		}

		var bx = x + 4
		var rankTip = false
		for ((label, slot) in m.extras) {
			val bw = font.width(label) + 10
			if (bx + bw > x + w - 4) break
			button(ctx, mx, my, bx, y + h - 36, bw, 14, label) { clickSlot(slot, it) }
			if (SkillRankings.BUTTON.matches(label) && hovered(mx, my, bx, y + h - 36, bw, 14)) rankTip = true
			bx += bw + 4
		}
		navRow(ctx, mx, my, m.nav)

		if (rankTip) ctx.setComponentTooltipForNextFrame(font,
			SkillRankings.tooltip(m.skills.map { it.skill }, live, SkillRankCache.cached()).map { net.minecraft.network.chat.Component.literal(it) }, mx, my)
	}
}
