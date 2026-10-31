// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import net.minecraft.util.StringRepresentable

enum class ShowOn(private val id: String) : StringRepresentable {
	OFF("off"), BOARD_1("board_1"), BOARD_2("board_2"), INVENTORY_BOARD("inventory_board"), OWN_HUD("own_hud");

	override fun getSerializedName() = id
}

enum class BoardSort(private val id: String) : StringRepresentable {
	TIME_LEFT("time_left"), NAME("name"), MANUAL("manual");

	override fun getSerializedName() = id
}

enum class InventoryAnchor(private val id: String) : StringRepresentable {
	LEFT("left"), RIGHT("right"), TOP("top"), BOTTOM("bottom"), FREE("free");

	override fun getSerializedName() = id
}

enum class InventoryTheme(private val id: String) : StringRepresentable {
	FOLLOW_MENUS("follow_menus"), DARK("dark"), LIGHT("light");

	override fun getSerializedName() = id
}

object TimerRouting {

	val SHOW_ON: List<Pair<String, ShowOn>> = listOf(
		"custom" to ShowOn.BOARD_1, "ability-cooldowns" to ShowOn.BOARD_1, "ability-extras" to ShowOn.OWN_HUD,
		"treecapitator" to ShowOn.BOARD_1, "experiments" to ShowOn.BOARD_1, "boss-ticks" to ShowOn.OWN_HUD,
		"spirit-bear" to ShowOn.OWN_HUD, "dragon-spawn" to ShowOn.OWN_HUD, "dungeon-splits" to ShowOn.OWN_HUD,
		"invincibility" to ShowOn.OWN_HUD, "quiz" to ShowOn.OWN_HUD, "creation-cooldown" to ShowOn.OWN_HUD,
		"kuudra-splits" to ShowOn.OWN_HUD, "fresh-tools" to ShowOn.OWN_HUD, "puzzler" to ShowOn.BOARD_1,
		"fetchur" to ShowOn.BOARD_1, "mithril-powder" to ShowOn.BOARD_1, "gemstone-powder" to ShowOn.BOARD_1,
		"sky-mall" to ShowOn.BOARD_1, "mining-event" to ShowOn.BOARD_1, "mineshaft" to ShowOn.BOARD_1,
		"pickaxe-ability" to ShowOn.BOARD_1, "jacobs-contest" to ShowOn.BOARD_1, "pest-spawn" to ShowOn.OWN_HUD,
		"visitor" to ShowOn.OWN_HUD, "trevor" to ShowOn.BOARD_1, "cast-hook" to ShowOn.OWN_HUD,
		"golden-fish" to ShowOn.BOARD_1, "totem" to ShowOn.OWN_HUD, "dark-auction" to ShowOn.BOARD_1,
		"spooky-festival" to ShowOn.BOARD_1, "season-of-jerry" to ShowOn.BOARD_1, "new-year" to ShowOn.BOARD_1,
		"traveling-zoo" to ShowOn.BOARD_1, "cake" to ShowOn.BOARD_1, "jerry" to ShowOn.BOARD_1,
		"primal-fear" to ShowOn.OWN_HUD, "jyrre" to ShowOn.OWN_HUD, "slayer-quest" to ShowOn.BOARD_1,
		"slayer-boss" to ShowOn.BOARD_1, "slayer-pb" to ShowOn.BOARD_1, "fire-pillar" to ShowOn.OWN_HUD,
		"arachne" to ShowOn.BOARD_1, "rift-time" to ShowOn.BOARD_1, "infusion" to ShowOn.BOARD_1,
		"crimson-minibosses" to ShowOn.BOARD_1, "crimson-quests" to ShowOn.BOARD_1, "heavy-pearls" to ShowOn.BOARD_1,
		"booster-cookie" to ShowOn.BOARD_1, "god-potion" to ShowOn.BOARD_1,
	)

	val DEFAULTS: Map<String, ShowOn> = SHOW_ON.toMap()

	private val ENTRY_ALIASES = mapOf("abilities" to "ability-cooldowns")

	val OWN_HUD_ELEMENTS: Map<String, String> = mapOf(
		"ability-cooldowns.extras-hud" to "ability-extras",
		"boss-tick-timers.hud" to "boss-ticks",
		"spirit-bear-timer.hud" to "spirit-bear",
		"wither-dragons.hud" to "dragon-spawn",
		"dungeon-splits.hud" to "dungeon-splits",
		"dungeon-status-huds.invincibility-hud" to "invincibility",
		"puzzle-solvers.quiz-timer-position" to "quiz",
		"dungeon-tweaks.creation-cooldown-hud" to "creation-cooldown",
		"kuudra-splits.hud" to "kuudra-splits",
		"kuudra-info.fresh-hud-pos" to "fresh-tools",
		"great-spook.timer-hud" to "primal-fear",
		"plus-seasonal.jyrre-hud" to "jyrre",
		"slayer-extras.fire-pillar-hud" to "fire-pillar",
	)

	val LINE_TIMERS: Set<String> = setOf("pest-spawn", "visitor", "cast-hook", "totem")

	fun showOnId(entryId: String): String = ENTRY_ALIASES[entryId] ?: entryId

	fun needsNewOwnHud(id: String): Boolean = id !in OWN_HUD_ELEMENTS.values && id !in LINE_TIMERS

	data class Item(val id: String, val text: String, val remaining: Duration? = null)

	data class Routed(
		val board1: List<Item>,
		val board2: List<Item>,
		val inventory: List<Item>,

		val own: Map<String, List<Item>>,
	)

	fun route(items: List<Item>, showOn: (String) -> ShowOn, inventoryIncludesBoard1: Boolean): Routed {
		val b1 = ArrayList<Item>(); val b2 = ArrayList<Item>(); val inv = ArrayList<Item>()
		val own = LinkedHashMap<String, MutableList<Item>>()
		for (item in items) when (showOn(item.id)) {
			ShowOn.OFF -> {}
			ShowOn.BOARD_1 -> { b1 += item; if (inventoryIncludesBoard1) inv += item }
			ShowOn.BOARD_2 -> b2 += item
			ShowOn.INVENTORY_BOARD -> inv += item
			ShowOn.OWN_HUD -> own.getOrPut(item.id) { ArrayList() } += item
		}
		return Routed(b1, b2, inv, own)
	}

	private val CODES = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun plain(text: String) = text.replace(CODES, "")

	fun sort(items: List<Item>, sort: BoardSort): List<Item> = when (sort) {
		BoardSort.MANUAL -> items
		BoardSort.NAME -> items.sortedBy { plain(it.text).trim().lowercase() }
		BoardSort.TIME_LEFT -> items.sortedWith(compareBy<Item> { it.remaining != null }.thenBy { it.remaining })
	}

	enum class Capture { DRAW, DROP, TO_BOARD }

	fun capture(target: ShowOn): Capture = when (target) {
		ShowOn.OWN_HUD -> Capture.DRAW
		ShowOn.OFF -> Capture.DROP
		else -> Capture.TO_BOARD
	}

	fun fade(colour: Int, percent: Int): Int {
		val a = (colour ushr 24) * percent.coerceIn(0, 100) / 100
		return (a shl 24) or (colour and 0xFFFFFF)
	}
}
