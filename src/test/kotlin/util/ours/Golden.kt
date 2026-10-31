// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.fail

class Golden(private val name: String) {
	private val rows = ArrayList<String>()

	fun case(label: String, compute: () -> Any?) {
		val value = try {
			render(compute())
		} catch (e: Throwable) {
			"!" + e.javaClass.name + ": " + e.message
		}
		rows += esc(label) + "\t" + esc(value)
	}

	fun verify() {
		val file = dir.resolve("$name.tsv")
		if (System.getenv("APEX_GOLDEN_RECORD") == "1") {
			Files.createDirectories(dir)
			Files.writeString(file, rows.joinToString("\n", postfix = "\n"))
			return
		}
		if (!Files.exists(file)) fail<Unit>("missing golden fixture $file")
		val expected = Files.readAllLines(file)
		val bad = ArrayList<String>()
		if (expected.size != rows.size) bad += "row count: expected ${expected.size}, got ${rows.size}"
		for (i in 0 until minOf(expected.size, rows.size)) {
			if (expected[i] != rows[i]) bad += "#$i\n  want ${expected[i]}\n  got  ${rows[i]}"
			if (bad.size > 15) break
		}
		if (bad.isNotEmpty()) fail<Unit>("$name: ${bad.size} mismatch(es)\n" + bad.joinToString("\n"))
	}

	companion object {
		val dir: Path = Path.of(System.getProperty("apex.projectDir") ?: ".").resolve("src/test/resources/ours-util-golden")

		fun esc(s: String) = buildString {
			for (c in s) when (c) {
				'\\' -> append("\\\\")
				'\n' -> append("\\n")
				'\r' -> append("\\r")
				'\t' -> append("\\t")
				else -> if (c.code < 0x20 || c.isSurrogate()) append("\\u%04x".format(c.code)) else append(c)
			}
		}

		fun render(v: Any?): String = when (v) {
			null -> "<null>"
			is FloatArray -> v.joinToString(",", "f[", "]")
			is IntArray -> v.joinToString(",", "i[", "]")
			is LongArray -> v.joinToString(",", "l[", "]")
			is ByteArray -> v.joinToString(",", "b[", "]")
			is Array<*> -> v.joinToString(",", "[", "]") { render(it) }
			is Iterable<*> -> v.joinToString(",", "(", ")") { render(it) }
			is Sequence<*> -> v.toList().let { render(it) }
			is Float -> "F" + v.toRawBits().toString(16) + "/" + v
			is Double -> "D" + v.toRawBits().toString(16) + "/" + v
			else -> v.toString()
		}

		inline fun golden(name: String, body: Golden.() -> Unit) {
			val g = Golden(name)
			g.body()
			g.verify()
		}
	}
}
