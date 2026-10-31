// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.h6

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.features.calendar.CalendarScreen

object CalendarMenuParser {

	private const val SEASON = "(?:(?:Early|Late) )?(?:Spring|Summer|Autumn|Winter)"

	val TITLE = Regex("^(?:Calendar and Events|Event Rewards|$SEASON, Year \\d+)$")
	private val MONTH = Regex("^($SEASON), Year (\\d+)$")
	const val REWARDS = "Event Rewards"

	data class Event(val slot: Int, val name: String, val startsIn: String, val lasts: String?, val seconds: Double?)

	private val LASTS = Regex("Event lasts for (.+?)!?$")

	private val DAY_EVENT = Regex("^(\\d{1,2}:\\d{2} [ap]m-\\d{1,2}:\\d{2} [ap]m): (.+?)(?: \\(([^)]+)\\))?$")

	fun events(slots: List<SlotText>): List<Event> = slots.mapNotNull { s ->
		val c = HubText.clean(s.lore)
		val starts = c.firstOrNull { it.startsWith("Starts in:") }?.substringAfter(":")?.trim() ?: return@mapNotNull null
		Event(s.slot, s.cleanName, starts, c.firstNotNullOfOrNull { LASTS.find(it)?.groupValues?.get(1) }, MenuText.durationSeconds(starts)?.toDouble())
	}

	private fun pageOf(s: SlotText?) = s?.cleanLore?.firstNotNullOfOrNull { Regex("^Page (\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toInt() }

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		MONTH.matchEntire(t)?.let { return month(it.groupValues[1], it.groupValues[2], slots) }
		if (t == REWARDS) return rewards(slots)
		val ev = events(slots)
		val others = slots.filter { !HubText.isEmpty(it) && !HubText.isNav(it) && ev.none { e -> e.slot == it.slot } }
		if (ev.isEmpty() && others.isEmpty()) return null

		val evTiles = ev.map { HubTile(it.slot, it.name, listOfNotNull("in ${it.startsIn}", it.lasts?.let { l -> "lasts $l" }), clickable = false,
			tooltip = listOf("Starts in: ${it.startsIn}") + listOfNotNull(it.lasts?.let { l -> "Lasts: $l" })) }
		val more = others.map { HubTile(it.slot, it.cleanName, HubText.infoLines(it.lore).take(3), tooltip = HubText.infoLines(it.lore)) }
		val chips = listOfNotNull(ev.firstOrNull()?.let { "Next" to "${it.name} · ${it.startsIn}" },
			others.firstOrNull { it.cleanName.startsWith("Mayor ") }?.let { "Mayor" to it.cleanName.removePrefix("Mayor ") })
		return HubModel("Calendar and Events", chips, listOf(HubSection("Upcoming events", evTiles), HubSection("More", more)).filter { it.tiles.isNotEmpty() }, HubText.navOf(slots))
	}

	private fun rewards(slots: List<SlotText>): HubModel? {
		val filled = slots.filter { !HubText.isEmpty(it) && !HubText.isNav(it) }
		if (filled.isEmpty()) return null
		val header = filled.firstOrNull { it.cleanName == REWARDS }

		val notices = filled.filter { it !== header && HubText.clean(it.lore).none { l -> l.startsWith("Click", true) } }
		val rewards = filled.filter { it !== header && it !in notices }
		val empty = notices.firstOrNull { it.cleanName.startsWith("No ", true) }
		val chips = listOf("Rewards" to rewards.size.toString()) + notices.filter { it !== empty }.map { it.cleanName.trimEnd('!') to HubText.infoLines(it.lore).joinToString(" ") }
		val tiles = rewards.map { HubTile(it.slot, it.cleanName, HubText.infoLines(it.lore).take(2), tooltip = HubText.infoLines(it.lore)) }
		return HubModel(REWARDS, chips, if (tiles.isEmpty()) emptyList() else listOf(HubSection("Rewards", tiles)), HubText.navOf(slots),
			emptyText = empty?.let { HubText.infoLines(it.lore).joinToString(" ").ifEmpty { it.cleanName } } ?: "No rewards")
	}

	private fun month(season: String, year: String, slots: List<SlotText>): HubModel? {
		val days = slots.filter { HubText.clean(it.lore).let { c -> c.isNotEmpty() && c.all { l -> DAY_EVENT.matches(l) || l.startsWith("○") || l == "No events" } } && !HubText.isNav(it) }
		if (days.isEmpty()) return null
		val tiles = days.map { d ->
			val evs = HubText.clean(d.lore).mapNotNull { DAY_EVENT.matchEntire(it) }
			HubTile(d.slot, (if (evs.isEmpty()) "§7" else "§f") + d.cleanName,
				evs.map { it.groupValues[2] }.ifEmpty { listOf("No events") }, clickable = false, tooltip = HubText.clean(d.lore))
		}
		val prev = slots.firstOrNull { it.cleanName == "Previous Page" }; val next = slots.firstOrNull { it.cleanName == "Next Page" }
		val page = pageOf(next)?.minus(1) ?: pageOf(prev)?.plus(1)
		val nav = HubText.navOf(slots).map { (l, s) -> when (l) {
			"Next Page" -> "Next (Page ${pageOf(next)})" to s; "Previous Page" -> "Prev (Page ${pageOf(prev)})" to s; else -> l to s } }
		val evCount = days.sumOf { d -> HubText.clean(d.lore).count { DAY_EVENT.matches(it) } }
		val chips = listOfNotNull("Month" to season, "Year" to year, page?.let { "Page" to it.toString() }, "Events" to evCount.toString())
		return HubModel("$season, Year $year", chips, listOf(HubSection("Days", tiles)), nav)
	}
}

@Config
object CalendarMenuSettings : MenuSettings("calendar") {
	val reset by button("reset") { resetToDefaults() }
}

object CalendarMenu : CustomMenu {
	override val id = "calendar"
	override val displayName = "Calendar and Events"
	override val title = CalendarMenuParser.TITLE
	override val settings get() = CalendarMenuSettings
	override val defaultSize = 360 to 230

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = CalendarMenuView(screen)
}

class CalendarMenuView(screen: AbstractContainerScreen<*>) : HubView(screen, { CalendarMenuSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = CalendarMenuParser.parse(title, slots)
	override fun extraButtons(m: HubModel) = listOf<Pair<String, () -> Unit>>("Apex Calendar" to { CalendarScreen.open() })

	override fun liveOnly(m: HubModel) = m.heading == CalendarMenuParser.REWARDS ||
		MenuText.clean(screen.title.string) == CalendarMenuParser.REWARDS
}
