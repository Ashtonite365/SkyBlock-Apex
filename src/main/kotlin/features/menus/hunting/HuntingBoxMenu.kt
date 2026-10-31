// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.hunting

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.foraging.GalateaHelpers
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.data.Config

data class ShardEntry(val name: String, val slot: Int, val family: String?, val attribute: AttributeInfo?, val owned: Int?, val toNext: Int?, val maxed: Boolean)
data class HuntingBoxModel(val total: String?, val shards: List<ShardEntry>, val swapSlot: Int?, val sort: CycleOption?, val massSyphon: Pair<Int, String>?,
                           val nav: Map<String, Int>, val extras: List<Pair<String, Int>>) {
	val byCategory get() = shards.groupBy { it.attribute?.category ?: "Other" }
}

object HuntingBoxParser {
	val TITLE = Regex("^(?i)Hunting Box$")

	fun parse(title: String, slots: List<SlotText>): HuntingBoxModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null

		val header = slots.firstOrNull { it.cleanName.equals("Hunting Box", true) && HuntingLore.counter(it.cleanLore, "Shards") != null } ?: return null
		val shards = slots.filter { s -> s.slot != header.slot && HuntingLore.owned(s.cleanLore) != null }.map { s ->
			val l = s.cleanLore
			ShardEntry(s.cleanName, s.slot, HuntingLore.family(l), HuntingLore.attribute(l), HuntingLore.owned(l), HuntingLore.toNextLevel(l), HuntingLore.maxed(l))
		}
		val sort = slots.firstOrNull { it.cleanName.equals("Sort", true) }?.let(HuntingLore::cycle)
		val mass = slots.firstOrNull { it.cleanName.equals("Mass Syphon", true) }?.let { s ->
			s.slot to (s.cleanLore.firstOrNull { it.startsWith("You have", true) }.orEmpty() + if (s.cleanLore.any { it.startsWith("Nothing to Syphon", true) }) " (nothing to syphon)" else "")
		}
		val used = shards.map { it.slot }.toSet() + header.slot + listOfNotNull(sort?.slot, mass?.first)
		return HuntingBoxModel(HuntingLore.counter(header.cleanLore, "Shards"), shards, header.slot, sort, mass, SkillLore.nav(slots), SkillLore.extras(slots, used))
	}
}

@Config
object HuntingBoxSettings : MenuSettings("hunting-box", panels = listOf("effects")) {
	val reset by button("reset") { resetToDefaults() }
}

object HuntingBoxMenu : CustomMenu {
	override val id = "hunting-box"
	override val displayName = "Hunting Box"
	override val title = HuntingBoxParser.TITLE
	override val settings get() = HuntingBoxSettings
	override val defaultSize = 460 to 280

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = HuntingBoxView(screen)
}

class HuntingBoxView(screen: AbstractContainerScreen<*>) : SkillViewBase<HuntingBoxModel>(screen) {
	override fun palette() = HuntingBoxSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = HuntingBoxParser.parse(title, slots)

	private fun value(s: ShardEntry): Double? {
		if (!GalateaHelpers.TConfig.huntingBoxValue) return null
		val price = runCatching { HypixelStaticData.getPriceOfItem(GalateaHelpers.shardId(s.name)) }.getOrNull() ?: return null
		return price * (s.owned ?: 0)
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: HuntingBoxModel, mx: Int, my: Int) {
		val total = m.shards.mapNotNull(::value).takeIf { it.isNotEmpty() }?.sum()
		header(ctx, mx, my, "Hunting Box" + (m.total?.let { "  §7shards §f$it" } ?: "") + (total?.let { "  §6" + MenuText.coins(it) } ?: ""))
		var gy = y + 22
		if (m.shards.isEmpty()) {
			val t = "No shards stored"; ctx.text(font, "§7$t", x + (w - font.width(t)) / 2, gy + 20, MenuTheme.GREY); gy += 40
		}
		val cols = 3; val gap = 4; val th = 32
		val tw = (w - 16 - gap * (cols - 1)) / cols
		for ((cat, list) in m.byCategory) {
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, cat)
			list.forEachIndexed { i, s ->
				val sub = buildList {
					s.owned?.let { add("x$it") }
					if (s.maxed) add("MAX") else s.toNext?.let { add("+$it to lvl") }
					value(s)?.let { add("§6" + MenuText.coins(it)) }
					if (HuntingBoxSettings.shows("effects")) s.attribute?.let { add("§7" + it.name) }
				}.joinToString(" ")
				tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, s.slot, "§f${s.name}", sub, null)
			}
			gy += gh + 4
		}

		var bx = x + 4; val by = y + h - 36
		fun btn(l: String, slot: Int) { val bw = font.width(l) + 10; if (bx + bw <= x + w - 4) { button(ctx, mx, my, bx, by, bw, 14, l) { clickSlot(slot, it) }; bx += bw + 4 } }
		m.swapSlot?.let { btn("Attributes", it) }
		m.sort?.let { btn("Sort: ${it.selected ?: "?"}", it.slot) }
		m.massSyphon?.let { btn("Mass Syphon", it.first) }
		for ((l, s) in m.extras) btn(l, s)
		navRow(ctx, mx, my, m.nav)
	}
}
