package dev.ashtonite365.apex.test.util.plus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.plus.ChatPatternTest
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.groupOrNull
import dev.ashtonite365.apex.util.plus.matchMatcher

class PlusInfraTest {
	@Test
	fun trackerSessionAndTotal() {
		var now = 0L
		val total = ProfitTracker.Data()
		val t = ProfitTracker("Test", { total }, { if (it == "A") 10.0 else null }, { now })
		t.addItem("A", 3); now += 60_000; t.addCoins(50.0); now += 600_000; t.addItem("B")
		assertEquals(80.0, t.profit())
		assertEquals(60_000L, t.session.activeMillis)
		t.resetSession()
		assertEquals(0.0, t.profit())
		t.mode = ProfitTracker.Mode.TOTAL
		assertEquals(80.0, t.profit())
		assertEquals(4800.0, t.profitPerHour()!!, 1e-6)
		t.resetTotal()
		assertTrue(total.items.isEmpty())
	}

	@Test
	fun patterns() {
		val p = PlusPatterns.compile("test.coins", "You earned (?<amount>[\\d,]+) coins!")
		assertEquals("1,200", p.matchMatcher("§aYou earned §61,200 coins!") { groupOrNull("amount") })
		assertNull(p.matchMatcher("nope") { 1 })
		assertTrue(ChatPatternTest.check(p, listOf("You earned 5 coins!"), listOf("You earned coins!")).isEmpty())
		assertTrue(PlusPatterns.compile("test.coins", "ignored") === p)
	}
}
