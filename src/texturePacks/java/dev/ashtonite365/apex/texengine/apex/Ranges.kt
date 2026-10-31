/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: number and rarity tests for pack files (written from SPEC §3.4).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import io.github.moulberry.repo.data.Rarity

fun interface NumberTest {
	fun test(value: Double): Boolean

	companion object {
		private val interval = Regex("""^([\[(])\s*([0-9.]+)\s*,\s*([0-9.]+)\s*([\])])$""")
		private val comparison = Regex("""^(<=|>=|<|>)\s*([0-9.]+)$""")

		fun readOrNull(json: JsonElement): NumberTest? {
			if (!json.isJsonPrimitive) return null
			val prim = json.asJsonPrimitive
			if (prim.isNumber) {
				val written = prim.asString
				val target = prim.asDouble

				return if ('.' in written) {
					val whole = target.toLong()
					NumberTest { it.toLong() == whole }
				} else NumberTest { it == target }
			}
			if (!prim.isString) return null
			val text = prim.asString.trim()
			interval.matchEntire(text)?.let { m ->
				val low = m.groupValues[2].toDoubleOrNull() ?: return null
				val high = m.groupValues[3].toDoubleOrNull() ?: return null
				val lowIn = m.groupValues[1] == "["
				val highIn = m.groupValues[4] == "]"
				return NumberTest { v -> (if (lowIn) v >= low else v > low) && (if (highIn) v <= high else v < high) }
			}
			comparison.matchEntire(text)?.let { m ->
				val bound = m.groupValues[2].toDoubleOrNull() ?: return null
				return when (m.groupValues[1]) {
					"<" -> NumberTest { it < bound }
					"<=" -> NumberTest { it <= bound }
					">" -> NumberTest { it > bound }
					else -> NumberTest { it >= bound }
				}
			}
			return null
		}
	}
}

fun interface RarityTest {
	fun test(rarity: Rarity): Boolean

	companion object {
		private val interval = Regex("""^([\[(])\s*([A-Za-z_]*)\s*,\s*([A-Za-z_]*)\s*([\])])$""")

		private fun rarity(name: String): Rarity? = Rarity.entries.firstOrNull { it.name == name }

		fun readOrNull(json: JsonElement): RarityTest? {
			if (!json.isJsonPrimitive || !json.asJsonPrimitive.isString) return null
			val text = json.asString.trim()
			val m = interval.matchEntire(text)
			if (m == null) {
				val exact = rarity(text) ?: return null
				return RarityTest { it == exact }
			}
			val low = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { rarity(it) ?: return null }
			val high = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { rarity(it) ?: return null }
			val lowIn = m.groupValues[1] == "["
			val highIn = m.groupValues[4] == "]"
			return RarityTest { r ->
				val o = r.ordinal
				(low == null || (if (lowIn) o >= low.ordinal else o > low.ordinal)) &&
					(high == null || (if (highIn) o <= high.ordinal else o < high.ordinal))
			}
		}
	}
}
