// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.levelling

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.Progress
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class LevelSummary(val level: Int?, val xp: Double?, val tasksPct: Double?, val ranking: String?, val mode: String?)

data class TrackLevel(val level: Int, val slot: Int, val tag: String?, val unlocked: Boolean, val rewards: List<String>, val progress: Progress?, val xpLeft: String?)
data class ExploreTile(val name: String, val slot: Int, val progress: Progress?, val state: String?)

data class LevellingModel(val summary: LevelSummary, val levels: List<TrackLevel>, val explore: List<ExploreTile>, val nav: Map<String, Int>) {
	val milestone get() = levels.firstOrNull { it.tag.equals("Next Milestone Level", true) }
	val track get() = levels.filter { it !== milestone }
}

object LevellingParser {
	val TITLE = Regex("^(?i)SkyBlock Leveling$")
	private val LEVEL_NAME = Regex("^Level (\\d+)$", RegexOption.IGNORE_CASE)
	private val YOUR_LEVEL = Regex("Your level: (\\d+)", RegexOption.IGNORE_CASE)
	private val YOU_HAVE = Regex("You have: ([\\d,.]+) XP", RegexOption.IGNORE_CASE)
	private val TASKS = Regex("completed ([\\d.]+)% of the", RegexOption.IGNORE_CASE)
	private val RANK = Regex("Global Ranking: (#[\\d,]+)", RegexOption.IGNORE_CASE)
	private val XP_LEFT = Regex("XP Left to Gain: (.+)$", RegexOption.IGNORE_CASE)
	private val STATE = Regex("^(ENABLED|DISABLED)$")
	private val TAGS = Regex("^(Your Level|Next Level|Next Milestone Level)$", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): LevellingModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val track = slots.mapNotNull { s ->
			val n = LEVEL_NAME.matchEntire(s.cleanName)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
			val lore = s.cleanLore
			TrackLevel(n, s.slot, lore.firstOrNull()?.takeIf { TAGS.matches(it) }, lore.any { it.equals("UNLOCKED", true) },
				SkillLore.rewards(lore), SkillLore.progress(lore), lore.firstNotNullOfOrNull { XP_LEFT.find(it)?.groupValues?.get(1) })
		}.sortedBy { it.level }

		if (track.size < 2) return null
		val all = slots.flatMap { it.cleanLore }
		val summarySlot = slots.firstOrNull { s -> s.cleanLore.any { YOUR_LEVEL.containsMatchIn(it) } }
		val summary = LevelSummary(
			all.firstNotNullOfOrNull { YOUR_LEVEL.find(it)?.groupValues?.get(1)?.toInt() } ?: track.firstOrNull { it.tag.equals("Your Level", true) }?.level,
			all.firstNotNullOfOrNull { YOU_HAVE.find(it)?.groupValues?.get(1)?.let(MenuText::number) },
			all.firstNotNullOfOrNull { TASKS.find(it)?.groupValues?.get(1)?.toDoubleOrNull() },
			all.firstNotNullOfOrNull { RANK.find(it)?.groupValues?.get(1) },
			summarySlot?.cleanLore?.firstOrNull()?.takeIf { it.endsWith("Mode", true) })
		val used = track.map { it.slot }.toSet() + listOfNotNull(summarySlot?.slot)
		val explore = SkillLore.extras(slots, used).map { (name, slot) ->
			val lore = slots.first { it.slot == slot }.cleanLore
			ExploreTile(name, slot, SkillLore.progress(lore), lore.firstOrNull { STATE.matches(it) })
		}
		return LevellingModel(summary, track, explore, SkillLore.nav(slots))
	}
}

@Config
object LevellingSettings : MenuSettings("skyblock-levelling", panels = listOf("ranking", "rewards")) {
	val reset by button("reset") { resetToDefaults() }
}

object LevellingMenu : CustomMenu {
	override val id = "skyblock-levelling"
	override val displayName = "SkyBlock Levelling"
	override val title = LevellingParser.TITLE
	override val settings get() = LevellingSettings
	override val defaultSize = 440 to 280

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = LevellingView(screen)
}

object LevellingLayout {
	const val COLS = 4
	const val GAP = 4
	const val LINE = 10
	const val TEXT_X = 22
	const val BAR_H = 10

	const val HEADER = 20
	const val MIN_TILE_H = 24

	data class TileSpec(val slot: Int, val title: List<String>, val sub: List<String>, val progress: Progress?, val x: Int, val y: Int, val w: Int, val h: Int) {
		val bounds get() = MenuBounds(x, y, w, h)
	}
	data class GroupSpec(val label: String, val y: Int, val h: Int)
	data class Plan(val summary: List<String>, val summaryY: Int, val barY: Int?, val groups: List<GroupSpec>, val tiles: List<TileSpec>, val contentHeight: Int)

	fun view(x: Int, y: Int, w: Int, navTop: Int) = MenuBounds(x + 1, y + HEADER, w - 2, maxOf(0, navTop - ScrollClip.BAR_GAP - (y + HEADER)))

	fun wrap(text: String, w: Int, measure: (String) -> Int): List<String> {
		val colour = Regex("^(§.)+").find(text)?.value ?: ""
		val out = mutableListOf<String>(); var cur = ""
		for (word in text.removePrefix(colour).split(' ').filter { it.isNotEmpty() }) {
			val next = if (cur.isEmpty()) word else "$cur $word"
			if (cur.isNotEmpty() && measure(colour + next) > w) { out += colour + cur; cur = word } else cur = next
		}
		if (cur.isNotEmpty()) out += colour + cur
		return out
	}

	fun tileH(lines: Int, bar: Boolean) = maxOf(MIN_TILE_H, 3 + lines * LINE + (if (bar) BAR_H + 1 else 0) + 2)

	fun plan(m: LevellingModel, x0: Int, w: Int, measure: (String) -> Int, showRanking: Boolean, showRewards: Boolean): Plan {
		val groups = mutableListOf<GroupSpec>(); val tiles = mutableListOf<TileSpec>()
		val s = m.summary

		val parts = listOfNotNull(s.mode, s.xp?.let { MenuText.coins(it) + " XP" }, s.tasksPct?.let { "$it% of tasks" },
			s.ranking?.takeIf { showRanking }?.let { "rank $it" })
		val summary = wrap("§f" + parts.joinToString("  §7·§f  "), w - 20, measure)
		val next = m.track.firstOrNull { it.progress != null }
		var gy = 2
		val summaryY = gy + 12
		val barY = next?.let { summaryY + summary.size * LINE + 2 }
		val yourH = 12 + summary.size * LINE + (if (next != null) 12 else 0) + 6
		groups += GroupSpec("Your Level", gy, yourH); gy += yourH + 4
		val tw = (w - 16 - GAP * (COLS - 1)) / COLS
		val textW = tw - TEXT_X - 4
		fun grid(label: String, items: List<Triple<Int, Pair<String, String?>, Progress?>>) {
			if (items.isEmpty()) return
			val inner = gy + 12
			var ry = inner
			for (row in items.chunked(COLS)) {
				val specs = row.map { (slot, text, prog) -> Triple(slot, wrap(text.first, textW, measure) to (text.second?.let { wrap(it, textW, measure) } ?: emptyList()), prog) }
				val rh = specs.maxOf { (_, t, p) -> tileH(t.first.size + t.second.size, p != null) }
				specs.forEachIndexed { i, (slot, t, p) -> tiles += TileSpec(slot, t.first, t.second, p, x0 + 8 + i * (tw + GAP), ry, tw, rh) }
				ry += rh + GAP
			}
			val gh = ry - gy + 2
			groups += GroupSpec(label, gy, gh); gy += gh + 4
		}
		grid("Level Track", m.track.map { t ->
			val col = when { t.unlocked -> "§a"; t.progress != null -> "§e"; t.tag != null -> "§6"; else -> "§7" }
			val sub = if (showRewards) t.xpLeft ?: t.rewards.joinToString(", ") else t.tag
			Triple(t.slot, "${col}Level ${t.level}" to sub?.takeIf { it.isNotEmpty() }?.let { "§7$it" }, t.progress)
		})
		m.milestone?.let { ms ->
			val fw = w - 16; val ftw = fw - TEXT_X - 4
			val title = wrap("§6Level ${ms.level}", ftw, measure)
			val subText = (ms.xpLeft?.let { "$it · " } ?: "") + ms.rewards.joinToString(", ")
			val sub = if (subText.isNotEmpty()) wrap("§7$subText", ftw, measure) else emptyList()
			val th = tileH(title.size + sub.size, false)
			tiles += TileSpec(ms.slot, title, sub, null, x0 + 8, gy + 12, fw, th)
			val gh = 12 + th + 4
			groups += GroupSpec("Next Milestone", gy, gh); gy += gh + 4
		}
		grid("Explore", m.explore.map { e -> Triple(e.slot, "§f${e.name}" to (e.state ?: e.progress?.label())?.let { "§7$it" }, null as Progress?) })
		return Plan(summary, summaryY, barY, groups, tiles, gy)
	}
}

class LevellingView(screen: AbstractContainerScreen<*>) : SkillViewBase<LevellingModel>(screen) {
	override fun palette() = LevellingSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = LevellingParser.parse(title, slots)

	companion object { private val scroll = ScrollView() }
	override val scrollView get() = scroll

	override fun draw(ctx: GuiGraphicsExtractor, m: LevellingModel, mx: Int, my: Int) {
		val s = m.summary
		header(ctx, mx, my, "SkyBlock Level" + (s.level?.let { " §b$it" } ?: ""))
		val plan = LevellingLayout.plan(m, x, w, font::width, LevellingSettings.shows("ranking"), LevellingSettings.shows("rewards"))
		val view = LevellingLayout.view(x, y, w, navTop())
		scroll.layout(view, plan.contentHeight)
		val oy = view.y - scroll.offset

		beginClip(ctx, view)
		for (g in plan.groups) group(ctx, x + 4, oy + g.y, w - 8, g.h, g.label)
		plan.summary.forEachIndexed { i, l -> ctx.text(font, l, x + 10, oy + plan.summaryY + i * LevellingLayout.LINE, MenuTheme.WHITE) }
		val next = m.track.firstOrNull { it.progress != null }
		if (next != null && plan.barY != null)
			MenuTheme.progressBar(ctx, font, x + 10, oy + plan.barY, w - 20, 10, next.progress!!.fraction, 1.0, "to ${next.level}: " + next.progress.label(), palette = palette())
		for (t in plan.tiles) wrappedTile(ctx, mx, my, t, oy)
		endClip(ctx)

		navRow(ctx, mx, my, m.nav)
	}

	private fun wrappedTile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, t: LevellingLayout.TileSpec, oy: Int) {
		val ty = oy + t.y
		button(ctx, mx, my, t.x, ty, t.w, t.h, "") { b -> clickSlot(t.slot, b) }
		stacks[t.slot]?.let { ctx.item(it, t.x + 3, ty + 3) }
		var ly = ty + 3
		for (l in t.title) { ctx.text(font, l, t.x + LevellingLayout.TEXT_X, ly, MenuTheme.WHITE); ly += LevellingLayout.LINE }
		for (l in t.sub) { ctx.text(font, l, t.x + LevellingLayout.TEXT_X, ly, MenuTheme.GREY); ly += LevellingLayout.LINE }
		t.progress?.let { p ->
			MenuTheme.progressBar(ctx, font, t.x + 3, ty + t.h - 2 - LevellingLayout.BAR_H, t.w - 6, LevellingLayout.BAR_H, p.fraction, 1.0, p.label(), palette = palette())
		}
	}
}
