package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.features.fishing.NearbySeaCreatures
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureExtras

class PlusSeaCreatureExtrasTest {
	@Test
	fun health() {
		assertEquals("1,500" to "2k", NearbySeaCreatures.parseHealth("[Lv45] Sea Walker 1,500/2k❤"))
		assertEquals(1.5e6, NearbySeaCreatures.parseShort("1.5M"))
		assertEquals(2000.0, NearbySeaCreatures.parseShort("2,000"))
		assertEquals("§c", NearbySeaCreatures.healthColour(10.0, 100.0))
		assertEquals("§a", NearbySeaCreatures.healthColour(100.0, 100.0))
	}

	@Test
	fun ring() {
		val pts = PlusSeaCreatureExtras.ring(Vec3.ZERO, 30.0, 0.0, 4)
		assertEquals(5, pts.size)
		assertEquals(30.0, pts[0].x, 1e-9)
		assertEquals(30.0, pts[1].z, 1e-9)
	}
}
