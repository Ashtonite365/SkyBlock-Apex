// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry

open class SectionHome(
	val main: MainSection,

	val holders: List<String>,

	val groups: Map<String, String> = emptyMap(),

	val links: List<Pair<MainSection, String>> = emptyList(),

	val borrowed: List<String> = emptyList(),
) {

	fun entries(map: MigrationMap) = holders.flatMap { h -> map.entries.filter { it.oldConfig == h && it.oldKey !in lentOut() } } +
		borrowed.mapNotNull { k -> map.entries.firstOrNull { it.oldKey == k } }

	fun lentOut(): Set<String> = H5.filter { it !== this }.flatMap { it.borrowed }.toSet()

	fun install(reg: SettingsRegistry, map: MigrationMap) {
		for (e in entries(map)) {

			val here = e.newPath.main == main
			reg.register(e.newPath.main, e.newPath.subsection, if (here) groups[e.newPath.subsection] ?: e.group else e.group)
			reg.claim(e.newPath, e.oldConfig)
		}
		for ((from, path) in links) {
			val (m, sub) = path.split('.', limit = 2)
			val target = requireNotNull(MainSection.byId(m)) { path }

			if (reg.get(target, sub) == null && map.entries.none { it.newPath.main == target && it.newPath.subsection == sub }) continue
			val home = requireNotNull(reg.get(target, sub)) { "Link target $path not registered" }
			reg.link(from, home)
		}
	}

	fun paths(map: MigrationMap): List<SettingPath> = entries(map).map { it.newPath }

	companion object {

		val H5: List<SectionHome> by lazy {
			listOf(DungeonsHome, dev.ashtonite365.apex.features.dungeon.secrets.SecretsHome, dev.ashtonite365.apex.features.mining.MiningHome,
			       dev.ashtonite365.apex.features.garden.FarmingHome, dev.ashtonite365.apex.features.fishing.FishingHome)
		}
	}
}

object DungeonsHome : SectionHome(
	MainSection.DUNGEONS,
	listOf(
		"terminal-solver", "f7-devices", "puzzle-solvers",
		"dungeon-map", "dungeon-score", "dungeon-splits",
		"dungeon-status-huds", "dungeon-chest-profit", "dungeon-loot-highlighter", "dungeon-extras", "blood-mob-esp",
		"shadow-assassin-finder",
		"terminal-extras", "sharpshooter",
		"wither-dragons", "king-relics", "dungeon-tweaks",
		"kuudra-info", "kuudra-supplies", "kuudra-splits", "kuudra-profit",
	),
	groups = mapOf("kuudra-info" to "kuudra", "kuudra-supplies" to "kuudra", "kuudra-splits" to "kuudra", "kuudra-profit" to "kuudra"),

	links = listOf(
		MainSection.DUNGEONS to "timers.boss-timers",
	),
)
