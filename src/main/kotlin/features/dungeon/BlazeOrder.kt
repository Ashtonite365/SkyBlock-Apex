/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: pure logic for the Dungeons Blaze puzzle ("Higher Or Lower"), rebuilt from scratch.
 * No Minecraft types here so every rule is unit-testable; BlazeSolver.kt feeds it entity snapshots and renders.
 *
 * Direction signal (Hypixel wiki, Catacombs Puzzles / Tutorial:Dungeon Puzzles): the reward chest (a chest with
 * iron bars on top) STARTS at the bottom of the room => kill lowest HP to highest; it STARTS at the top => kill
 * highest HP to lowest. The chest then moves along its iron-bar chain towards the other end, about
 * [CHEST_STEP] blocks per kill, so it is only read while at most [MAX_KILLS_FOR_CHEST] blazes are confirmed dead
 * (it cannot have crossed the middle of the blazes yet). Ideas credited in CREDITS.md; no code copied.
 */
package dev.ashtonite365.apex.features.dungeon

object BlazeOrder {
	enum class Direction { LOWEST_FIRST, HIGHEST_FIRST }

	data class Blaze(val id: Int, val hp: Long, val maxHp: Long?, val x: Double, val y: Double, val z: Double)

	private val nametag = Regex("""Blaze\b.*?([\d][\d,.]*[kKmMbB]?)(?:\s*/\s*([\d][\d,.]*[kKmMbB]?))?\s*❤""")

	fun parseNametag(name: String): Pair<Long, Long?>? {
		val clean = name.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")
		val m = nametag.find(clean) ?: return null
		val hp = parseAmount(m.groupValues[1]) ?: return null
		val maxText = m.groupValues[2]
		val max = if (maxText.isEmpty()) null else parseAmount(maxText) ?: return null
		return hp to max
	}

	fun parseAmount(s: String): Long? {
		val mult = when (s.lastOrNull()?.lowercaseChar()) { 'k' -> 1_000L; 'm' -> 1_000_000L; 'b' -> 1_000_000_000L; else -> 1L }
		val digits = (if (mult == 1L) s else s.dropLast(1)).replace(",", "")
		val v = digits.toBigDecimalOrNull() ?: return null
		return (v * mult.toBigDecimal()).toLong()
	}

	data class Tag(val id: Int, val hp: Long, val maxHp: Long?, val x: Double, val y: Double, val z: Double)

	data class Body(val id: Int, val x: Double, val y: Double, val z: Double, val height: Double = 1.8)

	const val PAIR_HORIZONTAL = 1.5

	const val PAIR_BELOW_TOP = 2.5
	const val PAIR_ABOVE_TOP = 2.0

	private fun distSq(t: Tag, b: Body): Double? {
		val dx = t.x - b.x; val dz = t.z - b.z
		if (dx * dx + dz * dz > PAIR_HORIZONTAL * PAIR_HORIZONTAL) return null
		val dy = t.y - (b.y + b.height)
		if (dy < -PAIR_BELOW_TOP || dy > PAIR_ABOVE_TOP) return null
		return dx * dx + dy * dy + dz * dz
	}

	fun pair(tags: List<Tag>, bodies: List<Body>, owners: Map<Int, Int> = emptyMap()): List<Pair<Tag, Body>> {
		val result = mutableListOf<Pair<Tag, Body>>()
		val usedTags = mutableSetOf<Int>(); val usedBodies = mutableSetOf<Int>()
		fun greedy(tagSet: List<Tag>, allowed: (Tag, Body) -> Boolean) {
			val cands = tagSet.flatMap { t -> bodies.mapNotNull { b -> if (allowed(t, b)) distSq(t, b)?.let { Triple(t, b, it) } else null } }
				.sortedWith(compareBy<Triple<Tag, Body, Double>>({ it.third }, { it.first.id }, { it.second.id }))
			for ((t, b, _) in cands) {
				if (t.id in usedTags || b.id in usedBodies) continue
				usedTags += t.id; usedBodies += b.id; result += t to b
			}
		}
		greedy(tags.filter { it.hp > 0 }) { _, _ -> true }
		val named = owners.values.toSet()
		greedy(tags.filter { it.hp <= 0 }) { t, b ->
			val owner = owners[t.id]
			if (owner != null) owner == b.id else b.id !in named
		}
		return result
	}

	const val FULL_COUNT = 10

	const val CHEST_STEP = 5

	const val MAX_KILLS_FOR_CHEST = 2

	fun killsSoFar(killed: Int, aliveInRoom: Int) = maxOf(killed, FULL_COUNT - aliveInRoom.coerceAtMost(FULL_COUNT))

	fun chestReadable(killed: Int, aliveInRoom: Int) = killsSoFar(killed, aliveInRoom) <= MAX_KILLS_FOR_CHEST

	fun detectDirection(chestYs: List<Int>, blazeYs: List<Double>, killed: Int = 0, aliveInRoom: Int = FULL_COUNT): Direction? {
		if (!chestReadable(killed, aliveInRoom)) return null
		if (chestYs.isEmpty() || blazeYs.isEmpty()) return null
		val mid = (blazeYs.min() + blazeYs.max()) / 2
		val above = chestYs.count { it > mid }
		val below = chestYs.count { it < mid }
		return when {
			below > 0 && above == 0 -> Direction.LOWEST_FIRST
			above > 0 && below == 0 -> Direction.HIGHEST_FIRST
			else -> null
		}
	}

	fun label(direction: Direction) = when (direction) {
		Direction.LOWEST_FIRST -> "Lowest -> highest"
		Direction.HIGHEST_FIRST -> "Highest -> lowest"
	}

	private fun useMax(blazes: Collection<Blaze>) = blazes.all { it.maxHp != null }
	private fun key(b: Blaze, max: Boolean): Long = if (max) b.maxHp ?: b.hp else b.hp

	fun order(blazes: Collection<Blaze>, direction: Direction): List<Blaze> {
		val m = useMax(blazes)
		val hp = compareBy<Blaze>({ key(it, m) }, { it.hp })
		val primary = if (direction == Direction.LOWEST_FIRST) hp else hp.reversed()
		return blazes.sortedWith(primary.thenBy { it.y }.thenBy { it.x }.thenBy { it.z }.thenBy { it.id })
	}

	fun hasTie(blazes: Collection<Blaze>): Boolean {
		val m = useMax(blazes)
		return blazes.groupBy { key(it, m) }.any { it.value.size > 1 }
	}

	fun tiedWith(target: Blaze, alive: Collection<Blaze>, direction: Direction): List<Blaze> {
		val m = useMax(alive)
		val k = key(target, m)
		return order(alive.filter { key(it, m) == k }, direction)
	}

	fun isFailMessage(line: String) = PuzzleFailMarks.parseChat(line)?.puzzle == PuzzleFailMarks.Puzzle.BLAZE

	enum class Status { IDLE, FAILED, DONE, FINDING_DIRECTION, SOLVING }

	data class Plan(
		val status: Status,
		val boxes: List<Int> = emptyList(),
		val next: Int? = null,
		val tie: Boolean = false,
		val targetOutOfView: Boolean = false,
	)

	class Tracker {
		val known = linkedMapOf<Int, Blaze>()
		val dead = linkedSetOf<Int>()

		val tagOwners = mutableMapOf<Int, Int>()
		private val visible = mutableSetOf<Int>()
		var direction: Direction? = null

		var failed = false
		private var doneAnnounced = false

		fun reset() {
			known.clear(); dead.clear(); tagOwners.clear(); visible.clear(); direction = null; failed = false; doneAnnounced = false
		}

		fun observe(seen: List<Blaze>, died: Set<Int> = emptySet(), gone: Set<Int> = emptySet(), playerNear: Boolean = true) {
			dead += died
			if (playerNear) dead += gone.filter { it in known }
			visible.clear()
			for (b in seen) {
				if (b.id in dead) continue
				if (b.hp <= 0) { dead += b.id; continue }
				known[b.id] = b; visible += b.id
			}
		}

		fun onChat(line: String): Boolean {
			if (!isFailMessage(line)) return false
			failed = true; return true
		}

		fun alive(): List<Blaze> = known.values.filter { it.id !in dead }

		fun orderedVisible(): List<Blaze> = direction?.let { d -> order(alive().filter { it.id in visible }, d) } ?: emptyList()

		fun orderedAlive(): List<Blaze> = direction?.let { order(alive(), it) } ?: emptyList()
		fun current(): Blaze? = orderedAlive().getOrNull(0)
		fun next(): Blaze? = orderedAlive().getOrNull(1)

		fun tiedTargets(): List<Blaze> {
			val d = direction ?: return emptyList()
			val c = current() ?: return emptyList()
			return tiedWith(c, alive(), d)
		}
		fun isDone() = known.isNotEmpty() && dead.isNotEmpty() && alive().isEmpty()
		fun isVisible(id: Int) = id in visible

		fun takeDoneAnnouncement(): Boolean {
			if (failed || !isDone() || doneAnnounced) return false
			doneAnnounced = true; return true
		}

		fun plan(drawable: (Int) -> Boolean): Plan {
			if (known.isEmpty()) return Plan(if (failed) Status.FAILED else Status.IDLE)
			if (failed) return Plan(Status.FAILED)
			if (isDone()) return Plan(Status.DONE)
			if (direction == null) return Plan(Status.FINDING_DIRECTION)
			val tied = tiedTargets()
			if (tied.size > 1) {
				val drawn = tied.map { it.id }.filter(drawable)
				return Plan(Status.SOLVING, boxes = drawn, tie = true, targetOutOfView = drawn.size < tied.size)
			}
			val cur = current() ?: return Plan(Status.DONE)
			if (!drawable(cur.id)) return Plan(Status.SOLVING, targetOutOfView = true)
			val nxt = next()?.id?.takeIf(drawable)
			return Plan(Status.SOLVING, boxes = listOf(cur.id), next = nxt)
		}
	}

	data class Lines(val target: Int?, val next: Int?)

	fun lines(plan: Plan, targetLine: Boolean, nextLine: Boolean): Lines {
		if (plan.status != Status.SOLVING || plan.tie || plan.boxes.size != 1) return Lines(null, null)
		val target = plan.boxes.single()
		val next = plan.next?.takeIf { nextLine && it != target }
		return Lines(target.takeIf { targetLine || next != null }, next)
	}

	const val BLOCKED_DISTANCE = 1.0

	fun segmentDistance(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, px: Double, py: Double, pz: Double): Double {
		val dx = bx - ax; val dy = by - ay; val dz = bz - az
		val len2 = dx * dx + dy * dy + dz * dz
		val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / len2).coerceIn(0.0, 1.0)
		val cx = ax + t * dx - px; val cy = ay + t * dy - py; val cz = az + t * dz - pz
		return Math.sqrt(cx * cx + cy * cy + cz * cz)
	}

	fun blocked(a: DoubleArray, b: DoubleArray, others: List<DoubleArray>): Boolean =
		others.any { o -> segmentDistance(a[0], a[1], a[2], b[0], b[1], b[2], o[0], o[1], o[2]) < BLOCKED_DISTANCE }

	fun centreOf(blazes: Collection<Blaze>): Triple<Double, Double, Double>? {
		if (blazes.isEmpty()) return null
		return Triple(blazes.map { it.x }.average(), blazes.map { it.y }.average(), blazes.map { it.z }.average())
	}
}
