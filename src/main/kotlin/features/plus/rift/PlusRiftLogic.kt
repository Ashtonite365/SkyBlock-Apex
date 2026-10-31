// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import kotlin.math.sqrt

class MotesOrbTracker {
	class Orb(var x: Double, var y: Double, var z: Double, val start: Long) {
		var last = start
		var count = 0
		var isOrb = false
		var pickedUp = false
	}

	val orbs = mutableListOf<Orb>()

	private fun distance(o: Orb, x: Double, y: Double, z: Double): Double {
		val dx = o.x - x
		val dy = o.y - y
		val dz = o.z - z
		return sqrt(dx * dx + dy * dy + dz * dz)
	}

	fun particle(x: Double, y: Double, z: Double, now: Long) {
		var orb: Orb? = null
		for (o in orbs) if (!o.pickedUp && distance(o, x, y, z) < 3) { orb = o; break }
		val o = orb ?: Orb(x, y, z, now).also { orbs.add(it) }
		o.x = x
		o.y = y
		o.z = z
		o.last = now
		o.count += 1
		val age = (now - o.start) / 1000.0
		if (age < 0.5) return
		val perSecond = o.count / age
		o.isOrb = (perSecond >= 60.0 && perSecond <= 90.0) || (o.isOrb && perSecond > 30)
	}

	fun update(now: Long) {
		orbs.forEach { if (now - it.last > 300) it.pickedUp = true }
		orbs.removeAll { now - it.last > 5000 }
	}

	fun pickedUpNear(x: Double, y: Double, z: Double) {
		for (o in orbs) if (distance(o, x, y, z) < 5) o.pickedUp = true
	}

	fun visible() = orbs.filter { it.isOrb && !it.pickedUp }

	companion object {
		val pickupChat = Regex("ORB! Picked up \\+(?<motes>[\\d,]+) Motes.*")
	}
}

object RiftRules {

	fun agaricusLabel(block: String, elapsedMs: Long?): String? {
		if (block == "red_mushroom") return "§cClick!"
		if (block != "brown_mushroom" || elapsedMs == null) return null
		return "§e%.1fs".format(elapsedMs / 1000.0)
	}

	enum class SkullTarget { WORN, HELD }

	fun skullTarget(heldId: String?): SkullTarget? = when (heldId) {
		"LARVA_HOOK" -> SkullTarget.WORN
		"EMPTY_ODONATA_BOTTLE" -> SkullTarget.HELD
		else -> null
	}

	enum class Terminal(val x: Double, val y: Double, val z: Double) {
		RED(-69.0, 65.0, -63.0), ORANGE(-44.0, 71.0, -62.0), YELLOW(-39.0, 71.0, -95.0), GREEN(-62.0, 71.0, -83.0),
		AQUA(-33.0, 70.0, -134.5), BLUE(-66.5, 72.0, -119.0), PURPLE(-89.0, 73.0, -115.0), PINK(-110.0, 73.0, -107.0);

		val label get() = name.substring(0, 1) + name.substring(1).lowercase()
	}

	fun nearestTerminal(x: Double, y: Double, z: Double, max: Double = 5.0): Terminal? {
		var best: Terminal? = null
		var bestDist = 0.0
		for (t in Terminal.entries) {
			val d = sqrt((t.x - x) * (t.x - x) + (t.y - y) * (t.y - y) + (t.z - z) * (t.z - z))
			if (!(d <= max)) continue
			if (best == null || d.compareTo(bestDist) < 0) { best = t; bestDist = d }
		}
		return best
	}

	fun isHackingInv(title: String) = title == "Hacking" || title == "Hacking (As seen on CSI)"
	val colourSetChat = Regex("You've set the color of this terminal to (?<color>.*)!")

	fun hackingColour(slot: Int, names: (Int) -> String?): Int {
		if (slot !in 9..53) return 0
		val row = slot / 9 - 1
		if (row < 0 || row > 4) return 0
		val target = names(2 + row) ?: return 0
		val answer = 11 + 10 * row
		if (slot == answer) return if (names(slot) == target) 1 else 2
		val sameRow = slot / 9 == answer / 9
		val col = slot % 9
		return if (sameRow && col >= 2 && col <= 8 && names(slot) == target) 3 else 0
	}

	val punched = Regex("PUNCHCARD! You punched (?:\\[[^]]+] )?(?<name>\\w+) and both regained .*")
	val awkward = Regex("AWKWARD! This player has already been punched by you\\.\\.\\. somehow!")
	val punchLimit = Regex("UH OH! You reached the limit of 20 players you can punch in one session!")

	fun isRiftGuide(slot40Lore: List<String>) = slot40Lore.size == 1 && slot40Lore[0].startsWith("To Rift Guide")

	fun guideNotCompleted(lore: List<String>): Boolean = lore.isNotEmpty() && lore.last().trim() == "✖ Not completed yet!"

	private val cruxLine = Regex(".*?(?<tier>[IV1-4-]+)\\s+(?<name>\\w+):\\s*(?<progress>MAXED|\\d+/\\d+).*")

	data class CruxLine(val tier: String, val name: String, val done: Int, val total: Int?)

	fun cruxProgress(lore: List<String>): List<CruxLine> {
		val out = ArrayList<CruxLine>()
		for (line in lore) {
			val m = cruxLine.matchEntire(line) ?: continue
			val tier = m.groups["tier"]!!.value
			val name = m.groups["name"]!!.value
			val progress = m.groups["progress"]!!.value
			if (progress == "MAXED") {
				out.add(CruxLine(tier, name, 1, null))
				continue
			}
			val slash = progress.indexOf('/')
			out.add(CruxLine(tier, name, progress.substring(0, slash).toInt(), progress.substring(slash + 1).toInt()))
		}
		return out
	}

	fun cruxPercent(lines: List<CruxLine>): Int? {
		if (lines.isEmpty()) return null
		var sum = 0.0
		for (l in lines) sum += l.total?.let { l.done.toDouble() / it } ?: 1.0
		return (sum / lines.size * 100).toInt()
	}

	fun progressBar(fraction: Double, len: Int = 20): String {
		val clamped = if (fraction < 0.0) 0.0 else if (fraction > 1.0) 1.0 else fraction
		val green = (clamped * len).toInt()
		val greenPart = "|".repeat(green)
		return "§a" + greenPart + "§7" + "|".repeat(len - green) + "§f"
	}

	val ubikCooldown = Regex("SPLIT! You need to wait (?<duration>.+) before you can play again\\.")
	private val durationPart = Regex("(\\d+)\\s*([dhms])")

	fun parseDuration(s: String): Long? {
		var seconds: Long? = null
		for (part in durationPart.findAll(s)) {
			val n = part.groupValues[1].toLong()
			val unit = when (part.groupValues[2]) {
				"d" -> 86400
				"h" -> 3600
				"m" -> 60
				else -> 1
			}
			seconds = (seconds ?: 0L) + n * unit
		}
		return seconds?.let { it * 1000 }
	}

	private val geckoComboText = Regex(".*\\[.*] x(?<combo>\\d+) .*]")

	fun geckoCombo(actionBar: String): Int? {
		val m = geckoComboText.matchEntire(actionBar) ?: return null
		return m.groups["combo"]?.value?.toInt()
	}
}

class SnakeTracker {
	data class P(val x: Int, val y: Int, val z: Int)
	class Snake(val blocks: MutableList<P>, var lastAdd: Long)

	val snakes = mutableListOf<Snake>()

	private fun distSq(a: P, b: P): Int {
		val dx = a.x - b.x
		val dy = a.y - b.y
		val dz = a.z - b.z
		return dx * dx + dy * dy + dz * dz
	}

	fun added(p: P, now: Long) {
		val snake = snakes.firstOrNull { s -> s.blocks.isNotEmpty() && distSq(s.blocks[0], p) <= 3 }
		when {
			snake == null -> snakes.add(Snake(mutableListOf(p), now))
			p in snake.blocks -> {}
			else -> {
				snake.blocks.add(0, p)
				snake.lastAdd = now
			}
		}
	}

	fun removed(p: P) {
		for (s in snakes) s.blocks.remove(p)
		snakes.removeAll { it.blocks.isEmpty() }
	}

	fun expire(now: Long) {
		snakes.removeAll { now - it.lastAdd > 30_000 }
	}
}
