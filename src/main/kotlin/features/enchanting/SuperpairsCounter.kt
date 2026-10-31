
package dev.ashtonite365.apex.features.enchanting

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenForegroundEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

data class SuperpairsCard(val key: String?, val powerup: Boolean = false)

class SuperpairsCounterState {
	private val remembered = mutableMapOf<Int, String>()
	private val powerups = mutableSetOf<Int>()
	var clicks = 0; private set
	var hypixelClicksLeft: Int? = null; private set
	private var visible: Map<Int, String> = emptyMap()

	fun reset() { remembered.clear(); powerups.clear(); clicks = 0; hypixelClicksLeft = null; visible = emptyMap() }

	fun update(cards: List<SuperpairsCard>, clicksLeftFromGui: Int?) {
		val vis = mutableMapOf<Int, String>()
		for ((i, c) in cards.withIndex()) {
			val key = c.key ?: continue
			if (c.powerup) { powerups += i; continue }
			remembered[i] = key; vis[i] = key
		}
		visible = vis
		hypixelClicksLeft = clicksLeftFromGui
	}

	fun onClick() { clicks++ }

	val matchedPairs: Int get() = matchedKeys().size

	private fun matchedKeys() = visible.values.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys

	val rememberedPairs: Int
		get() {
			val matched = matchedKeys()
			return remembered.values.filter { it !in matched }.groupingBy { it }.eachCount().values.sumOf { it / 2 }
		}

	fun clicksLeft(maxClicks: Int?): Int? = hypixelClicksLeft ?: maxClicks?.let { (it - clicks).coerceAtLeast(0) }

	companion object {
		private val CLICKS_REGEX = Regex("""Clicks? (?:Remaining|Left):?\s*(\d+)""", RegexOption.IGNORE_CASE)
		fun parseClicksLeft(lines: List<String>): Int? =
			lines.firstNotNullOfOrNull { CLICKS_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
	}
}

object SuperpairsCounter {
	private val state = SuperpairsCounterState()
	private var screenRef: AbstractContainerScreen<*>? = null

	private var maxClicks: Int? = null

	private fun active(screen: AbstractContainerScreen<*>) =
		ExperimentSolvers.TConfig.superpairsCounter && (SkyBlockSession.inSkyBlock || SuperpairsTestDriver.enabled) &&
			screen.title.bareText.let { it.startsWith("Superpairs") && !it.contains("Stakes") && !it.contains("Rewards") }

	private val ItemStack.path get() = BuiltInRegistries.ITEM.getKey(item).path
	private fun chest(screen: AbstractContainerScreen<*>) = screen.menu.slots.filter { it.container !== Client.player?.inventory }

	private fun card(stack: ItemStack): SuperpairsCard {
		if (stack.isEmpty) return SuperpairsCard(null)
		val p = stack.path
		if (p.endsWith("_stained_glass") || p.endsWith("_stained_glass_pane")) return SuperpairsCard(null)
		val name = stack.hoverName.bareText
		return SuperpairsCard(p + "|" + name, name.contains("Powerup", ignoreCase = true))
	}

	private fun sync(screen: AbstractContainerScreen<*>) {
		if (screen !== screenRef) { screenRef = screen; state.reset(); maxClicks = null }
		val slots = chest(screen)

		val grid = slots.take((slots.size - 9).coerceAtLeast(0))
		val info = slots.flatMap { s -> listOf(s.item.hoverName.bareText) + s.item.view().loreAccordingToNbt.map { it.bareText } }
		val parsed = SuperpairsCounterState.parseClicksLeft(info)
		if (parsed != null && maxClicks == null) maxClicks = parsed + state.clicks
		state.update(grid.map { card(it.item) }, parsed)
	}

	@Subscribe
	fun onClick(event: SlotClickEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		if (!active(screen) || screen !== screenRef) return
		if (event.slot.container === Client.player?.inventory) return
		if (card(event.stack).key == null) state.onClick()
	}

	@Subscribe
	fun onForeground(event: HandledScreenForegroundEvent) {
		if (!active(event.screen)) { if (event.screen === screenRef) screenRef = null; return }
		sync(event.screen)
		val acc = event.screen.castAccessor()
		val font = Client.font
		val left = state.clicksLeft(maxClicks)?.toString() ?: "?"
		val parts = listOf("§eRemembered: §f${state.rememberedPairs}", "§aMatched: §f${state.matchedPairs}", "§bClicks left: §f$left")
		val line = parts.joinToString("  ")
		val px = acc.x_Apex; val py = acc.y_Apex; val pw = acc.backgroundWidth_Apex
		val ctx = event.context
		when {
			py >= 12 -> ctx.text(font, line, px + (pw - font.width(line)) / 2, py - 11, -1, true)
			px >= parts.maxOf { font.width(it) } + 6 ->
				parts.forEachIndexed { i, t -> ctx.text(font, t, px - 4 - font.width(t), py + 4 + i * 10, -1, true) }
			else -> parts.forEachIndexed { i, t -> ctx.text(font, t, px + pw + 4, py + 4 + i * 10, -1, true) }
		}
	}
}
