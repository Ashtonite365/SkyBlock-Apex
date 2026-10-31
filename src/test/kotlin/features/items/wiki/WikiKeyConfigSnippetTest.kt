// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import com.google.gson.JsonParser
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.ItemWikiMigration
import dev.ashtonite365.apex.keybindings.GenericInputButton

class WikiKeyConfigSnippetTest {
	private val snippet = """{"item-wiki":{"enabled":true,"hold-tenths":10,"key":{"button":{"keyCode":13},"modifiers":{"modifiers":0}}}}"""

	@Test fun keyCode13IsJOn263() = assertEquals(13, com.mojang.blaze3d.platform.InputConstants.KEY_J)

	@Test fun snippetLoadsAsJ() {
		val button = Json.decodeFromString(GenericInputButton.Serializer, """{"keyCode":13}""")
		assertEquals(GenericInputButton.ofKeyCode(com.mojang.blaze3d.platform.InputConstants.KEY_J), button)
	}

	@Test fun migrationLeavesTheSnippetAlone() {
		val d = JsonParser.parseString(snippet).asJsonObject
		ItemWikiMigration.migrate(d)
		assertEquals(JsonParser.parseString(snippet), d)
	}
}
