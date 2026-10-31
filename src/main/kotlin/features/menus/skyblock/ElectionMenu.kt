// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class ElectionCandidate(val slot: Int, val name: String, val special: Boolean, val votes: String?, val percent: Double?,
                             val votedFor: Boolean, val leading: Boolean, val info: List<String>)

data class ElectionModel(val year: String?, val candidates: List<ElectionCandidate>, val voted: Boolean, val nav: List<Pair<String, Int>>)

object ElectionParser {

	private val CANDIDATE = Regex("^Year (\\d+) (Special )?Candidate$", RegexOption.IGNORE_CASE)

	val VOTES = Regex("^Votes:\\s*(\\S+)\\s*\\(([\\d.]+)%\\)", RegexOption.IGNORE_CASE)
	const val VOTED_FOR = "You voted for this candidate!"
	private const val LEADING = "Leading in votes!"
	private val CLICK = Regex("^Click to vote for .+!?$", RegexOption.IGNORE_CASE)
	const val UPCOMING = "Upcoming Special Mayor"

	fun candidate(s: SlotText): ElectionCandidate? {
		val lore = s.cleanLore
		val head = lore.firstOrNull()?.trim() ?: return null
		val m = CANDIDATE.matchEntire(head) ?: return null
		if (s.cleanName.isBlank() || s.cleanName.startsWith(UPCOMING, true)) return null
		val v = lore.firstNotNullOfOrNull { VOTES.find(it.trim()) }
		val info = lore.drop(1).map { it.trimEnd() }.filter { l -> VOTES.find(l.trim()) == null && !CLICK.matches(l.trim()) &&
			!l.trim().equals(VOTED_FOR, true) && !l.trim().equals(LEADING, true) }

		val tidy = mutableListOf<String>()
		for (l in info.map { if (it.isBlank()) "" else it.trim() }) if (l.isNotEmpty() || tidy.lastOrNull()?.isNotEmpty() == true) tidy += l
		while (tidy.lastOrNull()?.isEmpty() == true) tidy.removeAt(tidy.lastIndex)
		return ElectionCandidate(s.slot, s.cleanName.trim(), m.groupValues[2].isNotEmpty(), v?.groupValues?.get(1), v?.groupValues?.get(2)?.toDoubleOrNull(),
			lore.any { it.trim().equals(VOTED_FOR, true) }, lore.any { it.trim().equals(LEADING, true) }, tidy)
	}

	fun parse(title: String, slots: List<SlotText>): ElectionModel? {
		val t = MenuText.clean(title)
		if (!MenuTitles.ELECTION.matches(t)) return null
		val cands = slots.sortedBy { it.slot }.mapNotNull { candidate(it) }.distinctBy { it.name.lowercase() }
		if (cands.isEmpty()) return null
		val voted = cands.any { it.votedFor || it.percent != null }
		val year = Regex("Year (\\d+)").find(t)?.groupValues?.get(1)
			?: slots.firstNotNullOfOrNull { s -> s.cleanLore.firstOrNull()?.let { CANDIDATE.matchEntire(it.trim())?.groupValues?.get(1) } }
		val nav = slots.sortedBy { it.slot }.mapNotNull { s ->
			val n = s.cleanName.trim()
			when {
				n.equals("Close", true) || n.equals("Go Back", true) -> n to s.slot
				n.startsWith(UPCOMING, true) -> UPCOMING to s.slot
				else -> null
			}
		}.distinctBy { it.first }
		return ElectionModel(year, cands, voted, nav)
	}

	fun fillPx(percent: Double?, inner: Int): Int = if (percent == null || inner <= 0) 0 else MenuTheme.progressWidth(inner, percent, 100.0)
}

@Config
object ElectionMenuSettings : MenuSettings("election") {
	val reset by button("reset") { resetToDefaults() }
}

object ElectionMenu : CustomMenu {
	override val id = "election"
	override val displayName = "Election"
	override val title = MenuTitles.ELECTION
	override val settings get() = ElectionMenuSettings
	override val defaultSize = 520 to 290

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = ElectionView(screen)
}

class ElectionView(screen: AbstractContainerScreen<*>) : ApexViewBase<ElectionModel>(screen) {
	override fun palette() = ElectionMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = ElectionParser.parse(title, slots)
	private val scroller = ScrollView { scrollKey() }
	override val scrollView get() = scroller

	override fun draw(ctx: GuiGraphicsExtractor, m: ElectionModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lElection" + (m.year?.let { " §7· Year $it" } ?: ""), x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		if (!m.voted) ctx.text(font, "§7Vote to see the standings", x + 6, y + 19, MenuTheme.GREY)
		val top = y + 30; val bottom = y + h - 24
		val n = m.candidates.size
		val gap = 4
		val tw = (w - 12 - gap * (n - 1)) / n
		val lines = m.candidates.map { c -> tileLines(c, tw - 8) }
		val tileH = lines.maxOf { 44 + it.size * 10 } + 4
		val view = MenuBounds(x + 1, top, w - 2, bottom - top)
		scroller.layout(view, tileH)
		val ty = top - scroller.offset
		beginClip(ctx, view)
		m.candidates.forEachIndexed { i, c ->
			val tx = x + 6 + i * (tw + gap)
			val hov = hovered(mx, my, tx, ty, tw, tileH)
			MenuTheme.frame(ctx, tx, ty, tx + tw, ty + tileH, if (hov) MenuTheme.FILL_HOVER else MenuTheme.FILL)

			val bx = tx + 4; val by = ty + 3; val bw = tw - 8; val bh = 11
			if (!m.voted) {
				MenuTheme.frame(ctx, bx, by, bx + bw, by + bh, palette().fill, palette().border)
				ctx.text(font, "§7?", bx + (bw - font.width("?")) / 2, by + 2, MenuTheme.GREY)
			} else MenuTheme.progressBar(ctx, font, bx, by, bw, bh, c.percent ?: 0.0, 100.0, label = null, palette = palette())
			stacks[c.slot]?.let { ctx.item(it, tx + 4, ty + 16) }
			val nameCol = if (c.votedFor) "§a" else if (c.special) "§d" else "§f"
			ctx.text(font, nameCol + c.name, tx + 24, ty + 17, MenuTheme.WHITE)
			ctx.text(font, if (m.voted && c.votes != null) "§7${c.votes} votes" else "§8votes hidden", tx + 24, ty + 27, MenuTheme.GREY)
			lines[i].forEachIndexed { j, l -> ctx.text(font, l, tx + 4, ty + 40 + j * 10, MenuTheme.GREY) }
			area(tx, ty, tw, tileH) { b -> clickSlot(c.slot, b) }
		}
		endClip(ctx)
		var nx = x + 6
		for ((label, s) in m.nav) { val bw = font.width(label) + 14; button(ctx, mx, my, nx, y + h - 20, bw, 14, label) { b -> clickSlot(s, b) }; nx += bw + 4 }
	}

	private fun tileLines(c: ElectionCandidate, width: Int): List<String> {
		val out = mutableListOf<String>()
		if (c.votedFor) out += EnchantLabel.wrap("§a✔ You voted", width, font::width)
		if (c.leading) out += EnchantLabel.wrap("§6Leading in votes", width, font::width)
		c.info.forEachIndexed { i, l ->
			if (l.isEmpty()) { out += ""; return@forEachIndexed }

			val col = if (l.startsWith("✯") || c.info.getOrNull(i - 1)?.isEmpty() == true) "§e" else "§7"
			EnchantLabel.wrap(l, width) { font.width(col + it) }.forEach { out += col + it }
		}
		return out
	}
}
