package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConfigAnnotationTest {

	@Test
	fun dataHoldersInitialise() {
		Assertions.assertDoesNotThrow { dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Data.explicitDefaultLoad() }
		Assertions.assertDoesNotThrow { dev.ashtonite365.apex.features.world.WaypointRouteManager.DConfig.explicitDefaultLoad() }
		Assertions.assertNotNull(dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Data.javaClass.getAnnotation(dev.ashtonite365.apex.util.data.Config::class.java))
	}
}
