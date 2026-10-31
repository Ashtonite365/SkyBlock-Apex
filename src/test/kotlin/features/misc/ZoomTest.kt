// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import dev.ashtonite365.apex.features.misc.MiscRender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoomTest {
	@Test fun notZoomingKeepsFov() = assertEquals(70F, MiscRender.zoomFov(70F, false, 4), 1e-6F)
	@Test fun zoomDividesByFactor() {
		for (f in 2..10) assertEquals(80F / f, MiscRender.zoomFov(80F, true, f), 1e-5F)
	}
	@Test fun badFactorIsSafe() = assertEquals(70F, MiscRender.zoomFov(70F, true, 0), 1e-6F)

	@Test fun mixinTargetsCalculateFov() {
		val bytes = javaClass.classLoader.getResourceAsStream("dev/ashtonite365/apex/mixins/ZoomAndGlowPatches.class")!!.readBytes()
		val text = String(bytes, Charsets.ISO_8859_1)
		assertTrue(text.contains("calculateFov"))
		assertTrue(!text.contains("getFov"))
		assertNotNull(Class.forName("net.minecraft.client.Camera").getDeclaredMethod("calculateFov", Float::class.javaPrimitiveType))
	}
}
