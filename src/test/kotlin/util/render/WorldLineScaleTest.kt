package dev.ashtonite365.apex.test.util.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.render.WorldLineScale

class WorldLineScaleTest {
	@Test
	fun scalesWithDistance() {
		assertEquals(10f, WorldLineScale.width(10f, 4.0, true, 8, 20))
		assertEquals(5f, WorldLineScale.width(10f, 16.0, true, 8, 20), 1e-4f)
		assertEquals(2f, WorldLineScale.width(10f, 400.0, true, 8, 20), 1e-4f)
		assertEquals(10f, WorldLineScale.width(10f, 400.0, false, 8, 20))
	}

	@Test
	fun fairySoulOutlineScales() {

		val near = WorldLineScale.width(10f, 2.0, true, 8, 20)
		val far = WorldLineScale.width(10f, 32.0, true, 8, 20)
		assert(far < near) { "32 blocks ($far) must be thinner than 2 blocks ($near)" }
		assertEquals(10f, near)
		assertEquals(2f, WorldLineScale.width(10f, 1000.0, true, 8, 20), 1e-4f)
		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/util/render/WorldDraw.kt").readText()
		val cube = src.substringAfter("fun wireframeCube(").substringBefore("private fun emitEdgeVertex")
		assert("WorldLineScale.width(" in cube) { "wireframeCube must use the shared distance scaling" }
	}
}
