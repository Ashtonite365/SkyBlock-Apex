
package dev.ashtonite365.apex.features.enchanting

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.experiments.ExperimentationTable
import dev.ashtonite365.apex.features.inventory.TooltipFit
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bareText

object ExperimentTooltips {
	enum class Game { SUPERPAIRS, CHRONOMATRON, ULTRASEQUENCER }

	fun gameOf(title: String): Game? = when {
		"Stakes" in title -> null
		title.startsWith("Superpairs") -> Game.SUPERPAIRS
		title.startsWith("Chronomatron") -> Game.CHRONOMATRON
		title.startsWith("Ultrasequencer") -> Game.ULTRASEQUENCER
		else -> null
	}

	private val HIDDEN_NAMES = setOf("Click any button!", "Click the button on time!", "?")

	fun isHidden(itemPath: String, name: String): Boolean =
		itemPath.endsWith("glass_pane") || name.trim() in HIDDEN_NAMES

	fun <T> trim(lines: List<T>, hidden: Boolean): List<T> = if (hidden) emptyList() else lines.take(1)

	fun gridRect(slotXs: List<Int>, slotYs: List<Int>): IntArray? {
		if (slotXs.isEmpty()) return null
		return intArrayOf(slotXs.min() - 1, slotYs.min() - 1, slotXs.max() + 17, slotYs.max() + 17)
	}

	private fun enabledFor(g: Game) = when (g) {
		Game.SUPERPAIRS -> ExperimentationTable.superpairsApexMenus
		Game.CHRONOMATRON -> ExperimentationTable.chronoApexMenus
		Game.ULTRASEQUENCER -> ExperimentationTable.ultraApexMenus
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val game = gameOf(screen.title.bareText) ?: return
		if (!enabledFor(game)) return
		val acc = screen as AccessorHandledScreen
		val chest = screen.menu.slots.filter { it.container !is Inventory }
		gridRect(chest.map { acc.x_Apex + it.x }, chest.map { acc.y_Apex + it.y })?.let { TooltipFit.avoidFor(it) }
		val hovered = acc.focusedSlot_Apex ?: return
		if (hovered.container is Inventory || hovered.item !== event.stack) return
		val hidden = isHidden(BuiltInRegistries.ITEM.getKey(event.stack.item).path, event.stack.hoverName.bareText)
		val kept = trim(event.lines.toList(), hidden)
		event.lines.clear(); event.lines.addAll(kept)
	}
}
