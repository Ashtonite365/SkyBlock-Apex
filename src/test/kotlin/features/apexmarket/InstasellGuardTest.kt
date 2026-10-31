package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.InstasellGuard

class InstasellGuardTest {
	@Test
	fun reasons() {
		Assertions.assertNotNull(InstasellGuard.reason(70.0, 100.0, null, 20, 0.0))
		Assertions.assertNull(InstasellGuard.reason(90.0, 100.0, 500.0, 20, 1000.0))
		Assertions.assertNotNull(InstasellGuard.reason(null, null, 5000.0, 20, 1000.0))
		Assertions.assertNull(InstasellGuard.reason(null, null, 5000.0, 20, 0.0))
	}
}
