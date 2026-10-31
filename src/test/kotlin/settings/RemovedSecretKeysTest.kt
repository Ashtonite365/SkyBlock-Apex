// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MigrationMap

class RemovedSecretKeysTest {
	@Test
	fun oldSecretKeysAreDroppedSilently() {
		val old = Json.parseToJsonElement(
			"""{"dungeon-secret-routes":{"enabled":true,"kit-pack-colours":true},"dungeon-secret-tracker":{"enabled":true,"hud":{"x":1}},
			"breakable-walls":{"stonk":true,"stonk-colour":-1},"puzzle-solvers":{"lost-adventurer":true,"lost-adventurer-colour":-1},
			"apex-far-terrain":{"enabled":true,"generous-occlusion-culling":false}}"""
		).jsonObject
		val res = ConfigMigrator(MigrationMap.bundled()).migrate(old)
		assertTrue(res.unmapped.isEmpty(), "unmapped: ${res.unmapped}")
		assertEquals(listOf("dungeon-secret-routes.", "dungeon-secret-tracker.", "breakable-walls.stonk", "far-terrain.", "apex-far-terrain.generous-occlusion-culling", "stat-bars.debug-log-action-bar"), ConfigMigrator.REMOVED_KEYS)
		assertTrue(ConfigMigrator.isRemoved("apex-far-terrain.generous-occlusion-culling"))
		assertFalse(ConfigMigrator.isRemoved("apex-far-terrain.enabled"))
		assertTrue(ConfigMigrator.isRemoved("stat-bars.debug-log-action-bar"))
		assertFalse(ConfigMigrator.isRemoved("stat-bars.enabled"))
		assertTrue(ConfigMigrator.isRemoved("dungeon-secret-routes.enabled"))
		assertTrue(ConfigMigrator.isRemoved("dungeon-secret-tracker.hud"))
		assertTrue(ConfigMigrator.isRemoved("breakable-walls.stonk"))
		assertFalse(ConfigMigrator.isRemoved("breakable-walls.stonk-colour"))

		assertFalse(ConfigMigrator.isRemoved("puzzle-solvers.lost-adventurer"))
		assertFalse(ConfigMigrator.isRemoved("puzzle-solvers.lost-adventurer-colour"))
		val ps = res.tree["dungeons"]!!.jsonObject["mob-highlights"]!!.jsonObject
		assertEquals("true", ps["lost-adventurer"].toString())
		assertEquals("-1", ps["lost-adventurer-colour"].toString())

		val far = res.tree["misc"]!!.jsonObject["textures-terrain"]!!.jsonObject
		assertEquals("true", far["enabled"].toString())
		assertFalse("generous-occlusion-culling" in far)

		assertEquals("-1", res.tree["secrets"]!!.jsonObject["breakable-walls"]!!.jsonObject["colour"].toString())
	}
}
