// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.RowKind

class ModuleOwnersTest {
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))
	private val rules = setOf("file", "nested", "package", "holder", "core")

	private val owners: List<List<String>> by lazy {
		val text = javaClass.getResourceAsStream("/assets/apex/modules/owners.tsv")!!.readBytes().decodeToString()
		text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split('\t') }
	}

	private fun strip(source: String): String {
		val out = StringBuilder(source.length)
		var i = 0
		val n = source.length
		while (i < n) {
			val c = source[i]
			when {
				source.startsWith("//", i) -> { while (i < n && source[i] != '\n') i++ }
				source.startsWith("/*", i) -> { val end = source.indexOf("*/", i + 2); i = if (end < 0) n else end + 2 }
				source.startsWith("\"\"\"", i) -> {
					val end = source.indexOf("\"\"\"", i + 3); i = if (end < 0) n else end + 3; out.append("\"\"")
				}
				c == '"' -> {
					i++
					while (i < n && source[i] != '"' && source[i] != '\n') i += if (source[i] == '\\') 2 else 1
					i++; out.append("\"\"")
				}
				c == '\'' && i + 2 < n && (source[i + 1] == '\\' || source[i + 2] == '\'') -> {

					val end = source.indexOf('\'', i + 2 + if (source[i + 1] == '\\') 1 else 0); i = if (end < 0) n else end + 1
				}
				else -> { out.append(c); i++ }
			}
		}
		return out.toString()
	}

	private fun subscribers(source: String): List<String> {
		val text = strip(source)
		val stack = ArrayList<Pair<String, String>?>()
		var pending: Pair<String, String>? = null
		val found = LinkedHashSet<String>()
		for (m in Regex("\\b(object|class|interface)\\s+(\\w+)|[{}]|@Subscribe\\b").findAll(text)) {
			when {
				m.groupValues[1].isNotEmpty() -> pending = m.groupValues[1] to m.groupValues[2]
				m.value == "{" -> { stack += pending; pending = null }
				m.value == "}" -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
				else -> {
					val path = stack.filterNotNull()
					if (path.isNotEmpty() && path.last().first == "object") found += path.joinToString(".") { it.second }
				}
			}
		}
		return found.toList()
	}

	private fun scanned(): Set<String> {
		val out = HashSet<String>()
		val sets = project.resolve("src").listFiles()!!.filter { it.isDirectory && it.name !in setOf("test", "gametest") }
		for (set in sets) for (f in set.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
			val source = f.readText()
			val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(source)?.groupValues?.get(1).orEmpty()
			for (o in subscribers(source)) out += "$pkg.$o"
		}
		return out
	}

	@Test fun everySubscriberObjectHasAnOwner() {
		val rows = owners.map { it[0] }.toSet()
		val found = scanned()
		assertTrue(found.size > 300, "only ${found.size} subscriber objects found")
		assertEquals(emptyList<String>(), (found - rows).sorted(), "@Subscribe objects with no owner: run python3 scripts/gen_module_owners.py")
		assertEquals(emptyList<String>(), (rows - found).sorted(), "owners.tsv rows that name no @Subscribe object")
	}

	@Test fun ownersAreModuleRowsOrCore() {
		val table = ModuleTable.DEFAULT
		for (row in owners) {
			assertEquals(3, row.size, row.joinToString("\t"))
			val (obj, owner, rule) = row
			assertTrue(rule in rules, "$obj: unknown rule $rule")
			if (owner == "core") assertEquals("core", rule, "$obj: core owner from rule $rule")
			else assertEquals(RowKind.MODULE, table.byKey[owner]?.kind, "$obj: owner $owner is not a module row")
		}
		assertEquals(owners.size, owners.map { it[0] }.toSet().size, "duplicate rows")
	}
}
