// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class Perk(val name: String, val description: List<String>)
data class Vote(val candidate: String, val percent: Double, val votes: String)
data class MayorModel(val role: String, val person: String, val personSlot: Int, val perks: List<Perk>, val footer: List<String>,
                      val year: String?, val votes: List<Vote>, val resultsSlot: Int?, val nav: Map<String, Int>)

object MayorParser {

	val TITLE = Regex("^(Mayor|Minister)\\s+(\\S.*)$", RegexOption.IGNORE_CASE)
	private val HEADER = listOf("Perks List", "Active Perk", "Perks")
	private val VOTE = Regex("^([\\d.]+)%\\s*\\S?\\s*([\\d,]+) votes\\s*\\|\\s*(.+)$", RegexOption.IGNORE_CASE)
	val NAV = listOf("Go Back", "Close")
	private fun isRule(l: String) = l.isNotEmpty() && l.all { it == '-' || it == '▬' || it == '━' }

	fun perks(lore: List<String>): Pair<List<Perk>, List<String>> {
		val start = lore.indexOfFirst { l -> HEADER.any { l.equals(it, true) } }
		if (start < 0) return emptyList<Perk>() to emptyList()
		val rest = lore.drop(start + 1).dropWhile { it.isBlank() || isRule(it) }
		val perks = mutableListOf<Perk>(); var cur = mutableListOf<String>(); var end = rest.size
		for ((i, l) in rest.withIndex()) {
			if (isRule(l) || l.isBlank() && rest.getOrNull(i + 1)?.isBlank() == true) { end = i; break }
			if (l.isBlank()) { if (cur.isNotEmpty()) perks += Perk(cur[0], cur.drop(1)); cur = mutableListOf() } else cur += l
		}
		if (cur.isNotEmpty()) perks += Perk(cur[0], cur.drop(1))
		return perks to rest.drop(end).filter { it.isNotBlank() && !isRule(it) }
	}

	fun votes(lore: List<String>) = lore.mapNotNull { VOTE.matchEntire(it.trim()) }.map {
		Vote(it.groupValues[3].trim(), it.groupValues[1].toDouble(), it.groupValues[2])
	}

	fun parse(title: String, slots: List<SlotText>): MayorModel? {
		val t = MenuText.clean(title)
		val m = TITLE.matchEntire(t) ?: return null
		val person = slots.firstOrNull { it.cleanName.equals(t, true) } ?: return null
		val (perks, footer) = perks(person.cleanLore)
		if (perks.isEmpty()) return null
		val results = slots.firstOrNull { s -> votes(s.cleanLore).isNotEmpty() }
		val year = results?.cleanLore?.firstOrNull { it.startsWith("Year", true) }
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		return MayorModel(m.groupValues[1].replaceFirstChar { it.uppercase() }, m.groupValues[2].trim(), person.slot, perks, footer,
			year, results?.let { votes(it.cleanLore) } ?: emptyList(), results?.slot, nav)
	}
}

@Config
object MayorMenuSettings : MenuSettings("mayor", panels = listOf("votes")) {
	val reset by button("reset") { resetToDefaults() }
}

object MayorMenu : CustomMenu {
	override val id = "mayor"
	override val displayName = "Mayor & Minister"
	override val title = MayorParser.TITLE
	override val settings get() = MayorMenuSettings
	override val defaultSize = 420 to 240

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = MayorView(screen)
}

class MayorView(screen: AbstractContainerScreen<*>) : ApexViewBase<MayorModel>(screen) {
	override fun palette() = MayorMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = MayorParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: MayorModel, mx: Int, my: Int) {
		ctx.text(font, "§6§l${m.role} §f§l${m.person}", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val showVotes = MayorMenuSettings.shows("votes") && m.votes.isNotEmpty()
		val lw = if (showVotes) (w - 18) * 3 / 5 else w - 12
		stacks[m.personSlot]?.let { ctx.item(it, x + 6, y + 20) }
		ctx.text(font, "§6Perks", x + 26, y + 24, MenuTheme.GOLD)
		var yy = y + 40
		for (p in m.perks) {
			if (yy > y + h - 40) break
			ctx.text(font, "§e" + MenuTheme.fit(p.name, lw, font::width), x + 6, yy, MenuTheme.WHITE); yy += 10
			for (d in p.description) { if (yy > y + h - 34) break; ctx.text(font, "§7" + MenuTheme.fit(d, lw - 6, font::width), x + 12, yy, MenuTheme.GREY); yy += 9 }
			yy += 4
		}
		m.footer.firstOrNull()?.let { ctx.text(font, "§8" + MenuTheme.fit(m.footer.joinToString(" "), lw, font::width), x + 6, y + h - 32, MenuTheme.DIM) }
		if (showVotes) {
			val vx = x + 12 + lw; val bw = w - lw - 18
			ctx.text(font, "§6Election" + (m.year?.let { " · $it" } ?: ""), vx, y + 24, MenuTheme.GOLD)
			for ((i, v) in m.votes.withIndex()) {
				val vy = y + 38 + i * 24
				if (vy > y + h - 46) break
				val win = v.candidate.equals(m.person, true)
				ctx.text(font, (if (win) "§a" else "§f") + v.candidate + " §7${v.votes}", vx, vy, MenuTheme.WHITE)
				MenuTheme.progressBar(ctx, font, vx, vy + 10, bw, 9, v.percent, 100.0, "%.1f%%".format(v.percent), palette = palette())
			}
		}
		var bx = x + 6
		for ((n, s) in m.nav) { val b = font.width(n) + 14; button(ctx, mx, my, bx, y + h - 20, b, 14, n) { k -> clickSlot(s, k) }; bx += b + 4 }
	}
}
