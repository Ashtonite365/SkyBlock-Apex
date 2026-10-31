// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.events.HoppityCompactChat
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch34GoldenTest {
	@Test
	fun hoppity() {
		val out = mutableListOf<Pair<String, String>>()
		for ((si, script) in Batch34Hoppity.scripts.withIndex()) {
			val summary = HoppityCompactChat.Summary()
			val finds = mutableListOf<HoppityCompactChat.Find>()
			val c = HoppityCompactChat.Compactor { finds += it; HoppityCompactChat.record(summary, it) }
			for ((li, line) in script.withIndex()) out += "feed|$si|$li|$line" to Golden.run { "${c.feed(line)} finds=${finds.size}" }
			out += "finds|$si" to "$finds"
			out += "summary|$si" to "$summary // ${HoppityCompactChat.summaryLines(summary)}"
		}
		val n = Golden.verify("batch34-hoppity", out, minCases = 2259)
		assertTrue(n >= 2259)
	}
}
