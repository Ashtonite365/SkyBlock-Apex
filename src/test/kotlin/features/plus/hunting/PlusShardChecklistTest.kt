package dev.ashtonite365.apex.test.features.plus.hunting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.hunting.PlusShardChecklist

class PlusShardChecklistTest {
	@Test
	fun parse() {
		assertEquals(PlusShardChecklist.Biome.ICY, PlusShardChecklist.biomeOf(listOf("Oct 16", " ⏣ Icy Biome")))
		assertNull(PlusShardChecklist.biomeOf(listOf("⏣ Hub")))
		assertEquals("Mantis Shrimp", PlusShardChecklist.shardOf("You caught x2 Mantis Shrimp Shards!"))
		assertNull(PlusShardChecklist.shardOf("You caught a Grove Shard!"))
	}

	@Test
	fun display() {
		PlusShardChecklist.caught.clear(); PlusShardChecklist.caught += "Wumpa"
		val l = PlusShardChecklist.lines(PlusShardChecklist.Biome.ICY, true, false)
		assertTrue(l.any { "Icy" in it && "1/9" in it })
		assertTrue(l.any { "✔" in it && "Wumpa" in it })
		assertEquals(4 + 9, l.size)
	}
}
