// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

enum class MainSection(val id: String) {
	TIMERS("timers"),
	GUI("gui"),
	HUD("hud"),
	WAYPOINTS("waypoints"),
	DUNGEONS("dungeons"),
	SECRETS("secrets"),
	MINING("mining"),
	FARMING("farming"),
	FISHING("fishing"),
	CUSTOM_MENUS("custom_menus"),
	EXPERIMENTATION("experimentation"),
	MISC("misc"),
	;

	val translationKey: String get() = "apex.settings.section.$id"

	companion object {
		private val byId = entries.associateBy { it.id }
		fun byId(id: String): MainSection? = byId[id]
	}
}

private val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]*")

data class Subsection(val main: MainSection, val id: String, val group: String = "") {
	init {
		require(ID_PATTERN.matches(id)) { "Bad subsection id '$id'" }
		require(group.isEmpty() || ID_PATTERN.matches(group)) { "Bad group '$group'" }
	}

	val path: String get() = "${main.id}.$id"
	val translationKey: String get() = "apex.settings.$path"
}

data class SettingPath(val main: MainSection, val subsection: String, val key: String) {
	override fun toString() = "${main.id}.$subsection.$key"

	companion object {
		fun parse(text: String): SettingPath {
			val parts = text.split('.', limit = 3)
			require(parts.size == 3 && parts.all { it.isNotEmpty() }) { "Setting path needs main.subsection.key: '$text'" }
			val main = requireNotNull(MainSection.byId(parts[0])) { "Unknown main section '${parts[0]}' in '$text'" }
			return SettingPath(main, parts[1], parts[2])
		}
	}
}

class SettingsRegistry {
	private val subsections = linkedMapOf<String, Subsection>()
	private val links = linkedMapOf<String, MutableSet<Subsection>>()
	private val settingOwners = hashMapOf<String, String>()

	fun register(main: MainSection, id: String, group: String = ""): Subsection {
		val sub = Subsection(main, id, group)
		val old = subsections[sub.path]
		require(old == null || old == sub) { "Subsection ${sub.path} registered twice with different groups" }
		subsections[sub.path] = sub
		return sub
	}

	fun link(from: MainSection, home: Subsection) {
		require(subsections[home.path] == home) { "Link target ${home.path} is not registered" }
		require(from != home.main) { "A subsection cannot link into its own main section" }
		links.getOrPut(from.id) { linkedSetOf() } += home
	}

	fun claim(path: SettingPath, feature: String) {
		require("${path.main.id}.${path.subsection}" in subsections) { "Subsection of $path is not registered" }
		val prev = settingOwners.putIfAbsent(path.toString(), feature)
		require(prev == null || prev == feature) { "Duplicate setting $path: claimed by '$prev' and '$feature'" }
	}

	fun subsections(main: MainSection): List<Subsection> = subsections.values.filter { it.main == main }
	fun linked(main: MainSection): List<Subsection> = links[main.id]?.toList() ?: emptyList()
	fun get(main: MainSection, id: String): Subsection? = subsections["${main.id}.$id"]
	val all: Collection<Subsection> get() = subsections.values

	companion object {

		val SPEC_TIMERS: List<String> get() = LayoutOrder.bundled.subsections(MainSection.TIMERS)

		fun default(map: MigrationMap = MigrationMap.bundled()): SettingsRegistry = SettingsRegistry().apply {
			val order = LayoutOrder.bundled
			SPEC_TIMERS.forEach { register(MainSection.TIMERS, it, order.groupOf(MainSection.TIMERS, it) ?: "") }
			HudSettings.SUBSECTIONS.forEach { register(MainSection.HUD, it, order.groupOf(MainSection.HUD, it) ?: "") }
			for (e in map.entries) register(e.newPath.main, e.newPath.subsection, e.group)

			for (p in map.prefixes) if (get(p.newPrefix.main, p.newPrefix.subsection) == null) register(p.newPrefix.main, p.newPrefix.subsection, p.group)
			HudSettings.registerInto(this, map)
			H1Sections.claimAll(this, map)
			dev.ashtonite365.apex.features.dungeon.SectionHome.H5.forEach { it.install(this, map) }
		}
	}
}

object RiskDefaults {
	const val BAN_RISK_LABEL = "⚠"
	fun defaultFor(main: MainSection, isAutoClick: Boolean): Boolean = main == MainSection.CUSTOM_MENUS && isAutoClick
}
