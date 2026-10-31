// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.melody

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class Song(val name: String, val slot: Int, val difficulty: String, val best: Double?, val perfects: Int?, val rewards: List<String>, val locked: String?)
data class MelodyModel(val songs: List<Song>, val nav: Map<String, Int>) {
	val byDifficulty get() = songs.groupBy { it.difficulty }
}

object MelodyParser {
	val TITLE = Regex("^(?i)Melody(?: ♫)?$")
	private val DIFF = Regex("^(\\w+) Song$", RegexOption.IGNORE_CASE)
	private val BEST = Regex("^Best: ([\\d.]+)%", RegexOption.IGNORE_CASE)
	private val PERF = Regex("^Perfects: (\\d+)", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): MelodyModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val songs = slots.mapNotNull { s ->
			val lore = s.cleanLore
			val d = lore.firstNotNullOfOrNull { DIFF.matchEntire(it)?.groupValues?.get(1) } ?: return@mapNotNull null
			Song(s.cleanName, s.slot, d, lore.firstNotNullOfOrNull { BEST.find(it)?.groupValues?.get(1)?.toDoubleOrNull() },
				lore.firstNotNullOfOrNull { PERF.find(it)?.groupValues?.get(1)?.toInt() }, SkillLore.rewards(lore),
				lore.lastOrNull { it.isNotEmpty() }?.takeIf { !it.startsWith("Click", true) })
		}
		if (songs.size < 2) return null
		return MelodyModel(songs, SkillLore.nav(slots))
	}
}

@Config
object MelodySettings : MenuSettings("melody") {
	val reset by button("reset") { resetToDefaults() }
}

object MelodyMenu : CustomMenu {
	override val id = "melody"
	override val displayName = "Melody's Harp (song list)"
	override val title = MelodyParser.TITLE
	override val settings get() = MelodySettings
	override val defaultSize = 440 to 260

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = MelodyView(screen)
}

class MelodyView(screen: AbstractContainerScreen<*>) : SkillViewBase<MelodyModel>(screen) {
	override fun palette() = MelodySettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = MelodyParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: MelodyModel, mx: Int, my: Int) {
		header(ctx, mx, my, "Melody's Harp")
		var gy = y + 22
		val cols = 4; val gap = 3; val th = 24
		val tw = (w - 16 - gap * (cols - 1)) / cols
		for ((diff, list) in m.byDifficulty) {
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, "$diff Songs")
			list.forEachIndexed { i, s ->
				val col = when { s.locked != null -> "§c"; s.best != null && s.best >= 100 -> "§a"; else -> "§e" }
				val sub = s.locked ?: listOfNotNull(s.best?.let { "best $it%" } ?: "best N/A", s.perfects?.let { "$it perfects" }).joinToString(" · ")
				tile(ctx, mx, my, x + 8 + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th, s.slot, "$col${s.name}", sub, null)
			}
			gy += gh + 3
		}
		navRow(ctx, mx, my, m.nav)
	}
}
