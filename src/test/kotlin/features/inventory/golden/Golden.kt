// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import java.io.File
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.Assertions

object Golden {
	private val projectDir: File by lazy {
		System.getProperty("apex.projectDir")?.let(::File)
			?: generateSequence(File(".").absoluteFile) { it.parentFile }.first { File(it, "build.gradle.kts").isFile }
	}

	fun file(name: String): File = File(projectDir, "src/test/resources/golden/inv/$name.tsv")

	fun write(name: String, rows: List<Pair<String, String>>) {
		val f = file(name)
		f.parentFile.mkdirs()
		f.writeText(rows.joinToString("") { (k, v) -> "${esc(k)}\t${esc(v)}\n" })
	}

	fun read(name: String): List<Pair<String, String>> =
		file(name).readLines().filter { it.isNotEmpty() }.map { line ->
			val tab = line.indexOf('\t')
			unesc(line.substring(0, tab)) to unesc(line.substring(tab + 1))
		}

	fun check(name: String, compute: (String) -> String): Int {
		val rows = read(name)
		Assertions.assertTrue(rows.isNotEmpty(), "golden record $name is empty")
		val wrong = rows.mapNotNull { (k, v) ->
			val got = runCatching { compute(k) }.getOrElse { "ERR:" + it.javaClass.simpleName }
			if (got == v) null else "$k\n  want: $v\n  got:  $got"
		}
		Assertions.assertEquals(emptyList<String>(), wrong, "$name: ${wrong.size} of ${rows.size} differ")
		return rows.size
	}

	private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
	private fun unesc(s: String): String {
		val sb = StringBuilder()
		var i = 0
		while (i < s.length) {
			val c = s[i]
			if (c == '\\' && i + 1 < s.length) {
				sb.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
			} else { sb.append(c); i++ }
		}
		return sb.toString()
	}

	fun styled(c: Component): String {
		val st = c.style
		val flags = listOfNotNull(st.color?.serialize(), "b".takeIf { st.isBold }, "i".takeIf { st.isItalic },
			"u".takeIf { st.isUnderlined }, st.clickEvent?.let { "click=$it" }, st.hoverEvent?.let { "hover" })
		val body = when (val content = c.contents) {
			is PlainTextContents -> "\"" + content.text() + "\""
			is TranslatableContents -> "tr(" + content.key + (content.fallback?.let { "|$it" } ?: "") + content.args.joinToString("") { a ->
				"," + if (a is Component) styled(a) else a.toString() } + ")"
			else -> content.toString()
		}
		val kids = if (c.siblings.isEmpty()) "" else c.siblings.joinToString(" ", " [", "]") { styled(it) }
		return "{" + flags.joinToString(",") + "}" + body + kids
	}
}
