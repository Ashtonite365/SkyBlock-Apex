// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class RewardBundle(val name: String, val slot: Int, val declaredCount: Int?, val rewards: List<String>, val requirement: String?)
data class SpookyRewardsModel(val bundles: List<RewardBundle>, val nav: Map<String, Int>, val extras: List<Pair<String, Int>>)

object SpookyRewardsParser {
	val TITLE = Regex("^(?i)Spooky Rewards$")
	private val BUNDLE = Regex("Rewards? Bundle$", RegexOption.IGNORE_CASE)
	private val COUNT = Regex("^Rewards? \\((\\d+)\\):?$", RegexOption.IGNORE_CASE)
	private val REQ = Regex("^Requires (.+?)\\s*!?$", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): SpookyRewardsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val bundles = slots.filter { BUNDLE.containsMatchIn(it.cleanName) }.map { s ->
			val lore = s.cleanLore
			val i = lore.indexOfFirst { COUNT.matches(it) }
			val items = if (i < 0) emptyList() else lore.drop(i + 1).takeWhile { it.startsWith("-") }.map { it.removePrefix("-").trim() }
			RewardBundle(s.cleanName, s.slot, if (i < 0) null else COUNT.matchEntire(lore[i])!!.groupValues[1].toInt(), items,
				lore.firstNotNullOfOrNull { REQ.matchEntire(it)?.groupValues?.get(1) })
		}
		if (bundles.isEmpty()) return null
		return SpookyRewardsModel(bundles, SkillLore.nav(slots), SkillLore.extras(slots, bundles.map { it.slot }.toSet()))
	}
}

@Config
object SpookyRewardsSettings : MenuSettings("spooky-rewards") {
	val reset by button("reset") { resetToDefaults() }
}

object SpookyRewardsMenu : CustomMenu {
	override val id = "spooky-rewards"
	override val displayName = "Spooky Rewards"
	override val title = SpookyRewardsParser.TITLE
	override val settings get() = SpookyRewardsSettings
	override val defaultSize = 520 to 290

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = SpookyRewardsView(screen)
}

class SpookyRewardsView(screen: AbstractContainerScreen<*>) : SkillViewBase<SpookyRewardsModel>(screen) {
	override fun palette() = SpookyRewardsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SpookyRewardsParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: SpookyRewardsModel, mx: Int, my: Int) {
		header(ctx, mx, my, "§6Spooky Rewards")
		val n = m.bundles.size; val gap = 4
		val cw = (w - 8 - gap * (n - 1)) / n
		val gh = h - 22 - 42
		m.bundles.forEachIndexed { i, b ->
			val gx = x + 4 + i * (cw + gap)
			val top = group(ctx, gx, y + 22, cw, gh, b.name.replace(Regex("(?i) Rewards? Bundle$"), ""))
			tile(ctx, mx, my, gx + 3, top, cw - 6, 22, b.slot, "§f${b.declaredCount ?: b.rewards.size} rewards", b.requirement, null)
			val maxLines = (gh - 40) / 9
			b.rewards.take(maxLines).forEachIndexed { j, r -> ctx.text(font, "§7" + MenuTheme.fit(r, cw - 10, font::width), gx + 5, top + 26 + j * 9, MenuTheme.GREY) }
			if (b.rewards.size > maxLines) ctx.text(font, "§8+${b.rewards.size - maxLines} more", gx + 5, top + 26 + maxLines * 9, MenuTheme.GREY)
		}
		var bx = x + 4
		for ((l, slot) in m.extras) { val bw = font.width(l) + 10; button(ctx, mx, my, bx, y + h - 36, bw, 14, l) { clickSlot(slot, it) }; bx += bw + 4 }
		navRow(ctx, mx, my, m.nav)
	}
}
