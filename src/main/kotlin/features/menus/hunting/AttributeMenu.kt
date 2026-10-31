// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.hunting

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.Progress
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class AttributeEntry(val name: String, val slot: Int, val level: Int?, val category: String, val effect: String?, val owned: Int?, val progress: Progress?, val maxed: Boolean)
data class AttributeMenuModel(val attributes: List<AttributeEntry>, val unique: String?, val filter: CycleOption?, val sort: CycleOption?, val swapSlot: Int?,
                              val nav: Map<String, Int>, val extras: List<Pair<String, Int>>) {
	val byCategory get() = attributes.groupBy { it.category }
}

object AttributeMenuParser {
	val TITLE = Regex("^(?i)(?:\\(\\d+/\\d+\\) )?Attribute Menu$")
	private val LEVEL_LINE = Regex("^Level:? ([IVX]+|\\d+)(?:/\\d+)?", RegexOption.IGNORE_CASE)

	private val CONTROLS = setOf("search", "filter", "sort", "attribute transfer", "advanced mode", "attribute menu")

	fun parse(title: String, slots: List<SlotText>): AttributeMenuModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val header = slots.firstOrNull { it.cleanName.equals("Attribute Menu", true) }
		val filter = slots.firstOrNull { it.cleanName.equals("Filter", true) }?.let(HuntingLore::cycle)
		val sort = slots.firstOrNull { it.cleanName.equals("Sort", true) }?.let(HuntingLore::cycle)

		if (header == null && (filter == null || sort == null)) return null
		val nav = SkillLore.nav(slots)
		val attrs = slots.filter { s ->
			!SkillLore.isFiller(s) && s.cleanName.lowercase() !in CONTROLS && s.slot !in nav.values && s.lore.isNotEmpty() &&
				(HuntingLore.attribute(s.cleanLore) != null || HuntingLore.owned(s.cleanLore) != null || s.cleanLore.any { LEVEL_LINE.containsMatchIn(it) })
		}.map { s ->
			val l = s.cleanLore
			val (base, nameLvl) = HuntingLore.splitLevel(s.cleanName)
			val info = HuntingLore.attribute(l)
			AttributeEntry(info?.name ?: base, s.slot, nameLvl ?: l.firstNotNullOfOrNull { LEVEL_LINE.find(it)?.groupValues?.get(1)?.let(SkillLore::level) },
				info?.category ?: "Other", info?.effect, HuntingLore.owned(l), SkillLore.progress(l), HuntingLore.maxed(l))
		}
		val unique = slots.firstNotNullOfOrNull { HuntingLore.counter(it.cleanLore, "Unique Attributes") }
		val used = attrs.map { it.slot }.toSet() + listOfNotNull(header?.slot, filter?.slot, sort?.slot)
		return AttributeMenuModel(attrs, unique, filter, sort, header?.slot, nav, SkillLore.extras(slots, used))
	}
}

@Config
object AttributeMenuSettings : MenuSettings("attribute-menu", panels = listOf("effects")) {
	val reset by button("reset") { resetToDefaults() }
}

object AttributeMenuMenu : CustomMenu {
	override val id = "attribute-menu"
	override val displayName = "Attribute Menu"
	override val title = AttributeMenuParser.TITLE
	override val settings get() = AttributeMenuSettings
	override val defaultSize = 460 to 280

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = AttributeMenuView(screen)
}

class AttributeMenuView(screen: AbstractContainerScreen<*>) : SkillViewBase<AttributeMenuModel>(screen) {
	override fun palette() = AttributeMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = AttributeMenuParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: AttributeMenuModel, mx: Int, my: Int) {
		header(ctx, mx, my, "Attributes" + (m.unique?.let { "  §7unique §f$it" } ?: ""))
		var gy = y + 22
		if (m.attributes.isEmpty()) {
			val t = "No attributes unlocked yet — syphon shards in the Hunting Box"
			ctx.text(font, "§7" + MenuTheme.fit(t, w - 12, font::width), x + 6, gy + 20, MenuTheme.GREY); gy += 40
		}
		val cols = 3; val gap = 4; val th = barTileH(32)
		val tw = (w - 16 - gap * (cols - 1)) / cols
		for ((cat, list) in m.byCategory) {
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, cat)
			list.forEachIndexed { i, a ->
				val sub = if (AttributeMenuSettings.shows("effects")) a.effect else a.owned?.let { "x$it shards" }
				val lvl = when { a.maxed -> " §6MAX"; a.level != null -> " §a${a.level}"; else -> "" }
				tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, a.slot, "§f${a.name}$lvl", sub, a.progress)
			}
			gy += gh + 4
		}
		var bx = x + 4; val by = y + h - 36
		fun btn(l: String, slot: Int) { val bw = font.width(l) + 10; if (bx + bw <= x + w - 4) { button(ctx, mx, my, bx, by, bw, 14, l) { clickSlot(slot, it) }; bx += bw + 4 } }
		m.swapSlot?.let { btn("Hunting Box", it) }
		m.filter?.let { btn("Filter: ${it.selected ?: "?"}", it.slot) }
		m.sort?.let { btn("Sort: ${it.selected ?: "?"}", it.slot) }
		for ((l, s) in m.extras) btn(l, s)
		navRow(ctx, mx, my, m.nav)
	}
}
