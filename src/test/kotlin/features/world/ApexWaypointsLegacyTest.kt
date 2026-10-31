// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.CoordListWaypoints
import dev.ashtonite365.apex.features.world.WaypointRouteManager
import dev.ashtonite365.apex.features.world.WaypointRoute
import dev.ashtonite365.apex.features.world.Waypoints
import dev.ashtonite365.apex.features.world.wrappingWindow
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry

class ApexWaypointsLegacyTest {
	private val legacyKeys = listOf("temp-waypoint-duration", "show-index", "skip-to-nearest", "reset-order-on-swap")
	private val newKeys = listOf("enabled", "manager", "marker-style", "colour")

	@Test
	fun oneConfigKeepsTheOldKeysAndAddsTheNewOnes() {
		val c = Waypoints.TConfig
		assertEquals("waypoints", c.name)
		assertEquals(newKeys + legacyKeys, c.sortedOptions.map { it.propertyName })
		c.explicitDefaultLoad()
		assertTrue(c.enabled, "visual feature: on by default")
		assertEquals(Waypoints.MarkerStyle.BEAM_AND_BOX, c.markerStyle)
		assertTrue(c.showIndex); assertFalse(c.skipToNearest); assertTrue(c.resetWaypointOrderOnWorldSwap)
		assertEquals(30, c.tempWaypointDuration.inWholeSeconds)
		c.sortedOptions.forEach { assertEquals(RiskLevel.NONE, it.riskLevel, it.propertyName) }

		c.loadFrom(Unit, Json.parseToJsonElement("""{"waypoints":{"show-index":false,"skip-to-nearest":true,"reset-order-on-swap":false}}""").jsonObject)
		assertFalse(c.showIndex); assertTrue(c.skipToNearest); assertFalse(c.resetWaypointOrderOnWorldSwap); assertTrue(c.enabled)
		val saved = c.saveTo(Unit)["waypoints"]!!.jsonObject
		assertEquals(JsonPrimitive(false), saved["show-index"])
		assertFalse("manager" in saved, "the manager button saves nothing")
		assertEquals(JsonPrimitive("beam-and-box"), saved["marker-style"])
		c.explicitDefaultLoad()
	}

	@Test
	fun settingsMapKeepsOldPathsAndPutsEverythingInTheRenderGroup() {
		val map = MigrationMap.bundled()

		val sub = mapOf("enabled" to "wp-personal", "manager" to "wp-personal", "marker-style" to "wp-personal", "colour" to "wp-personal",
			"show-index" to "wp-routes", "skip-to-nearest" to "wp-routes", "reset-order-on-swap" to "wp-routes",
			"temp-waypoint-duration" to "wp-temporary")
		for (k in legacyKeys + newKeys) {
			val e = map["waypoints.$k"]!!
			assertEquals(SettingPath(MainSection.WAYPOINTS, sub.getValue(k), k), e.newPath, k)
			assertEquals(sub.getValue(k), e.group, k)
		}
		for (s in sub.values.toSet()) assertEquals(s, SettingsRegistry.default(map).get(MainSection.WAYPOINTS, s)!!.group)

		val m = ConfigMigrator(map)
		val res = m.migrate(Json.parseToJsonElement("""{"waypoints":{"show-index":false,"skip-to-nearest":true}}""").jsonObject)
		assertTrue(res.unmapped.isEmpty())
		assertEquals(JsonPrimitive(false), m.lookup(res.tree, SettingPath.parse("waypoints.wp-routes.show-index")))
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, SettingPath.parse("waypoints.wp-routes.skip-to-nearest")))
	}

	@Test
	fun savedRouteSetsStillLoad() {

		val old = Json.parseToJsonElement("""{"waypoints":{"mine":{"label":"Unlabeled","id":"mine","isRelativeTo":null,
			"waypoints":[{"x":1,"y":2,"z":3},{"x":4,"y":5,"z":6}],"isOrdered":true},
			"dungeon-route:Blaze":{"label":"Blaze","id":"dungeon-route:Blaze","isRelativeTo":"dungeon room corner","waypoints":[{"x":0,"y":70,"z":0}],"isOrdered":true}}}""").jsonObject
		WaypointRouteManager.DConfig.loadFrom(Unit, old)
		val mine = WaypointRouteManager.DConfig.data["mine"]!!
		assertTrue(mine.isOrdered)
		assertEquals(listOf(BlockPos(1, 2, 3), BlockPos(4, 5, 6)), mine.waypoints.map { it.blockPos })
		assertEquals("dungeon room corner", WaypointRouteManager.DConfig.data["dungeon-route:Blaze"]!!.isRelativeTo)
		WaypointRouteManager.DConfig.loadFrom(Unit, WaypointRouteManager.DConfig.saveTo(Unit))
		assertEquals(2, WaypointRouteManager.DConfig.data.size)
		WaypointRouteManager.DConfig.explicitDefaultLoad()
	}

	@Test
	fun relativeExportAndCoordListImport() {
		val w = WaypointRoute("t", "t", "here", mutableListOf(WaypointRoute.Waypoint(110, 70, 210)), false)
		w.lastRelativeImport = BlockPos(100, 64, 200)
		assertEquals(WaypointRoute.Waypoint(10, 6, 10), WaypointRouteManager.createExportableCopy(w).waypoints.single())
		val cw = CoordListWaypoints.tryParse("""[{"x":1,"y":2,"z":3,"r":255},{"x":null,"y":1,"z":1}]""").getOrThrow()
		val imported = CoordListWaypoints.intoRoute(cw, BlockPos(10, 10, 10))
		assertEquals(listOf(WaypointRoute.Waypoint(11, 12, 13)), imported.waypoints, "null coords are skipped, relative offset applied")
		assertEquals(listOf(CoordListWaypoints.CoordListEntry(1, 2, 3)), CoordListWaypoints.fromRoute(imported, BlockPos(10, 10, 10)))
		assertTrue(CoordListWaypoints.tryParse("not json").isFailure)
	}

	@Test
	fun orderedRouteWindow() {
		val l = listOf(0, 1, 2, 3, 4)
		assertEquals(listOf(1, 2, 3), l.wrappingWindow(1, 3))
		assertEquals(listOf(3, 4, 0), l.wrappingWindow(3, 3))
		assertEquals(listOf(4, 0, 1), l.wrappingWindow(4, 3))
		assertEquals(listOf(0, 1), listOf(0, 1).wrappingWindow(0, 3))
	}
}
