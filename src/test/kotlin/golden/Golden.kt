// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

object Golden {
	private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

	fun write(name: String, cases: List<Pair<String, String>>) {
		val arr = JsonArray()
		for ((i, o) in cases) arr.add(JsonObject().apply { addProperty("in", i); addProperty("out", o) })
		val f = File("build/golden-out/$name.json")
		f.parentFile.mkdirs()
		f.writeText(gson.toJson(JsonObject().apply { addProperty("cases", cases.size); add("data", arr) }) + "\n")
	}

	fun load(name: String): List<Pair<String, String>> {
		val text = Golden::class.java.getResourceAsStream("/apex-golden/$name.json")?.reader()?.readText()
			?: error("missing golden fixture apex-golden/$name.json")
		val root = JsonParser.parseString(text).asJsonObject
		return root.getAsJsonArray("data").map { it.asJsonObject.let { o -> o["in"].asString to o["out"].asString } }
	}

	fun verify(name: String, actual: List<Pair<String, String>>, minCases: Int = 1, only: (String) -> Boolean = { true }): Int {
		val expected = load(name).filter { only(it.first) }
		assertTrue(expected.size >= minCases, "$name: only ${expected.size} recorded cases")
		assertEquals(expected.map { it.first }, actual.map { it.first }, "$name: case keys differ")
		val bad = expected.zip(actual).filter { (e, a) -> withoutPlatformInit(e.second) != withoutPlatformInit(a.second) }
			.map { (e, a) -> "IN  ${e.first}\nEXP ${e.second}\nGOT ${a.second}" }
		assertEquals(0, bad.size, "$name: ${bad.size}/${expected.size} differ; first:\n" + bad.take(5).joinToString("\n---\n"))
		File("build/golden-counts").mkdirs()
		File("build/golden-counts/$name.txt").writeText("${expected.size}\n")
		return expected.size
	}

	private val platformInit = Regex("THROWS (ServiceConfigurationError|NoClassDefFoundError)\\b")
	private fun withoutPlatformInit(text: String) = platformInit.replace(text, "THROWS moulconfig-platform-unavailable")

	fun run(f: () -> String): String = runCatching(f).getOrElse { e -> "THROWS ${e::class.simpleName}" }
}
