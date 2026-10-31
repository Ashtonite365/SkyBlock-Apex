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

object BoosterCookieParser {

	val TITLE = Regex("^Booster Cookie$")

	data class Figures(val purse: String?, val available: String?, val multiplier: String?, val rank: String?, val fame: String?, val gems: String?, val buffs: List<String>,
	                   val duration: String? = null, val perCookie: String? = null, val nextRank: String? = null)

	fun joinBuffs(lines: List<String>): List<String> = lines.fold(mutableListOf<String>()) { acc, l ->
		if (l.startsWith("‣") || acc.isEmpty()) acc += l.removePrefix("‣").trim() else acc[acc.size - 1] = acc.last() + " " + l.trim()
		acc
	}

	fun figures(slots: List<SlotText>): Figures {
		val kv = slots.filter { !HubText.isEmpty(it) }.map { HubText.kv(it.lore) }.fold(mapOf<String, String>()) { a, b -> b + a }
		val cookie = slots.firstOrNull { it.cleanName == "Booster Cookie" }
		val buffs = joinBuffs(cookie?.let { HubText.block(it.lore, "Cookie Buff:") }.orEmpty())

		val next = slots.firstNotNullOfOrNull { s -> HubText.clean(s.lore).let { c -> c.indexOfFirst { it.startsWith("Next rank:") }.takeIf { it >= 0 }?.let { i ->
			c[i].substringAfter(":").trim() + (c.getOrNull(i + 1)?.takeIf { Regex("^[\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?$").matches(it) }?.let { " ($it)" } ?: "") } } }
		return Figures(kv["bits purse"], kv["bits available"], kv["bits multiplier"], kv["your rank"], kv["your total"], kv["you have"], buffs,
			kv["duration"], kv["per cookie"], next)
	}

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val filled = slots.filter { !HubText.isEmpty(it) && !HubText.isNav(it) && it.cleanName != "Sell Item" }
		if (filled.none { it.cleanName == "Booster Cookie" }) return null
		val f = figures(slots)

		val locked = filled.filter { HubText.requirement(it.lore) != null }
		val chips = listOfNotNull(f.purse?.let { "Bits Purse" to it }, f.available?.let { "Bits Available" to it },
			f.multiplier?.let { "Multiplier" to it }, f.duration?.let { "Cookie left" to it }, f.perCookie?.let { "Per cookie" to it },
			f.rank?.let { "Fame" to (it + (f.fame?.let { " · $it" } ?: "")) }, f.nextRank?.let { "Next rank" to it }, f.gems?.let { "Gems" to it },
			locked.size.takeIf { it > 0 }?.let { "Locked" to it.toString() })
		fun tile(s: SlotText): HubTile {
			val info = when (s.cleanName) {
				"Booster Cookie" -> f.buffs.ifEmpty { HubText.infoLines(s.lore) }
				else -> HubText.clean(s.lore).filter { it.contains(": ") && !it.startsWith("Also accessible") }
			}
			return HubTile(s.slot, s.cleanName, info, tooltip = HubText.infoLines(s.lore))
		}

		val (quick, other) = (filled - locked.toSet()).partition { s -> HubText.clean(s.lore).any { it.startsWith("Also accessible via") || it == "Click to open!" } }
		val sections = listOf(HubSection("Cookie", other.map(::tile)), HubSection("Quick access", quick.map(::tile))).filter { it.tiles.isNotEmpty() }
		return HubModel("Booster Cookie", chips, sections, HubText.navOf(slots))
	}
}

@Config
object BoosterCookieSettings : MenuSettings("booster-cookie") {
	val reset by button("reset") { resetToDefaults() }
}

object BoosterCookieMenu : CustomMenu {
	override val id = "booster-cookie"
	override val displayName = "Booster Cookie"
	override val title = BoosterCookieParser.TITLE
	override val settings get() = BoosterCookieSettings

	override val defaultSize = 400 to 230

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = BoosterCookieView(screen)
}

class BoosterCookieView(screen: AbstractContainerScreen<*>) : HubView(screen, { BoosterCookieSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = BoosterCookieParser.parse(title, slots)

	override val clipAtNav = true
}
