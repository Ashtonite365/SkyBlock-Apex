package dev.ashtonite365.apex.test.features.plus.rift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.PlusRiftMisc

class PlusRiftMiscTest {
	@Test
	fun killZone() {
		assertEquals(3, PlusRiftMisc.killExclamations("⚠ Get back in the arena or you will DIE!!! ⚠"))
		assertNull(PlusRiftMisc.killExclamations("hello"))
		assertEquals(2250L, PlusRiftMisc.killMillis(3))
	}
}
