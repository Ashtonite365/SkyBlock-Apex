// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.customchatcommands

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.customchatcommands.ChatCommand
import dev.ashtonite365.apex.features.customchatcommands.CustomChatCommandsUI
import dev.ashtonite365.apex.features.customchatcommands.LegacyMacros

class CustomChatCommandsTest {
	@Test fun renamedCommandAndAlias() {
		assertEquals("customchatcommands", CustomChatCommandsUI.COMMAND)
		assertEquals("ccc", CustomChatCommandsUI.ALIAS)
		assertFalse(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/macros").exists())
		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }
		assertTrue(src.none { it.contains("subcommand(\"macros\"") || it.contains("addSubcommand(\"macros\"") || it.contains("ComboProcessor") || it.contains("KeyComboTrie") })
	}

	@Test fun noDirectKeyToCommandBinding() {

		val dir = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/customchatcommands")
		val sends = dir.walkTopDown().filter { it.isFile }.flatMap { f -> f.readLines().filter { it.contains("sendCommand(") || it.contains("runCommand(") }.map { f.name } }.toList()
		assertEquals(listOf("ChatCommand.kt"), sends)
		val radial = File(dir, "RadialMenu.kt").readText()

		assertTrue(radial.contains("close()\n\t\tif (chosen) picked.resolve()"))
		assertEquals(1, Regex("""\.resolve\(\)""").findAll(radial).count())
	}

	@Test fun legacyWheelsKeptCombosBecomeAnUnboundWheel() {
		val old = """{"comboActions":[{"action":{"type":"command","command":"warp dh"},"keySequence":[]}],
			"wheels":[{"options":[{"type":"command","command":"equipment"},{"type":"command","command":"wardrobe"}]}]}"""
		val w = LegacyMacros.parse(old)
		assertEquals(2, w.size)
		assertEquals(listOf(ChatCommand("equipment"), ChatCommand("wardrobe")), w[0].commands)
		assertEquals(listOf(ChatCommand("warp dh")), w[1].commands)
		assertEquals(emptyList<Any>(), LegacyMacros.parse("{}"))
	}
}
