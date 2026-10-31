package dev.ashtonite365.apex.test.features.world

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.ApexWaypointCommands
import dev.ashtonite365.apex.features.world.ApexWaypointParse
import dev.ashtonite365.apex.features.world.ApexWaypointParse.Pos

class WaypointCompletionTest {
	private val added = ArrayList<String>()
	private val sink = object : ApexWaypointCommands.Sink<Any> {
		override fun add(source: Any, rest: String) { added += rest }
		override fun list(source: Any, all: Boolean) {}
		override fun remove(source: Any, name: String) {}
		override fun names(source: Any) = listOf("Crystal Hollows entrance", "Crypt", "forge")
		override fun lookedAt(source: Any) = "10 64 -3"
		override fun playerPos(source: Any) = Pos(1, 70, 2)
	}
	private val d = CommandDispatcher<Any>().apply {
		register(LiteralArgumentBuilder.literal<Any>("apex")
			.then(ApexWaypointCommands.attach(LiteralArgumentBuilder.literal<Any>("waypoint"), sink)))
		dev.ashtonite365.apex.commands.ApexCommands.registerAlias(this, "waypoint", "waypoint")
	}
	private fun sugg(cmd: String) = d.getCompletionSuggestions(d.parse(cmd, Any())).get().list.map { it.text }
	private fun tips(cmd: String) = d.getCompletionSuggestions(d.parse(cmd, Any())).get().list.map { it.tooltip?.string }

	@Test fun rootSuggestsSubcommands() {
		assertEquals(listOf("add", "list", "remove"), sugg("waypoint ").sorted())
		assertEquals(listOf("add", "list", "remove"), sugg("apex waypoint ").sorted())
		assertEquals(listOf("all"), sugg("waypoint list "))
	}

	@Test fun addHints() {
		assertEquals(listOf("[name]"), sugg("waypoint add "))
		assertEquals(emptyList<String>(), sugg("waypoint add Cry"))
		assertEquals(setOf("10 64 -3", "~ ~ ~", "10", "~", "1"), sugg("waypoint add My base ").toSet())
		assertTrue(tips("waypoint add My base ").contains("x"))
		assertEquals(setOf("64", "~", "70"), sugg("waypoint add My base 10 ").toSet())
		assertEquals(listOf("y", "y", "y"), tips("waypoint add My base 10 "))
		assertEquals(listOf("z"), tips("waypoint add My base 10 64 ").distinct())
		assertEquals(emptyList<String>(), sugg("waypoint add My base 10 64 -3 "))

		assertEquals(listOf("x"), tips("waypoint add 42 ").filterNotNull().filter { it.length == 1 }.distinct())
	}

	@Test fun removeSuggestsNamesWithSpaces() {
		assertEquals(listOf("Crypt", "Crystal Hollows entrance"), sugg("waypoint remove cry"))
		assertEquals(listOf("Crystal Hollows entrance"), sugg("waypoint remove Crystal H"))
	}

	@Test fun parsingUnchanged() {
		d.execute("waypoint add My base 10 64 -3", Any())
		assertEquals(listOf("My base 10 64 -3"), added)
		val r = ApexWaypointParse.add(added[0], null) as ApexWaypointParse.AddRequest.Ok
		assertEquals("My base", r.name); assertEquals(Pos(10, 64, -3), r.pos)
	}

	@Test fun hintsSurviveMergeIntoServerTreeWithOwnWaypoint() {
		val server = CommandDispatcher<Any>()
		server.register(LiteralArgumentBuilder.literal<Any>("waypoint").executes { 0 })
		fun copy(from: com.mojang.brigadier.tree.CommandNode<Any>, to: com.mojang.brigadier.tree.CommandNode<Any>) {
			for (c in from.children) {
				val built = c.createBuilder().build()
				to.addChild(built)
				copy(c, to.getChild(c.name))
			}
		}
		copy(d.root, server.root)
		fun s(cmd: String) = server.getCompletionSuggestions(server.parse(cmd, Any())).get().list.map { it.text }
		assertEquals(listOf("add", "list", "remove"), s("waypoint ").sorted())
		assertEquals(listOf("[name]"), s("waypoint add "))
		assertEquals(setOf("64", "~", "70"), s("waypoint add My base 10 ").toSet())
	}
}
