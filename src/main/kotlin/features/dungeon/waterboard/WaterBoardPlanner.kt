/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Water Board planner and step-by-step guide. Written from scratch; pure.
 *
 * How it plans: every combination of the six gate levers (64) is flowed once on the [WaterGrid] with the water on, from a dry board,
 * giving the set of doors that combination's water reaches ([FlowAtlas]). A "pulse" is: set the levers, water on until
 * its doors are reached, water off. The plan is the cheapest list of pulses (fewest lever flips, then fewest pulses)
 * that reaches every remaining door, where no pulse may touch a door that is already done (or not wanted). Nothing is
 * stored per layout: the board is read live and everything is recomputed whenever it changes.
 *
 * The guide ([WaterBoardGuide]) turns the plan plus the live lever/water state into one instruction at a time:
 * flip these levers (in walking order), turn the water ON, keep it on until the door is reached, turn it OFF.
 * No timers: every instruction is driven by what is seen in the world.
 */
package dev.ashtonite365.apex.features.dungeon.waterboard

import java.util.PriorityQueue

class FlowAtlas(val board: Board) {
	private val results = arrayOfNulls<WaterGrid>(64)
	private val doorMasks = IntArray(64) { -1 }

	fun flow(config: Int): WaterGrid {
		val c = canonical(config)
		return results[c] ?: board.grid(c, waterOn = true).flow().also { results[c] = it }
	}

	fun doors(config: Int): Int {
		val c = canonical(config)
		if (doorMasks[c] < 0) doorMasks[c] = board.doorsWet(flow(c))
		return doorMasks[c]
	}

	fun canonical(config: Int) = config and board.usedGates and Gate.ALL
}

object WaterBoardPlanner {
	data class Pulse(val config: Int, val doors: Int)
	data class Plan(val pulses: List<Pulse>, val flips: Int)

	private const val FLIP_COST = 4
	private const val PULSE_COST = 1

	fun plan(atlas: FlowAtlas, current: Int, remaining: Int): Plan? {
		if (remaining == 0) return Plan(emptyList(), 0)
		val used = atlas.board.usedGates
		val configs = (0..Gate.ALL).filter { it and used.inv() == 0 }
		val cur = current and used
		val doors = configs.associateWith { atlas.doors(it) }

		val dist = HashMap<Int, Int>()
		val prev = HashMap<Int, Int>()
		val queue = PriorityQueue<IntArray>(compareBy { it[0] })
		val start = remaining * 64 + cur
		dist[start] = 0
		queue += intArrayOf(0, start)
		var goal = -1
		while (queue.isNotEmpty()) {
			val (d, s) = queue.poll().let { it[0] to it[1] }
			if (d > (dist[s] ?: Int.MAX_VALUE)) continue
			val rem = s / 64
			val cfg = s % 64
			if (rem == 0) { goal = s; break }
			for (c in configs) {
				val reach = doors[c] ?: 0
				if (reach == 0 || reach and rem.inv() != 0) continue
				val ns = (rem and reach.inv()) * 64 + c
				val nd = d + Integer.bitCount(c xor cfg) * FLIP_COST + PULSE_COST
				if (nd < (dist[ns] ?: Int.MAX_VALUE)) { dist[ns] = nd; prev[ns] = s; queue += intArrayOf(nd, ns) }
			}
		}
		if (goal < 0) return null
		val pulses = mutableListOf<Pulse>()
		var s = goal
		while (s != start) {
			val p = prev[s] ?: break
			pulses += Pulse(s % 64, (p / 64) and (s / 64).inv())
			s = p
		}
		pulses.reverse()
		var flips = 0
		var c = cur
		for (p in pulses) { flips += Integer.bitCount(p.config xor c); c = p.config }
		return Plan(pulses, flips)
	}

	fun unreachable(atlas: FlowAtlas, remaining: Int): Int {
		val used = atlas.board.usedGates
		var ok = 0
		for (c in 0..Gate.ALL) if (c and used.inv() == 0) {
			val r = atlas.doors(c)
			if (r != 0 && r and remaining.inv() == 0) ok = ok or r
		}
		return remaining and ok.inv()
	}

	fun quietConfig(atlas: FlowAtlas, current: Int): Int? {
		val used = atlas.board.usedGates
		return (0..Gate.ALL).filter { it and used.inv() == 0 && atlas.doors(it) == 0 }
			.minByOrNull { Integer.bitCount(it xor (current and used)) }
	}

	fun walkOrder(gates: List<Gate>, from: Triple<Double, Double, Double>?, levers: Map<Gate, Triple<Double, Double, Double>>): List<Gate> {
		if (from == null) return gates
		val left = gates.filter { it in levers }.toMutableList()
		val out = mutableListOf<Gate>()
		var at = from
		while (left.isNotEmpty()) {
			val next = left.minWith(compareBy<Gate> { dist2(at!!, levers[it]!!) }.thenBy { it.ordinal })
			out += next; left -= next; at = levers[next]
		}
		return out + gates.filter { it !in levers }
	}

	private fun dist2(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
		val dx = a.first - b.first; val dy = a.second - b.second; val dz = a.third - b.third
		return dx * dx + dy * dy + dz * dz
	}
}

sealed interface WaterStep {

	data class Flip(val order: List<Gate>, val target: Int, val chest: Boolean = false) : WaterStep

	data class WaterOn(val target: Int, val chest: Boolean = false) : WaterStep

	data class KeepOn(val waiting: Int) : WaterStep
	data class WaterOff(val reason: OffReason) : WaterStep

	data class WaitDrain(val target: Int) : WaterStep
	data object Done : WaterStep
	data class Stuck(val unreachable: Int) : WaterStep

	data object Reading : WaterStep
}

enum class OffReason { TARGET_REACHED, WRONG_LEVERS, LEVERS_CHANGED, CHEST_PULSE, ALL_DONE }

data class WaterObservation(
	val config: Int?,
	val waterOn: Boolean,
	val remaining: Int,
	val touching: Int,
	val wetNow: IntArray? = null,
)

class WaterBoardGuide(val atlas: FlowAtlas, var chestPulse: Boolean = true) {
	private data class ActivePulse(val config: Int, val target: Int, val chest: Boolean, var reached: Int = 0)

	private var pulse: ActivePulse? = null
	private var wasOn = false
	private var remainingAtOn = 0
	var chestDone = false
		private set
	var lastPlan: WaterBoardPlanner.Plan? = null
		private set

	fun update(
		obs: WaterObservation,
		player: Triple<Double, Double, Double>? = null,
		levers: Map<Gate, Triple<Double, Double, Double>> = emptyMap(),
	): WaterStep {
		val config = obs.config ?: return WaterStep.Reading
		val cfg = atlas.canonical(config)
		val remaining = obs.remaining and atlas.board.doors

		if (obs.waterOn && !wasOn) {
			val reach = atlas.doors(cfg)
			pulse = ActivePulse(cfg, reach and remaining, chest = remaining == 0 && reach == 0)
			remainingAtOn = remaining
		}
		if (!obs.waterOn && wasOn) {
			if (pulse?.chest == true) chestDone = true
			pulse = null
		}
		wasOn = obs.waterOn

		if (obs.waterOn) {
			val p = pulse ?: return WaterStep.WaterOff(OffReason.WRONG_LEVERS)
			if (p.config != cfg) return WaterStep.WaterOff(OffReason.LEVERS_CHANGED)
			if (p.chest) return WaterStep.WaterOff(OffReason.CHEST_PULSE)
			val reach = atlas.doors(cfg)

			if (p.target == 0 || reach and remainingAtOn.inv() != 0) return WaterStep.WaterOff(if (remaining == 0) OffReason.ALL_DONE else OffReason.WRONG_LEVERS)
			p.reached = p.reached or (obs.touching and p.target) or (p.target and remaining.inv())
			return if (p.reached and p.target == p.target) WaterStep.WaterOff(OffReason.TARGET_REACHED)
			else WaterStep.KeepOn(p.target and p.reached.inv())
		}

		if (remaining == 0) {
			if (!chestPulse || chestDone) { lastPlan = null; return WaterStep.Done }
			val quiet = WaterBoardPlanner.quietConfig(atlas, cfg) ?: return WaterStep.Done
			val flips = quiet xor cfg
			return if (flips == 0) WaterStep.WaterOn(0, chest = true)
			else WaterStep.Flip(WaterBoardPlanner.walkOrder(Gate.of(flips), player, levers), 0, chest = true)
		}
		val plan = WaterBoardPlanner.plan(atlas, cfg, remaining)
		lastPlan = plan
		if (plan == null) return WaterStep.Stuck(WaterBoardPlanner.unreachable(atlas, remaining).takeIf { it != 0 } ?: remaining)
		val first = plan.pulses.first()
		val flips = first.config xor cfg
		if (flips != 0) return WaterStep.Flip(WaterBoardPlanner.walkOrder(Gate.of(flips), player, levers), first.doors)
		if (obs.wetNow != null && !drainOk(cfg, obs.wetNow, remaining)) return WaterStep.WaitDrain(first.doors)
		return WaterStep.WaterOn(first.doors)
	}

	fun drainOk(config: Int, wetNow: IntArray, remaining: Int): Boolean {
		if (wetNow.none { it > 0 }) return true
		val b = atlas.board
		val touchingNow = b.doorTriggers.entries.fold(0) { m, (d, cells) -> if (cells.any { wetNow[it] > 0 }) m or d.bit else m }

		val reached = b.doorsWet(b.grid(config, waterOn = true) { wetNow[it] > 0 }.flow())
		val newly = reached and touchingNow.inv()
		return newly and remaining.inv() == 0
	}

	fun reset() { pulse = null; wasOn = false; chestDone = false; lastPlan = null }
}
