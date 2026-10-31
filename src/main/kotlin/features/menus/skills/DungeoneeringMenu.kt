// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.GuiGraphicsExtractor
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

data class DungeonTrack(val name: String, val level: Int, val slot: Int, val progress: Progress?, val isClass: Boolean, val detail: String?)

data class DungeoneeringModel(val dungeons: List<DungeonTrack>, val classes: List<DungeonTrack>, val nav: Map<String, Int>, val extras: List<Pair<String, Int>>,
                              val tips: Map<Int, Pair<String, List<String>>> = emptyMap()) {
	val classAverage get() = classes.map { it.level }.takeIf { it.isNotEmpty() }?.average()
}

object DungeoneeringParser {
	val TITLE = Regex("^(?i)Dungeoneering$")
	private val NAME = Regex("^(.+?) (\\d+)$")

	fun parse(title: String, slots: List<SlotText>): DungeoneeringModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val tracks = slots.mapNotNull { s ->
			val m = NAME.matchEntire(s.cleanName) ?: return@mapNotNull null
			val p = SkillLore.progress(s.cleanLore) ?: return@mapNotNull null
			val lore = s.cleanLore
			val isClass = lore.any { it.equals("Class Passives", true) }

			val detail = if (isClass) lore.firstOrNull { it.contains(':') }
				else lore.indexOfFirst { it.startsWith("Current Mastery Reward", true) }.takeIf { it >= 0 }?.let { i -> lore.drop(i + 1).takeWhile { it.isNotEmpty() }.joinToString(" ") }
			DungeonTrack(m.groupValues[1], m.groupValues[2].toInt(), s.slot, p, isClass, detail)
		}
		if (tracks.none { !it.isClass } && tracks.size < 2) return null
		val extras = SkillLore.extras(slots, tracks.map { it.slot }.toSet()).filter { !it.first.equals("Not Coming Soon", true) }
		val shown = tracks.map { it.slot }.toSet() + extras.map { it.second }
		val tips = slots.filter { it.slot in shown }.associate { it.slot to (it.name to it.lore) }
		return DungeoneeringModel(tracks.filter { !it.isClass }, tracks.filter { it.isClass }, SkillLore.nav(slots), extras, tips)
	}
}

@Config
object DungeoneeringSettings : MenuSettings("dungeoneering") {
	val reset by button("reset") { resetToDefaults() }
}

object DungeoneeringMenu : CustomMenu {
	override val id = "dungeoneering"
	override val displayName = "Dungeoneering"
	override val title = DungeoneeringParser.TITLE
	override val settings get() = DungeoneeringSettings
	override val defaultSize = 400 to 220

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = DungeoneeringView(screen)
}

class DungeoneeringView(screen: AbstractContainerScreen<*>) : SkillViewBase<DungeoneeringModel>(screen) {
	override fun palette() = DungeoneeringSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = DungeoneeringParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: DungeoneeringModel, mx: Int, my: Int) {
		var tipSlot: Int? = null
		header(ctx, mx, my, "Dungeoneering" + (m.classAverage?.let { "  §7class avg §f" + "%.1f".format(java.util.Locale.ROOT, it) } ?: ""))
		var gy = y + 22
		for ((label, list) in listOf("Dungeons" to m.dungeons, "Classes" to m.classes)) {
			if (list.isEmpty()) continue
			val cols = 3; val gap = 4; val th = barTileH(32)
			val tw = (w - 16 - gap * (cols - 1)) / cols
			val rows = (list.size + cols - 1) / cols
			val gh = 14 + rows * (th + gap)
			val top = group(ctx, x + 4, gy, w - 8, gh, label)
			list.forEachIndexed { i, t ->
				val tx = x + 8 + (i % cols) * (tw + gap); val ty = top + (i / cols) * (th + gap)
				tile(ctx, mx, my, tx, ty, tw, th, t.slot, "§f${t.name} §a${t.level}", t.detail, t.progress)
				if (hovered(mx, my, tx, ty, tw, th)) tipSlot = t.slot
			}
			gy += gh + 4
		}
		var bx = x + 4
		for ((l, slot) in m.extras) {
			val bw = font.width(l) + 10; button(ctx, mx, my, bx, y + h - 36, bw, 14, l) { clickSlot(slot, it) }
			if (hovered(mx, my, bx, y + h - 36, bw, 14)) tipSlot = slot
			bx += bw + 4
		}
		navRow(ctx, mx, my, m.nav)

		tipSlot?.let { s -> if (screen.menu.carried.isEmpty) {
			val label = m.extras.firstOrNull { it.second == s }?.first ?: (m.dungeons + m.classes).first { it.slot == s }.let { "${it.name} ${it.level}" }
			val tip = m.tips[s]
			SkillLevelTips.show(ctx, font, SkillLevelTips.itemLines(tip?.first ?: "§f$label", tip?.second), mx, my, screen.width)
		} }
	}
}
