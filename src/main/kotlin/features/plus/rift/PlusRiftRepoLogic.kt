// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import dev.ashtonite365.apex.util.plus.PlusRepoData
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RVec(val x: Double, val y: Double, val z: Double) {
	operator fun plus(o: RVec) = RVec(x + o.x, y + o.y, z + o.z)

	fun distanceSq(o: RVec): Double {
		val dx = x - o.x
		val dy = y - o.y
		val dz = z - o.z
		return dx * dx + dy * dy + dz * dz
	}

	fun distance(o: RVec) = sqrt(distanceSq(o))

	fun distanceIgnoreY(o: RVec): Double {
		val dx = x - o.x
		val dz = z - o.z
		return sqrt(dx * dx + dz * dz)
	}

	fun block() = RVec(floor(x), floor(y), floor(z))

	companion object {

		fun parse(s: String): RVec? {
			val nums = ArrayList<Double>(3)
			for (part in s.split(':')) part.trim().toDoubleOrNull()?.let { nums.add(it) }
			if (nums.size != 3) return null
			return RVec(nums[0], nums[1], nums[2])
		}
	}
}

object RiftRepo {
	const val RACE = "constants/rift/RiftRace.json"
	const val UPSIDE_DOWN = "constants/RiftUpsideDownParkour.json"
	const val LAVA_MAZE = "constants/RiftLavaMazeParkour.json"
	const val TUBULATOR = "constants/RiftTubulator.json"
	const val DANCE = "constants/DanceRoomInstructions.json"
	const val BUTTONS = "constants/rift/RiftWoodenButtons.json"
	const val BERBERIS = "constants/rift/WiltedBerberisLocations.json"
	const val EFFIGIES = "constants/RiftEffigies.json"
	const val ENIGMA = "constants/EnigmaSouls.json"
	const val GRAPH = "constants/island_graphs/THE_RIFT.json"

	data class Parkour(val locations: List<RVec>, val shortCuts: List<Pair<Int, Int>>)
	data class ButtonSpot(val house: String, val position: RVec, val buttons: List<RVec>)
	data class GraphNode(val name: String?, val position: RVec, val tags: List<String>)

	private fun positions(e: JsonElement?): List<RVec> {
		if (e == null) return emptyList()
		val out = ArrayList<RVec>()
		for (item in e.jsonArray) RVec.parse(item.jsonPrimitive.content)?.let { out.add(it) }
		return out
	}

	private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

	fun parkour(j: JsonElement): Parkour {
		val root = j.jsonObject
		val locations = positions(root["locations"])
		val cuts = ArrayList<Pair<Int, Int>>()
		root["shortCuts"]?.jsonArray?.forEach { cut ->
			val from = cut.jsonObject["from"]?.jsonPrimitive?.intOrNull ?: return@forEach
			val to = cut.jsonObject["to"]?.jsonPrimitive?.intOrNull ?: return@forEach
			cuts.add(from to to)
		}
		return Parkour(locations, cuts)
	}

	fun dance(j: JsonElement): List<String> {
		val arr = j.jsonObject["instructions"]?.jsonArray ?: return emptyList()
		return arr.map { it.jsonPrimitive.content }
	}

	fun effigies(j: JsonElement) = positions(j.jsonObject["locations"])

	fun berberis(j: JsonElement): Map<RVec, Int> {
		val fields = j.jsonObject["field_centers"]?.jsonArray ?: return emptyMap()
		val pairs = ArrayList<Pair<RVec, Int>>()
		for (f in fields) {
			val o = f.jsonObject
			val pos = o.text("position") ?: continue
			val count = o["count"]?.jsonPrimitive?.intOrNull ?: continue
			RVec.parse(pos)?.let { pairs.add(it to count) }
		}
		return pairs.toMap()
	}

	fun enigma(j: JsonElement): Map<String, Map<String, RVec>> {
		val areas = j.jsonObject["areas"] as? JsonObject ?: return emptyMap()
		return areas.mapValues { (_, souls) ->
			val list = souls as? JsonArray ?: return@mapValues emptyMap<String, RVec>()
			val pairs = ArrayList<Pair<String, RVec>>()
			for (s in list) {
				val o = s as? JsonObject ?: continue
				val pos = o.text("position") ?: continue
				val name = o.text("name") ?: continue
				RVec.parse(pos)?.let { pairs.add(name to it) }
			}
			pairs.toMap()
		}
	}

	fun buttons(j: JsonElement): List<ButtonSpot> {
		val houses = j.jsonObject["houses"] as? JsonObject ?: return emptyList()
		val out = ArrayList<ButtonSpot>()
		for ((house, spots) in houses) {
			val list = spots as? JsonArray ?: continue
			for (s in list) {
				val o = s as? JsonObject ?: continue
				val pos = o.text("position") ?: continue
				val at = RVec.parse(pos) ?: continue
				out.add(ButtonSpot(house, at, positions(o["buttons"])))
			}
		}
		return out
	}

	fun graph(j: JsonElement): List<GraphNode> {
		val nodes = j as JsonObject
		val out = ArrayList<GraphNode>()
		for (n in nodes.values) {
			val o = n.jsonObject
			val at = o.text("Position")?.let { RVec.parse(it) } ?: continue
			val name = o.text("Name")
			val tags = o["Tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
			out.add(GraphNode(name, at, tags))
		}
		return out
	}

	private fun <T> load(path: String, convert: (JsonElement) -> T): T? {
		val json = PlusRepoData.loadRepoJson(path) ?: return null
		return try { convert(json) } catch (t: Throwable) { null }
	}

	fun loadParkour(path: String) = load(path, ::parkour)
	fun loadDance() = load(DANCE, ::dance) ?: emptyList()
	fun loadEffigies(): List<RVec> {
		val all = load(EFFIGIES, ::effigies) ?: return emptyList()
		return if (all.size == 6) all else emptyList()
	}
	fun loadBerberis() = load(BERBERIS, ::berberis) ?: emptyMap()
	fun loadEnigma() = load(ENIGMA, ::enigma) ?: emptyMap()
	fun loadButtons() = load(BUTTONS, ::buttons) ?: emptyList()

	fun loadButtonSpotsFromGraph(): List<ButtonSpot> {
		val spots = loadButtons()
		val graphNodes = load(GRAPH, ::graph) ?: return spots
		val quest = graphNodes.filter { "rift_buttons_quest" in it.tags }
		val confirmed = spots.filter { s -> quest.any { n -> n.position == s.position && n.name == "${s.house} House" } }
		return if (confirmed.isEmpty()) spots else confirmed
	}
}

class ParkourState(
	val locations: List<RVec>,
	val shortCuts: List<Pair<Int, Int>>,
	val platformSize: Double = 1.0,
	val detectionRange: Double = 1.0,
	val goInOrder: Boolean = false,
) {
	var current = -1
		private set
	var visible = false
		private set
	var lookAhead = 3

	fun inParkour() = current != -1

	fun reset() {
		current = -1
		visible = false
	}

	fun center(v: RVec) = RVec(v.x + platformSize / 2, v.y + 1.0, v.z + platformSize / 2)

	private fun standingOn(loc: RVec, player: RVec, onGround: Boolean) = onGround && center(loc).distance(player) < detectionRange

	fun update(player: RVec, onGround: Boolean): Boolean {
		if (locations.isEmpty()) return false
		val last = locations.size - 1
		if (current == last) visible = false
		if (visible) {
			for (i in locations.indices) {
				if (!standingOn(locations[i], player, onGround)) continue
				val skips = i < current - 1 || i > current + 1
				if (goInOrder && skips) continue
				current = i
			}
		}
		val fromStart = center(locations[0]).distance(player)
		when {
			fromStart < detectionRange -> visible = true
			fromStart > 15 && current < 1 -> visible = false
		}
		return visible && current == last
	}

	fun shouldRender() = visible && current >= 0

	fun visibleSteps(): List<IndexedValue<RVec>> {
		if (!shouldRender()) return emptyList()
		require(lookAhead >= 0) { "Requested element count $lookAhead is less than zero." }
		val out = ArrayList<IndexedValue<RVec>>()
		var i = current
		while (i < locations.size && out.size < lookAhead) {
			out.add(IndexedValue(i, locations[i]))
			i++
		}
		return out
	}

	fun visibleShortCuts(): List<Pair<Int, Int>> {
		if (!shouldRender()) return emptyList()
		val end = current + lookAhead
		return shortCuts.filter { (from, to) -> from >= current && from < end && to >= 0 && to < locations.size }
	}

	companion object {

		fun rainbowRgb(index: Int): Int {
			val hue = (-index / 12f).mod(1f)
			return java.awt.Color.HSBtoRGB(hue, 1f, 0.7f) and 0xFFFFFF
		}
	}
}

object DanceRoom {

	fun inRoom(x: Double, y: Double, z: Double) =
		x >= -267.0 && x <= -260.0 && y >= 32.0 && y <= 40.0 && z >= -110.0 && z <= -102.0

	const val NOW = "§7Now:"
	const val NEXT = "§7Next:"
	const val LATER = "§7Later:"

	private val wordColours = mapOf("Move" to "§e", "Stand" to "§e", "Sneak" to "§5", "Jump" to "§b", "Punch" to "§d")

	fun colour(word: String) = wordColours[word] ?: "§f"

	fun formatInstruction(line: String): String {
		val sb = StringBuilder()
		for ((n, w) in line.split(" ").withIndex()) {
			if (n > 0) sb.append(' ')
			val word = if (w.isEmpty()) w else w[0].uppercase() + w.substring(1)
			sb.append(colour(word)).append(word)
		}
		return sb.toString()
	}

	fun countdown(ticks: Int): String {
		if (ticks <= 0) return ""
		val ms = ticks * 50
		return "§f%01d:%03d".format(ms / 1000, ms % 1000)
	}

	fun lines(instructions: List<String>, index: Int, countdownTicks: Int, linesToShow: Int): List<String> {
		if (instructions.isEmpty()) return listOf("§cError fetching Dance Room Instructions!")
		val out = ArrayList<String>()
		for ((i, raw) in instructions.withIndex()) {
			val text = formatInstruction(raw)
			if (i == index) out.add("$NOW $text ${countdown(countdownTicks)}".trimEnd())
			else if (i == index + 1) out.add("$NEXT $text")
			else if (i >= index + 2 && i <= index + linesToShow) out.add("$LATER $text")
		}
		return out
	}

	fun isSuccess(sound: String, pitch: Float, volume: Float): Boolean {
		if (sound != "block.note_block.bass") return false
		return (pitch == 0.6984127f || pitch == 0.52380955f) && volume == 1f
	}

	fun isFailure(sound: String, pitch: Float, volume: Float): Boolean = when (sound) {
		"entity.player.burp" -> volume == 0.8f
		"entity.player.levelup" -> pitch == 1.8412699f && volume == 1f
		else -> false
	}
}

object Effigies {
	val heartsPattern = Regex("Effigies: (?<hearts>(?:(?:§[7c])?⧯)*)")
	val timerPattern = Regex("§eRespawn §c(?<time>.*) §7\\(or click!\\)")
	const val BREAK = "§eBreak it!"
	private val durationPart = Regex("(\\d+)([hms])")

	fun parseHearts(line: String): List<Boolean>? {
		val hearts = heartsPattern.matchEntire(line)?.groups?.get("hearts")?.value ?: return null
		val out = ArrayList<Boolean>()
		for (i in hearts.indices) {
			if (hearts[i] != '§' || i + 1 >= hearts.length) continue
			when (hearts[i + 1]) {
				'7' -> out.add(true)
				'c' -> out.add(false)
			}
		}
		return out
	}

	fun parseDuration(s: String): Long? {
		var ms: Long? = null
		for (part in durationPart.findAll(s)) {
			val n = part.groupValues[1].toLong()
			val unit = when (part.groupValues[2]) {
				"h" -> 3_600_000L
				"m" -> 60_000L
				else -> 1000L
			}
			ms = (ms ?: 0L) + n * unit
		}
		return ms
	}

	fun format(ms: Long): String {
		val secs = maxOf(ms / 1000, 0L)
		if (secs < 60) return "${secs}s"
		return "${secs / 60}m ${secs % 60}s"
	}
}

object EnigmaRules {
	val title = Regex("(?:✔ )?Enigma: (?<name>.+)")
	const val NOT_COMPLETED = "✖ Not completed yet!"
	val guideArea = Regex("To Rift Guide ➜ (?<area>.+)")
	val found = Regex("SOUL! You unlocked an Enigma Soul!|You have already found that Enigma Soul!")
	val inventory = Regex(".*Enigma Souls$")

	fun closest(souls: Map<String, Map<String, RVec>>, player: RVec): Pair<String, String>? {
		var bestArea: String? = null
		var bestName: String? = null
		var limit = 8.0
		for ((area, inArea) in souls) {
			for ((name, at) in inArea) {
				val d = at.distance(player)
				if (d >= limit || d.isNaN()) continue
				limit = d
				bestArea = area
				bestName = name
			}
		}
		return if (bestArea == null) null else bestArea to bestName!!
	}
}

object ButtonRules {
	val hit = Regex("§eYou have hit §r§b\\d+/56 §r§eof the wooden buttons!")
	const val ALL = "§eYou've hit all §r§b56 §r§ewooden buttons!"

	fun nearestSpot(spots: List<RiftRepo.ButtonSpot>, hit: Set<RVec>, player: RVec): RiftRepo.ButtonSpot? {
		var best: RiftRepo.ButtonSpot? = null
		var bestDist = 0.0
		for (s in spots) {
			if (s.buttons.all { it in hit }) continue
			val d = s.position.distance(player)
			if (best == null || d.compareTo(bestDist) < 0) {
				best = s
				bestDist = d
			}
		}
		return best
	}
}

class BerberisSequence(val expectedCount: Int) {
	val sequence = mutableListOf<RVec>()
	var isValid = false
		private set
	var isRendering = false
		private set
	var currentIndex = 0
		private set
	var lastParticle = Long.MIN_VALUE / 2

	fun reset() {
		sequence.clear()
		isValid = false
		isRendering = false
		currentIndex = 0
		lastParticle = Long.MIN_VALUE / 2
	}

	val currentTarget get() = sequence.getOrNull(currentIndex)
	val nextTarget get() = sequence.getOrNull(currentIndex + 1)

	fun respawn(block: RVec, now: Long): Boolean {
		if (isValid) return false
		val stale = now - lastParticle > 3000
		if (stale) sequence.clear()
		lastParticle = now
		if (sequence.isNotEmpty() && sequence.last() == block) return false
		sequence.add(block)
		if (sequence.size != expectedCount) return false
		isValid = true
		isRendering = true
		return true
	}

	fun broke(block: RVec): Boolean {
		if (!isRendering) return false
		if (currentTarget != block) return false
		currentIndex += 1
		if (currentIndex < sequence.size) return true

		val keepRendering = isValid
		reset()
		isRendering = keepRendering
		return true
	}
}
