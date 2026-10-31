// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

data class MigrationEntry(val oldKey: String, val newPath: SettingPath, val kind: String, val group: String = "") {
	val oldConfig: String get() = oldKey.substringBefore('.')
	val oldOption: String get() = oldKey.substringAfter('.')
}

enum class MergePolicy {

	ANY_TRUE,

	FIRST_SAVED,

	ALL_TRUE,
}

data class DuplicateRule(val oldKeys: List<String>, val target: SettingPath, val policy: MergePolicy)

data class MigrationPrefix(
	val oldPrefix: String,
	val newPrefix: SettingPath,
	val kind: String,
	val group: String = "",

	val numeric: Boolean = false,
) {
	init {
		require('.' in oldPrefix && oldPrefix.substringAfter('.').isNotEmpty()) { "Prefix rule needs config.key-prefix: '$oldPrefix'" }
	}

	private fun suffixOk(rest: String) = rest.isNotEmpty() && (!numeric || rest.all { it in '0'..'9' })
	fun matches(oldKey: String) = oldKey.startsWith(oldPrefix) && suffixOk(oldKey.substring(oldPrefix.length))
	fun target(oldKey: String) = newPrefix.copy(key = newPrefix.key + oldKey.removePrefix(oldPrefix))
	fun oldKeyFor(path: SettingPath): String? =
		if (path.main == newPrefix.main && path.subsection == newPrefix.subsection && path.key.startsWith(newPrefix.key)
			&& suffixOk(path.key.substring(newPrefix.key.length))) oldPrefix + path.key.removePrefix(newPrefix.key) else null
}

data class DerivedInput(val oldKey: String, val default: JsonElement)

data class DerivedRule(val inputs: List<DerivedInput>, val target: SettingPath, val expression: String) {
	private val program: DerivedExpr = DerivedExpr.parse(expression, inputs.size)

	fun evaluate(saved: Map<String, JsonElement>): JsonElement? {
		if (inputs.none { it.oldKey in saved }) return null
		return program.eval(inputs.map { saved[it.oldKey] ?: it.default })
	}

	companion object {
		fun parseInput(text: String): DerivedInput {
			val key = text.substringBefore('=')
			val def = if ('=' in text) Json.parseToJsonElement(text.substringAfter('=')) else JsonNull
			require('.' in key) { "Derived input needs config.key: '$text'" }
			return DerivedInput(key, def)
		}
	}
}

sealed interface DerivedExpr {
	fun eval(args: List<JsonElement>): JsonElement

	data class Arg(val index: Int) : DerivedExpr {
		override fun eval(args: List<JsonElement>) = args[index]
	}
	data class Const(val value: Boolean) : DerivedExpr {
		override fun eval(args: List<JsonElement>) = JsonPrimitive(value)
	}
	data class Not(val e: DerivedExpr) : DerivedExpr {
		override fun eval(args: List<JsonElement>) = JsonPrimitive(!truthy(e.eval(args)))
	}
	data class And(val a: DerivedExpr, val b: DerivedExpr) : DerivedExpr {
		override fun eval(args: List<JsonElement>) = JsonPrimitive(truthy(a.eval(args)) && truthy(b.eval(args)))
	}
	data class Or(val a: DerivedExpr, val b: DerivedExpr) : DerivedExpr {
		override fun eval(args: List<JsonElement>) = JsonPrimitive(truthy(a.eval(args)) || truthy(b.eval(args)))
	}

	companion object {
		fun truthy(e: JsonElement) = (e as? JsonPrimitive)?.booleanOrNull == true

		fun parse(text: String, argCount: Int): DerivedExpr {
			val tokens = Regex("""\$\d+|true|false|&&|\|\||!|\(|\)|\S""").findAll(text).map { it.value }.toList()
			var i = 0
			fun peek() = tokens.getOrNull(i)
			fun next() = tokens.getOrNull(i++) ?: throw IllegalArgumentException("Unexpected end of '$text'")
			lateinit var or: () -> DerivedExpr
			fun atom(): DerivedExpr = when (val t = next()) {
				"!" -> Not(atom())
				"(" -> or().also { require(next() == ")") { "Missing ) in '$text'" } }
				"true" -> Const(true)
				"false" -> Const(false)
				else -> {
					require(t.startsWith("$")) { "Bad token '$t' in '$text'" }
					val n = t.drop(1).toInt()
					require(n in 1..argCount) { "\$$n out of range (1..$argCount) in '$text'" }
					Arg(n - 1)
				}
			}
			fun and(): DerivedExpr { var e = atom(); while (peek() == "&&") { i++; e = And(e, atom()) }; return e }
			or = { var e = and(); while (peek() == "||") { i++; e = Or(e, and()) }; e }
			val e = or()
			require(i == tokens.size) { "Trailing '${tokens.drop(i).joinToString(" ")}' in '$text'" }
			return e
		}
	}
}

class MigrationMap(
	val entries: List<MigrationEntry>,
	val duplicates: List<DuplicateRule> = emptyList(),
	val prefixes: List<MigrationPrefix> = emptyList(),
	val derived: List<DerivedRule> = emptyList(),
) {
	private val byOld = entries.associateBy { it.oldKey }
	private val byNew = entries.associateBy { it.newPath.toString() }
	private val dupByTarget = duplicates.associateBy { it.target.toString() }

	init {
		val derivedTargets = derived.map { it.target.toString() }
		require(derivedTargets.distinct().size == derivedTargets.size) { "Derived target used twice" }
		require(prefixes.map { it.oldPrefix }.distinct().size == prefixes.size) { "Prefix rule given twice" }
		for (e in entries) require(prefixes.none { it.matches(e.oldKey) }) { "Old key ${e.oldKey} is also covered by a prefix rule" }
		require(byOld.size == entries.size) { "Old key mapped twice" }
		val paths = entries.map { it.newPath.toString() }
		val dups = paths.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
		require(dups.isEmpty()) { "New paths used twice (use a DuplicateRule instead): $dups" }
	}

	operator fun get(oldKey: String): MigrationEntry? = byOld[oldKey]

	fun resolve(oldKey: String): SettingPath? =
		byOld[oldKey]?.newPath ?: prefixes.filter { it.matches(oldKey) }.maxByOrNull { it.oldPrefix.length }?.target(oldKey)

	fun oldKeysFor(path: SettingPath): List<String> {
		dupByTarget[path.toString()]?.let { return it.oldKeys }
		byNew[path.toString()]?.let { return listOf(it.oldKey) }
		return prefixes.mapNotNull { it.oldKeyFor(path) }.take(1)
	}

	companion object {
		const val RESOURCE = "/assets/apex/settings/migration-map.tsv"
		const val DUPLICATES_RESOURCE = "/assets/apex/settings/duplicates.tsv"
		const val DERIVED_RESOURCE = "/assets/apex/settings/derived.tsv"

		fun parse(tsv: String, duplicatesTsv: String = "", derivedTsv: String = ""): MigrationMap {
			val all = rows(tsv)
			all.forEach { c -> require(c.size >= 3) { "Bad migration row: $c" } }
			val (prefixRows, exactRows) = all.partition { it[0].endsWith("*") || it[0].endsWith("#") }
			val entries = exactRows.map { c -> MigrationEntry(c[0], SettingPath.parse(c[1]), c[2], c.getOrElse(3) { "" }) }
			val prefixes = prefixRows.map { c ->
				val mark = c[0].last()
				require(c[1].endsWith(mark)) { "Prefix row must map to a prefix path ending in the same $mark: $c" }
				MigrationPrefix(c[0].dropLast(1), SettingPath.parse(c[1].dropLast(1)), c[2], c.getOrElse(3) { "" }, numeric = mark == '#')
			}
			val dups = rows(duplicatesTsv).map { c ->
				require(c.size == 3) { "Bad duplicate row: $c" }
				DuplicateRule(c[0].split(','), SettingPath.parse(c[1]), MergePolicy.valueOf(c[2]))
			}
			val derived = rows(derivedTsv).map { c ->
				require(c.size == 3) { "Bad derived row: $c" }
				DerivedRule(c[0].split(',').map(DerivedRule::parseInput), SettingPath.parse(c[1]), c[2])
			}
			return MigrationMap(entries, dups, prefixes, derived)
		}

		private fun rows(tsv: String) = tsv.lineSequence()
			.filter { it.isNotBlank() && !it.startsWith("#") }
			.map { line -> line.split('\t').map { it.trim() }.dropLastWhile { it.isEmpty() } }
			.toList()

		private fun resource(name: String) =
			MigrationMap::class.java.getResourceAsStream(name)?.bufferedReader()?.use { it.readText() }

		fun bundled(): MigrationMap = parse(
			requireNotNull(resource(RESOURCE)) { "Missing $RESOURCE" },
			resource(DUPLICATES_RESOURCE) ?: "",
			resource(DERIVED_RESOURCE) ?: "",
		)
	}
}

data class MigrationResult(
	val tree: JsonObject,

	val unmapped: List<String>,

	val conflicts: List<String>,
	val alreadyMigrated: Boolean = false,
)

class ConfigMigrator(private val map: MigrationMap) {
	companion object {
		const val VERSION_KEY = "_settingsVersion"
		const val LEGACY_KEY = "_legacy"
		const val VERSION = 2

		val REMOVED_KEYS = listOf(
			"dungeon-secret-routes.", "dungeon-secret-tracker.",
			"breakable-walls.stonk",
			"far-terrain.",
			"apex-far-terrain.generous-occlusion-culling",
			"stat-bars.debug-log-action-bar",
		)

		fun isRemoved(oldKey: String) = REMOVED_KEYS.any { if (it.endsWith('.')) oldKey.startsWith(it) else oldKey == it }
	}

	fun migrate(old: JsonObject): MigrationResult {
		if ((old[VERSION_KEY] as? JsonPrimitive)?.intOrNull?.let { it >= VERSION } == true)
			return MigrationResult(old, emptyList(), emptyList(), alreadyMigrated = true)

		val out = linkedMapOf<String, MutableMap<String, MutableMap<String, JsonElement>>>()
		fun put(path: SettingPath, value: JsonElement) {
			out.getOrPut(path.main.id) { linkedMapOf() }.getOrPut(path.subsection) { linkedMapOf() }[path.key] = value
		}

		val saved = linkedMapOf<String, JsonElement>()
		for ((config, body) in old) {
			val obj = body as? JsonObject ?: continue
			for ((key, value) in obj) saved["$config.$key"] = value
		}

		val handledByDup = map.duplicates.flatMap { it.oldKeys }.toSet()
		val unmapped = mutableListOf<String>()
		for ((oldKey, value) in saved) {
			if (oldKey in handledByDup || isRemoved(oldKey)) continue
			val target = map.resolve(oldKey)
			if (target == null) unmapped += oldKey else put(target, value)
		}

		val conflicts = mutableListOf<String>()
		for (rule in map.duplicates) {
			val values = rule.oldKeys.mapNotNull { k -> saved[k]?.let { k to it } }
			if (values.isEmpty()) continue
			if (values.map { it.second }.distinct().size > 1) conflicts += "${rule.target}: ${values.joinToString { "${it.first}=${it.second}" }}"
			val merged = when (rule.policy) {
				MergePolicy.FIRST_SAVED -> values.first().second
				MergePolicy.ANY_TRUE -> JsonPrimitive(values.any { (it.second as? JsonPrimitive)?.booleanOrNull == true })
				MergePolicy.ALL_TRUE -> JsonPrimitive(values.all { (it.second as? JsonPrimitive)?.booleanOrNull == true })
			}
			put(rule.target, merged)
		}

		for (rule in map.derived) {
			val value = rule.evaluate(saved) ?: continue
			val prev = out[rule.target.main.id]?.get(rule.target.subsection)?.get(rule.target.key)
			if (prev != null && prev != value) conflicts += "${rule.target}: derived ${rule.expression} = $value (was $prev)"
			put(rule.target, value)
		}

		val tree = buildJsonObject {
			put(VERSION_KEY, JsonPrimitive(VERSION))
			for ((main, subs) in out) put(main, JsonObject(subs.mapValues { JsonObject(it.value) }))
			put(LEGACY_KEY, old)
		}
		return MigrationResult(tree, unmapped, conflicts)
	}

	fun lookup(tree: JsonObject, path: SettingPath): JsonElement? =
		tree[path.main.id]?.jsonObject?.get(path.subsection)?.jsonObject?.get(path.key)
}
