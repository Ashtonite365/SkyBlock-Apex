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

object BagsParser {

	const val SACK_COLS = 4
	const val SACK_TEXT_SCALE = 0.88f

	fun acceptsItems(heading: String) = !heading.equals("Your Bags", true)

	fun contentIsRealItem(heading: String) = acceptsItems(heading) && !heading.equals("Sack of Sacks", true)
	val TITLE = Regex("^(?:Your Bags|Accessory Bag(?: \\(\\d+/\\d+\\))?|Quiver|Potion Bag(?: \\(\\d+/\\d+\\))?|Sack of Sacks|.+ Sack(?: \\(\\d+/\\d+\\))?)$")

	fun summary(lore: List<String>): List<String> {
		val c = HubText.clean(lore)
		val req = HubText.requirement(lore)
		val kv = c.filter { Regex("^[A-Z][A-Za-z ]+: .+").matches(it) && !it.startsWith("Also accessible") }
		return listOfNotNull(req) + kv
	}

	fun bottomRow(filled: List<SlotText>) = ((filled.filter(HubText::isNav).maxOfOrNull { it.slot } ?: (filled.maxOf { it.slot } + 9)) / 9) * 9

	fun isLockedSlot(s: SlotText) = HubText.requirement(s.lore) != null || s.cleanName.startsWith("Locked", true) ||
		HubText.clean(s.lore).any { it.equals("Locked", true) || it.startsWith("Not unlocked", true) }

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val filled = slots.filter { !HubText.isEmpty(it) }
		if (filled.isEmpty()) return null
		val nav = HubText.navOf(slots).toMutableList()
		if (t == "Your Bags") {

			val entries = filled.filter { !HubText.isNav(it) }
			val lockedBags = entries.filter(::isLockedSlot)
			val bags = entries.map { s ->
				if (s in lockedBags) HubTile(s.slot, "§7✖ " + s.cleanName, listOfNotNull(HubText.requirement(s.lore)?.let { "§c$it" } ?: "§cLocked"),
					locked = true, tooltip = HubText.clean(s.lore).filter { it.isNotEmpty() } + "§cLocked")
				else HubTile(s.slot, s.cleanName, summary(s.lore), tooltip = HubText.infoLines(s.lore))
			}
			if (bags.isEmpty() && lockedBags.isEmpty()) return null
			val acc = filled.firstOrNull { it.cleanName == "Accessory Bag" }?.let { HubText.kv(it.lore) }.orEmpty()
			val chips = listOfNotNull(acc["accessory power"]?.let { "Accessory Power" to it }, acc["selected power"]?.let { "Selected Power" to it },
				lockedBags.size.takeIf { it > 0 }?.let { "Locked" to it.toString() })
			return HubModel("Your Bags", chips, listOf(HubSection("Bags", bags)), nav)
		}
		if (SackPage.isSackPage(filled)) return SackPage.parse(t, filled, nav, bottomRow(filled))
		if (t != "Sack of Sacks" && t.endsWith(" Sack")) return null

		val bottom = bottomRow(filled)
		val content = filled.filter { it.slot < bottom && !HubText.isNav(it) }
		val actions = filled.filter { it.slot >= bottom && !HubText.isNav(it) }
		nav += actions.map { it.cleanName to it.slot }
		val sack = t == "Sack of Sacks"
		if (sack) SackDirectory.seen(content.map { it.cleanName })

		val (lockedSlots, owned) = content.partition { isLockedSlot(it) }
		val tiles = owned.map { s ->
			val info = HubText.clean(s.lore).filter { it.contains(": ") }.take(2).ifEmpty { listOfNotNull(HubText.rarity(s.lore)) }
			val rightOpens = HubText.clean(s.lore).any { it.startsWith("Right Click to open", true) }
			HubTile(s.slot, HubText.colour(HubText.rarity(s.lore)) + s.cleanName, info, button = if (sack && rightOpens) 1 else 0,
				tooltip = HubText.infoLines(s.lore), realItem = BagsParser.contentIsRealItem(t), leftPicksUp = sack && rightOpens)
		}
		val chips = mutableListOf<Pair<String, String>>()
		HubText.page(t)?.let { chips += "Page" to "${it.first}/${it.second}" }
		chips += "Items" to tiles.size.toString()
		if (lockedSlots.isNotEmpty()) chips += "Locked" to lockedSlots.size.toString()
		val power = owned.sumOf { s -> HubText.kv(s.lore)["accessory power"]?.let { MenuText.number(it.removePrefix("+")) }?.toLong() ?: 0L }
		if (power > 0) chips += "Accessory Power (page)" to "+$power"

		if (t == "Quiver") {
			val types = owned.groupingBy { it.cleanName }.eachCount()
			if (types.isNotEmpty()) chips += "Types" to types.size.toString()
			types.entries.sortedByDescending { it.value }.take(3).forEach { chips += it.key to "${it.value} slots" }
		}

		if (t.startsWith("Accessory Bag")) owned.mapNotNull { HubText.rarity(it.lore) }.groupingBy { it }.eachCount().entries
			.sortedByDescending { it.value }.take(2).forEach { chips += it.key.lowercase().replaceFirstChar(Char::uppercase) to it.value.toString() }
		val emptyText = if (HubText.page(t) != null) "Empty page" else "Empty"
		val lockedPage = if (tiles.isEmpty() && lockedSlots.isNotEmpty()) "Locked" else null
		val sections = if (tiles.isEmpty()) emptyList() else listOf(HubSection(if (sack) "Sacks" else "Contents", tiles))
		return HubModel(t, chips, sections, nav, emptyText = emptyText, locked = lockedPage)
	}
}

object SackDirectory {
	var owned: List<String> = emptyList(); private set
	private var clicked: Pair<String, Long>? = null

	fun seen(names: List<String>) { if (names.isNotEmpty()) owned = names }
	fun clicked(name: String, now: Long = System.currentTimeMillis()) { clicked = name to now }
	fun resetForTests() { owned = emptyList(); clicked = null }

	private fun words(s: String) = MenuText.clean(s).lowercase().split(' ').filter { it.isNotEmpty() }.map { it.removeSuffix("s") }

	fun resolve(title: String, now: Long = System.currentTimeMillis()): Pair<String, String?>? {
		val w = words(title.replace(Regex("^\\(\\d+/\\d+\\) "), ""))
		fun tierOf(name: String): String? {
			val n = words(name); if (n.size < w.size || n.size > w.size + 1 || n.takeLast(w.size) != w) return null
			return MenuText.clean(name).split(' ').take(n.size - w.size).joinToString(" ")
		}

		val recent = clicked?.takeIf { now - it.second < 10_000 }?.first
		val hits = (owned + listOfNotNull(recent)).distinct().mapNotNull { n -> tierOf(n)?.let { n to it } }
		return hits.minByOrNull { it.second.length }?.let { it.first to it.second.ifEmpty { null } }
	}
}

object SackPage {
	private val STORED = Regex("^Stored: ([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)$")

	data class Entry(val slot: Int, val name: String, val stored: Long, val capacity: Long, val detail: List<String>, val stackable: Boolean)

	fun stored(lore: List<String>): Pair<Long, Long>? = HubText.clean(lore).firstNotNullOfOrNull { STORED.matchEntire(it) }?.let {
		val a = MenuText.number(it.groupValues[1])?.toLong() ?: return null
		val b = MenuText.number(it.groupValues[2])?.toLong() ?: return null
		a to b
	}

	fun isSackPage(filled: List<SlotText>) = filled.any { stored(it.lore) != null }

	fun entries(filled: List<SlotText>) = filled.mapNotNull { s ->
		val (a, b) = stored(s.lore) ?: return@mapNotNull null
		val c = HubText.clean(s.lore)

		val detail = c.takeWhile { STORED.matchEntire(it) == null }.filter { it.contains(": ") }
		Entry(s.slot, s.cleanName, a, b, detail, c.any { it.startsWith("Right-Click", true) && it.contains("stack", true) })
	}

	private fun pct(a: Long, b: Long) = if (b <= 0) "—" else "%.0f%%".format(java.util.Locale.ROOT, a * 100.0 / b)
	private fun n(v: Long) = "%,d".format(java.util.Locale.ROOT, v)
	private fun cap(v: Long) = MenuText.coins(v.toDouble())

	fun parse(t: String, filled: List<SlotText>, nav0: List<Pair<String, Int>>, bottom: Int): HubModel {
		val es = entries(filled)
		val actions = filled.filter { it.slot >= bottom && !HubText.isNav(it) && stored(it.lore) == null }
		val nav = nav0 + actions.map { a ->
			val sel = HubText.clean(a.lore).firstOrNull { it.startsWith("▶") }?.removePrefix("▶")?.trim()
			(if (sel != null) "${a.cleanName}: $sel" else a.cleanName) to a.slot
		}
		fun tile(e: Entry) = HubTile(e.slot, (if (e.stored == 0L) "§7" else "§f") + e.name,
			listOf("${n(e.stored)}/${cap(e.capacity)} · ${pct(e.stored, e.capacity)}") + e.detail.take(1),
			tooltip = listOf("Stored: ${n(e.stored)} / ${cap(e.capacity)} (${pct(e.stored, e.capacity)})") + e.detail +
				listOf(if (e.stored > 0) "§eClick: pick up · right click: stack" else "§7Empty"))
		val (have, empty) = es.partition { it.stored > 0 }
		val known = SackDirectory.resolve(t)
		val total = actions.firstNotNullOfOrNull { HubText.kv(it.lore)["total items"] } ?: n(es.sumOf { it.stored })
		val caps = es.map { it.capacity }.distinct()
		val chips = listOfNotNull(
			"Sack" to (known?.first ?: t), known?.second?.let { "Tier" to it },
			"Stored" to total, "Items" to "${have.size}/${es.size}",
			caps.singleOrNull()?.let { "Capacity" to "${cap(it)} each" },
			es.maxByOrNull { if (it.capacity > 0) it.stored.toDouble() / it.capacity else 0.0 }?.takeIf { it.stored > 0 }?.let { "Fullest" to "${it.name} ${pct(it.stored, it.capacity)}" },
			HubText.page(t)?.let { "Page" to "${it.first}/${it.second}" })
		return HubModel(t, chips, listOf(HubSection("Stored", have.map(::tile)), HubSection("Empty", empty.map(::tile))).filter { it.tiles.isNotEmpty() }, nav)
	}
}

@Config
object BagsSettings : MenuSettings("bags") {
	val reset by button("reset") { resetToDefaults() }
}

object BagsMenu : CustomMenu {
	override val id = "bags"
	override val displayName = "Your Bags"
	override val title = BagsParser.TITLE
	override val settings get() = BagsSettings
	override val defaultSize = 360 to 330

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = BagsView(screen)
}

class BagsView(screen: AbstractContainerScreen<*>) : HubView(screen, { BagsSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = BagsParser.parse(title, slots)
	override fun showsInventory(m: HubModel) = BagsParser.acceptsItems(m.heading)

	override fun wrapsTileText(m: HubModel) = BagsParser.acceptsItems(m.heading)

	override fun tileColumns(m: HubModel) = if (m.heading == "Sack of Sacks") BagsParser.SACK_COLS else null
	override fun textScale(m: HubModel) = if (m.heading == "Sack of Sacks") BagsParser.SACK_TEXT_SCALE else 1f
	override fun onPress(m: HubModel, t: HubTile) { if (m.heading == "Sack of Sacks") SackDirectory.clicked(MenuText.clean(t.name)) }
}
