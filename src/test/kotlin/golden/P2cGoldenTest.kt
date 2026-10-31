// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class P2cGoldenTest {
	@Test
	fun slayerCombat() {
		val n = Golden.verify("p2c-slayer-combat", P2cCorpus.cases(), minCases = MIN)
		assertTrue(n >= MIN)
	}

	private companion object { const val MIN = 3490 }
}
