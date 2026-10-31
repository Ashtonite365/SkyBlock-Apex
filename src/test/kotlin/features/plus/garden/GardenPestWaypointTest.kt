// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenPestWaypoint

class GardenPestWaypointTest {
	@Test
	fun trail() {
		GardenPestWaypoint.reset()
		assertTrue(GardenPestWaypoint.isTrailParticle(true, 1, 0f, true))
		assertFalse(GardenPestWaypoint.isTrailParticle(true, 2, 0f, true))
		val player = Vec3(0.0, 70.0, 0.0)
		assertFalse(GardenPestWaypoint.add(Vec3(20.0, 70.0, 0.0), player, 1000, 0.0))
		for (i in 0 until 8) assertTrue(GardenPestWaypoint.add(Vec3(1.0 + i * 0.5, 71.0, 0.0), player, 1000L + i, 0.0))
		assertNotNull(GardenPestWaypoint.guess)
		val g = GardenPestWaypoint.guess!!
		assertTrue(g.x > 4.5, "guess continues along +x: $g")
		GardenPestWaypoint.reset()
	}
}
