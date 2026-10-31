// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.guides

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object GuideLore {
	private val CLICK = Regex("^(?:(?:Left|Right|Shift)[- ]?)?Click(?:s)?\\b.*|^Right-click.*", RegexOption.IGNORE_CASE)
	fun clickable(s: SlotText) = s.cleanLore.any { CLICK.matches(it) }
	fun header(name: String) = MenuText.clean(name).removePrefix("⚑").trim()

	fun paragraphs(lore: List<String>): List<String> {
		val out = mutableListOf<String>(); val cur = mutableListOf<String>()
		fun flush() { if (cur.isNotEmpty()) { out += cur.joinToString(" ").replace(Regex("\\s{2,}"), " ").trim(); cur.clear() } }
		for (l in lore.map(MenuText::clean)) if (l.isBlank()) flush() else if (!CLICK.matches(l)) cur += l.trim()
		flush()
		return out
	}

	fun info(lore: List<String>) = paragraphs(lore).joinToString(" · ")

	fun headerSlot(chest: List<SlotText>, title: String) = chest.firstOrNull { it.cleanName.startsWith("⚑") && header(it.cleanName).equals(title, true) }

	fun nav(chest: List<SlotText>) = HubText.navOf(chest) + chest.filter { HubText.selected(it.lore) != null && !HubText.isNav(it) }.map { HubText.actionLabel(it) to it.slot }
}

object SkyBlockGuidesParser {
	val TITLE = Regex("^SkyBlock Guides$")

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val head = GuideLore.headerSlot(chest, t)
		val nav = GuideLore.nav(chest); val navSlots = nav.map { it.second }.toSet()

		val guides = chest.filter { it.slot != head?.slot && it.slot !in navSlots && it.cleanName.startsWith("⚑") }
		if (guides.isEmpty()) return null
		val tiles = guides.map { HubTile(it.slot, GuideLore.header(it.cleanName), listOf(GuideLore.info(it.lore)), tooltip = HubText.clean(it.lore)) }
		val others = chest.filter { it.slot != head?.slot && it.slot !in navSlots && guides.none { g -> g.slot == it.slot } }
			.map { HubTile(it.slot, it.cleanName, listOf(GuideLore.info(it.lore)), tooltip = HubText.clean(it.lore), clickable = GuideLore.clickable(it)) }
		return HubModel(head?.let { GuideLore.header(it.cleanName) } ?: t,
			listOfNotNull(head?.let { "About" to GuideLore.info(it.lore) }),
			listOf(HubSection("Guides", tiles), HubSection("Other", others)).filter { it.tiles.isNotEmpty() }, nav)
	}
}

object DragonGuideParser {
	val TITLE = Regex("^Dragon Guide$")
	private val STAT = Regex("^([A-Za-z][A-Za-z ]+):\\s*(.+)$")

	fun stats(lore: List<String>) = lore.map(MenuText::clean).mapNotNull { STAT.matchEntire(it)?.let { m -> m.groupValues[1].trim() to m.groupValues[2].trim() } }
		.filter { it.second.isNotEmpty() }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val head = GuideLore.headerSlot(chest, t)
		val nav = GuideLore.nav(chest); val navSlots = nav.map { it.second }.toSet()
		val rest = chest.filter { it.slot != head?.slot && it.slot !in navSlots }

		val (dragons, info) = rest.partition { s -> stats(s.lore).any { it.first.equals("Kills", true) } }
		if (dragons.isEmpty() && info.isEmpty()) return null
		fun tile(s: SlotText): HubTile {
			val st = stats(s.lore)

			val paras = GuideLore.paragraphs(s.lore)
			val text = if (s in dragons) (paras.filter { p -> st.none { p.startsWith(it.first + ":") } && p.indexOf(':').let { it < 0 } } .take(1) +
				st.map { "${it.first}: ${it.second}" } + paras.drop(1).filter { p -> !p.contains(':') }).joinToString(" · ")
				else GuideLore.info(s.lore)
			return HubTile(s.slot, s.cleanName, listOf(text), tooltip = HubText.clean(s.lore), clickable = GuideLore.clickable(s))
		}

		val chips = info.flatMap { stats(it.lore) }
		return HubModel(head?.let { GuideLore.header(it.cleanName) } ?: t, chips,
			listOf(HubSection("Summoning", info.map(::tile)), HubSection("Dragons", dragons.map(::tile))).filter { it.tiles.isNotEmpty() }, nav)
	}
}

object ReforgeGuideParser {
	val TITLE = Regex("^(?:Reforge Guide|(?:\\(\\d+/\\d+\\)\\s*)?(?:Basic|Advanced) Reforges)$")
	private val STATS_TILE = Regex("^([A-Z][A-Z ]*?) Reforge Stats$")

	fun isDetail(slots: List<SlotText>) = slots.count { STATS_TILE.matches(it.cleanName) } >= 1 &&
		slots.any { it.cleanName.equals("Go Back", true) && it.cleanLore.firstOrNull()?.let { l -> l.equals("To Basic Reforges", true) || l.equals("To Advanced Reforges", true) } == true }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val t = MenuText.clean(title).trim()
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		if (!TITLE.matches(t)) return if (isDetail(chest)) detail(t, chest) else null
		val nav = GuideLore.nav(chest); val navSlots = nav.map { it.second }.toSet()
		val base = t.replace(Regex("^\\(\\d+/\\d+\\)\\s*"), "")
		val head = chest.firstOrNull { GuideLore.header(it.cleanName).equals(base, true) && (it.slot == 4 || it.cleanName.startsWith("⚑")) }
		val tiles = chest.filter { it.slot != head?.slot && it.slot !in navSlots }
			.map { HubTile(it.slot, it.cleanName, listOf(GuideLore.info(it.lore)), tooltip = HubText.clean(it.lore), clickable = GuideLore.clickable(it)) }
		if (tiles.isEmpty()) return null
		val chips = listOfNotNull(head?.let { "About" to GuideLore.info(it.lore) }, HubText.page(t)?.let { "Page" to "${it.first}/${it.second}" })
		return HubModel(base, chips, listOf(HubSection(if (base == "Reforge Guide") "Reforges" else base, tiles)), nav)
	}

	private fun detail(t: String, chest: List<SlotText>): HubModel {
		val nav = GuideLore.nav(chest); val navSlots = nav.map { it.second }.toSet()
		val head = chest.firstOrNull { it.cleanName.equals(t, true) && it.slot !in navSlots }
		val rest = chest.filter { it.slot != head?.slot && it.slot !in navSlots }
		val (scaling, other) = rest.partition { STATS_TILE.matches(it.cleanName) }
		fun tile(s: SlotText): HubTile {
			val paras = GuideLore.paragraphs(s.lore)

			val text = (if (STATS_TILE.matches(s.cleanName) && paras.size > 1) paras.drop(1) else paras).joinToString(" · ")
			return HubTile(s.slot, s.cleanName, listOf(text), tooltip = HubText.clean(s.lore), clickable = GuideLore.clickable(s))
		}
		return HubModel(t, listOfNotNull(head?.let { "About" to GuideLore.info(it.lore) }),
			listOf(HubSection("Rarity Scaling", scaling.map(::tile)), HubSection("Bonus", other.map(::tile))).filter { it.tiles.isNotEmpty() }, nav)
	}
}

@Config object SkyBlockGuidesMenuSettings : MenuSettings("skyblock-guides") { val reset by button("reset") { resetToDefaults() } }
@Config object DragonGuideMenuSettings : MenuSettings("dragon-guide") { val reset by button("reset") { resetToDefaults() } }
@Config object ReforgeGuideMenuSettings : MenuSettings("reforge-guide") { val reset by button("reset") { resetToDefaults() } }

object SkyBlockGuidesMenu : CustomMenu {
	override val id = "skyblock-guides"
	override val displayName = "SkyBlock Guides"
	override val title = SkyBlockGuidesParser.TITLE
	override val settings get() = SkyBlockGuidesMenuSettings
	override val defaultSize = 440 to 260
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) GuideHubView(screen, { SkyBlockGuidesMenuSettings }, SkyBlockGuidesParser::parse) else null
}

object DragonGuideMenu : CustomMenu {
	override val id = "dragon-guide"
	override val displayName = "Dragon Guide"
	override val title = DragonGuideParser.TITLE
	override val settings get() = DragonGuideMenuSettings
	override val defaultSize = 460 to 280
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) GuideHubView(screen, { DragonGuideMenuSettings }, DragonGuideParser::parse, live = true) else null
}

object ReforgeGuideMenu : CustomMenu {
	override val id = "reforge-guide"
	override val displayName = "Reforge Guide"
	override val title = ReforgeGuideParser.TITLE

	override val slotPredicate: (String, List<SlotText>) -> Boolean = { _, slots -> ReforgeGuideParser.isDetail(slots) }
	override val settings get() = ReforgeGuideMenuSettings
	override val defaultSize = 460 to 280
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) GuideHubView(screen, { ReforgeGuideMenuSettings }, ReforgeGuideParser::parse) else null
}

class GuideHubView(
	screen: AbstractContainerScreen<*>, settings: () -> MenuSettings,
	private val parser: (String, List<SlotText>, Int) -> HubModel?, private val live: Boolean = false,
) : HubView(screen, settings) {
	override fun parse(title: String, slots: List<SlotText>) = parser(title, slots, screen.menu.slots.size - 36)
	override fun wrapsTileText(m: HubModel) = true
	override fun liveOnly(m: HubModel) = live
}
