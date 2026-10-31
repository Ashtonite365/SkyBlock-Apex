// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.data.Config

class DataHolderConfigTest {
	private val stores = listOf(
		"dev.ashtonite365.apex.repo.LearnedSkullTextures\$Store",
	)

	@Test fun newStoresCarryConfig() {
		for (n in stores) assertNotNull(Class.forName(n, false, javaClass.classLoader).getAnnotation(Config::class.java), "$n lacks @Config")
	}

	@Test fun constructingTheStoreDoesNotFailTheRequirement() {
		for (n in stores) {

			val err = runCatching { Class.forName(n, true, javaClass.classLoader) }.exceptionOrNull()
			var c: Throwable? = err
			while (c != null) { assertFalse(c.message == "Failed requirement.", "$n: ${c}"); c = c.cause }
		}
	}

	@Test fun learnedTexturesLookupIsNeverFatal() {
		runCatching { dev.ashtonite365.apex.repo.LearnedSkullTextures.get("ESSENCE_GOLD") }
			.onFailure { throw AssertionError("get() must not throw", it) }
	}
}
