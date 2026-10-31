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
import dev.ashtonite365.apex.features.misc.CombatStatsVitality

object StatsParser {
	val TITLE = Regex("Stats & Equipment|Your Stats Breakdown")

	private val STAT = Regex("^[^A-Za-z]*([A-Z][A-Za-z' ]*?[A-Za-z])\\s+(-?[\\d,]+(?:\\.\\d+)?%?)$")

	fun stat(line: String): Pair<String, String>? = STAT.matchEntire(MenuText.clean(line))?.let { it.groupValues[1] to it.groupValues[2] }
	fun stats(lore: List<String>) = lore.mapNotNull(::stat)

	data class Category(val slot: Int, val name: String, val stats: List<Pair<String, String>>, val vitality: Double?)

	fun categories(slots: List<SlotText>) = slots.filter { it.cleanName.endsWith(" Stats") }.mapNotNull { s ->
		val st = stats(s.lore).ifEmpty { return@mapNotNull null }
		Category(s.slot, s.cleanName, st, if (s.cleanName == "Combat Stats") CombatStatsVitality.parseLore(s.lore) else null)
	}

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val filled = slots.filter { !HubText.isEmpty(it) && !HubText.isNav(it) }
		val nav = HubText.navOf(slots)
		return if (t == "Your Stats Breakdown") breakdown(filled, nav) else overview(filled, nav)
	}

	private fun overview(filled: List<SlotText>, nav: List<Pair<String, Int>>): HubModel? {
		val cats = categories(filled)
		if (cats.isEmpty()) return null
		val catTiles = cats.map { c ->
			val top = c.stats.take(2).map { "${it.first} ${it.second}" }
			HubTile(c.slot, c.name.removeSuffix(" Stats"), top, tooltip = c.stats.map { "${it.first}: §f${it.second}" } + listOfNotNull(c.vitality?.let { "Vitality: §f$it" }))
		}
		val rest = filled.filter { s -> cats.none { it.slot == s.slot } && !s.cleanName.contains("SkyBlock Menu") }
		val pet = rest.filter { it.cleanName.startsWith("[Lvl ") }
		val gear = rest.filter { it !in pet && HubText.rarity(it.lore) != null }
		val more = rest.filter { it !in pet && it !in gear }
		fun item(s: SlotText) = HubTile(s.slot, HubText.colour(HubText.rarity(s.lore)) + s.cleanName,
			HubText.clean(s.lore).filter { it.contains(": ") }.take(2), tooltip = HubText.infoLines(s.lore))
		val moreTiles = more.map { s -> HubTile(s.slot, s.cleanName, HubText.clean(s.lore).filter { Regex("^[A-Z][A-Za-z ]+: .+").matches(it) && !it.startsWith("Also") }.take(2), tooltip = HubText.infoLines(s.lore)) }
		val combat = cats.firstOrNull { it.name == "Combat Stats" }?.stats?.toMap().orEmpty()
		val kv = more.map { HubText.kv(it.lore) }.fold(mapOf<String, String>()) { a, b -> b + a }
		val chips = listOfNotNull(combat["Health"]?.let { "Health" to it }, combat["Defense"]?.let { "Defense" to it },
			combat["Strength"]?.let { "Strength" to it }, cats.firstNotNullOfOrNull { it.vitality }?.let { "Vitality" to MenuText.clean("%.0f".format(java.util.Locale.ROOT, it)) },
			kv["unlocked"]?.let { "Achievements" to it }, kv["currently active"]?.let { "Effects" to it })
		val sections = listOf(HubSection("Stats", catTiles), HubSection("Equipment", gear.map(::item)), HubSection("Pet", pet.map(::item)), HubSection("More", moreTiles)).filter { it.tiles.isNotEmpty() }
		return HubModel("Stats & Equipment", chips, sections, nav)
	}

	private fun breakdown(filled: List<SlotText>, nav: List<Pair<String, Int>>): HubModel? {
		val cat = categories(filled).firstOrNull() ?: return null
		val statTiles = filled.filter { it.slot != cat.slot }.mapNotNull { s ->
			val (n, v) = stat(s.name) ?: return@mapNotNull null
			val derived = HubText.clean(s.lore).filter { it.contains(": ") }
			HubTile(s.slot, "$n §f$v", derived.take(2), tooltip = derived)
		}
		if (statTiles.isEmpty()) return null
		val actions = filled.filter { s -> s.slot != cat.slot && stat(s.name) == null }
		val chips = listOf("Category" to cat.name) + listOfNotNull(cat.vitality?.let { "Vitality" to "%.0f".format(java.util.Locale.ROOT, it) }) +
			actions.mapNotNull { a -> HubText.kv(a.lore).entries.firstOrNull()?.let { a.cleanName.removePrefix("Toggle ") to it.value } }
		return HubModel("Your Stats · ${cat.name}", chips, listOf(HubSection(cat.name, statTiles)), nav + actions.map { it.cleanName to it.slot })
	}
}

@Config
object StatsSettings : MenuSettings("stats") {
	val reset by button("reset") { resetToDefaults() }
}

object StatsMenu : CustomMenu {
	override val id = "stats"
	override val displayName = "Stats & Equipment"
	override val title = StatsParser.TITLE
	override val settings get() = StatsSettings
	override val defaultSize = 380 to 250

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = StatsView(screen)
}

class StatsView(screen: AbstractContainerScreen<*>) : HubView(screen, { StatsSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = StatsParser.parse(title, slots)
}
