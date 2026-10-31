// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class P2aGoldenTest {
	@Test
	fun garden() {
		val n = Golden.verify("p2a-garden", P2aCorpus.cases(), minCases = MIN)
		assertTrue(n >= MIN)
	}

	private companion object { const val MIN = 13280 }
}
