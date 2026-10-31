// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours

import java.io.File
import java.security.MessageDigest
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import org.junit.jupiter.api.Assertions
import dev.ashtonite365.apex.test.testutil.RepoFile
import dev.ashtonite365.apex.util.Client

object OursGolden {
	private val dir: File get() = RepoFile("src/test/resources/ours-core-golden")
	private val recording: Boolean get() = File(dir, "RECORD").isFile

	private val identityHash = Regex("@[0-9a-f]{5,8}\\b|@[0-9a-f]{1,4}(?=\\[)")
	private val tempName = Regex("(/tmp/apex-ours-[a-z]+)[0-9]+")

	private val lambdaAddress = Regex("\\$\\$" + "Lambda/0x[0-9a-f]+")
	private fun withoutLambdaAddress(text: String) = lambdaAddress.replace(text, "\\$\\$" + "Lambda/#")

	private val platformInit = Regex("ERR:(ServiceConfigurationError|NoClassDefFoundError)\\b")
	private fun withoutPlatformInit(text: String) = platformInit.replace(text, "ERR:moulconfig-platform-unavailable")

	fun stable(text: String): String = withoutPlatformInit(withoutLambdaAddress(tempName.replace(identityHash.replace(text, "@#"), "$1#")))

	fun sha(text: String): String =
		MessageDigest.getInstance("SHA-256").digest(stable(text).toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

	fun check(name: String, lines: List<String>) {
		val file = File(dir, "$name.txt")
		val body = lines.joinToString("\n") { stable(it).replace("\n", "\\n") }
		if (recording) {
			file.parentFile.mkdirs()
			file.writeText(body + "\n")
			println("[ours-golden] recorded $name (${lines.size} lines)")
			return
		}
		Assertions.assertTrue(file.isFile, "golden $name missing")
		val expected = file.readText().removeSuffix("\n").split("\n").map { withoutPlatformInit(withoutLambdaAddress(it)) }
		val actual = body.split("\n")
		val firstDiff = expected.indices.firstOrNull { it >= actual.size || expected[it] != actual[it] }
			?: if (actual.size != expected.size) expected.size else null
		if (firstDiff != null) {
			Assertions.fail<Unit>(
				"golden $name differs at line ${firstDiff + 1} (expected ${expected.size} lines, got ${actual.size})\n" +
					"expected: ${expected.getOrNull(firstDiff)}\nactual:   ${actual.getOrNull(firstDiff)}"
			)
		}
		println("[ours-golden] $name: ${actual.size} lines equal")
	}

	fun checkDigests(name: String, entries: List<Pair<String, String>>) {

		runCatching { File(RepoFile("build"), "ours-golden-raw/$name.txt").apply { parentFile.mkdirs() }.writeText(entries.joinToString("\n") { (k, v) -> "$k\t${stable(v).replace("\n", "\\n")}" }) }
		check(name, entries.map { (k, v) -> "$k\t${sha(v)}" })
	}

	private val gson = GsonBuilder().disableHtmlEscaping().create()

	fun canonical(e: JsonElement): JsonElement = when (e) {
		is JsonObject -> JsonObject().also { o -> e.keySet().sorted().forEach { o.add(it, canonical(e.get(it))) } }
		is JsonArray -> JsonArray().also { a -> e.forEach { a.add(canonical(it)) } }
		else -> e
	}

	fun json(e: JsonElement): String = gson.toJson(canonical(e))

	fun text(c: Component?): String = if (c == null) "null" else
		json(ComponentSerialization.CODEC.encodeStart(Client.registries.createSerializationContext(JsonOps.INSTANCE), c).getOrThrow())

	class Digests(private val name: String, private val sampleEvery: Int) {
		private val lines = ArrayList<String>()
		private val samples = ArrayList<String>()
		operator fun plusAssign(entry: Pair<String, String>) {
			if (lines.size % sampleEvery == 0) samples += "${entry.first}\t${entry.second}"
			lines += "${entry.first}\t${sha(entry.second)}"
		}
		fun check() {
			check(name, lines)
			check("$name-sample", samples)
		}
	}
}
