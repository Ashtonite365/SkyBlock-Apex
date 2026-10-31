// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.ApexWaypoint
import dev.ashtonite365.apex.features.world.ApexWaypointData
import dev.ashtonite365.apex.features.world.ApexWaypointRow
import dev.ashtonite365.apex.features.world.ApexWaypointStore
import dev.ashtonite365.apex.features.world.ApexWaypoints
import dev.ashtonite365.apex.features.world.NavigationHelper

class ApexWaypointsGroupsTest {
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
	private val three = listOf("mining_2", "mining_3", "crystal_hollows")

	@Test
	fun groupMapping() {
		three.forEach { assertEquals("mining", ApexWaypointStore.keyFor(it), it) }
		assertEquals("mining", ApexWaypointStore.keyFor("Mining_3"))
		listOf("mineshaft", "hub", "crimson_isle", "mining_1", "rift").forEach { assertEquals(it, ApexWaypointStore.keyFor(it), it) }
		assertEquals("Mining (Deep Caverns, Dwarven Mines, Crystal Hollows)", ApexWaypointStore.headerLabel("mining"))
		assertEquals("Hub", ApexWaypointStore.headerLabel("hub"))
		assertEquals("Mining", ApexWaypointStore.islandLabel("mining"))
	}

	@Test
	fun showsOnEachOfTheThreeIslands() {
		val s = ApexWaypointStore()
		s.add("mining_3", "Forge", 0, 149, -69)
		s.add("crystal_hollows", "Nucleus", 513, 106, 513)
		for (island in three) assertEquals(listOf("Forge", "Nucleus"), s.visibleOn(island).map { it.name }, island)
		assertTrue(s.visibleOn("mineshaft").isEmpty(), "Glacite Mineshafts stay separate")
		assertTrue(s.remove("mining_2", "forge"), "remove from any member island")
	}

	@Test
	fun singleListEntry() {
		val s = ApexWaypointStore()
		s.add("mining_2", "A", 1, 1, 1); s.add("mining_3", "B", 2, 2, 2); s.add("crystal_hollows", "C", 3, 3, 3)
		s.add("mineshaft", "M", 4, 4, 4)
		val headers = s.grouped("crystal_hollows").filterIsInstance<ApexWaypointRow.Header>()
		assertEquals(listOf(ApexWaypointRow.Header("mining", 3), ApexWaypointRow.Header("mineshaft", 1)), headers)
	}

	@Test
	fun migrationMergesAndDedupes() {

		val old = """{"islands":{
			"mining":[{"name":"Existing","x":0,"y":0,"z":0}],
			"mining_3":[{"name":"Forge","x":1,"y":2,"z":3},{"name":"Base","x":4,"y":5,"z":6,"visible":false}],
			"crystal_hollows":[{"name":"FORGE","x":9,"y":9,"z":9},{"name":"Jungle","x":7,"y":8,"z":9}],
			"mining_2":[{"name":"Lapis","x":1,"y":1,"z":1}],
			"hub":[{"name":"Bank","x":-1,"y":70,"z":-60}],
			"mineshaft":[{"name":"Shaft","x":2,"y":2,"z":2}]}}"""
		val data = json.decodeFromString(ApexWaypointData.serializer(), old)
		val logged = mutableListOf<String>()
		assertTrue(ApexWaypointStore.migrate(data) { logged += it })
		assertEquals(setOf("mining", "hub", "mineshaft"), data.islands.keys)
		assertEquals(listOf(
			ApexWaypoint("Existing", 0, 0, 0), ApexWaypoint("Forge", 1, 2, 3), ApexWaypoint("Base", 4, 5, 6, visible = false),
			ApexWaypoint("Jungle", 7, 8, 9), ApexWaypoint("Lapis", 1, 1, 1),
		), data.islands["mining"])
		assertEquals(1, logged.size); assertTrue("FORGE" in logged.single() && "crystal_hollows" in logged.single(), logged.single())
		assertFalse(ApexWaypointStore.migrate(data), "second run changes nothing")

		ApexWaypoints.DConfig.loadFrom(Unit, JsonObject(mapOf("apex-waypoints" to json.parseToJsonElement(old))))
		assertEquals(5, ApexWaypoints.store.list("mining_2").size)
		assertTrue(ApexWaypoints.pendingSave)
		val saved = ApexWaypoints.DConfig.saveTo(Unit)["apex-waypoints"]!!.jsonObject["islands"]!!.jsonObject
		assertFalse(saved.keys.any { it in three }, "saved: ${saved.keys}")
		ApexWaypoints.pendingSave = false
		ApexWaypoints.DConfig.explicitDefaultLoad()
	}

	@Test
	fun seedsRiftOnFirstLoad() {
		ApexWaypoints.DConfig.loadFrom(Unit, JsonObject(mapOf()))
		assertEquals(listOf(ApexWaypoint("Rift", 45, 152, 73)), ApexWaypoints.store.list("hub"))
		assertEquals(listOf("hub-rift"), ApexWaypoints.DConfig.data.seededDefaults)
		ApexWaypoints.pendingSave = false
		ApexWaypoints.DConfig.explicitDefaultLoad()
	}

	@Test
	fun notReSeededAfterDelete() {
		ApexWaypoints.DConfig.loadFrom(Unit, JsonObject(mapOf()))
		assertTrue(ApexWaypoints.store.remove("hub", "Rift"))
		val saved = ApexWaypoints.DConfig.saveTo(Unit)
		ApexWaypoints.DConfig.loadFrom(Unit, saved)
		assertTrue(ApexWaypoints.store.list("hub").isEmpty())
		ApexWaypoints.pendingSave = false
		ApexWaypoints.DConfig.explicitDefaultLoad()
	}

	@Test
	fun noDuplicateWhenRiftExists() {
		val data = ApexWaypointData(islands = linkedMapOf("hub" to mutableListOf(ApexWaypoint("rift", 1, 2, 3, visible = false))))
		assertTrue(ApexWaypointStore.seedDefaults(data))
		assertEquals(listOf(ApexWaypoint("rift", 1, 2, 3, visible = false)), data.islands["hub"])
		assertFalse(ApexWaypointStore.seedDefaults(data))
	}

	@Test
	fun hubRiftTeleporterMovedToTheWizardPortal() {
		assertEquals(BlockPos(45, 152, 73), NavigationHelper.portalOverride("hub", "rift"))
	}
}
