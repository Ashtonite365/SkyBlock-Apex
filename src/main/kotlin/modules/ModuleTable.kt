// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

enum class ModuleTag(val id: String) {
	ALLOWED("allowed"),

	RED("red"),

	PURPLE("purple"),
	;

	val gated get() = this != ALLOWED

	companion object {
		fun of(id: String) = entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown tag '$id'")
		fun highest(tags: Iterable<ModuleTag>) = tags.maxOrNull() ?: ALLOWED
	}
}

enum class RowKind(val id: String) {

	MODULE("module"),

	CORE("core"),

	EXCLUDED("excluded"),

	OPTION("option"),

	SCREEN("screen"),
	;

	val holder get() = this == MODULE || this == CORE || this == EXCLUDED

	companion object {
		fun of(id: String) = entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown kind '$id'")
	}
}

data class ModuleRow(
	val key: String,
	val kind: RowKind,
	val category: String,

	val tag: ModuleTag,

	val effective: ModuleTag,
	val rule: String,
	val deps: List<String>,

	val master: String,

	val askAshton: Boolean,
	val note: String,
) {

	val holder get() = if (kind == RowKind.OPTION) key.substringBefore('.') else key
	val option get() = if (kind == RowKind.OPTION) key.substringAfter('.') else null
}

class ModuleTable(val rows: List<ModuleRow>, val sourceSha256: String?) {
	val byKey: Map<String, ModuleRow> = rows.associateBy { it.key }
	val holders: List<ModuleRow> = rows.filter { it.kind.holder }
	val modules: List<ModuleRow> = rows.filter { it.kind == RowKind.MODULE }
	val optionRows: Map<String, List<ModuleRow>> = rows.filter { it.kind == RowKind.OPTION }.groupBy { it.holder }

	init {
		val dup = rows.groupBy { it.key }.filterValues { it.size > 1 }.keys
		require(dup.isEmpty()) { "duplicate keys in module table: $dup" }
	}

	fun tagOf(holder: String, option: String? = null): ModuleTag? {
		if (option != null) byKey["$holder.$option"]?.let { return it.tag }
		return byKey[holder]?.tag
	}

	fun descriptors(): List<ModuleDescriptor> = modules.map { ModuleDescriptor(it.key, it.category, it.tag, it.deps) }

	companion object {
		const val RESOURCE = "/assets/apex/modules/modules.tsv"
		val COLUMNS = listOf("key", "kind", "category", "tag", "effective", "rule", "deps", "master", "ask", "note")

		fun parse(text: String): ModuleTable {
			var sha: String? = null
			var header: List<String>? = null
			val rows = mutableListOf<ModuleRow>()
			for ((n, line) in text.lineSequence().withIndex()) {
				if (line.isEmpty()) continue
				if (line.startsWith("#")) {
					if (line.startsWith("# source-sha256:")) sha = line.substringAfter(':').trim()
					continue
				}
				val f = line.split('\t')
				if (header == null) {
					require(f == COLUMNS) { "module table header is $f, want $COLUMNS" }
					header = f
					continue
				}
				require(f.size == COLUMNS.size) { "module table line ${n + 1}: ${f.size} columns" }
				rows += ModuleRow(
					key = f[0], kind = RowKind.of(f[1]), category = f[2], tag = ModuleTag.of(f[3]), effective = ModuleTag.of(f[4]),
					rule = f[5], deps = f[6].split(',').filter { it.isNotEmpty() }, master = f[7], askAshton = f[8] == "ashton", note = f[9],
				)
			}
			requireNotNull(header) { "module table has no header" }
			return ModuleTable(rows, sha)
		}

		fun load(): ModuleTable = parse(
			ModuleTable::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
				?: error("missing $RESOURCE")
		)

		val DEFAULT: ModuleTable by lazy { load() }
	}
}
