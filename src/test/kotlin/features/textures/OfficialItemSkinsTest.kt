package dev.ashtonite365.apex.test.features.textures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.LearnedSkullTextures
import dev.ashtonite365.apex.repo.OfficialItemSkins

class OfficialItemSkinsTest {
	private val text = javaClass.getResource("/apex-fixtures/official-items-skins.json")!!.readText()

	@Test fun parsesSkinsIncludingWither() {
		val m = OfficialItemSkins.parse(text)
		assertEquals(setOf("ESSENCE_WITHER", "ESSENCE_GOLD"), m.keys)
		assertTrue(m.getValue("ESSENCE_WITHER").startsWith("ewogICJ0ZXh0dXJlcyIg"))
	}

	@Test fun parseToleratesMissingItems() {
		assertTrue(OfficialItemSkins.parse("{\"success\":false}").isEmpty())
	}

	@Test fun fallbackOrderRepoThenOfficialThenLearned() {
		val learned = LearnedSkullTextures.Learned("00000000-0000-0000-0000-000000000001", "LEARNED")
		assertEquals("REPO", OfficialItemSkins.headTexture("X", "REPO", "OFFICIAL", learned)?.value)
		assertEquals("OFFICIAL", OfficialItemSkins.headTexture("X", null, "OFFICIAL", learned)?.value)
		assertEquals("OFFICIAL", OfficialItemSkins.headTexture("X", "", "OFFICIAL", null)?.value)
		assertEquals(learned, OfficialItemSkins.headTexture("X", null, null, learned))
		assertEquals(learned, OfficialItemSkins.headTexture("X", null, "", learned))
		assertNull(OfficialItemSkins.headTexture("X", null, null, null))
	}

	@Test fun officialUuidIsStable() {
		val w = OfficialItemSkins.parse(text).getValue("ESSENCE_WITHER")
		assertEquals(OfficialItemSkins.headTexture("ESSENCE_WITHER", null, w, null)?.uuid,
			OfficialItemSkins.headTexture("ESSENCE_WITHER", null, w, null)?.uuid)
	}
}
