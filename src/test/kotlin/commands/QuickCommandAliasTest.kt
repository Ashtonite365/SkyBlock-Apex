// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.commands.ApexCommands
import dev.ashtonite365.apex.features.chat.QuickCommands

class QuickCommandAliasTest {
	private val calls = mutableListOf<String>()

	private fun clientTree(join: Boolean = true, dh: Boolean = true) = CommandDispatcher<Any>().apply {
		var apex = literal<Any>("apex")
		if (join) apex = apex.then(literal<Any>("join")
			.executes { calls += "join:explain"; 1 }
			.then(argument<Any, String>("what", StringArgumentType.greedyString()).executes { calls += "join:" + StringArgumentType.getString(it, "what"); 2 }))
		if (dh) apex = apex.then(literal<Any>("dh").executes { calls += "dh"; 3 })
		register(apex)
		for ((alias, target) in ApexCommands.ALIASES) ApexCommands.registerAlias(this, alias, target)
	}

	@Test
	fun joinAndDhAreClientRootCommands() {
		assertEquals("join", ApexCommands.ALIASES["join"])
		assertEquals("dh", ApexCommands.ALIASES["dh"])
		val d = clientTree()
		assertEquals(d.execute("apex join f7", Any()), d.execute("join f7", Any()))
		assertEquals(d.execute("apex join", Any()), d.execute("join", Any()))
		assertEquals(d.execute("apex dh", Any()), d.execute("dh", Any()))
		assertEquals(listOf("join:f7", "join:f7", "join:explain", "join:explain", "dh", "dh"), calls)
	}

	@Test
	fun survivesRebuildAndRespectsToggles() {

		repeat(3) { val d = clientTree(); assertNotNull(d.root.getChild("join")); assertNotNull(d.root.getChild("dh")) }

		val off = clientTree(dh = false)
		assertNull(off.root.getChild("dh"))
		assertNotNull(off.root.getChild("join"))
	}

	@Test
	fun offSkyblockJoinIsPassedOnUnchanged() {
		assertEquals("join bedwars", QuickCommands.serverJoinCommand("bedwars"))
	}
}
