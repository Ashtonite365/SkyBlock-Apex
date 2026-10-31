// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.commands.ApexCommands

class ApexAliasTest {
	private val calls = mutableListOf<String>()
	private fun dispatcher() = CommandDispatcher<Any>().apply {
		register(literal<Any>("apex").then(literal<Any>("edit")
			.executes { calls += "edit"; 1 }
			.then(argument<Any, String>("arg", StringArgumentType.greedyString()).executes { calls += "edit:" + StringArgumentType.getString(it, "arg"); 2 })))
	}

	@Test
	fun aeBehavesLikeApexEdit() {
		val d = dispatcher()
		assertTrue(ApexCommands.registerAlias(d, "ae", "edit"))
		assertEquals(d.execute("apex edit", Any()), d.execute("ae", Any()))
		assertEquals(d.execute("apex edit foo bar", Any()), d.execute("ae foo bar", Any()))
		assertEquals(listOf("edit", "edit", "edit:foo bar", "edit:foo bar"), calls)
		assertEquals("edit", ApexCommands.ALIASES["ae"])
	}

	@Test
	fun missingTargetIsNoop() {
		val d = CommandDispatcher<Any>()
		assertFalse(ApexCommands.registerAlias(d, "ae", "edit"))
		assertTrue(d.root.getChild("ae") == null)
	}
}
