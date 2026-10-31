/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: bundled Ice Fill solutions (data file ice-fill-floors.json, third-party
 * BSD-3-Clause data; CREDITS.md, NOTICE).
 * The merged Ice Fill solver (IceFillSolver.kt) uses it when "use bundled solutions" is on. Positions are
 * room-local (PuzzleRoom convention); the chosen rotation is the first one whose identifier blocks match on every floor.
 */
package dev.ashtonite365.apex.features.kit.puzzles

import dev.ashtonite365.apex.features.dungeon.PuzzleRoom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.BlockPos

object IceFillFloors {

	data class Data(
		val identifier: List<List<List<BlockPos>>>,
		val easy: List<List<List<BlockPos>>>,
		val hard: List<List<List<BlockPos>>>,
	)

	private const val RESOURCE = "/assets/apex/kit/puzzles/ice-fill-floors.json"

	val data: Data? by lazy {
		val stream = IceFillFloors::class.java.getResourceAsStream(RESOURCE) ?: return@lazy null
		parse(stream.reader().readText())
	}

	private fun coord(o: Map<String, JsonElement>, key: String): Int = o[key]!!.jsonPrimitive.int

	private fun blockPos(e: JsonElement): BlockPos {
		val o = e.jsonObject
		return BlockPos(coord(o, "x"), coord(o, "y"), coord(o, "z"))
	}

	private fun nested(e: JsonElement): List<List<List<BlockPos>>> =
		e.jsonArray.map { floor -> floor.jsonArray.map { pattern -> pattern.jsonArray.map { blockPos(it) } } }

	fun parse(text: String): Data {
		val root = Json.parseToJsonElement(text).jsonObject
		val identifier = nested(root["identifier"]!!)
		val easy = nested(root["easy"]!!)
		val hard = nested(root["hard"]!!)
		return Data(identifier, easy, hard)
	}

	fun patterns(data: Data, isAir: (BlockPos) -> Boolean): List<Int>? {
		val chosen = ArrayList<Int>(data.identifier.size)
		for (floor in data.identifier) {
			var found = -1
			for ((i, ids) in floor.withIndex()) {
				if (isAir(ids[0]) && !isAir(ids[1])) { found = i; break }
			}
			if (found < 0) return null
			chosen.add(found)
		}
		return chosen
	}

	fun floors(data: Data, corner: BlockPos, optimal: Boolean, isAir: (BlockPos) -> Boolean): List<List<BlockPos>>? {
		val table = if (optimal) data.hard else data.easy
		for (rotation in 0..3) {
			val chosen = patterns(data) { local -> isAir(PuzzleRoom.toReal(corner, local, rotation)) } ?: continue
			return List(chosen.size) { floor -> table[floor][chosen[floor]].map { PuzzleRoom.toReal(corner, it, rotation) } }
		}
		return null
	}

	fun solve(data: Data, corner: BlockPos, optimal: Boolean, isAir: (BlockPos) -> Boolean): List<BlockPos>? =
		floors(data, corner, optimal, isAir)?.flatten()
}
