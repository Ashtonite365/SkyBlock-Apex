// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.transformer.IMixinTransformer

class MixinTest {
	@Test
	fun hasInstalledMixinTransformer() {
		val active: Any? = MixinEnvironment.getCurrentEnvironment().activeTransformer
		assertTrue(active is IMixinTransformer, "active transformer: $active")
	}
}
