// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.enchanting

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.bareText

object ExperimentSolvers {
	val identifier: String
		get() = "experiment-solvers"

	object TConfig {
		private val t get() = dev.ashtonite365.apex.features.experiments.ExperimentationTable
		val enabled get() = t.enabled
		val showNext get() = t.ultraShowNext
		val sequencerNumbers get() = t.ultraNumbers
		val superpairsReveal get() = t.superpairsReveal && t.superpairsApexMenus
		val superpairsCounter get() = t.superpairsCounter && t.superpairsApexMenus
		val ultraRareBookAlert get() = t.ultraRareBookAlert && t.superpairsApexMenus

		val ultrasequencerOn get() = t.ultraApexMenus
		val superpairsOn get() = t.superpairsApexMenus
		val debugLog get() = t.ultraDebugLog
		val blockWrongClicks get() = t.blockWrongClicks
	}

	enum class Solver { NONE, ULTRASEQUENCER, SUPERPAIRS }

	data class Cell(val path: String, val name: String, val count: Int, val empty: Boolean = false) {
		val isPane get() = path.endsWith("_stained_glass_pane")
		val isGlass get() = path.endsWith("_stained_glass") || isPane

		val face get() = path to name
	}

	data class Paint(val box: Int? = null, val label: String? = null, val reveal: Boolean = false)

	private const val GREEN = 0x8000FF00.toInt()
	private const val YELLOW = 0x60FFFF00
	private const val RED = 0x60FF0000
	private const val BLUE = 0x600000FF

	fun gate(s: Solver, ultrasequencerOn: Boolean, superpairsOn: Boolean): Solver {
		val on = when (s) {
			Solver.ULTRASEQUENCER -> ultrasequencerOn
			Solver.SUPERPAIRS -> superpairsOn
			Solver.NONE -> true
		}
		return if (on) s else Solver.NONE
	}

	private fun gate(s: Solver): Solver = gate(s, TConfig.ultrasequencerOn, TConfig.superpairsOn)

	fun solverFor(title: String): Solver {
		if ("Stakes" in title) return Solver.NONE
		for ((prefix, solver) in listOf("Ultrasequencer" to Solver.ULTRASEQUENCER, "Superpairs" to Solver.SUPERPAIRS))
			if (title.startsWith(prefix)) return solver
		return Solver.NONE
	}

	fun statusOf(cells: List<Cell>): Cell? = if (cells.size >= 5) cells[cells.size - 5] else null

	class SequencerRound {
		val order = sortedMapOf<Int, Int>()
		var position = 0
			private set
		private var memorising = false

		fun observe(cells: List<Cell>) {
			val status = statusOf(cells) ?: return
			val nowMemorising = status.path == "glowstone"
			if (nowMemorising && !memorising) order.clear()
			memorising = nowMemorising
			if (!nowMemorising) return
			position = 0
			cells.forEachIndexed { slot, c -> UltrasequencerOrder.orderIndex(SeqItem(c.path, c.name, c.count))?.let { order[it] = slot } }
		}

		fun clicked(slot: Int) {
			position = UltrasequencerOrder.advance(order, position, slot)
		}

		fun replaying(cells: List<Cell>) = statusOf(cells)?.path == "clock"

		fun allowed(cells: List<Cell>): Set<Int>? = ExperimentClickGuard.sequencerAllowed(order, position, replaying(cells))

		fun paint(cells: List<Cell>, slot: Int, showNext: Boolean, numbers: Boolean): Paint? {
			if (!replaying(cells)) return null
			val step = order.entries.firstOrNull { it.value == slot }?.key ?: return null
			val box = when (step - position) {
				0 -> GREEN
				1 -> if (showNext) YELLOW else null
				else -> null
			}
			val label = if (numbers && step >= position) "${step + 1}" else null
			return Paint(box = box, label = label)
		}

		fun clear() {
			order.clear(); position = 0; memorising = false
		}
	}

	class PairsMemory<T> {
		val seen = mutableMapOf<Int, Pair<Cell, T>>()

		fun observe(cells: List<Cell>, payload: (Int) -> T) {
			cells.forEachIndexed { slot, c -> if (!c.empty && !c.isGlass) seen[slot] = c to payload(slot) }
		}

		fun allowed(cells: List<Cell>): Set<Int>? = ExperimentClickGuard.superpairsAllowed(
			cells.indices.filter { cells[it].isGlass }.toSet(), seen.mapValues { it.value.first.face })

		fun paint(current: Cell, slot: Int, reveal: Boolean): Paint? {
			val card = seen[slot]?.first ?: return null
			return when {
				current.isGlass -> Paint(box = if (seen.values.count { it.first.face == card.face } >= 2) GREEN else RED, reveal = reveal)
				card.name.contains("Powerup", ignoreCase = true) -> Paint(box = BLUE)
				else -> null
			}
		}

		fun clear() = seen.clear()
	}

	private var solver = Solver.NONE
	private var screenRef: AbstractContainerScreen<*>? = null
	private val round = SequencerRound()
	private val pairs = PairsMemory<ItemStack>()

	fun debugSequencerOrder(): Map<Int, Int> = round.order.toMap()

	private fun forget() {
		round.clear(); pairs.clear()
	}

	private fun chestSlots(screen: AbstractContainerScreen<*>): List<Slot> =
		screen.menu.slots.filter { it.container !== Client.player?.inventory }

	private fun cellOf(stack: ItemStack): Cell =
		Cell(BuiltInRegistries.ITEM.getKey(stack.item).path, stack.hoverName.bareText, stack.count, stack.isEmpty)

	private fun cells(screen: AbstractContainerScreen<*>) = chestSlots(screen).map { cellOf(it.item) }

	@Subscribe
	fun onTick(event: TickEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*>
		val active = screen != null && TConfig.enabled && (SkyBlockSession.inSkyBlock || UltrasequencerTestDriver.enabled)
		if (!active) {
			if (screenRef != null) forget()
			solver = Solver.NONE; screenRef = null; return
		}
		screen!!
		if (screen !== screenRef) {
			screenRef = screen
			val title = screen.title.bareText
			solver = gate(solverFor(title))
			forget()
			if (TConfig.debugLog && solver != Solver.NONE) Apex.logger.info("[ExperimentSolvers] detected $solver for '$title'")
		}
		if (solver != Solver.NONE && gate(solver) == Solver.NONE) { solver = Solver.NONE; forget() }
		when (solver) {
			Solver.ULTRASEQUENCER -> round.observe(cells(screen))
			Solver.SUPERPAIRS -> {
				val slots = chestSlots(screen)
				pairs.observe(slots.map { cellOf(it.item) }) { slots[it].item.copy() }
			}
			Solver.NONE -> {}
		}
	}

	private fun allowedClicks(screen: AbstractContainerScreen<*>): Set<Int>? = when (solver) {
		Solver.ULTRASEQUENCER -> round.allowed(cells(screen))
		Solver.SUPERPAIRS -> pairs.allowed(cells(screen))
		Solver.NONE -> null
	}

	@Subscribe
	fun onGuardClick(event: IsSlotProtectedEvent) {
		if (UltrasequencerTestDriver.enabled) return
		if (event.origin != IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE) return
		val screen = screenRef ?: return
		if (solver == Solver.NONE || Client.screen !== screen) return

		val slot = event.slot?.let { chestSlots(screen).indexOf(it) } ?: -1
		if (slot < 0) return

		val alt = listOf(com.mojang.blaze3d.platform.InputConstants.KEY_LALT, com.mojang.blaze3d.platform.InputConstants.KEY_RALT)
			.any { com.mojang.blaze3d.platform.InputConstants.isKeyDown(it) }
		if (ExperimentClickGuard.shouldBlock(TConfig.blockWrongClicks, allowedClicks(screen), slot, alt)) event.protectSilent()
	}

	@Subscribe
	fun onClick(event: SlotClickEvent) {
		val screen = screenRef ?: return
		if (Client.screen !== screen || solver != Solver.ULTRASEQUENCER) return
		val slot = chestSlots(screen).indexOf(event.slot)
		if (slot < 0) return
		if (TConfig.debugLog) {
			val want = round.order[round.position]
			Apex.logger.info("[ExperimentSolvers] ultrasequencer click $slot: step ${round.position + 1}/${round.order.size} expected $want ${if (want == slot) "OK" else "WRONG"} (order ${round.order.toMap()})")
		}
		round.clicked(slot)
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		val screen = screenRef ?: return
		if (solver == Solver.NONE || Client.screen !== screen) return
		val slots = chestSlots(screen)
		val slot = slots.indexOf(event.slot)
		if (slot < 0) return
		val paint = when (solver) {
			Solver.ULTRASEQUENCER -> round.paint(slots.map { cellOf(it.item) }, slot, TConfig.showNext, TConfig.sequencerNumbers)
			Solver.SUPERPAIRS -> pairs.paint(cellOf(event.slot.item), slot, TConfig.superpairsReveal)
			Solver.NONE -> null
		} ?: return
		val ctx = event.context
		val x = event.slot.x
		val y = event.slot.y
		paint.box?.let { ctx.fill(x, y, x + 16, y + 16, it) }
		if (paint.reveal) pairs.seen[slot]?.second?.let { ctx.item(it, x, y) }
		paint.label?.let { ctx.centeredText(Client.font, it, x + 8, y + 4, 0xFFC0C0C0.toInt()) }
	}
}
