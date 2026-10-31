// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

object ModSettingsPages {
	sealed interface Row {
		val label: String
		val desc: String
		data class Module(override val label: String) : Row { override val desc get() = "" }
		data class Toggle(override val label: String, override val desc: String, val on: () -> Boolean, val flip: () -> Unit) : Row
		data class Cycle(override val label: String, override val desc: String, val value: () -> String, val step: (Int) -> Unit) : Row

		data class Option(val key: String) : Row { override val label get() = key; override val desc get() = "" }
	}

	data class Page(val id: String, val title: String, val rows: () -> List<Row>) {
		val moved: Set<String> get() = rows().filterIsInstance<Row.Option>().map { it.key }.toSet()
	}

	private val pages = java.util.concurrent.CopyOnWriteArrayList<Page>()

	fun register(p: Page) { if (pages.none { it.id == p.id }) pages += p }
	val all: List<Page> get() = pages.toList()
	fun byId(id: String): Page? = pages.firstOrNull { it.id == id }

	fun movedKeys(): Set<String> = pages.flatMapTo(HashSet()) { it.moved }

	fun search(p: Page, query: String, optionText: (String) -> Pair<String, String>?): List<Row> = p.rows().filter { r ->
		when (r) {
			is Row.Module -> false
			is Row.Option -> optionText(r.key)?.let { (l, d) -> l.contains(query, true) || d.contains(query, true) } ?: false
			else -> r.label.contains(query, true) || r.desc.contains(query, true)
		}
	}

	fun resetForTests() { pages.clear() }
}
