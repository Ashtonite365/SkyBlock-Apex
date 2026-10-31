// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

data class Placement(val path: SettingPath, val group: String = "") {
	val subsectionPath: String get() = "${path.main.id}.${path.subsection}"
}

object SettingsPlacement {

	val CATEGORY_MAIN: Map<String, MainSection> = mapOf(
		"TIMERS" to MainSection.TIMERS,
		"HUD" to MainSection.HUD,
		"INVENTORY" to MainSection.GUI,
		"AUCTION_BAZAAR" to MainSection.GUI,
		"PROFILE_VIEWER" to MainSection.GUI,
		"MUSEUM" to MainSection.GUI,
		"DUNGEONS" to MainSection.DUNGEONS,
		"KUUDRA" to MainSection.DUNGEONS,
		"MINING" to MainSection.MINING,
		"GARDEN" to MainSection.FARMING,
		"FISHING" to MainSection.FISHING,
		"CUSTOM_MENUS" to MainSection.CUSTOM_MENUS,
	)

	const val CUSTOM_MENU_PREFIX = "custom-menu-"

	private val SAFE = Regex("[^a-z0-9-]+")
	private fun safeId(s: String) = s.lowercase().replace(SAFE, "-").trim('-').ifEmpty { "general" }

	fun fallbackSubsection(config: String): String = when {
		config == "custom-menus" -> "general"
		config.startsWith(CUSTOM_MENU_PREFIX) -> safeId(config.removePrefix(CUSTOM_MENU_PREFIX))
		else -> safeId(config)
	}

	fun place(config: String, key: String, oldCategory: String, map: MigrationMap, siblingOf: (String) -> Placement?): Placement {
		map["$config.$key"]?.let { return Placement(it.newPath, it.group) }
		siblingOf(config)?.let { return Placement(SettingPath(it.path.main, it.path.subsection, "$config.$key"), it.group) }
		val main = CATEGORY_MAIN[oldCategory] ?: MainSection.MISC
		val group = if (main == MainSection.MISC && oldCategory != "MISC" && oldCategory != "CREDITS") safeId(oldCategory) else ""
		return Placement(SettingPath(main, fallbackSubsection(config), key), group)
	}

	fun siblings(map: MigrationMap): Map<String, Placement> {
		val out = linkedMapOf<String, Placement>()
		for (e in map.entries) out.putIfAbsent(e.oldConfig, Placement(e.newPath, e.group))
		return out
	}
}

data class LayoutItem<T>(val config: String, val key: String, val oldCategory: String, val value: T)

data class SubsectionView<T>(val subsection: Subsection, val items: List<T>)

data class SectionPage<T>(
	val main: MainSection,
	val group: String,
	val subsections: List<SubsectionView<T>>,
	val links: List<Subsection> = emptyList(),
) {
	val identifier: String get() = if (group.isEmpty()) main.name else "${main.name}__$group"
	val parentIdentifier: String? get() = if (group.isEmpty()) null else main.name
	val translationKey: String get() = if (group.isEmpty()) main.translationKey else "apex.settings.${main.id}.group.$group"

	val fallbackTranslationKey: String get() = if (group.isEmpty()) main.translationKey else "apex.settings.group.$group"
}

object SettingsLayout {

	val DEFAULT_LINKS: List<Pair<MainSection, String>> = listOf(
		MainSection.TIMERS to "farming.trevor",

		MainSection.WAYPOINTS to "secrets.secret-wayfinder",
		MainSection.SECRETS to "waypoints.wp-secret-wayfinder",
		MainSection.WAYPOINTS to "mining.solvers",
		MainSection.MINING to "waypoints.wp-mining-solvers",
		MainSection.WAYPOINTS to "misc.islands-npcs",
		MainSection.MISC to "waypoints.wp-npcs",
	)

	fun <T> pages(
		items: List<LayoutItem<T>>,
		map: MigrationMap = MigrationMap.bundled(),
		registry: SettingsRegistry = SettingsRegistry.default(map),
		links: List<Pair<MainSection, String>> = DEFAULT_LINKS,
		order: LayoutOrder = LayoutOrder.bundled,
	): List<SectionPage<T>> {
		val siblings = SettingsPlacement.siblings(map)
		val bySubItems = linkedMapOf<Subsection, MutableList<LayoutItem<T>>>()
		for (it in items) {
			val p = SettingsPlacement.place(it.config, it.key, it.oldCategory, map) { c -> siblings[c] }
			val sub = registry.get(p.path.main, p.path.subsection) ?: registry.register(p.path.main, p.path.subsection, p.group)
			bySubItems.getOrPut(sub) { mutableListOf() }.add(it)
		}
		val bySub = linkedMapOf<Subsection, List<T>>()
		for ((sub, list) in bySubItems) bySub[sub] = solversFirst(sub, list.sortedBy { order.rowIndex("${it.config}.${it.key}") }).map { it.value }
		for ((from, path) in links) {
			val (m, s) = path.split('.', limit = 2)
			val home = registry.get(MainSection.byId(m) ?: continue, s) ?: continue
			if (home in bySub && from != home.main) registry.link(from, home)
		}
		return buildList {
			for (main in MainSection.entries) {
				val subs = bySub.filterKeys { it.main == main }
				val groups = subs.keys.map { it.group }.distinct().sortedWith(compareBy({ it.isNotEmpty() }, { order.groupIndex(main, it) }, { it }))
				val top = registry.linked(main).filter { it in bySub }
				if ("" !in groups) add(SectionPage(main, "", emptyList(), top))
				for (g in groups) {
					val views = subs.filterKeys { it.group == g }.map { (k, v) -> SubsectionView(k, v) }
						.sortedWith(compareBy({ order.subsectionIndex(main, it.subsection.id) }, { specIndex(it.subsection) }, { it.subsection.id }))
					add(SectionPage(main, g, views, if (g.isEmpty()) top else emptyList()))
				}
			}
		}
	}

	val EXPERIMENTATION_ORDER = listOf("experimentation-table", "superpairs", "chronomatron", "ultrasequencer")

	val SECRETS_ORDER = listOf(
		"crypts", "chests", "mini-bosses", "secret-wayfinder", "superboom", "breakable-walls", "room-clear-alert", "room-ticks",
		"secret-chime", "mute-secret-sound", "run-summary",
	)

	const val MASTER_SWITCH_SUFFIX = "-apex-menus"

	val DUNGEONS_SOLVER_SUBSECTIONS = listOf("terminal-solver", "f7-devices", "puzzle-solvers")

	val PUZZLE_SOLVER_KEYS = listOf(
		"blaze-solver", "blaze-current-colour", "blaze-next-colour", "blaze-done-colour",
		"blaze-target-line", "blaze-target-line-colour", "blaze-next-line", "blaze-next-line-colour", "blaze-blocked-line-colour",
		"creeper-beams-solver", "creeper-beams-colour-1", "creeper-beams-colour-2", "creeper-beams-colour-3", "creeper-beams-colour-4",
		"three-weirdos", "three-weirdos-npc", "three-weirdos-wrong-red",
		"tic-tac-toe", "tic-tac-toe-colour", "tic-tac-toe-key",
		"quiz", "quiz-box", "quiz-box-colour", "quiz-timer", "quiz-timer-position",
		"ice-fill-solver", "ice-fill-bundled", "ice-fill-optimal", "ice-fill-colour", "ice-fill-next-colour",
		"ice-path-solver", "ice-path-colour", "ice-path-next-colour",
		"boulder", "boulder-show-all", "boulder-path", "boulder-path-colour",
		"water-board-solver", "water-board-guide-line", "water-board-preview", "water-board-steps-hud", "water-board-hud-position", "water-board-next-colour", "water-board-flow-colour", "water-board-chest-pulse",
		"teleport-maze", "teleport-maze-colour", "teleport-maze-guide", "teleport-maze-guide-colour", "teleport-maze-guide-line", "teleport-maze-pad-links",
	)

	private fun <T> solversFirst(sub: Subsection, list: List<LayoutItem<T>>): List<LayoutItem<T>> {
		if (sub.main != MainSection.DUNGEONS || sub.id != "puzzle-solvers") return list
		return list.sortedBy { PUZZLE_SOLVER_KEYS.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
	}

	private fun specIndex(s: Subsection): Int = when {
		s.main == MainSection.DUNGEONS && s.id in DUNGEONS_SOLVER_SUBSECTIONS -> -100 + DUNGEONS_SOLVER_SUBSECTIONS.indexOf(s.id)
		s.id.endsWith(MASTER_SWITCH_SUFFIX) -> -1
		s.main == MainSection.TIMERS -> SettingsRegistry.SPEC_TIMERS.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it }
		s.main == MainSection.EXPERIMENTATION -> EXPERIMENTATION_ORDER.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it }
		s.main == MainSection.SECRETS -> SECRETS_ORDER.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it }
		else -> 0
	}

	fun labelKeys(sub: Subsection): List<String> = listOf(
		sub.translationKey,
		"apex.config.${sub.id}",
		"apex.config.${SettingsPlacement.CUSTOM_MENU_PREFIX}${sub.id}",
	)

	fun pretty(id: String): String = id.split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
