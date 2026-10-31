/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): Fossil Excavator solver.
 * Fossil shapes, tile counts and progress percentages are data
 * (de/hysky/skyblocker/skyblock/dwarven/fossil/FossilTypes.java, LGPL-3.0); the slot meaning
 * (white pane = fossil, brown pane = undug) follows the FossilCalculations.java.
 * The solver itself (placement enumeration and per-tile probability) is an original implementation.
 */
package dev.ashtonite365.apex.features.mining

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.bareText

object FossilExcavator {
	@Config
	object TConfig : ManagedConfig("fossil-excavator", Category.MINING) {
		val solver by riskyToggle("solver", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
	}

	const val W = 9
	const val H = 6

	enum class Fossil(val percent: String, rows: String) {
		CLAW("7.7", ".#....|####..|.##.#.|.#.#.#|..#.#."),
		TUSK("12.5", "....#|.#..#|#...#|.#.#.|..#.."),
		UGLY("6.2", "..##..|.####.|######|.####."),
		HELIX("7.1", "#####|#...#|#.#.#|#.###"),
		WEBBED("10", "...#...|#..#..#|.#.#.#.|..###.."),
		FOOTPRINT("7.7", "#.#.#|#.#.#|.###.|.###.|..#.."),
		CLUBBED("9.1", "......##|.#....##|#....#..|.####..."),
		SPINE("8.3", "..##..|.####.|######");

		val base: Set<Pair<Int, Int>> = rows.split('|').flatMapIndexed { y, r -> r.mapIndexedNotNull { x, c -> if (c == '#') x to y else null } }.toSet()

		val shapes: List<Set<Pair<Int, Int>>> by lazy {
			val out = mutableSetOf<Set<Pair<Int, Int>>>()
			var s = base
			repeat(4) {
				s = s.map { (x, y) -> y to -x }.toSet()
				out += norm(s); out += norm(s.map { (x, y) -> -x to y }.toSet())
			}
			out.toList()
		}
	}

	private fun norm(s: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
		val mx = s.minOf { it.first }; val my = s.minOf { it.second }
		return s.map { (x, y) -> x - mx to y - my }.toSet()
	}

	enum class Tile { FOSSIL, UNKNOWN, EMPTY }

	data class Result(val placements: Int, val probability: DoubleArray, val names: Set<Fossil>)

	fun solve(grid: List<Tile>, percent: String? = null): Result {
		val counts = IntArray(W * H)
		var total = 0
		val names = mutableSetOf<Fossil>()
		val fossils = grid.indices.filter { grid[it] == Tile.FOSSIL }
		for (f in Fossil.entries) {
			if (percent != null && f.percent != percent) continue
			for (shape in f.shapes) {
				val sw = shape.maxOf { it.first } + 1; val sh = shape.maxOf { it.second } + 1
				for (oy in 0..H - sh) for (ox in 0..W - sw) {
					val cells = shape.map { (x, y) -> (y + oy) * W + x + ox }
					if (cells.any { grid[it] == Tile.EMPTY }) continue
					val set = cells.toSet()
					if (fossils.any { it !in set }) continue
					total++; names += f
					for (c in cells) counts[c]++
				}
			}
		}
		return Result(total, DoubleArray(W * H) { if (total == 0 || grid[it] != Tile.UNKNOWN) 0.0 else counts[it].toDouble() / total }, names)
	}

	private val PROGRESS = Regex("Fossil Excavation Progress: (\\d{1,2}(?:\\.\\d)?)%")

	private fun inExcavator() = Client.screenTitle?.startsWith("Fossil Excavator") == true
	private var cacheKey: List<Tile>? = null
	private var cache: Result? = null

	private fun current(): Result? {
		val screen = Client.containerScreen ?: return null
		val slots = screen.menu.slots.filter { it.container !is Inventory }.take(W * H)
		if (slots.size < W * H) return null
		val grid = slots.map {
			when {
				it.item.`is`(Items.STAINED_GLASS_PANE.white) -> Tile.FOSSIL
				it.item.`is`(Items.STAINED_GLASS_PANE.brown) -> Tile.UNKNOWN
				else -> Tile.EMPTY
			}
		}
		if (grid == cacheKey) return cache
		val percent = slots.firstNotNullOfOrNull { s ->
			s.item.view().loreAccordingToNbt.firstNotNullOfOrNull { PROGRESS.find(it.bareText)?.groupValues?.get(1) }
		}?.let { if (it.endsWith(".0")) it.dropLast(2) else it }
		cacheKey = grid; cache = solve(grid, percent)
		return cache
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (!TConfig.solver || !inExcavator() || event.slot.container is Inventory) return
		val r = current() ?: return
		val i = event.slot.index
		if (i >= W * H) return
		val p = r.probability[i]
		if (p <= 0) return
		val best = r.probability.max()
		val colour = if (p == best) 0x8000FF00.toInt() else ((p * 0x90).toInt() shl 24) or 0xFFFF00
		event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, colour)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.solver || !inExcavator()) return
		val r = current() ?: return
		event.lines.add(Component.literal("§7Possible placements: §e${r.placements}"))
		if (r.names.size in 1..3) event.lines.add(Component.literal("§7Fossil: §e${r.names.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }}"))
	}
}
