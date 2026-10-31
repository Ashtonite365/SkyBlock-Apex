// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

object HudSettings {

	val HOLDERS: Map<String, String> = linkedMapOf(
		"hud" to "layout",
		"hud-style" to "style",
		"stat-bars" to "stat-bars",
		"stat-bars-vanilla" to "stat-bars",
		"stat-bars-action-bar" to "stat-bars",
		"custom-scoreboard" to "scoreboard",
		"compact-tab-list" to "tab-list",
		"skill-overlay" to "skills",
		"deployable-hud" to "deployables",
		"sack-hud" to "sacks",
	)

	val SUBSECTIONS: List<String> get() = LayoutOrder.bundled.subsections(MainSection.HUD).ifEmpty { HOLDERS.values.distinct() }

	val KEYBIND_KINDS = setOf("keyBinding", "keyBindingWithDefaultUnbound", "keyBindingWithOutDefaultModifiers")

	fun hudEntries(map: MigrationMap): List<MigrationEntry> = map.entries.filter { it.newPath.main == MainSection.HUD }

	fun registerInto(reg: SettingsRegistry, map: MigrationMap = MigrationMap.bundled()): List<Subsection> {
		SUBSECTIONS.forEach { reg.register(MainSection.HUD, it, LayoutOrder.bundled.groupOf(MainSection.HUD, it) ?: "") }
		for (e in hudEntries(map)) {
			reg.register(MainSection.HUD, e.newPath.subsection, e.group)
			reg.claim(e.newPath, e.oldConfig)
		}
		val homes = map.entries.filter { it.kind == "position" && it.newPath.main != MainSection.HUD }
			.map { reg.register(it.newPath.main, it.newPath.subsection, it.group) }
			.distinct()
		homes.forEach { reg.link(MainSection.HUD, it) }
		return homes
	}

	fun keybindsBySubsection(map: MigrationMap = MigrationMap.bundled()): Map<String, List<SettingPath>> =
		hudEntries(map).filter { it.kind in KEYBIND_KINDS }.groupBy({ it.newPath.subsection }, { it.newPath })

	fun riskyEntries(map: MigrationMap = MigrationMap.bundled()): List<MigrationEntry> =
		hudEntries(map).filter { it.kind == "riskyToggle" || it.kind == "customMenuAutoClick" }
}
