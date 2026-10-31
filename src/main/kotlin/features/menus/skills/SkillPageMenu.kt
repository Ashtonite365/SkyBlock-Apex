// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

enum class LevelState { UNLOCKED, IN_PROGRESS, LOCKED }

data class SkillLevel(val level: Int, val slot: Int, val state: LevelState, val progress: Progress?, val rewards: List<String>,
                      val name: String = "", val lore: List<String> = emptyList())

data class SkillSummary(val slot: Int, val level: Int?, val next: Int?, val progress: Progress)
data class SkillPageModel(val skill: Skill?, val levels: List<SkillLevel>, val nav: Map<String, Int>, val extras: List<Pair<String, Int>>, val summary: SkillSummary? = null, val label: String = skill?.label ?: "",

                          val tips: Map<Int, Pair<String, List<String>>> = emptyMap()) {
	val current: SkillLevel? get() = levels.firstOrNull { it.state == LevelState.IN_PROGRESS }

	val level: Int? get() = summary?.level ?: current?.let { it.level - 1 } ?: levels.lastOrNull { it.state == LevelState.UNLOCKED }?.level
	val maxed: Boolean get() = summary?.progress?.maxed == true || (levels.isNotEmpty() && levels.all { it.state == LevelState.UNLOCKED } && nav["Scroll Right"] == null && nav["Next Page"] == null)
}

object SkillPageParser {
	val TITLE = Regex("^(?i)(?:\\(\\d+/\\d+\\) )?(${Skill.NAMES}) Skill$")
	const val MIN_LEVELS = 3
	private val UNLOCKED = Regex("\\bUNLOCKED\\b|Unlocked!?$|already unlocked", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): SkillPageModel? {
		val m = TITLE.matchEntire(MenuText.clean(title)) ?: return null
		val skill = Skill.byLabel(m.groupValues[1]) ?: return null
		return parseTrack(skill.label, "${skill.label} Skill", slots)?.copy(skill = skill)
	}

	fun parseTrack(label: String, summaryName: String, slots: List<SlotText>): SkillPageModel? {
		val skill = object { val label = label }

		val lvlName = Regex("^${skill.label} (?:Level )?([IVXLCDM]+|\\d+)$", RegexOption.IGNORE_CASE)

		val summarySlot = slots.firstOrNull { it.cleanName.equals(summaryName, true) && SkillLore.progress(it.cleanLore) != null }
		val summary = summarySlot?.let { s ->
			val p = SkillLore.progress(s.cleanLore)!!
			val next = SkillLore.nextLevel(s.cleanLore)
			SkillSummary(s.slot, next?.minus(1), next, p)
		}
		val raw = slots.mapNotNull { s -> lvlName.matchEntire(s.cleanName)?.let { SkillLore.level(it.groupValues[1]) }?.let { s to it } }
		val inProgress = raw.firstOrNull { (s, _) -> SkillLore.progress(s.cleanLore)?.let { !it.maxed } == true && !s.cleanLore.any { UNLOCKED.containsMatchIn(it) } }?.second
			?: summary?.next?.takeIf { !summary.progress.maxed }
		val levels = raw.distinctBy { it.second }.map { (s, lvl) ->
			val state = when {
				s.cleanLore.any { UNLOCKED.containsMatchIn(it) } -> LevelState.UNLOCKED
				summary?.progress?.maxed == true -> LevelState.UNLOCKED
				inProgress != null && lvl < inProgress -> LevelState.UNLOCKED
				lvl == inProgress -> LevelState.IN_PROGRESS
				else -> LevelState.LOCKED
			}
			SkillLevel(lvl, s.slot, state, if (state == LevelState.IN_PROGRESS) SkillLore.progress(s.cleanLore) else null, SkillLore.rewards(s.cleanLore), s.name, s.lore)
		}.sortedBy { it.level }
		if (levels.size < MIN_LEVELS) return null
		val used = levels.map { it.slot }.toSet() + listOfNotNull(summary?.slot)
		val extras = SkillLore.extras(slots, used)
		val tipSlots = extras.map { it.second }.toSet() + listOfNotNull(summary?.slot)
		val tips = slots.filter { it.slot in tipSlots }.associate { it.slot to (it.name to it.lore) }
		return SkillPageModel(null, levels, SkillLore.nav(slots), extras, summary, label, tips)
	}
}

@Config
object SkillPageSettings : MenuSettings("skill-page", panels = listOf("rewards", "locked")) {
	val reset by button("reset") { resetToDefaults() }
}

object SkillPageMenu : CustomMenu {
	override val id = "skill-page"
	override val displayName = "Skill pages (all skills)"
	override val title = SkillPageParser.TITLE
	override val settings get() = SkillPageSettings
	override val defaultSize = 440 to 300

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = SkillPageView(screen)
}

open class SkillPageView(screen: AbstractContainerScreen<*>) : SkillViewBase<SkillPageModel>(screen) {

	protected open fun shows(panel: String) = SkillPageSettings.shows(panel)

	protected open val levelTips: Boolean get() = true
	override fun palette() = SkillPageSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SkillPageParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: SkillPageModel, mx: Int, my: Int) {
		val cur = m.current
		var tipSlot: Int? = null
		var hoveredGroup: SkillLevel? = null
		header(ctx, mx, my, "${m.label}" + (if (m.skill != null) " Skill" else "") + (m.level?.let { "  §7level §a$it" } ?: "") + (if (m.maxed) "  §6MAX" else ""))
		var gy = y + 22
		if (cur == null && m.summary != null && !m.maxed) {
			val top = group(ctx, x + 4, gy, w - 8, 30, "Progress" + (m.summary.next?.let { " to ${m.label} $it" } ?: ""))
			if (hovered(mx, my, x + 4, gy, w - 8, 30)) tipSlot = m.summary.slot
			MenuTheme.progressBar(ctx, font, x + 10, top, w - 20, 10, m.summary.progress.fraction, 1.0, m.summary.progress.label(), palette = palette())
			gy += 34
		}
		if (cur != null) {
			val top = group(ctx, x + 4, gy, w - 8, 58, "Next: ${m.label} ${cur.level}")
			if (levelTips && hovered(mx, my, x + 4, gy, w - 8, 58)) hoveredGroup = cur
			cur.progress?.let { MenuTheme.progressBar(ctx, font, x + 10, top, w - 20, 10, it.fraction, 1.0, it.label(), palette = palette()) }
			if (shows("rewards"))
				cur.rewards.take(3).forEachIndexed { i, r -> ctx.text(font, "§7" + MenuTheme.fit(r, w - 24, font::width), x + 10, top + 14 + i * 10, MenuTheme.GREY) }
			gy += 62
		}
		val shown = m.levels.filter { shows("locked") || it.state != LevelState.LOCKED }
		val cols = 6; val gap = 3; val th = 22
		val tw = (w - 16 - gap * (cols - 1)) / cols
		val rows = (shown.size + cols - 1) / cols
		val top = group(ctx, x + 4, gy, w - 8, 14 + rows * (th + gap), "Levels")
		var hoveredLevel: SkillLevel? = null
		shown.forEachIndexed { i, l ->
			val tx = x + 8 + (i % cols) * (tw + gap); val ty = top + (i / cols) * (th + gap)
			val col = when (l.state) { LevelState.UNLOCKED -> "§a"; LevelState.IN_PROGRESS -> "§e"; LevelState.LOCKED -> "§c" }
			tile(ctx, mx, my, tx, ty, tw, th, l.slot, "$col${l.level}", if (shows("rewards")) l.rewards.firstOrNull() else null, null)
			if (levelTips && hovered(mx, my, tx, ty, tw, th)) hoveredLevel = l
		}
		var bx = x + 4
		for ((label, slot) in m.extras) {
			val bw = font.width(label) + 10
			if (bx + bw > x + w - 60) break
			button(ctx, mx, my, bx, y + h - 36, bw, 14, label) { clickSlot(slot, it) }
			if (hovered(mx, my, bx, y + h - 36, bw, 14)) tipSlot = slot
			bx += bw + 4
		}
		navRow(ctx, mx, my, m.nav)

		if (!screen.menu.carried.isEmpty) return
		(hoveredLevel ?: hoveredGroup)?.let { l -> SkillLevelTips.show(ctx, font, SkillLevelTips.levelLines(m.label, l.level, l), mx, my, screen.width); return }

		tipSlot?.let { s ->
			val tip = m.tips[s]
			val label = m.extras.firstOrNull { it.second == s }?.first ?: m.label
			SkillLevelTips.show(ctx, font, SkillLevelTips.itemLines(tip?.first ?: "§f$label", tip?.second), mx, my, screen.width)
		}
	}
}
