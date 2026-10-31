// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.settings

object LayoutMoves {
	const val RESOURCE = "/assets/apex/settings/layout-moves-1006.tsv"

	fun parse(text: String): Map<String, String> {
		val out = LinkedHashMap<String, String>()
		for (raw in text.lineSequence()) {
			if (raw.isBlank() || raw.startsWith("#")) continue
			val cols = raw.split('\t')
			require(cols.size >= 2 && cols[0].isNotBlank() && cols[1].isNotBlank()) { "Bad layout move: '$raw'" }
			require(out.put(cols[0], cols[1]) == null) { "Path ${cols[0]} moved twice" }
		}
		require(out.values.toSet().size == out.size) { "Two paths moved to the same place" }
		return out
	}

	val bundled: Map<String, String> by lazy {
		LayoutMoves::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { parse(it.readText()) } ?: emptyMap()
	}
}
