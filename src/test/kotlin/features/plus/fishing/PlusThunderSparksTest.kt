package dev.ashtonite365.apex.test.features.plus.fishing

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.fishing.PlusThunderSparks
import dev.ashtonite365.apex.util.plus.PlusRepoSource

class PlusThunderSparksTest {
	@Test
	fun hashFromBundledSkulls() {
		val h = PlusThunderSparks.sparkHashFrom(PlusRepoSource.readText("constants/Skulls.json"))
		assertNotNull(h)
		assertEquals(h!!.startsWith("7b3328d3e9d710420322555b17239307f12270adf81bf63afc50faa"), true)
	}

	@Test
	fun hashIgnoresTimestamp() {
		fun enc(ts: Long) = Base64.getEncoder().encodeToString(
			"""{"timestamp":$ts,"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/ABCDEF12"}}}""".toByteArray())
		assertEquals("abcdef12", PlusThunderSparks.textureHash(enc(1)))
		assertEquals(PlusThunderSparks.textureHash(enc(1)), PlusThunderSparks.textureHash(enc(2)))
		assertNull(PlusThunderSparks.textureHash("not base64!!"))
		assertNull(PlusThunderSparks.textureHash(null))
	}
}
