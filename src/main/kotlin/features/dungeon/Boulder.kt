/*
 * SPDX-License-Identifier: GPL-3.0-or-later AND BSD-3-Clause
 *
 * SkyBlock Apex: Boulder puzzle solver. The solution table is bundled third-party BSD-3-Clause data
 * (boulder-solutions.json; CREDITS.md, NOTICE, LICENSES/BSD-3-Clause.txt). Visual only: shows which boulder to
 * push and never clicks.
 */
package dev.ashtonite365.apex.features.dungeon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.BlockPos

object Boulder {

	data class Step(val render: BlockPos, val click: BlockPos)

	private const val RESOURCE = "/assets/apex/puzzles/boulder-solutions.json"

	val table: Map<String, List<Step>> by lazy {
		val json = Boulder::class.java.getResourceAsStream(RESOURCE)!!.reader().readText()
		Json.parseToJsonElement(json).jsonObject.mapValues { entry -> entry.value.jsonArray.map { stepOf(it) } }
	}

	private fun stepOf(e: kotlinx.serialization.json.JsonElement): Step {
		val n = e.jsonArray.map { it.jsonPrimitive.int }
		return Step(BlockPos(n[0], 65, n[1]), BlockPos(n[2], 65, n[3]))
	}

	private val CELLS: List<BlockPos> = buildList {
		var z = 24
		while (z >= 9) {
			var x = 24
			while (x >= 6) { add(BlockPos(x, 66, z)); x -= 3 }
			z -= 3
		}
	}

	fun boardKey(isAir: (BlockPos) -> Boolean): String {
		val sb = StringBuilder(CELLS.size)
		for (cell in CELLS) sb.append(if (isAir(cell)) '0' else '1')
		return sb.toString()
	}

	fun solve(corner: BlockPos, table: Map<String, List<Step>>, isAir: (BlockPos) -> Boolean): List<Step> {
		for (rotation in 0..3) {
			val toWorld = { p: BlockPos -> PuzzleRoom.toReal(corner, p, rotation) }
			val steps = table[boardKey { isAir(toWorld(it)) }] ?: continue
			return steps.map { Step(toWorld(it.render), toWorld(it.click)) }
		}
		return emptyList()
	}

	sealed interface Scan {

		data object NotBoulder : Scan

		data class Solved(val key: String, val steps: List<Step>) : Scan

		data class Blocked(val key: String) : Scan
	}

	const val MIN_BOULDERS = 8

	fun scan(corner: BlockPos, table: Map<String, List<Step>>, isAir: (BlockPos) -> Boolean, isBoulder: (BlockPos) -> Boolean): Scan {
		var blocked: String? = null
		for (rotation in 0..3) {
			val toWorld = { p: BlockPos -> PuzzleRoom.toReal(corner, p, rotation) }
			var allBoulders = true
			for (cell in CELLS) {
				val p = toWorld(cell)
				if (!isAir(p) && !isBoulder(p)) allBoulders = false
			}
			if (!allBoulders) continue
			val key = boardKey { isAir(toWorld(it)) }
			val steps = table[key]
			if (steps != null) return Scan.Solved(key, steps.map { Step(toWorld(it.render), toWorld(it.click)) })
			if (blocked == null && key.count { it == '1' } >= MIN_BOULDERS) blocked = key
		}
		return if (blocked != null) Scan.Blocked(blocked) else Scan.NotBoulder
	}
}
