package dev.ashtonite365.apex.test.util.plus

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.plus.PlusRepoData

class PlusRepoDataTest {
	@Serializable
	data class Fixture(val values: List<Int>)

	@Test
	fun parse() {
		assertEquals(listOf(1, 2), PlusRepoData.parse("""{"values":[1,2],"extra":0}""", Fixture.serializer()).values)
	}
}
