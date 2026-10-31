// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.chat.AutoCompletions

class WikiKeyMigrationTest {
	private fun run(json: String) = JsonParser.parseString(json).asJsonObject.also { AutoCompletions.migrateWikiKey(it) }

	@Test fun unboundBecomesJ() {
		val d = run("{\"auto-completions\":{\"wiki-key\":{\"button\":null,\"modifiers\":{\"modifiers\":0}}}}")
		assertEquals(com.mojang.blaze3d.platform.InputConstants.KEY_J, d.getAsJsonObject("auto-completions").getAsJsonObject("wiki-key").getAsJsonObject("button").get("keyCode").asInt)
	}

	@Test fun userBindIsKept() {
		val src = "{\"auto-completions\":{\"wiki-key\":{\"button\":{\"keyCode\":75},\"modifiers\":{\"modifiers\":0}}}}"
		assertEquals(JsonParser.parseString(src), run(src))
		val withMods = "{\"auto-completions\":{\"wiki-key\":{\"button\":null,\"modifiers\":{\"modifiers\":2}}}}"
		assertEquals(JsonParser.parseString(withMods), run(withMods))
	}

	@Test fun absentIsLeftAlone() {
		assertEquals(JsonParser.parseString("{\"auto-completions\":{}}"), run("{\"auto-completions\":{}}"))
		assertEquals(JsonParser.parseString("{}"), run("{}"))
	}
}
