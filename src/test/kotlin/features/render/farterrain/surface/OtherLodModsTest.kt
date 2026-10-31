package dev.ashtonite365.apex.test.features.render.farterrain.surface

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.render.farterrain.surface.OtherLodMods

class OtherLodModsTest {
	@Test
	fun anyKnownModIdBlocksApexFarTerrain() {
		for (id in OtherLodMods.MOD_IDS) assertTrue(OtherLodMods { it == id }.loaded, id)
		assertFalse(OtherLodMods { false }.loaded)
		assertFalse(OtherLodMods { it == "sodium" }.loaded)
		assertFalse(OtherLodMods { error("lookup failed") }.loaded)
	}
}
