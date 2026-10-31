package dev.ashtonite365.apex.test.features.kit.terminals

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.terminals.DelayedClicks
import dev.ashtonite365.apex.features.kit.terminals.PickModel
import dev.ashtonite365.apex.features.kit.terminals.RubixModel

class TermSimModelsTest {
	@Test
	fun rubix() {
		val r = RubixModel(3, Random(1))
		assertFalse(r.done)
		while (r.cells[1] != r.cells[0]) r.click(1, false)
		while (r.cells[2] != r.cells[0]) r.click(2, true)
		assertTrue(r.done)
	}

	@Test
	fun pick() {
		val p = PickModel.startsWith("S", 21, Random(3))
		assertTrue(p.labels.any { it.startsWith("S") })
		p.labels.indices.filter { p.labels[it].startsWith("S") }.forEach(p::click)
		assertTrue(p.done)
		val q = PickModel.selectAll("Red", 28, Random(4))
		q.click(q.labels.indexOfFirst { !it.startsWith("Red ") })
		assertTrue(q.failed); assertFalse(q.done)
	}

	@Test
	fun ping() {
		var t = 0L
		val d = DelayedClicks<Int> { t }
		d.add(1, 100); d.add(2, 150)
		assertEquals(emptyList<Int>(), d.due())
		t = 120; assertEquals(listOf(1), d.due())
		t = 200; assertEquals(listOf(2), d.due())
	}
}

class TerminalPbsTest {
	@Test
	fun pbs() {
		val m = mutableMapOf<String, Long>()
		assertEquals(null to true, dev.ashtonite365.apex.features.kit.terminals.TerminalPbs.record(m, "Panes", 3000))
		assertEquals(3000L to false, dev.ashtonite365.apex.features.kit.terminals.TerminalPbs.record(m, "Panes", 3500))
		assertEquals(3000L to true, dev.ashtonite365.apex.features.kit.terminals.TerminalPbs.record(m, "Panes", 2000))
		assertEquals("Starts With", dev.ashtonite365.apex.features.kit.terminals.TerminalPbs.typeOf("What starts with: 'S'?"))
		assertEquals("Select All", dev.ashtonite365.apex.features.kit.terminals.TerminalPbs.typeOf("Select all the LIGHT BLUE items!"))
	}
}
