// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.render

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.MiscRender

class RenderHidersTest {
	@Test
	fun far() {
		assertTrue(MiscRender.isFar(41.0 * 41.0, 40))
		assertFalse(MiscRender.isFar(39.0 * 39.0, 40))
	}
}
