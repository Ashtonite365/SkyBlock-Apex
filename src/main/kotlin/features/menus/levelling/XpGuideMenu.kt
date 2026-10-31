// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.levelling

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.hunting.CycleOption
import dev.ashtonite365.apex.features.menus.hunting.HuntingLore
import dev.ashtonite365.apex.features.menus.skills.Progress
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

enum class StageState { SELECTED, AVAILABLE, LOCKED }
data class GuideStage(val name: String, val slot: Int, val phase: String?, val progress: Progress?, val state: StageState, val unlockHint: String?)
data class GuideTask(val name: String, val slot: Int, val done: Boolean, val worthXp: Int?, val progress: Progress?, val count: Int?,
                     val subtasks: List<Pair<String, Boolean>>, val hasMore: Boolean, val description: String?, val locked: Boolean = false, val clickable: Boolean = true)
data class XpGuideModel(val parent: String, val leaf: String, val page: Pair<Int, Int>?, val stages: List<GuideStage>, val header: GuideTask?,
                        val tasks: List<GuideTask>, val sort: CycleOption?, val nav: Map<String, Int>, val extras: List<Pair<String, Int>>) {
	val isStagePage get() = stages.isNotEmpty()
	val open get() = tasks.filter { !it.done && !it.locked }
	val locked get() = tasks.filter { it.locked }
	val done get() = tasks.filter { it.done }
	val openXp get() = open.mapNotNull { it.worthXp }.sum()
}

object XpGuideParser {

	val TITLE = Regex("^(?:\\((\\d+)/(\\d+)\\)\\s+)?(?:(.+?)\\s+➜\\s*(.*)|(Ways to Level Up|.+ Tasks|.+ Milestones|.+ Rewards))$")
	private val MARKED = Regex("^([✔✖])\\s*(.+)$")
	private val WORTH = Regex("^Total Worth: \\+([\\d,]+) XP", RegexOption.IGNORE_CASE)
	private val GAIN = Regex("gain \\+([\\d,]+) XP", RegexOption.IGNORE_CASE)
	private val COUNT = Regex("^(\\d+) tasks?$", RegexOption.IGNORE_CASE)
	private val SUB = Regex("^([✔✖▶])\\s*(.+)$")
	private val MILESTONE = Regex("^(?:.+ )?Milestone [IVXLC\\d]+(?: Rewards)?$", RegexOption.IGNORE_CASE)
	private val GAINED = Regex("^XP Gained from Task: ([\\d,]+) XP", RegexOption.IGNORE_CASE)

	fun isClickable(lore: List<String>) = lore.any { it.startsWith("Click", true) }

	fun task(s: SlotText): GuideTask? {
		val l = s.cleanLore
		val marked = MARKED.matchEntire(s.cleanName)
		val first = l.firstOrNull().orEmpty()
		val guideWorth = l.firstNotNullOfOrNull { WORTH.find(it)?.groupValues?.get(1)?.replace(",", "")?.toInt() }
		val isXpTask = first.equals("XP Task", true)
		val count = COUNT.matchEntire(first)?.groupValues?.get(1)?.toInt()

		val locked = first.startsWith("You have not unlocked the content", true)

		if (MILESTONE.matches(s.cleanName) && SkillLore.progress(l) != null) {
			val rewards = SkillLore.rewards(l).map { it.removePrefix("✔").trim() }
			val pr = SkillLore.progress(l)
			val claimed = l.any { it.equals("CLAIMED", true) } || (pr?.needed != null && pr.current != null && pr.current >= pr.needed)
			return GuideTask(s.cleanName, s.slot, claimed, null, pr, null,
				SkillLore.rewards(l).map { it.removePrefix("✔").trim() to it.startsWith("✔") }, false,
				rewards.joinToString(", ").takeIf { it.isNotEmpty() } ?: l.takeWhile { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() },
				clickable = isClickable(l))
		}
		if (!(marked != null && guideWorth != null) && !isXpTask && count == null && !locked) return null
		val name = marked?.groupValues?.get(2) ?: s.cleanName
		val progress = SkillLore.progress(l)
		val subs = l.mapNotNull { SUB.matchEntire(it)?.let { x -> x.groupValues[2].trim() to (x.groupValues[1] == "✔") } }
		val done = marked?.groupValues?.get(1) == "✔" || l.any { it.startsWith("You have completed this task", true) } ||
			(progress?.needed != null && progress.current == progress.needed && progress.needed > 0) ||
			(count != null && subs.isNotEmpty() && subs.all { it.second } && subs.none { l.any { x -> x.startsWith("▶") } })
		val worth = guideWorth ?: l.firstNotNullOfOrNull { GAIN.find(it)?.groupValues?.get(1)?.replace(",", "")?.toInt() }
			?: progress?.needed?.takeIf { l.any { it.endsWith(" XP") && it.contains('/') } }?.toInt()
		val desc = l.drop(if (isXpTask || count != null) 1 else 0).dropWhile { COUNT.matches(it) || it.isEmpty() }
			.takeWhile { it.isNotEmpty() && SUB.matchEntire(it) == null }.joinToString(" ").takeIf { it.isNotEmpty() }
			?: l.firstNotNullOfOrNull { GAINED.find(it)?.let { m -> "Gained ${m.groupValues[1]} XP" } }
		return GuideTask(name, s.slot, done, worth, progress, count, subs, l.any { it.startsWith("Click to view", true) || it.startsWith("Click to see", true) }, if (locked) l.joinToString(" ") else desc, locked,
			clickable = isClickable(l))
	}

	fun stage(s: SlotText): GuideStage? {
		val l = s.cleanLore
		val state = when {
			l.any { it.equals("SELECTED", true) } -> StageState.SELECTED
			l.any { it.equals("LOCKED", true) } -> StageState.LOCKED
			l.any { it.startsWith("Click to select", true) } -> StageState.AVAILABLE
			else -> return null
		}
		return GuideStage(s.cleanName, s.slot, l.firstOrNull()?.takeIf { it.isNotEmpty() }, SkillLore.progress(l), state,
			l.firstOrNull { it.startsWith("Reach ", true) }?.let { i -> l.drop(l.indexOf(i)).takeWhile { it.isNotEmpty() }.joinToString(" ") })
	}

	fun parse(title: String, slots: List<SlotText>): XpGuideModel? {
		val t = TITLE.matchEntire(MenuText.clean(title)) ?: return null
		val tasks = slots.mapNotNull(::task)

		if (tasks.isEmpty()) return null
		val stages = slots.mapNotNull(::stage).filter { st -> tasks.none { it.slot == st.slot } }
		val header = tasks.firstOrNull { it.slot < 9 }
		val sort = slots.firstOrNull { it.cleanName.equals("Sort", true) }?.let(HuntingLore::cycle)
		val nav = SkillLore.nav(slots)
		val used = tasks.map { it.slot }.toSet() + stages.map { it.slot } + listOfNotNull(sort?.slot)
		val hub = t.groups[5]?.value
		val parent = hub?.let { slots.firstOrNull { it.cleanName == "Go Back" }?.cleanLore?.firstOrNull()?.removePrefix("To ")?.trim() ?: "" } ?: t.groupValues[3].trim()
		var leaf = hub ?: t.groupValues[4].trim()

		val cut = MenuText.clean(title).length >= dev.ashtonite365.apex.features.menus.MenuTitles.MIN_CUT
		if (cut && header != null && header.name.length > leaf.length && header.name.startsWith(leaf, true)) leaf = header.name
		return XpGuideModel(parent, leaf, t.groupValues[1].toIntOrNull()?.let { it to t.groupValues[2].toInt() },
			stages, header, tasks.filter { it !== header }, sort, nav, SkillLore.extras(slots, used).filter { !it.first.startsWith("Recently Viewed #") })
	}
}

@Config
object XpGuideSettings : MenuSettings("xp-guide", panels = listOf("completed")) {
	val reset by button("reset") { resetToDefaults() }
}

object XpGuideMenu : CustomMenu {
	override val id = "xp-guide"
	override val displayName = "SkyBlock XP Guide & Leveling Tasks"

	override val title = Regex("(?!)")
	override val slotPredicate: (String, List<SlotText>) -> Boolean = { t, s -> XpGuideParser.parse(t, s) != null }
	override val settings get() = XpGuideSettings
	override val defaultSize = 460 to 290
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = XpGuideView(screen)
}

object XpGuideLayout {
	const val COLS = 3
	const val GAP = 3
	const val HEADER_H = 38
	const val BLOCK_GAP = 4

	val TILE_H get() = maxOf(30, dev.ashtonite365.apex.features.menus.MenuTheme.BarTile.FULL)

	data class Frame(val view: MenuBounds, val barTop: Int)

	fun frame(x: Int, y: Int, w: Int, h: Int, stagePage: Boolean, extrasRow: Boolean): Frame {
		val top = y + 22 + if (stagePage) 18 else 0
		val barTop = if (extrasRow) y + h - 36 else y + h - 18
		val floor = barTop - ScrollClip.BAR_GAP
		return Frame(MenuBounds(x + 1, top, w - 2, maxOf(0, floor - top)), barTop)
	}

	fun tileW(w: Int) = (w - 16 - GAP * (COLS - 1)) / COLS
	fun rows(n: Int) = (n + COLS - 1) / COLS
	fun groupH(n: Int) = 14 + rows(n) * (TILE_H + GAP)

	fun contentHeight(hasHeader: Boolean, groupSizes: List<Int>): Int {
		val blocks = listOfNotNull(HEADER_H.takeIf { hasHeader }) + groupSizes.filter { it > 0 }.map(::groupH)
		return if (blocks.isEmpty()) 0 else blocks.sum() + BLOCK_GAP * (blocks.size - 1)
	}

	fun tilePos(x: Int, w: Int, groupY: Int, i: Int) =
		(x + 8 + (i % COLS) * (tileW(w) + GAP)) to (groupY + 12 + (i / COLS) * (TILE_H + GAP))
}

class XpGuideView(screen: AbstractContainerScreen<*>) : SkillViewBase<XpGuideModel>(screen) {
	override fun palette() = XpGuideSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = XpGuideParser.parse(title, slots)
	private val scroll = ScrollView { scrollKey("page=$lastPage") }
	override val scrollView get() = scroll
	private var lastPage: String? = null

	override fun draw(ctx: GuiGraphicsExtractor, m: XpGuideModel, mx: Int, my: Int) {
		val page = m.page?.let { "  §7page ${it.first}/${it.second}" } ?: ""
		header(ctx, mx, my, "XP Guide §7➜ §f${m.leaf}  §7open §b+${m.openXp} XP$page")

		val key = "${m.parent}|${m.leaf}|${m.page}"
		if (key != lastPage) lastPage = key
		if (m.isStagePage) {

			val gy = y + 22
			val sw = (w - 8 - 2 * (m.stages.size - 1)) / m.stages.size.coerceAtLeast(1)
			m.stages.forEachIndexed { i, s ->
				val bx = x + 4 + i * (sw + 2)
				val active = s.state == StageState.SELECTED; val enabled = s.state != StageState.LOCKED
				button(ctx, mx, my, bx, gy, sw, 14, "", active = active, enabled = enabled) { clickSlot(s.slot, it) }
				val st = MenuTheme.state(hovered(mx, my, bx, gy, sw, 14), active, enabled)
				val f = MenuTheme.fitScaled(s.name, sw - 6, font::width)
				val tw = (font.width(f.text) * f.scale).toInt()
				MenuTheme.drawFitted(ctx, font, s.name, bx + (sw - tw) / 2, gy + 3, sw - 6, MenuTheme.buttonColours(st, palette()).third)
			}
		}
		val groups = listOf("To do" to m.open) + (if (XpGuideSettings.shows("completed")) listOf("Completed" to m.done) else emptyList()) + listOf("Not unlocked" to m.locked)
		val extras = m.sort != null || m.extras.isNotEmpty()
		val fr = XpGuideLayout.frame(x, y, w, h, m.isStagePage, extras)
		scroll.layout(fr.view, XpGuideLayout.contentHeight(m.header != null, groups.map { it.second.size }))

		beginClip(ctx, fr.view)
		var gy = fr.view.y - scroll.offset
		m.header?.let { hd ->
			val top = group(ctx, x + 4, gy, w - 8, XpGuideLayout.HEADER_H, "Category")
			tile(ctx, mx, my, x + 8, top, w - 16, 22, hd.slot, (if (hd.done) "§a" else "§e") + hd.name, listOfNotNull(hd.worthXp?.let { "+$it XP" }, hd.progress?.label(), hd.count?.let { "$it tasks" }).joinToString(" · "), null)
			gy += XpGuideLayout.HEADER_H + XpGuideLayout.BLOCK_GAP
		}
		val th = XpGuideLayout.TILE_H; val tw = XpGuideLayout.tileW(w)
		for ((label, list) in groups) {
			if (list.isEmpty()) continue
			val gh = XpGuideLayout.groupH(list.size)
			group(ctx, x + 4, gy, w - 8, gh, "$label (${list.size})")
			list.forEachIndexed { i, t ->
				val sub = if (t.locked) "not unlocked" else (t.worthXp?.let { "+$it XP" } ?: t.description.orEmpty()) + (t.count?.let { " · $it tasks" } ?: "") + (if (t.hasMore) " ›" else "")
				val (tx, ty) = XpGuideLayout.tilePos(x, w, gy, i)
				tile(ctx, mx, my, tx, ty, tw, th, t.slot, (if (t.done) "§a" else "§f") + t.name, sub, t.progress?.takeIf { !t.done }, clickable = t.clickable)
			}
			gy += gh + XpGuideLayout.BLOCK_GAP
		}
		endClip(ctx)

		var bx = x + 4; val by = fr.barTop
		fun btn(l: String, slot: Int) { val bw = font.width(l) + 10; if (bx + bw <= x + w - 4) { button(ctx, mx, my, bx, by, bw, 14, l) { clickSlot(slot, it) }; bx += bw + 4 } }
		m.sort?.let { btn("Sort: ${it.selected ?: "?"}", it.slot) }
		for ((l, s) in m.extras) btn(l, s)
		navRow(ctx, mx, my, m.nav)
	}
}
