// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items
import net.minecraft.util.StringRepresentable
import io.github.notenoughupdates.moulconfig.ChromaColour
import dev.ashtonite365.apex.features.kit.terminals.KitTerminalLogic
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object TerminalSolver {
	val identifier: String
		get() = "terminal-solver"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val highlight by riskyToggle("highlight", RiskLevel.GREY_AREA)
		val orderNumbers by riskyToggle("order-numbers", RiskLevel.GREY_AREA)
		val blockWrongClicks by riskyToggle("block-wrong-clicks", RiskLevel.RISKY)
		val rubix by riskyToggle("rubix", RiskLevel.GREY_AREA)
		val rubixMode by choice("rubix-mode") { RubixModeOption.FEWEST_CLICKS }
		val melody by riskyToggle("melody", RiskLevel.GREY_AREA)
		val firstClickProtection by riskyToggle("first-click-protection", RiskLevel.RISKY)
		val firstClickMs by integer("first-click-ms", 0, 600) { 200 }
		val firstColour by colour("first-colour") { ChromaColour.fromStaticRGB(0, 255, 0, 160) }
		val secondColour by colour("second-colour") { ChromaColour.fromStaticRGB(0, 170, 255, 128) }
		val restColour by colour("rest-colour") { ChromaColour.fromStaticRGB(0, 255, 0, 96) }
		val rubixPositiveColour by colour("rubix-positive-colour") { ChromaColour.fromStaticRGB(85, 255, 85, 140) }
		val rubixNegativeColour by colour("rubix-negative-colour") { ChromaColour.fromStaticRGB(170, 0, 0, 140) }
		val melodyColour by colour("melody-colour") { ChromaColour.fromStaticRGB(85, 255, 85, 160) }
	}

	enum class RubixModeOption(val id: String, val mode: KitTerminalLogic.RubixMode) : StringRepresentable {
		FEWEST_CLICKS("fewest-clicks", KitTerminalLogic.RubixMode.FEWEST_CLICKS),
		LEFT_CLICKS_ONLY("left-clicks-only", KitTerminalLogic.RubixMode.LEFT_CLICKS_ONLY);

		override fun getSerializedName() = id
	}

	private const val PANES = "Correct all the panes!"
	private const val ORDER = "Click in order!"
	private const val RUBIX = "Change all to same color!"
	private const val MELODY = "Click the button on time!"
	private val fixedTitles = setOf(PANES, ORDER, RUBIX, MELODY)
	private val letterTitle = Regex("What starts with: '(.)'\\?")
	private val colourTitle = Regex("Select all the (.+) items!")

	private val rubixCycle = listOf(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED)

	private fun containerScreen() = Client.screen as? AbstractContainerScreen<*>
	private fun titleOf(screen: AbstractContainerScreen<*>) = screen.title.bareText.trim()
	private fun menuSlots(screen: AbstractContainerScreen<*>) = screen.menu.slots.filter { it.container !is Inventory }
	private fun isTerminalTitle(title: String) = title in fixedTitles || letterTitle.matches(title) || colourTitle.matches(title)

	private fun Slot.holds(item: net.minecraft.world.item.Item) = this.item.`is`(item)
	private fun Slot.redPane() = holds(Items.STAINED_GLASS_PANE.red)
	private fun Slot.limePane() = holds(Items.STAINED_GLASS_PANE.lime)

	fun rubixClicks(): Map<Int, Int>? {
		val screen = containerScreen() ?: return null
		if (titleOf(screen) != RUBIX) return null
		val colourBySlot = HashMap<Int, Int>()
		for (slot in menuSlots(screen)) {
			val c = rubixCycle.indexOfFirst { slot.holds(Items.STAINED_GLASS_PANE.pick(it)) }
			if (c >= 0) colourBySlot[slot.index] = c
		}
		return KitTerminalLogic.rubixSolve(colourBySlot, TConfig.rubixMode.mode)
	}

	fun solution(): List<Slot>? {
		val screen = containerScreen() ?: return null
		val title = titleOf(screen)
		val filled = menuSlots(screen).filter { !it.item.isEmpty }
		if (title == PANES) return filled.filter { it.redPane() }
		if (title == ORDER) return filled.filter { it.redPane() }.sortedBy { it.item.count }
		letterTitle.matchEntire(title)?.let { m ->
			val letter = m.groupValues[1]
			return filled.filter { s ->
				!s.redPane() && !s.limePane() && !s.item.hasFoil() && s.item.hoverName.bareText.startsWith(letter, ignoreCase = true)
			}
		}
		colourTitle.matchEntire(title)?.let { m ->
			val wanted = m.groupValues[1].lowercase().replace('_', ' ').replace("silver", "light gray")
			return filled.filter { s ->
				!s.item.hasFoil() && !s.holds(Items.STAINED_GLASS_PANE.black) &&
					wanted in s.item.hoverName.bareText.lowercase().replace("light grey", "light gray")
			}
		}
		return null
	}

	private fun fillSlot(event: SlotRenderEvents.After, argb: Int) =
		event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, argb)

	private fun labelSlot(event: SlotRenderEvents.After, text: String) =
		event.context.text(Client.font, text, event.slot.x + 8 - Client.font.width(text) / 2, event.slot.y + 4, -1, true)

	@Subscribe
	fun onRubixRender(event: SlotRenderEvents.After) {
		if (!TConfig.rubix) return
		val clicks = rubixClicks()?.get(event.slot.index) ?: return
		val colour = if (clicks > 0) TConfig.rubixPositiveColour else TConfig.rubixNegativeColour
		fillSlot(event, colour.getEffectiveColourRGB())
		labelSlot(event, clicks.toString())
	}

	@Subscribe
	fun onMelodyRender(event: SlotRenderEvents.After) {
		if (!TConfig.melody) return
		val screen = containerScreen() ?: return
		if (titleOf(screen) != MELODY) return
		val slots = menuSlots(screen)
		val bar = slots.firstOrNull { it.holds(Items.STAINED_GLASS_PANE.pick(DyeColor.MAGENTA)) }?.index ?: return
		val cursor = slots.lastOrNull { it.holds(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME)) }?.index ?: return
		val button = slots.lastOrNull { it.holds(Items.DYED_TERRACOTTA.pick(DyeColor.LIME)) }?.index
		val target = when {
			!KitTerminalLogic.melodyReady(bar, cursor) -> bar
			button == null -> return
			else -> button
		}
		if (event.slot.index == target) fillSlot(event, TConfig.melodyColour.getEffectiveColourRGB())
	}

	private var shownKey: String? = null
	private var shownSince = 0L

	private fun keyOf(screen: AbstractContainerScreen<*>) = titleOf(screen) + screen.menu.containerId

	@Subscribe
	fun onFirstClick(event: IsSlotProtectedEvent) {
		if (!TConfig.firstClickProtection) return
		val screen = containerScreen() ?: return
		if (!isTerminalTitle(titleOf(screen))) return
		val now = System.currentTimeMillis()
		val key = keyOf(screen)
		if (shownKey != key) { shownKey = key; shownSince = now }
		if (KitTerminalLogic.firstClickBlocked(shownSince, now, TConfig.firstClickMs)) event.protect()
	}

	@Subscribe
	fun onScreenTick(event: dev.ashtonite365.apex.events.TickEvent) {
		val key = containerScreen()?.let(::keyOf)
		if (key == shownKey) return
		shownKey = key
		shownSince = System.currentTimeMillis()
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		if (!TConfig.highlight) return
		val rank = solution()?.indexOf(event.slot) ?: return
		val colour = when (rank) {
			-1 -> return
			0 -> TConfig.firstColour
			1 -> TConfig.secondColour
			else -> TConfig.restColour
		}
		fillSlot(event, colour.getEffectiveColourRGB())
	}

	@Subscribe
	fun onOrderNumbers(event: SlotRenderEvents.After) {
		if (!TConfig.orderNumbers) return
		val screen = containerScreen() ?: return
		if (titleOf(screen) != ORDER) return
		val rank = solution()?.indexOf(event.slot) ?: return
		if (rank in 0..2) labelSlot(event, (rank + 1).toString())
	}

	@Subscribe
	fun onClick(event: IsSlotProtectedEvent) {
		if (!TConfig.blockWrongClicks) return
		val slot = event.slot ?: return
		if (slot.container is Inventory) return
		val answer = solution() ?: return
		val inOrder = containerScreen()?.let(::titleOf) == ORDER
		val allowed = if (inOrder) answer.firstOrNull() === slot else slot in answer
		if (!allowed) event.protect()
	}
}
