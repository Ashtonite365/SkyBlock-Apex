// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.boss

class TickTimerState {
	var goldorTick = -1
	var goldorStart = -1
	var pad = -1
	var lightning = -1
	var py = -1
	var stormTick = -1
	var fireFreeze = -1
	private var pyTriggered = false

	fun onChat(m: String) {
		if (goldorRe.matches(m)) {
			goldorTick = 60
		} else if (coreRe.matches(m)) {
			goldorStart = -1
			goldorTick = -1
		} else if (stormEndRe.matches(m)) {
			goldorStart = 64
			pad = -1
			stormTick = -1
		} else if (stormStartRe.matches(m)) {
			pad = 20
			lightning = 520
			stormTick = 0
		} else if (!pyTriggered && stormPyRe.matches(m)) {
			pyTriggered = true
			py = 55
		} else if (fireFreezeRe.matches(m)) {
			fireFreeze = 206
		}
	}

	fun onServerTick(goldorLoop: Boolean, padLoop: Boolean) {
		if (goldorLoop && goldorTick == 0 && goldorStart <= 0) goldorTick = 60
		if (goldorStart >= 0) goldorStart -= 1
		if (goldorTick >= 0) goldorTick -= 1
		if (padLoop && pad == 0) pad = 20
		if (pad >= 0) pad -= 1
		if (lightning >= 0) lightning -= 1
		if (py >= 0) py -= 1
		if (stormTick >= 0) stormTick += 1
		if (fireFreeze >= 0) fireFreeze -= 1
	}

	fun reset() {
		goldorTick = -1
		goldorStart = -1
		pad = -1
		lightning = -1
		py = -1
		stormTick = -1
		fireFreeze = -1
		pyTriggered = false
	}

	companion object {
		val goldorRe = Regex("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$")
		val coreRe = Regex("^The Core entrance is opening!$")
		val stormEndRe = Regex("^\\[BOSS] Storm: I should have known that I stood no chance\\.$")
		val stormStartRe = Regex("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$")
		val stormPyRe = Regex("^\\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$")
		val fireFreezeRe = Regex("^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$")

		fun format(time: Int, max: Int, prefix: String, inTicks: Boolean, symbol: Boolean, showPrefix: Boolean): String {
			val colour = if (time >= max * 0.66) "§a" else if (time >= max * 0.33) "§6" else "§c"
			val value = if (inTicks) {
				if (symbol) "${time}t" else "$time"
			} else {
				val secs = "%.2f".format(time / 20f)
				if (symbol) secs + "s" else secs
			}
			val head = if (showPrefix) "$prefix " else ""
			return head + colour + value
		}
	}
}

class SpiritBearState {
	var kills = 0

	var timer = -1

	fun onBlock(x: Int, y: Int, z: Int, old: String, new: String, master: Boolean) {
		val at = Triple(x, y, z)
		val lanterns = if (master) m4 else f4
		if (at !in lanterns) return
		val isLast = x == 7 && y == 77 && z == 34
		val maxKills = if (master) 30 else 25
		if (old == "coal_block" && new == "sea_lantern") {
			if (kills < maxKills) kills += 1
			if (isLast) timer = 68
		}
		if (old == "sea_lantern" && new == "coal_block") {
			if (kills > 0) kills -= 1
			if (isLast) timer = -1
		}
	}

	fun onServerTick() {
		if (timer > 0) timer -= 1
	}

	fun display(master: Boolean): String {
		if (timer < 0) return "§d$kills/" + (if (master) 30 else 25)
		if (timer > 0) return "§e%.2fs".format(timer / 20f)
		return "§aAlive!"
	}

	fun reset() {
		kills = 0
		timer = -1
	}

	companion object {

		private fun ring(vararg xz: Int): HashSet<Triple<Int, Int, Int>> {
			val out = HashSet<Triple<Int, Int, Int>>()
			var i = 0
			while (i < xz.size) {
				out.add(Triple(xz[i], 77, xz[i + 1]))
				i += 2
			}
			return out
		}

		val f4 = ring(-3, 33, -9, 31, -16, 26, -20, 20, -23, 13, -24, 6, -24, 0, -22, -7, -18, -13, -12, -19,
			-5, -22, 1, -24, 8, -24, 14, -23, 21, -19, 27, -14, 31, -8, 33, -1, 34, 5, 33, 12, 31, 19, 27, 25, 20, 30, 14, 33, 7, 34)
		val m4 = ring(-2, 33, -7, 32, -13, 28, -17, 24, -21, 18, -23, 13, -24, 7, -24, 2, -23, -4, -21, -9,
			-17, -14, -12, -19, -6, -22, -1, -23, 5, -24, 10, -24, 16, -22, 21, -19, 27, -15, 30, -10,
			32, -5, 34, 1, 34, 7, 33, 12, 31, 18, 28, 23, 23, 28, 18, 31, 12, 33, 7, 34)
	}
}

class TerracottaState {
	data class T(val x: Int, val y: Int, val z: Int, var seconds: Float)

	val spawning = mutableListOf<T>()

	fun onFlowerPot(x: Int, y: Int, z: Int, master: Boolean) {
		for (t in spawning) if (t.x == x && t.y == y && t.z == z) return
		spawning.add(T(x, y, z, if (master) 12f else 15f))
	}

	fun onServerTick() {
		for (t in spawning) t.seconds -= .05f
		spawning.removeAll { it.seconds <= 0 }
	}

	companion object {
		fun colour(t: Float) = if (t > 5f) "§a" else if (t > 2f) "§6" else "§c"
	}
}

class DragonState {
	enum class Dragon(val colour: Char, val sx: Int, val sy: Int, val sz: Int, val xr: ClosedFloatingPointRange<Double>, val zr: ClosedFloatingPointRange<Double>,
		val box: DoubleArray, val statue: Triple<Int, Int, Int>) {
		Red('c', 27, 14, 59, 24.0..30.0, 56.0..62.0, doubleArrayOf(14.5, 13.0, 45.5, 39.5, 28.0, 70.5), Triple(32, 22, 59)),
		Orange('6', 85, 14, 56, 82.0..88.0, 53.0..59.0, doubleArrayOf(72.0, 8.0, 47.0, 102.0, 28.0, 77.0), Triple(80, 23, 56)),
		Green('a', 27, 14, 94, 23.0..29.0, 91.0..97.0, doubleArrayOf(7.0, 8.0, 80.0, 37.0, 28.0, 110.0), Triple(32, 23, 94)),
		Blue('b', 84, 14, 94, 82.0..88.0, 91.0..97.0, doubleArrayOf(71.5, 13.0, 82.5, 96.5, 26.0, 107.5), Triple(79, 23, 94)),
		Purple('5', 56, 14, 125, 53.0..59.0, 122.0..128.0, doubleArrayOf(45.5, 13.0, 113.5, 68.5, 23.0, 136.5), Triple(56, 22, 120));

		fun contains(x: Double, y: Double, z: Double) =
			x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]
	}

	enum class S { SPAWNING, ALIVE, DEAD }

	val state: MutableMap<Dragon, S> = LinkedHashMap<Dragon, S>().also { m -> for (d in Dragon.entries) m[d] = S.DEAD }
	val timeToSpawn: MutableMap<Dragon, Int> = LinkedHashMap<Dragon, Int>().also { m -> for (d in Dragon.entries) m[d] = 0 }
	val spawnOrder = mutableListOf<Dragon>()
	val aliveSince = mutableMapOf<Dragon, Long>()
	var tick = 0L
	val priority get() = if (spawnOrder.isEmpty()) null else spawnOrder[0]

	private fun isSpawnBurst(isFlame: Boolean, count: Int, x: Double, y: Double, z: Double, dx: Float, dy: Float, dz: Float): Boolean {
		if (!isFlame || count != 20) return false
		if (y != 19.0) return false
		if (dx != 2f || dy != 3f || dz != 2f) return false
		return x % 1 == 0.0 && z % 1 == 0.0
	}

	fun onParticle(isFlame: Boolean, count: Int, x: Double, y: Double, z: Double, dx: Float, dy: Float, dz: Float): Dragon? {
		if (!isSpawnBurst(isFlame, count, x, y, z, dx, dy, dz)) return null
		for (d in Dragon.entries) {
			if (state[d] != S.DEAD) continue
			if (x < d.xr.start || x > d.xr.endInclusive || z < d.zr.start || z > d.zr.endInclusive) continue
			state[d] = S.SPAWNING
			timeToSpawn[d] = 100
			spawnOrder.add(d)
			return d
		}
		return null
	}

	fun onServerTick(): List<Dragon> {
		tick += 1
		val spawned = mutableListOf<Dragon>()
		for (d in Dragon.entries) {
			val left = timeToSpawn.getValue(d)
			when {
				left > 0 -> timeToSpawn[d] = left - 1
				state[d] == S.SPAWNING -> {
					state[d] = S.ALIVE
					aliveSince[d] = tick
					spawned.add(d)
				}
			}
		}
		return spawned
	}

	fun onStatueAir(x: Int, y: Int, z: Int): Pair<Dragon, Long>? {
		for (d in Dragon.entries) if (d.statue.first == x && d.statue.second == y && d.statue.third == z) return kill(d)
		return null
	}

	fun kill(d: Dragon): Pair<Dragon, Long>? {
		if (state[d] == S.DEAD) return null
		state[d] = S.DEAD
		spawnOrder.remove(d)
		val since = aliveSince[d] ?: tick
		return d to (tick - since)
	}

	fun reset() {
		for (d in Dragon.entries) {
			state[d] = S.DEAD
			timeToSpawn[d] = 0
		}
		spawnOrder.clear()
		aliveSince.clear()
	}

	companion object {

		fun timer(ticks: Int, style: Int, symbol: Boolean): String {
			val colour = if (ticks <= 20) "§c" else if (ticks <= 60) "§e" else "§a"
			val text = when (style) {
				0 -> (ticks * 50).toString() + (if (symbol) "ms" else "")
				1 -> "%.1f".format(ticks / 20f) + (if (symbol) "s" else "")
				else -> ticks.toString() + (if (symbol) "t" else "")
			}
			return colour + text
		}

		fun health(h: Float): String {
			val colour = when {
				h >= 750_000_000 -> "§a"
				h >= 500_000_000 -> "§e"
				h >= 250_000_000 -> "§6"
				else -> "§c"
			}
			val text = when {
				h >= 1e9 -> "%.1fb".format(h / 1e9)
				h >= 1e6 -> "%.1fm".format(h / 1e6)
				h >= 1e3 -> "%.1fk".format(h / 1e3)
				else -> h.toInt().toString()
			}
			return colour + text
		}
	}
}

enum class KingRelic(val id: String, val colour: Char, val rgb: Int, val x: Int, val y: Int, val z: Int) {
	Green("GREEN_KING_RELIC", 'a', 0x55FF55, 49, 7, 44),
	Purple("PURPLE_KING_RELIC", '5', 0xAA00AA, 54, 7, 41),
	Blue("BLUE_KING_RELIC", 'b', 0x5555FF, 59, 7, 44),
	Orange("ORANGE_KING_RELIC", '6', 0xFFAA00, 57, 7, 42),
	Red("RED_KING_RELIC", 'c', 0xFF5555, 51, 7, 42);

	companion object {
		fun of(id: String?): KingRelic? {
			for (r in entries) if (r.id == id) return r
			return null
		}
	}
}
