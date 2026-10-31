// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

import java.util.Locale

enum class ModuleBox { NONE, RED, PURPLE }

data class ModuleListRow(
	val id: String,
	val name: String,
	val box: ModuleBox,
	val switchedOn: Boolean,
	val inactive: InactiveReason?,
) {
	val active get() = inactive == null
}

data class ModuleListGroup(val header: String?, val rows: List<ModuleListRow>)

data class ModuleListSection(val category: String, val groups: List<ModuleListGroup>) {
	val rows get() = groups.flatMap { it.rows }
}

object ModuleListModel {

	val SECTION_ORDER = listOf(
		"Timers", "GUI", "HUD", "Waypoints", "Dungeons", "Secrets", "Mining", "Farming", "Fishing", "Custom Menus",
		"Experimentation", "Miscellaneous",
	)

	const val SEPARATOR = " > "

	fun listed(row: ModuleRow, dangerousAllowed: Boolean) =
		row.kind == RowKind.MODULE && (row.tag != ModuleTag.PURPLE || dangerousAllowed)

	fun box(table: ModuleTable, row: ModuleRow, dangerousAllowed: Boolean): ModuleBox {
		val seen = buildList {
			add(row.tag)
			for (o in table.optionRows[row.key].orEmpty()) if (o.tag != ModuleTag.PURPLE || dangerousAllowed) add(o.tag)
		}
		return when (ModuleTag.highest(seen)) {
			ModuleTag.ALLOWED -> ModuleBox.NONE
			ModuleTag.RED -> ModuleBox.RED
			ModuleTag.PURPLE -> ModuleBox.PURPLE
		}
	}

	fun matches(query: String, row: ModuleRow, name: String): Boolean {
		val words = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
		if (words.isEmpty()) return true
		val hay = listOf(name, row.key, row.category).map { it.lowercase(Locale.ROOT) }
		return words.all { w -> hay.any { w in it } }
	}

	fun inactiveNote(reason: InactiveReason?, nameOf: (String) -> String): String? = when (reason) {
		null, InactiveReason.SwitchedOff -> null
		InactiveReason.NeedsDangerous -> "Dangerous off"
		is InactiveReason.NeedsModule -> "needs " + nameOf(reason.id)
	}

	fun build(
		table: ModuleTable,
		states: Map<String, ModuleState>,
		dangerousAllowed: Boolean,
		nameOf: (String) -> String,
		query: String = "",
	): List<ModuleListSection> {
		val rows = table.modules.filter { listed(it, dangerousAllowed) && matches(query, it, nameOf(it.key)) }
		val byTop = rows.groupBy { it.category.substringBefore(SEPARATOR) }
		val tops = byTop.keys.sortedWith(compareBy<String>({ SECTION_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
		return tops.map { top ->
			val bySub = byTop.getValue(top).groupBy { if (SEPARATOR in it.category) it.category.substringAfter(SEPARATOR) else null }
			val subs = bySub.keys.sortedWith(nullsFirst(compareBy { it }))
			ModuleListSection(top, subs.map { sub ->
				ModuleListGroup(sub, bySub.getValue(sub).map { r ->
					val st = states[r.key]
					ModuleListRow(r.key, nameOf(r.key), box(table, r, dangerousAllowed), st?.switchedOn ?: false, st?.inactive ?: InactiveReason.SwitchedOff)
				}.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
			})
		}
	}
}
