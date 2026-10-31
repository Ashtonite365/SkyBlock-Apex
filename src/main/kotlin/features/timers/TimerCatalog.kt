// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

enum class TimerSection(val id: String, val label: String) {
	GENERAL("general", "General"),
	FISHING("fishing", "Fishing"),
	DUNGEON("dungeon", "Dungeon"),
	CONTEST("contest", "Contest"),
	EVENT("event", "Event"),
	MINING("mining", "Mining"),
	SLAYER("slayer", "Slayer"),
	RIFT("rift", "Rift"),
	CRIMSON_ISLE("crimson-isle", "Crimson Isle"),
	COOLDOWNS("cooldowns", "Cooldowns"),
	BUFFS("buffs", "Buffs"),
	ITEMS("items", "Items"),
	FARMING("farming", "Farming"),
	;
}

data class TimerOption(
	val key: String,
	val section: TimerSection,
	val timerId: String? = null,
	val master: String? = null,
	val default: Boolean = true,
) {
	val path: String get() = "timers.${section.id}.$key"
}

data class TimerHolderPlacement(val holder: String, val section: TimerSection, val isHome: Boolean)

object TimerCatalog {

	val removedKeys: Map<String, String> = linkedMapOf(
		"show-abilities" to "timers.cooldowns.ability-cooldowns.enabled",
		"show-trevor" to "farming.trevor.enabled",
	)

	val retiredIds: Set<String> = setOf(
		"slayer",
		"cookie",
		"events",
	)

	private fun t(key: String, section: TimerSection, id: String? = key.removePrefix("show-"), master: String? = null, default: Boolean = true) =
		TimerOption(key, section, id, master, default)

	val options: List<TimerOption> = listOf(

		TimerOption("enabled", TimerSection.GENERAL, default = false),
		TimerOption("hud", TimerSection.GENERAL),
		TimerOption("only-pending", TimerSection.GENERAL, default = false),
		TimerOption("open-calendar", TimerSection.GENERAL),
		t("show-custom", TimerSection.GENERAL),

		t("show-golden-fish", TimerSection.FISHING),

		t("show-jacobs-contest", TimerSection.CONTEST),

		TimerOption("show-events", TimerSection.EVENT),
		t("show-dark-auction", TimerSection.EVENT, master = "show-events"),
		t("show-spooky-festival", TimerSection.EVENT, master = "show-events"),
		t("show-season-of-jerry", TimerSection.EVENT, master = "show-events"),
		t("show-new-year", TimerSection.EVENT, master = "show-events"),
		t("show-traveling-zoo", TimerSection.EVENT, master = "show-events"),
		t("show-cake", TimerSection.EVENT),
		t("show-jerry", TimerSection.EVENT),
		t("show-arachne", TimerSection.EVENT),

		t("show-puzzler", TimerSection.MINING),
		t("show-fetchur", TimerSection.MINING),
		t("show-mithril-powder", TimerSection.MINING),
		t("show-gemstone-powder", TimerSection.MINING),
		t("show-pickaxe-ability", TimerSection.MINING),
		t("show-sky-mall", TimerSection.MINING),
		t("show-mining-event", TimerSection.MINING),
		t("show-mineshaft", TimerSection.MINING),

		TimerOption("show-slayer", TimerSection.SLAYER),
		t("show-slayer-quest", TimerSection.SLAYER, master = "show-slayer"),
		t("show-slayer-boss", TimerSection.SLAYER, master = "show-slayer"),
		t("show-slayer-pb", TimerSection.SLAYER, master = "show-slayer"),
		TimerOption("slayer-pb-pace", TimerSection.SLAYER),
		TimerOption("slayer-last-kill", TimerSection.SLAYER),

		t("show-rift-time", TimerSection.RIFT),
		t("show-infusion", TimerSection.RIFT),

		t("show-crimson-minibosses", TimerSection.CRIMSON_ISLE),
		t("show-crimson-quests", TimerSection.CRIMSON_ISLE),
		t("show-heavy-pearls", TimerSection.CRIMSON_ISLE),

		t("show-treecapitator", TimerSection.COOLDOWNS),
		t("show-experiments", TimerSection.COOLDOWNS),

		t("show-cookie", TimerSection.BUFFS, id = "booster-cookie"),
		TimerOption("cookie-warning", TimerSection.BUFFS),
		TimerOption("cookie-warning-minutes", TimerSection.BUFFS),
		TimerOption("cookie-expired-warning", TimerSection.BUFFS),
		TimerOption("cookie-warning-title", TimerSection.BUFFS, default = false),
		TimerOption("cookie-hud", TimerSection.BUFFS),
		TimerOption("cookie-hud-position", TimerSection.BUFFS),
		t("show-god-potion", TimerSection.BUFFS),
	)

	val holders: List<TimerHolderPlacement> = listOf(
		TimerHolderPlacement("boss-tick-timers", TimerSection.DUNGEON, true),
		TimerHolderPlacement("spirit-bear-timer", TimerSection.DUNGEON, true),
		TimerHolderPlacement("terracotta-timer", TimerSection.DUNGEON, true),
		TimerHolderPlacement("ability-cooldowns", TimerSection.COOLDOWNS, true),
		TimerHolderPlacement("treecapitator", TimerSection.COOLDOWNS, true),
		TimerHolderPlacement("lore-timers", TimerSection.ITEMS, true),
		TimerHolderPlacement("garden-tools", TimerSection.CONTEST, false),
		TimerHolderPlacement("trevor", TimerSection.FARMING, false),
	)

	private val byKey = options.associateBy { it.key }
	private val byTimerId = options.filter { it.timerId != null }.associateBy { it.timerId!! }

	fun option(key: String): TimerOption? = byKey[key]
	fun forTimer(id: String): TimerOption? = byTimerId[id]
	fun inSection(section: TimerSection): List<TimerOption> = options.filter { it.section == section }

	fun isShown(id: String, value: (String) -> Boolean): Boolean {
		if (id in retiredIds) return false
		val opt = forTimer(id) ?: return true
		return value(opt.key) && (opt.master == null || value(opt.master))
	}

	fun migrationRows(kindOf: (String) -> String): List<String> =
		options.map { "timers-overlay.${it.key}\t${it.path}\t${kindOf(it.key)}\t" }
}
