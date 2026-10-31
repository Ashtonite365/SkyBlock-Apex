// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.world

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.features.world.ApexWaypoint
import dev.ashtonite365.apex.features.world.ApexWaypointCommands
import dev.ashtonite365.apex.features.world.ApexWaypointData
import dev.ashtonite365.apex.features.world.ApexWaypointParse
import dev.ashtonite365.apex.features.world.ApexWaypointParse.AddRequest
import dev.ashtonite365.apex.features.world.ApexWaypointParse.Pos
import dev.ashtonite365.apex.features.world.ApexWaypointRow
import dev.ashtonite365.apex.features.world.ApexWaypointStore
import dev.ashtonite365.apex.features.world.ApexWaypoints

class ApexWaypointsTest {
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

	@Test
	fun storageRoundTripPerIsland() {
		val s = ApexWaypointStore()
		s.add("crimson_isle", "Barbarian Duke", 10, 64, -20)
		s.add("mining_3", "Royal Mines", -5, 150, 30)
		s.add("crimson_isle", "Kuudra", 1, 2, 3)
		s.setVisible("crimson_isle", "kuudra", false)
		val text = json.encodeToString(ApexWaypointData.serializer(), s.data)
		val back = ApexWaypointStore(json.decodeFromString(ApexWaypointData.serializer(), text))
		assertEquals(s.data, back.data)
		assertEquals(listOf("Barbarian Duke", "Kuudra"), back.list("crimson_isle").map { it.name })
		assertFalse(back.find("crimson_isle", "KUUDRA")!!.visible)
		assertEquals(ApexWaypoint("Royal Mines", -5, 150, 30), back.list("mining_3").single())

		val obj = json.parseToJsonElement(text).jsonObject
		assertEquals(setOf("crimson_isle", "mining"), obj["islands"]!!.jsonObject.keys)

		val partial = json.decodeFromString(ApexWaypointData.serializer(), """{"islands":{"hub":[{"name":"A","x":1,"y":2,"z":3,"extra":0}]}}""")
		assertTrue(ApexWaypointStore(partial).list("hub").single().visible)
	}

	@Test
	fun realDataHolderRoundTrip() {
		ApexWaypoints.DConfig.explicitDefaultLoad()
		ApexWaypoints.store.add("garden", "Plot 1", 5, 70, 5)
		val saved = ApexWaypoints.DConfig.saveTo(Unit)
		assertEquals(setOf("apex-waypoints"), saved.keys)
		ApexWaypoints.DConfig.loadFrom(Unit, JsonObject(mapOf()))
		assertTrue(ApexWaypoints.store.list("garden").isEmpty())
		ApexWaypoints.DConfig.loadFrom(Unit, saved)
		assertEquals("Plot 1", ApexWaypoints.store.list("garden").single().name)
		ApexWaypoints.DConfig.explicitDefaultLoad()
	}

	@Test
	fun perIslandIsolation() {
		val s = ApexWaypointStore()
		s.add("crimson_isle", "Spot", 1, 1, 1)
		s.add("mining_3", "Spot", 2, 2, 2)
		s.add("mining_3", "Hidden", 3, 3, 3)
		s.setVisible("mining_3", "Hidden", false)
		assertEquals(listOf(ApexWaypoint("Spot", 1, 1, 1)), s.visibleOn("crimson_isle"))
		assertEquals(listOf(ApexWaypoint("Spot", 2, 2, 2)), s.visibleOn("mining_3"))
		assertTrue(s.visibleOn("hub").isEmpty())
		assertTrue(s.remove("mining_3", "spot"))
		assertEquals(1, s.list("crimson_isle").size, "removing on one island must not touch another")
		assertFalse(s.remove("hub", "Spot"))

		assertEquals(ApexWaypointStore.AddResult.UPDATED, s.add("crimson_isle", "SPOT", 9, 9, 9))
		assertEquals(ApexWaypoint("Spot", 9, 9, 9), s.list("crimson_isle").single())

		assertEquals(ApexWaypointStore.UNKNOWN, ApexWaypointStore.keyFor(null))
		assertEquals(ApexWaypointStore.UNKNOWN, ApexWaypointStore.keyFor("  "))
		assertEquals("crimson_isle", ApexWaypointStore.keyFor("Crimson_Isle"))
		s.add(ApexWaypointStore.keyFor(null), "Lobby", 0, 0, 0)
		assertEquals("Lobby", s.visibleOn(ApexWaypointStore.UNKNOWN).single().name)
		assertTrue(s.visibleOn("crimson_isle").none { it.name == "Lobby" })

		assertTrue(s.remove("mining_3", "Hidden"))
		assertFalse("mining" in s.data.islands)
	}

	@Test
	fun groupingByIslandCurrentFirst() {
		val s = ApexWaypointStore()
		s.add("mining_3", "B", 1, 1, 1)
		s.add("crimson_isle", "A", 1, 1, 1)
		s.add("hub", "C", 1, 1, 1)
		s.add("crimson_isle", "D", 2, 2, 2)
		s.setVisible("crimson_isle", "D", false)
		val rows = s.grouped(current = "mining_3")
		assertEquals(listOf<ApexWaypointRow>(
			ApexWaypointRow.Header("mining", 1), ApexWaypointRow.Entry("mining", ApexWaypoint("B", 1, 1, 1)),
			ApexWaypointRow.Header("crimson_isle", 2), ApexWaypointRow.Entry("crimson_isle", ApexWaypoint("A", 1, 1, 1)),
			ApexWaypointRow.Entry("crimson_isle", ApexWaypoint("D", 2, 2, 2, visible = false)),
			ApexWaypointRow.Header("hub", 1), ApexWaypointRow.Entry("hub", ApexWaypoint("C", 1, 1, 1)),
		), rows)

		assertEquals(listOf("crimson_isle", "hub", "mining"), s.islands(null))
		assertEquals("Dwarven Mines", ApexWaypointStore.islandLabel("mining_3"))
		assertEquals("Unknown area", ApexWaypointStore.islandLabel(ApexWaypointStore.UNKNOWN))
		assertEquals("Some New Place", ApexWaypointStore.islandLabel("some_new_place"))
		assertEquals("Repo Name", ApexWaypointStore.islandLabel("x_y") { "Repo Name" })
		assertTrue(ApexWaypointStore().grouped("hub").isEmpty())
	}

	@Test
	fun coordsParsing() {
		val me = Pos(100, 64, -50)
		assertEquals(12, ApexWaypointParse.coord("12", null))
		assertEquals(-4, ApexWaypointParse.coord("-4", null))
		assertEquals(12, ApexWaypointParse.coord("12.7", null))
		assertEquals(-1, ApexWaypointParse.coord("-0.5", null))
		assertEquals(100, ApexWaypointParse.coord("~", 100))
		assertEquals(103, ApexWaypointParse.coord("~3", 100))
		assertEquals(98, ApexWaypointParse.coord("~-2", 100))
		assertNull(ApexWaypointParse.coord("~", null))
		assertNull(ApexWaypointParse.coord("abc", 1))
		assertNull(ApexWaypointParse.coord("NaN", 1))
		assertNull(ApexWaypointParse.coord("99999999999", 1))
		assertEquals(Pos(1, 2, 3), ApexWaypointParse.coords("1", " 2 ", "3"))
		assertEquals(Pos(100, 66, -50), ApexWaypointParse.coords("~", "~2", "~", me))
		assertNull(ApexWaypointParse.coords("1", "", "3"))
	}

	@Test
	fun addArgumentParsing() {
		val me = Pos(100, 64, -50)
		assertEquals(AddRequest.Ok("Duke", Pos(10, 70, -3), false), ApexWaypointParse.add("Duke 10 70 -3", me))
		assertEquals(AddRequest.Ok("Barbarian Duke", Pos(10, 70, -3), false), ApexWaypointParse.add("  Barbarian   Duke 10 70 -3 ", null))
		assertEquals(AddRequest.Ok("Here", me, true), ApexWaypointParse.add("Here", me))
		assertEquals(AddRequest.Ok("Plot 2", me, true), ApexWaypointParse.add("Plot 2", me), "one or two trailing numbers are part of the name")
		assertEquals(AddRequest.Ok("Up", Pos(100, 74, -50), false), ApexWaypointParse.add("Up ~ ~10 ~", me))
		assertTrue(ApexWaypointParse.add("Up ~ ~10 ~", null) is AddRequest.Error)
		assertTrue(ApexWaypointParse.add("Here", null) is AddRequest.Error)
		assertTrue(ApexWaypointParse.add("   ", me) is AddRequest.Error)
		assertTrue(ApexWaypointParse.add("1 2 3", me) is AddRequest.Ok, "three numbers alone are a name")
		assertTrue(ApexWaypointParse.add("§cRed 1 2 3", me) is AddRequest.Error)
		assertTrue(ApexWaypointParse.add("x".repeat(40) + " 1 2 3", me) is AddRequest.Error)
	}

	private class Recorder : ApexWaypointCommands.Sink<Any> {
		val calls = mutableListOf<String>()
		override fun add(source: Any, rest: String) { calls += "add:$rest" }
		override fun list(source: Any, all: Boolean) { calls += "list:$all" }
		override fun remove(source: Any, name: String) { calls += "remove:$name" }
		override fun names(source: Any) = listOf("Barbarian Duke", "Kuudra")
	}

	@Test
	fun commandTreeParsing() {
		val rec = Recorder()
		val d = CommandDispatcher<Any>()
		d.register(LiteralArgumentBuilder.literal<Any>("apex")
			.then(ApexWaypointCommands.attach(LiteralArgumentBuilder.literal<Any>("waypoint"), rec)))
		d.execute("apex waypoint add Barbarian Duke 10 70 -3", Unit)
		d.execute("apex waypoint add Here", Unit)
		d.execute("apex waypoint list", Unit)
		d.execute("apex waypoint list all", Unit)
		d.execute("apex waypoint remove Barbarian Duke", Unit)
		assertEquals(listOf("add:Barbarian Duke 10 70 -3", "add:Here", "list:false", "list:true", "remove:Barbarian Duke"), rec.calls)
		assertThrows<CommandSyntaxException> { d.execute("apex waypoint add", Unit) }
		assertThrows<CommandSyntaxException> { d.execute("apex waypoint remove", Unit) }
		assertThrows<CommandSyntaxException> { d.execute("apex waypoint list some", Unit) }
		val sugg = d.getCompletionSuggestions(d.parse("apex waypoint remove Ku", Unit)).get().list.map { it.text }
		assertEquals(listOf("Kuudra"), sugg)

		assertEquals(AddRequest.Ok("Barbarian Duke", Pos(10, 70, -3), false),
			ApexWaypointParse.add(rec.calls.first().removePrefix("add:"), null))
	}

	@Test
	fun rootWaypointAliasReachesTheSameNodes() {

		assertEquals("waypoint", dev.ashtonite365.apex.commands.ApexCommands.ALIASES["waypoint"])
		val rec = Recorder()
		val d = CommandDispatcher<Any>()
		d.register(LiteralArgumentBuilder.literal<Any>("apex")
			.then(ApexWaypointCommands.attach(LiteralArgumentBuilder.literal<Any>("waypoint"), rec)))
		assertTrue(dev.ashtonite365.apex.commands.ApexCommands.registerAlias(d, "waypoint", "waypoint"))
		d.execute("waypoint add Barbarian Duke 10 70 -3", Unit)
		d.execute("waypoint list all", Unit)
		d.execute("waypoint remove Kuudra", Unit)
		assertEquals(listOf("add:Barbarian Duke 10 70 -3", "list:true", "remove:Kuudra"), rec.calls)
	}
}
