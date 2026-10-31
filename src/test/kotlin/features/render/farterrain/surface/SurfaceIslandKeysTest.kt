// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIslandKeys
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceOwnership
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfacePlace
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceIslandKeysTest {
	private val profileA = UUID.fromString("11111111-2222-3333-4444-555555555555")
	private val profileB = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

	private fun hypixel(
		mode: String?, ownership: SurfaceOwnership = SurfaceOwnership.UNKNOWN, profile: UUID? = profileA,
		fresh: Boolean = true, gametype: String = "SKYBLOCK", dimension: String = "minecraft:overworld", connection: Long = 1,
	) = SurfacePlace("mc.hypixel.net", null, true, fresh, gametype, mode, ownership, profile, dimension, connection)

	@Test
	fun publicIslandsAreKeptByAreaOnly() {
		val hub = SurfaceIslandKeys.key(hypixel("hub"))!!
		assertEquals("hypixel:hub", hub.id)
		assertEquals("hypixel/hub", hub.path)
		assertTrue(hub.persistent)

		assertEquals(hub, SurfaceIslandKeys.key(hypixel("hub", profile = profileB, connection = 7)))
		assertEquals("hypixel/dwarven-mines", SurfaceIslandKeys.key(hypixel("mining_3"))!!.path)
		assertEquals("hypixel/some-new-area", SurfaceIslandKeys.key(hypixel("some_new_area"))!!.path)
	}

	@Test
	fun ownPrivateIslandAndGardenArePerProfile() {
		val a = SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.OWN, profileA))!!
		val b = SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.OWN, profileB))!!
		assertEquals("hypixel/private-island/$profileA", a.path)
		assertNotEquals(a.id, b.id)
		assertNotEquals(a.path, b.path)
		val garden = SurfaceIslandKeys.key(hypixel("garden", SurfaceOwnership.OWN, profileA))!!
		assertEquals("hypixel/garden/$profileA", garden.path)
		assertNotEquals(a.path, garden.path)
	}

	@Test
	fun visitedIslandsAreMemoryOnlyAndPerConnection() {
		val v1 = SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.GUEST, connection = 1))!!
		val v2 = SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.GUEST, connection = 2))!!
		assertFalse(v1.persistent)
		assertNull(v1.path)
		assertNotEquals(v1.id, v2.id)
		assertNotEquals(v1.id, SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.OWN))!!.id)
	}

	@Test
	fun instancedAreasAreMemoryOnly() {
		for (mode in listOf("dungeon", "kuudra", "mineshaft")) {
			val k1 = SurfaceIslandKeys.key(hypixel(mode, connection = 1))!!
			val k2 = SurfaceIslandKeys.key(hypixel(mode, connection = 2))!!
			assertFalse(k1.persistent, mode)
			assertNotEquals(k1.id, k2.id, mode)
		}
	}

	@Test
	fun nothingIsRecordedWhileUncertain() {
		assertNull(SurfaceIslandKeys.key(hypixel("hub", fresh = false)))
		assertNull(SurfaceIslandKeys.key(hypixel(null)))
		assertNull(SurfaceIslandKeys.key(hypixel("dynamic", SurfaceOwnership.UNKNOWN)))
		assertNull(SurfaceIslandKeys.key(hypixel("garden", SurfaceOwnership.OWN, profile = null)))
	}

	@Test
	fun dimensionsAreSeparate() {
		val over = SurfaceIslandKeys.key(hypixel("hub"))!!
		val nether = SurfaceIslandKeys.key(hypixel("hub", dimension = "minecraft:the_nether"))!!
		assertNotEquals(over.id, nether.id)
		assertNotEquals(over.path, nether.path)
	}

	@Test
	fun otherHypixelGamesAndOtherServersAndSingleplayer() {
		val lobby = SurfaceIslandKeys.key(hypixel(null, gametype = "BEDWARS"))!!
		assertFalse(lobby.persistent)
		val other = SurfaceIslandKeys.key(SurfacePlace("play.example.org:25565", null, false, false, null, null, SurfaceOwnership.UNKNOWN, null, "minecraft:overworld", 1))!!
		assertEquals("server/play-example-org", other.path)
		val sp = SurfaceIslandKeys.key(SurfacePlace(null, "My World", false, false, null, null, SurfaceOwnership.UNKNOWN, null, "minecraft:overworld", 1))!!
		assertEquals("singleplayer/my-world", sp.path)
		assertNotEquals(other.id, sp.id)
	}

	@Test
	fun sidebarOwnership() {
		assertEquals(SurfaceOwnership.OWN, SurfaceIslandKeys.ownershipFromSidebarTitle("SKYBLOCK"))
		assertEquals(SurfaceOwnership.OWN, SurfaceIslandKeys.ownershipFromSidebarTitle("§e§lSKYBLOCK CO-OP"))
		assertEquals(SurfaceOwnership.GUEST, SurfaceIslandKeys.ownershipFromSidebarTitle("SKYBLOCK GUEST"))
		assertEquals(SurfaceOwnership.UNKNOWN, SurfaceIslandKeys.ownershipFromSidebarTitle(null))
		assertEquals(SurfaceOwnership.UNKNOWN, SurfaceIslandKeys.ownershipFromSidebarTitle("BED WARS"))
	}

	@Test
	fun pathsAreSafe() {
		val k = SurfaceIslandKeys.key(hypixel("../../etc"))!!
		assertFalse(k.path!!.contains(".."))
		assertEquals("a-b-c", SurfaceIslandKeys.slug("A/B\\C"))
	}
}
