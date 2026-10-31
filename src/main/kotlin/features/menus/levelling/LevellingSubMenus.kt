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
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object LevellingLore {
	private val NEXT_AT = Regex("^at Level (\\d+)$", RegexOption.IGNORE_CASE)
	private val LEVEL = Regex("^Level (\\d+)$", RegexOption.IGNORE_CASE)

	fun nextReward(lore: List<String>): Pair<String, Int?>? {
		val i = lore.indexOfFirst { it.equals("Next Reward:", true) }
		if (i < 0 || i + 1 >= lore.size) return null
		return lore[i + 1] to lore.getOrNull(i + 2)?.let { NEXT_AT.matchEntire(it)?.groupValues?.get(1)?.toInt() }
	}

	fun recurring(lore: List<String>): List<String> = lore.indices.filter { lore[it].startsWith("For every", true) }
		.map { i -> lore[i].removePrefix("For ").removeSuffix(":") + ": " + lore.drop(i + 1).takeWhile { it.isNotEmpty() }.joinToString(", ") }

	fun level(lore: List<String>) = lore.firstNotNullOfOrNull { LEVEL.matchEntire(it)?.groupValues?.get(1)?.toInt() }
	fun unlocked(lore: List<String>) = lore.any { it.equals("UNLOCKED", true) }

	fun description(lore: List<String>): String? = lore.filter { l ->
		l.isNotEmpty() && LEVEL.matchEntire(l) == null && !l.equals("UNLOCKED", true) && !l.contains("Progress", true) && !l.contains('/') }
		.joinToString(" ").takeIf { it.isNotEmpty() }
}

data class RewardCategory(val name: String, val slot: Int, val blurb: String?, val next: Pair<String, Int?>?, val unlocked: Progress?, val recurring: List<String>)
data class LevelingRewardsModel(val categories: List<RewardCategory>, val nav: Map<String, Int>)

object LevelingRewardsParser {
	val TITLE = Regex("^(?i)Leveling Rewards$")
	fun parse(title: String, slots: List<SlotText>): LevelingRewardsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val cats = slots.filter { s -> LevellingLore.nextReward(s.cleanLore) != null }.map { s ->
			val l = s.cleanLore
			RewardCategory(s.cleanName, s.slot, l.takeWhile { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() },
				LevellingLore.nextReward(l), SkillLore.progress(l), LevellingLore.recurring(l))
		}
		if (cats.isEmpty()) return null
		return LevelingRewardsModel(cats, SkillLore.nav(slots))
	}
}

@Config
object LevelingRewardsSettings : MenuSettings("leveling-rewards") {
	val reset by button("reset") { resetToDefaults() }
}

object LevelingRewardsMenu : CustomMenu {
	override val id = "leveling-rewards"
	override val displayName = "Leveling Rewards"
	override val title = LevelingRewardsParser.TITLE
	override val settings get() = LevelingRewardsSettings
	override val defaultSize = 440 to 230
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = LevelingRewardsView(screen)
}

class LevelingRewardsView(screen: AbstractContainerScreen<*>) : SkillViewBase<LevelingRewardsModel>(screen) {
	override fun palette() = LevelingRewardsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = LevelingRewardsParser.parse(title, slots)
	override fun draw(ctx: GuiGraphicsExtractor, m: LevelingRewardsModel, mx: Int, my: Int) {
		header(ctx, mx, my, "Leveling Rewards")
		val cols = 2; val gap = 4; val th = 40
		val tw = (w - 16 - gap) / cols
		val rows = (m.categories.size + cols - 1) / cols
		val top = group(ctx, x + 4, y + 22, w - 8, 14 + rows * (th + gap), "Reward Categories")
		m.categories.forEachIndexed { i, c ->
			val sub = c.next?.let { (r, at) -> "Next: $r" + (at?.let { " @ $it" } ?: "") } ?: c.recurring.firstOrNull()
			tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, c.slot, "§f${c.name}", sub, c.unlocked)
		}
		navRow(ctx, mx, my, m.nav)
	}
}

data class LevelReward(val name: String, val slot: Int, val level: Int?, val unlocked: Boolean, val progress: Progress?, val description: String?)
data class RewardsModel(val category: String?, val next: Pair<String, Int?>?, val total: Progress?, val rewards: List<LevelReward>, val nav: Map<String, Int>) {
	val done get() = rewards.filter { it.unlocked }
	val upcoming get() = rewards.filter { !it.unlocked }
}

object RewardsParser {

	val TITLE = Regex("^(?i)(?:Level (\\d+) )?Rewards$")
	fun parse(title: String, slots: List<SlotText>): RewardsModel? {
		val tm = TITLE.matchEntire(MenuText.clean(title)) ?: return null
		val titleLevel = tm.groupValues[1].toIntOrNull()
		val nav = SkillLore.nav(slots)

		val header = slots.firstOrNull { LevellingLore.nextReward(it.cleanLore) != null }
		val fromLeveling = slots.any { it.cleanName == "Go Back" && it.cleanLore.firstOrNull()?.contains("Leveling Rewards", true) == true }
		val fromLevelingMenu = slots.any { it.cleanName == "Go Back" && it.cleanLore.firstOrNull()?.contains("SkyBlock Leveling", true) == true }
		if (header == null && !fromLeveling && !(titleLevel != null && fromLevelingMenu)) return null
		val rewards = slots.filter { s -> s.slot != header?.slot && LevellingLore.level(s.cleanLore) != null }.map { s ->
			val l = s.cleanLore
			LevelReward(s.cleanName, s.slot, LevellingLore.level(l), LevellingLore.unlocked(l), SkillLore.progress(l), LevellingLore.description(l))
		}.sortedWith(compareBy({ it.level ?: Int.MAX_VALUE }, { it.slot }))
		if (rewards.isEmpty()) return null
		return RewardsModel(header?.cleanName ?: titleLevel?.let { "Level $it Rewards" }, header?.let { LevellingLore.nextReward(it.cleanLore) }, header?.let { SkillLore.progress(it.cleanLore) }, rewards, nav)
	}
}

@Config
object RewardsSettings : MenuSettings("leveling-reward-list", panels = listOf("unlocked")) {
	val reset by button("reset") { resetToDefaults() }
}

object RewardsMenu : CustomMenu {
	override val id = "leveling-reward-list"
	override val displayName = "Leveling Rewards (category)"

	override val title = Regex("(?!)")
	override val slotPredicate: (String, List<SlotText>) -> Boolean = { t, s -> RewardsParser.parse(t, s) != null }
	override val settings get() = RewardsSettings
	override val defaultSize = 440 to 260
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = RewardsView(screen)
}

class RewardsView(screen: AbstractContainerScreen<*>) : SkillViewBase<RewardsModel>(screen) {
	override fun palette() = RewardsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = RewardsParser.parse(title, slots)
	override fun draw(ctx: GuiGraphicsExtractor, m: RewardsModel, mx: Int, my: Int) {
		header(ctx, mx, my, (m.category ?: "Rewards") + (m.total?.let { "  §7" + it.label() } ?: ""))
		var gy = y + 22
		m.next?.let { (r, at) ->
			ctx.text(font, "§7Next: §f" + MenuTheme.fit(r + (at?.let { " §7at Level §b$it" } ?: ""), w - 50, font::width), x + 8, gy, MenuTheme.WHITE); gy += 12
		}
		val cols = 3; val gap = 4; val th = barTileH(32)
		val tw = (w - 16 - gap * (cols - 1)) / cols
		val groups = listOf("Upcoming" to m.upcoming) + (if (RewardsSettings.shows("unlocked")) listOf("Unlocked" to m.done) else emptyList())
		for ((label, list) in groups) {
			if (list.isEmpty()) continue
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, label)
			list.forEachIndexed { i, r ->
				tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, r.slot,
					(if (r.unlocked) "§a" else "§e") + r.name, r.level?.let { "Level $it" + (r.description?.let { d -> " · $d" } ?: "") }, r.progress?.takeIf { !r.unlocked })
			}
			gy += gh + 4
		}
		navRow(ctx, mx, my, m.nav)
	}
}

data class EmblemGroup(val name: String, val slot: Int, val unlocked: Int?, val blurb: String?)
data class Emblem(val name: String, val slot: Int, val locked: Boolean, val selected: Boolean, val requirement: String?, val preview: String?, val stats: List<String>)
data class EmblemsModel(val groups: List<EmblemGroup>, val selected: Emblem?, val emblems: List<Emblem>, val page: Pair<Int, Int>?, val nav: Map<String, Int>) {
	val isOverview get() = groups.isNotEmpty()
}

object EmblemsParser {
	val TITLE = Regex("^(?i)(?:\\((\\d+)/(\\d+)\\) )?Emblems$")
	private val UNLOCKED = Regex("^(\\d+) Unlocked$", RegexOption.IGNORE_CASE)

	fun emblem(s: SlotText): Emblem? {
		val l = s.cleanLore
		val locked = l.firstOrNull().equals("Locked", true)
		val req = l.indexOfFirst { it.startsWith("Requires", true) || it.startsWith("Unlocked with", true) }
		val preview = l.firstOrNull { it.startsWith("Preview:", true) }?.removePrefix("Preview:")?.trim()
		if (!locked && preview == null) return null
		val stats = l.filter { it.contains(": ") && !it.startsWith("Preview:", true) }
		return Emblem(s.cleanName, s.slot, locked, l.any { it.equals("SELECTED", true) || it.startsWith("Click to deselect", true) },
			if (req < 0) null else l.drop(req).takeWhile { it.isNotEmpty() }.joinToString(" ").removeSuffix("."), preview, stats)
	}

	fun parse(title: String, slots: List<SlotText>): EmblemsModel? {
		val t = TITLE.matchEntire(MenuText.clean(title)) ?: return null
		val nav = SkillLore.nav(slots)
		val groups = slots.mapNotNull { s ->
			val n = s.cleanLore.firstNotNullOfOrNull { UNLOCKED.matchEntire(it)?.groupValues?.get(1)?.toInt() } ?: return@mapNotNull null
			EmblemGroup(s.cleanName, s.slot, n, s.cleanLore.dropWhile { it.isNotEmpty() }.drop(1).takeWhile { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() })
		}
		val emblems = slots.filter { it.slot !in nav.values }.mapNotNull(::emblem)
		val page = t.groupValues[1].toIntOrNull()?.let { it to t.groupValues[2].toInt() }
		if (groups.isNotEmpty()) return EmblemsModel(groups, emblems.firstOrNull { it.selected }, emblems.filter { !it.selected }, page, nav)

		val fromOverview = slots.any { it.cleanName == "Go Back" && it.cleanLore.firstOrNull().equals("To Emblems", true) }
		if (!fromOverview || emblems.isEmpty()) return null
		return EmblemsModel(emptyList(), emblems.firstOrNull { it.selected }, emblems, page, nav)
	}
}

@Config
object EmblemsSettings : MenuSettings("emblems", panels = listOf("locked")) {
	val reset by button("reset") { resetToDefaults() }
}

object EmblemsMenu : CustomMenu {
	override val id = "emblems"
	override val displayName = "Prefix Emblems"

	override val title = Regex("(?!)")
	override val slotPredicate: (String, List<SlotText>) -> Boolean = { t, s -> EmblemsParser.parse(t, s) != null }
	override val settings get() = EmblemsSettings
	override val defaultSize = 440 to 240

	internal var lastCategory: String? = null
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = EmblemsView(screen)
}

class EmblemsView(screen: AbstractContainerScreen<*>) : SkillViewBase<EmblemsModel>(screen) {
	override fun palette() = EmblemsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = EmblemsParser.parse(title, slots)
	override fun draw(ctx: GuiGraphicsExtractor, m: EmblemsModel, mx: Int, my: Int) {
		val page = m.page?.let { "  §7page ${it.first}/${it.second}" } ?: ""
		if (m.isOverview) header(ctx, mx, my, "Prefix Emblems  §7${m.groups.sumOf { it.unlocked ?: 0 }} unlocked$page")
		else header(ctx, mx, my, "Emblems" + (EmblemsMenu.lastCategory?.let { " §7➜ §f$it" } ?: "") + "  §7${m.emblems.count { !it.locked }}/${m.emblems.size} unlocked$page")
		var gy = y + 22
		m.selected?.let { s ->
			val top = group(ctx, x + 4, gy, w - 8, 38, "Selected")
			tile(ctx, mx, my, x + 8, top, w - 16, 22, s.slot, "§f${s.name}", s.preview ?: s.requirement, null)
			gy += 42
		}
		val cols = 4; val gap = 4; val th = 24
		val tw = (w - 16 - gap * (cols - 1)) / cols
		if (m.isOverview) {
			val rows = (m.groups.size + cols - 1) / cols
			val top = group(ctx, x + 4, gy, w - 8, 14 + rows * (th + gap), "Emblem Groups")
			m.groups.forEachIndexed { i, g ->
				tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, g.slot, (if ((g.unlocked ?: 0) > 0) "§a" else "§7") + g.name, "${g.unlocked} unlocked", null)
				if (MenuTheme.inside(mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th)) EmblemsMenu.lastCategory = g.name
			}
		} else {
			val groups = listOf("Available" to m.emblems.filter { !it.locked && !it.selected }) +
				(if (EmblemsSettings.shows("locked")) listOf("Locked" to m.emblems.filter { it.locked }) else emptyList())
			for ((label, list) in groups) {
				if (list.isEmpty()) continue
				val rows = (list.size + cols - 1) / cols
				val gh = 14 + rows * (th + gap)
				val top = group(ctx, x + 4, gy, w - 8, gh, label)
				list.forEachIndexed { i, e ->
					tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, e.slot,
						(if (e.locked) "§7" else "§a") + e.name, if (e.locked) e.requirement else e.stats.firstOrNull() ?: e.preview, null)
				}
				gy += gh + 4
			}
		}
		navRow(ctx, mx, my, m.nav)
	}
}
