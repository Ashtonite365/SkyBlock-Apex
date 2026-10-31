// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt

object LeverGate {
	data class Entry(val leverY: Int, val secretY: Int, val dist2: Int, val chest: Boolean)
	data class P(val x: Int, val y: Int, val z: Int)

	const val REACH_DY = 3
	const val DIST_TOLERANCE = 1.0
	private const val RESOURCE = "/assets/apex/dungeon-rooms/secret-levers.json"

	val catalog: Map<String, List<Entry>> by lazy {
		LeverGate::class.java.getResourceAsStream(RESOURCE)?.reader()?.use { parse(it.readText()) } ?: emptyMap()
	}

	fun parse(text: String): Map<String, List<Entry>> =
		Json.parseToJsonElement(text).jsonObject["rooms"]!!.jsonObject.mapValues { (_, v) ->
			v.jsonArray.map { e -> val a = e.jsonArray.map { it.jsonPrimitive.int }; Entry(a[0], a[1], a[2], a[3] == 1) }
		}

	fun gating(entries: List<Entry>, levers: Collection<P>, chests: Collection<P>): Set<P> = levers.filterTo(HashSet()) { l ->
		entries.any { e ->
			e.leverY == l.y && (!e.chest || chests.any { c ->
				c.y == e.secretY && abs(sqrt(((l.x - c.x) * (l.x - c.x) + (l.z - c.z) * (l.z - c.z)).toDouble()) - sqrt(e.dist2.toDouble())) <= DIST_TOLERANCE
			})
		}
	}

	val DECOR_BLOCKS = setOf("cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "fire", "soul_fire", "campfire", "soul_campfire")

	fun isDecorative(attachedTo: String, neighbours: Collection<String>): Boolean =
		attachedTo in DECOR_BLOCKS || neighbours.any { it in DECOR_BLOCKS }

	fun reachable(lever: P, playerFeetY: Double, ladderBelow: Boolean): Boolean = lever.y - playerFeetY <= REACH_DY || ladderBelow

	fun backup(levers: Collection<P>, ok: (P) -> Boolean, px: Double, py: Double, pz: Double): P? =
		levers.filter(ok).minByOrNull { (it.x + 0.5 - px).let { d -> d * d } + (it.y + 0.5 - py).let { d -> d * d } + (it.z + 0.5 - pz).let { d -> d * d } }
}
