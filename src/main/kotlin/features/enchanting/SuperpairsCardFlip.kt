// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.enchanting

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SuperpairsCardFlipEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack

class SuperpairsFlipDetector {

	private val faceDown = HashSet<Int>()
	private var previousClicks: Int? = null

	fun reset() {
		faceDown.clear()
		previousClicks = null
	}

	fun update(cards: Map<Int, Boolean>, clicksLeft: Int?): List<Int> {
		val roundOver = previousClicks == 0 && clicksLeft == 0
		previousClicks = clicksLeft
		val revealed = ArrayList<Int>()
		cards.forEach { (slot, up) ->
			when {
				!up -> faceDown.add(slot)
				faceDown.remove(slot) -> if (!roundOver) revealed.add(slot)
			}
		}
		return revealed
	}

	companion object {
		private val TITLE = Regex("""^Superpairs \((\w+)\)$""")

		fun gridSlots(title: String): List<Int>? {
			val tier = TITLE.matchEntire(title.trim())?.groupValues?.get(1) ?: return null
			val first: Int
			val last: Int
			val margin: Int
			when (tier) {
				"Beginner" -> { first = 18; last = 35; margin = 1 }
				"High", "Grand" -> { first = 10; last = 43; margin = 2 }
				"Supreme", "Transcendent", "Metaphysical" -> { first = 9; last = 44; margin = 1 }
				else -> return null
			}
			return (first..last).filter { val col = it % 9; col >= margin && col <= 8 - margin }
		}

		private val HIDDEN = Regex("""^(?:\?|Click a(?: seco)?n[dy] button!?|Next button is instantly rewarded!?)$""")

		fun isHiddenName(name: String) = HIDDEN.matches(name.trim())

		private const val ULTRA_RARE_LINE = "XX ULTRA-RARE BOOK! XX"

		fun ultraRareBook(lore: List<String>): String? {
			if (lore.isEmpty() || lore[0].trim() != ULTRA_RARE_LINE) return null
			if (lore.size < 3) return null
			val book = lore[2].trim()
			return if (book.isEmpty()) null else book
		}
	}
}

object SuperpairsCardFlip {
	private val detector = SuperpairsFlipDetector()
	private var watched: AbstractContainerScreen<*>? = null
	private var alerted = false

	private fun restart(screen: AbstractContainerScreen<*>?) {
		watched = screen
		detector.reset()
		alerted = false
	}

	private fun faceUp(stack: ItemStack): Boolean? {
		if (stack.isEmpty) return null
		if (SuperpairsFlipDetector.isHiddenName(stack.hoverName.bareText)) return false
		val path = BuiltInRegistries.ITEM.getKey(stack.item).path
		return !(path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane"))
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*>
		val grid = if (screen == null) null else SuperpairsFlipDetector.gridSlots(screen.title.bareText)
		val allowed = SkyBlockSession.inSkyBlock || SuperpairsTestDriver.enabled
		if (screen == null || grid == null || !allowed) {
			if (watched != null) restart(null)
			return
		}
		if (screen !== watched) restart(screen)
		val chest = screen.menu.slots.filter { it.container !== Client.player?.inventory }
		val text = ArrayList<String>()
		for (slot in chest) {
			text.add(slot.item.hoverName.bareText)
			slot.item.view().loreAccordingToNbt.mapTo(text) { it.bareText }
		}
		val clicks = SuperpairsCounterState.parseClicksLeft(text)
		val cards = LinkedHashMap<Int, Boolean>()
		for (i in grid) {
			val slot = chest.getOrNull(i) ?: continue
			faceUp(slot.item)?.let { cards[i] = it }
		}
		detector.update(cards, clicks).forEach { SuperpairsCardFlipEvent.publish(SuperpairsCardFlipEvent(it, chest[it].item)) }
	}

	@Subscribe
	fun onFlip(event: SuperpairsCardFlipEvent) {
		if (alerted || !ExperimentSolvers.TConfig.ultraRareBookAlert) return
		val book = SuperpairsFlipDetector.ultraRareBook(event.stack.view().loreAccordingToNbt.map { it.bareText }) ?: return
		alerted = true
		Alerts.show(Component.literal("§d§kXX§5 ULTRA-RARE BOOK! §d§kXX"), 3.seconds, sound = false)
		Client.player?.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1f, 1f)
		Client.showChat(Component.literal("§5[SkyBlock Apex] You uncovered an Ultra-Rare Book: §9$book"))
	}
}
