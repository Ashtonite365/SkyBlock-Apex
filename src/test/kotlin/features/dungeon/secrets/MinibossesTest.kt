// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon.secrets

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.secrets.DungeonSecrets
import dev.ashtonite365.apex.features.dungeon.secrets.MinibossNames
import dev.ashtonite365.apex.test.features.dungeon.DungeonExtrasMeta

class MinibossesTest {
	@Test
	fun namesFromTags() {
		assertEquals("Lost Adventurer", MinibossNames.of("§6✯ §cLost Adventurer §a1.5M§c❤"))
		assertEquals("King Midas", MinibossNames.of("[Lv80] King Midas 200k❤"))
		assertEquals("Frozen Adventurer", MinibossNames.of("✯ Frozen Adventurer 900k❤"))
		assertEquals("Angry Archaeologist", MinibossNames.of("Angry Archaeologist"))
		assertEquals("Redstone Warrior", MinibossNames.of("✯ Redstone Warrior 2M❤"))
		assertNull(MinibossNames.of("✯ Shadow Assassin 1.2M❤"))
		assertNull(MinibossNames.of("✯ Skeleton Master 400k❤"))
		assertTrue(MinibossNames.isDead("§cLost Adventurer §e0§c❤"))
		assertFalse(MinibossNames.isDead("Lost Adventurer 10❤"))
	}

	private fun j(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) ->
		k to when (v) { is JsonObject -> v; is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) } })

	@Test
	fun lostAdventurerSettingMigratesToMinibosses() {
		val colour = "0:96:0:255:170"
		val all = j("puzzle-solvers" to j("lost-adventurer" to true, "lost-adventurer-colour" to colour))
		val out = DungeonSecrets.migrateLostAdventurer(JsonObject(emptyMap()), all)
		assertEquals(JsonPrimitive(true), out["minibosses"])
		assertEquals(JsonPrimitive(true), out["minibosses-through-walls"])
		assertEquals(JsonPrimitive(colour), out["minibosses-colour"])

		val saved = j("minibosses" to false)
		assertEquals(saved, DungeonSecrets.migrateLostAdventurer(saved, all))

		val offAll = j("puzzle-solvers" to j("lost-adventurer" to false))
		assertNull(DungeonSecrets.migrateLostAdventurer(JsonObject(emptyMap()), offAll)["minibosses"])

		assertTrue("lost-adventurer" in PuzzleSolvers.TConfig.allOptions)
		assertTrue("lost-adventurer-colour" in PuzzleSolvers.TConfig.allOptions)
	}

	@Test
	fun secretsOptionsDefaultsAndRows() {
		val c = DungeonSecrets.TConfig
		c.explicitDefaultLoad()
		assertFalse(c.crypts); assertFalse(c.chests); assertFalse(c.minibosses)
		assertFalse(c.cryptsThroughWalls); assertFalse(c.chestsThroughWalls); assertFalse(c.minibossesThroughWalls)
		for (k in listOf("crypts-through-walls", "chests-through-walls", "minibosses-through-walls"))
			assertTrue(c.allOptions.getValue(k).riskLevel.isRisky, k)
		val rows = listOf(
			Triple("crypts", "toggle", "secrets.crypts.enabled"), Triple("crypts-colour", "colour", "secrets.crypts.colour"),
			Triple("crypts-through-walls", "riskyToggle", "secrets.crypts.through-walls"),
			Triple("crypts-whole-dungeon", "toggle", "secrets.crypts.whole-dungeon"),
			Triple("chests", "toggle", "secrets.chests.enabled"), Triple("chests-colour", "colour", "secrets.chests.colour"),
			Triple("chests-through-walls", "riskyToggle", "secrets.chests.through-walls"),
			Triple("chests-hide-opened", "toggle", "secrets.chests.hide-opened"),
			Triple("chests-whole-dungeon", "toggle", "secrets.chests.whole-dungeon"),
			Triple("minibosses", "toggle", "secrets.mini-bosses.enabled"), Triple("minibosses-colour", "colour", "secrets.mini-bosses.colour"),
			Triple("minibosses-through-walls", "riskyToggle", "secrets.mini-bosses.through-walls"),
			Triple("minibosses-label", "toggle", "secrets.mini-bosses.label"),
			Triple("superboom-chest-depth", "integer", "secrets.superboom.chest-depth"),
			Triple("superboom-chest-radius", "integer", "secrets.superboom.chest-radius"),
			Triple("superboom-crypt-radius", "integer", "secrets.superboom.crypt-radius"),
		)
		for ((k, kind, path) in rows) DungeonExtrasMeta.assertSecretsRows(c, k, kind, kind == "riskyToggle", path)
	}
}
