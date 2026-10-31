// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.devices

import net.minecraft.core.BlockPos

class SimonSaysState {

	val order = ArrayList<BlockPos>()

	var clickNeeded = 0
		private set

	var firstPhase = true

	var ticksSinceLantern = -1

	fun reset() {
		order.clear()
		clickNeeded = 0
		ticksSinceLantern = -1
	}

	fun onLanternShown(pos: BlockPos) {
		if (pos in order) return
		order += pos
		ticksSinceLantern = 0
		if (firstPhase) fixOpeningRound()
	}

	private fun fixOpeningRound() {
		when (order.size) {
			2 -> order.add(0, order.removeAt(1))
			3 -> order.removeAt(1)
		}
	}

	fun onButtonPressed(buttonPos: BlockPos) {
		clickNeeded = order.indexOf(lanternFor(buttonPos)) + 1
		if (clickNeeded >= order.size) {
			reset()
			firstPhase = false
		}
	}

	fun onStartPressed() {
		reset()
		firstPhase = true
	}

	fun onTick(buttonsVisible: Int) {
		if (!firstPhase || ticksSinceLantern < 0) return
		val waited = ticksSinceLantern++
		if (waited > 10 && buttonsVisible > 8) firstPhase = false
	}

	fun remaining(): List<BlockPos> = order.drop(clickNeeded).map { buttonFor(it) }

	fun isCorrectClick(buttonPos: BlockPos): Boolean = order.getOrNull(clickNeeded) == lanternFor(buttonPos)

	fun roundFinishedBy(buttonPos: BlockPos): Int? =
		order.size.takeIf { order.isNotEmpty() && isCorrectClick(buttonPos) && lanternFor(buttonPos) == order.last() }

	companion object {
		val START_BUTTON = BlockPos(110, 121, 91)
		val CENTER = BlockPos(110, 121, 93)

		fun inGrid(pos: BlockPos, x: Int) = pos.x == x && pos.y in 120..123 && pos.z in 92..95

		private fun lanternFor(button: BlockPos): BlockPos = button.east()
		private fun buttonFor(lantern: BlockPos): BlockPos = lantern.west()
	}
}

object ArrowAlignLogic {
	val GRID_CORNER = BlockPos(-2, 120, 75)
	val CENTER = BlockPos(0, 120, 77)
	private const val SIDE = 5

	fun indexOf(pos: BlockPos): Int? {
		if (pos.x != GRID_CORNER.x) return null
		val row = pos.y - GRID_CORNER.y
		val column = pos.z - GRID_CORNER.z
		return if (row in 0 until SIDE && column in 0 until SIDE) row + SIDE * column else null
	}

	fun posOf(index: Int): BlockPos = BlockPos(GRID_CORNER.x, GRID_CORNER.y + index % SIDE, GRID_CORNER.z + index / SIDE)

	fun shouldBlock(index: Int?, rotations: Map<Int, Int>, solved: Boolean, sneaking: Boolean): Boolean =
		index != null && solved && !sneaking && index !in rotations

	fun clicksNeeded(current: Int, target: Int) = (target - current + 8) % 8

	private fun samePattern(layout: List<Int>, wall: List<Int>) = layout.indices.all { (layout[it] == NO_FRAME) == (wall[it] == NO_FRAME) }

	fun solutionFor(rotations: List<Int>): List<Int>? = SOLUTIONS.lastOrNull { samePattern(it, rotations) }

	fun clicks(rotations: List<Int>): Map<Int, Int> {
		val layout = solutionFor(rotations) ?: return emptyMap()
		val out = LinkedHashMap<Int, Int>()
		layout.forEachIndexed { slot, target ->
			if (target != NO_FRAME) clicksNeeded(rotations[slot], target).takeIf { it != 0 }?.let { out[slot] = it }
		}
		return out
	}

	const val NO_FRAME = -1

	private val LAYOUTS = listOf(
		"77...1....13333....1...71",
		"..775.71.5......75.1..771",
		"77...1....13.75....5...33",
		"5333.5....77...1....1333.",
		"533335...177..1....1.7771",
		"7777.1....13333....1.7771",
		".....1.1.11.1.11.1.1.....",
		".....13333....177771.....",
		"......1.1.717131.1.1.....",
	)

	val SOLUTIONS: List<List<Int>> = LAYOUTS.map { row -> row.map { if (it == '.') NO_FRAME else it.digitToInt() } }
}

class ArrowAlignState {
	val rotations = MutableList(25) { ArrowAlignLogic.NO_FRAME }
	private val heldUntil = LongArray(25) { Long.MIN_VALUE }

	fun update(server: List<Int>, now: Long) {
		for (slot in rotations.indices) {
			if (rotations[slot] == ArrowAlignLogic.NO_FRAME || now >= heldUntil[slot]) rotations[slot] = server[slot]
		}
	}

	fun clicks(): Map<Int, Int> = ArrowAlignLogic.clicks(rotations)

	fun shouldBlock(index: Int?, sneaking: Boolean): Boolean = when {
		index == null || sneaking -> false
		rotations[index] == ArrowAlignLogic.NO_FRAME -> false
		ArrowAlignLogic.solutionFor(rotations) == null -> false
		else -> index !in clicks()
	}

	fun onClick(index: Int, now: Long) {
		if (rotations[index] == ArrowAlignLogic.NO_FRAME) return
		rotations[index] = (rotations[index] + 1) % 8
		heldUntil[index] = now + HOLD_MS
	}

	fun reset() {
		rotations.fill(ArrowAlignLogic.NO_FRAME)
		heldUntil.fill(Long.MIN_VALUE)
	}

	companion object {
		const val HOLD_MS = 1000L
	}
}
