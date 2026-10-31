// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.render

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.MiscRender

class ParticleHidersTest {
	@Test
	fun particles() {
		assertTrue(MiscRender.hideParticleByDistance(41.0 * 41, false, true, false))
		assertFalse(MiscRender.hideParticleByDistance(41.0 * 41, false, false, true))
		assertTrue(MiscRender.hideParticleByDistance(1.0, true, false, true))
		assertFalse(MiscRender.hideParticleByDistance(1.0, false, false, true))
	}

	@Test
	fun armorStands() {
		assertTrue(MiscRender.isTempArmorStand(3, false, true))
		assertFalse(MiscRender.isTempArmorStand(30, false, true))
		assertFalse(MiscRender.isTempArmorStand(3, true, true))
	}
}
