// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

class LayoutOrder(rows: List<Row>) {
	data class Row(val main: MainSection, val group: String, val subsection: String, val configKey: String)

	private val groupIndex = HashMap<String, Int>()
	private val subsectionIndex = HashMap<String, Int>()
	private val rowIndex = HashMap<String, Int>()
	private val subsectionsByMain = LinkedHashMap<MainSection, MutableList<String>>()
	private val groupOf = HashMap<String, String>()

	init {
		rows.forEachIndexed { i, r ->
			groupIndex.putIfAbsent("${r.main.id}/${r.group}", i)
			if (subsectionIndex.putIfAbsent("${r.main.id}.${r.subsection}", i) == null) {
				subsectionsByMain.getOrPut(r.main) { mutableListOf() } += r.subsection
				groupOf["${r.main.id}.${r.subsection}"] = r.group
			}
			rowIndex.putIfAbsent(r.configKey, i)
		}
	}

	fun groupIndex(main: MainSection, group: String): Int = groupIndex["${main.id}/$group"] ?: Int.MAX_VALUE
	fun subsectionIndex(main: MainSection, id: String): Int = subsectionIndex["${main.id}.$id"] ?: Int.MAX_VALUE
	fun rowIndex(configKey: String): Int = rowIndex[configKey] ?: Int.MAX_VALUE

	fun subsections(main: MainSection): List<String> = subsectionsByMain[main].orEmpty()

	fun groupOf(main: MainSection, id: String): String? = groupOf["${main.id}.$id"]

	companion object {
		const val RESOURCE = "/assets/apex/settings/layout-order.tsv"

		fun parse(tsv: String): LayoutOrder = LayoutOrder(tsv.lineSequence()
			.filter { it.isNotBlank() && !it.startsWith("#") }
			.map { line ->
				val c = line.split('\t')
				require(c.size == 4) { "Bad layout row: $line" }
				Row(requireNotNull(MainSection.byId(c[0])) { "Unknown main section in $line" }, c[1], c[2], c[3])
			}.toList())

		val EMPTY = LayoutOrder(emptyList())

		val bundled: LayoutOrder by lazy {
			LayoutOrder::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { parse(it.readText()) } ?: EMPTY
		}
	}
}
