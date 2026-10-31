// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.kuudra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PearlLineups {
	data class Pos(val x: Int, val y: Int, val z: Int) {

		fun distSq(x2: Double, y2: Double, z2: Double): Double {
			val dx = x + 0.5 - x2
			val dy = y + 0.5 - y2
			val dz = z + 0.5 - z2
			return dx * dx + dy * dy + dz * dz
		}
	}

	data class Lineup(val supply: String, val colour: String, val start: List<Pos>, val targets: List<Pos>)

	private fun posOf(e: JsonElement): Pos {
		val a = e.jsonArray
		return Pos(a[0].jsonPrimitive.int, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int)
	}

	private fun lineupOf(e: JsonElement): Lineup {
		val o = e.jsonObject
		val supply = o.getValue("supply").jsonPrimitive.content
		val colour = o.getValue("colour").jsonPrimitive.content
		val start = (o.getValue("start") as JsonArray).map(::posOf)
		val targets = (o.getValue("targets") as JsonArray).map(::posOf)
		return Lineup(supply, colour, start, targets)
	}

	fun parse(json: String): List<Lineup> = Json.parseToJsonElement(json).jsonObject.getValue("lineups").jsonArray.map(::lineupOf)

	val table: List<Lineup> by lazy {
		val stream = PearlLineups::class.java.getResourceAsStream("/assets/apex/kit/kuudra/pearl-lineups.json")
		stream?.use { parse(it.readBytes().decodeToString()) } ?: emptyList()
	}

	private fun nearestStart(l: Lineup, x: Double, y: Double, z: Double): Double {
		if (l.start.isEmpty()) return Double.MAX_VALUE
		return l.start.minOf { it.distSq(x, y, z) }
	}

	fun ordered(lineups: List<Lineup>, x: Double, y: Double, z: Double): List<Lineup> = lineups.sortedBy { nearestStart(it, x, y, z) }

	fun visible(lineups: List<Lineup>, x: Double, y: Double, z: Double, closestOnly: Boolean): List<Lineup> {
		val sorted = ordered(lineups, x, y, z)
		return if (closestOnly) sorted.take(1) else sorted
	}

	private val COLOURS = mapOf("red" to 0xFF5555, "light_purple" to 0xFF55FF, "yellow" to 0xFFFF55, "aqua" to 0x55FFFF, "green" to 0x55FF55, "blue" to 0x5555FF)

	fun rgb(colour: String): Int = COLOURS[colour] ?: 0xFFFFFF
}
