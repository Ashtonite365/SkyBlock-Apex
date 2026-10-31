package dev.ashtonite365.apex.test.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.IntegerInputHandler

class IntegerInputHandlerTest {
	private object H { fun parse(t: String) = IntegerInputHandler.parse(t, 40, 300) }
	private val h = H

	@Test
	fun typedValues() {
		assertEquals(120, h.parse("120"))
		assertNull(h.parse("1"))
		assertNull(h.parse("301"))
		assertNull(h.parse(""))
		assertEquals(40, h.parse(" 40 "))
	}

}
