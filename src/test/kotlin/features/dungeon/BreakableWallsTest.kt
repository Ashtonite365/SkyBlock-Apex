// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BreakableWalls
import dev.ashtonite365.apex.features.dungeon.BreakableWalls.Kind
import dev.ashtonite365.apex.gui.config.RiskLevel

class BreakableWallsTest {

	private fun wall(x: Int, y: Int, z: Int, w: Int, h: Int) =
		(0 until w).flatMap { dx -> (0 until h).map { dy -> Triple(x + dx, y + dy, z) } }

	@Test
	fun scanFindsWallShapedClustersOnly() {
		val wall = wall(-150, 70, -150, 3, 3)
		val scattered = listOf(Triple(-140, 70, -140), Triple(-140, 71, -140), Triple(-130, 72, -130))
		val floor = (0 until 4).flatMap { dx -> (0 until 4).map { dz -> Triple(-120 + dx, 68, -120 + dz) } }
		val found = BreakableWalls.scanWalls((wall + scattered + floor).toSet(), -150.0, 70.0, -150.0)
		assertEquals(1, found.size)
		assertEquals(wall.toSet(), found.single().toSet())
		assertEquals(3, BreakableWalls.clusters((wall + scattered).toSet()).size)
		assertFalse(BreakableWalls.isWall(wall(0, 70, 0, 5, 1)))
		assertTrue(BreakableWalls.isWall(wall(0, 70, 0, 2, 3)))
		val lp = BreakableWalls.labelPos(wall)
		assertEquals(-148.5, lp.x); assertEquals(73.5, lp.y); assertEquals(-149.5, lp.z)
	}

	@Test
	fun scanIsCappedNearestFirst() {
		val walls = (0 until 40).map { wall(-190 + it * 4, 70, -150, 2, 3) }
		val found = BreakableWalls.scanWalls(walls.flatten().toSet(), -190.0, 70.0, -150.0)
		assertEquals(BreakableWalls.MAX_CLUSTERS, found.size)
		assertTrue(found.sumOf { it.size } <= BreakableWalls.MAX_BLOCKS)
		assertEquals(-190, found.first().minOf { it.first })
		val big = (0 until 3).map { wall(-190 + it * 40, 70, -150, 9, 9) }
		assertEquals(1, BreakableWalls.scanWalls(big.flatten().toSet(), -190.0, 70.0, -150.0).size)
	}

	@Test
	fun eachWallIsDrawnOnceAsSuperboomOrPlain() {
		assertEquals(Kind.SUPERBOOM, BreakableWalls.kindOf(behind = true, superboom = true, locator = true))
		assertEquals(Kind.SUPERBOOM, BreakableWalls.kindOf(behind = true, superboom = true, locator = false))
		assertEquals(Kind.WALL, BreakableWalls.kindOf(behind = true, superboom = false, locator = true))
		assertEquals(Kind.WALL, BreakableWalls.kindOf(behind = false, superboom = true, locator = true))
		assertNull(BreakableWalls.kindOf(behind = false, superboom = true, locator = false))
		assertNull(BreakableWalls.kindOf(behind = true, superboom = false, locator = false))
	}

	@Test
	fun oldSavedLocatorMigrates() {
		fun j(vararg p: Pair<String, Boolean>) = JsonObject(p.associate { it.first to JsonPrimitive(it.second) })

		val off = BreakableWalls.migrateOldWalls(j("enabled" to false, "superboom" to true, "stonk" to true))
		assertEquals(JsonPrimitive(false), off["superboom"]); assertEquals(JsonPrimitive(false), off["through-walls"])
		assertFalse("stonk" in off)
		val on = BreakableWalls.migrateOldWalls(j("enabled" to true, "superboom" to true))
		assertEquals(JsonPrimitive(true), on["superboom"]); assertEquals(JsonPrimitive(true), on["through-walls"])
		val new = j("enabled" to false, "superboom" to true, "through-walls" to false)
		assertEquals(new, BreakableWalls.migrateOldWalls(new))
	}

	@Test
	fun optionsOffByDefaultInTheSecretsSection() {
		val c = BreakableWalls.TConfig
		c.explicitDefaultLoad()
		assertEquals("breakable-walls", c.name)
		assertFalse(c.enabled); assertFalse(c.superboom); assertFalse(c.throughWalls)
		assertEquals(RiskLevel.RISKY, c.allOptions.getValue("through-walls").riskLevel)
		assertFalse(c.allOptions.getValue("enabled").riskLevel.isRisky)
		assertFalse("stonk" in c.allOptions)
		DungeonExtrasMeta.assertSecretsRows(c, "enabled", "toggle", false, "secrets.breakable-walls.enabled")
		DungeonExtrasMeta.assertSecretsRows(c, "superboom", "toggle", false, "secrets.superboom.enabled")
		DungeonExtrasMeta.assertSecretsRows(c, "superboom-colour", "colour", false, "secrets.superboom.colour")
		DungeonExtrasMeta.assertSecretsRows(c, "stonk-colour", "colour", false, "secrets.breakable-walls.colour")
		DungeonExtrasMeta.assertSecretsRows(c, "labels", "toggle", false, "secrets.breakable-walls.labels")
		DungeonExtrasMeta.assertSecretsRows(c, "through-walls", "riskyToggle", true, "secrets.breakable-walls.through-walls")
	}
}
