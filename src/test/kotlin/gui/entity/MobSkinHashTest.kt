package dev.ashtonite365.apex.test.gui.entity

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.gui.entity.ModifyPlayerSkin
import dev.ashtonite365.apex.gui.entity.ModifyPlayerSkin.SkinSource
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.readTexturesProperty

@ExtendWith(AutoBootstrapExtension::class)
class MobSkinHashTest {
	private fun mod(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
	private val hash = "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3"

	@Test
	fun hashIsParsedAndWinsOverPng() {
		assertEquals(SkinSource.Hash(hash), ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"$hash"}""")))
		assertEquals(SkinSource.Hash(hash.uppercase()),
			ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"${hash.uppercase()}","skin":"neurepo:mobs/x.png"}""")))
	}

	@Test
	fun shortHashesAreKeptExactlyAsGiven() {

		for (h in listOf("44958d7c69ae484c65f31347cdcc92c69f5406805b53652a75a8ed799df7", "4e31a387a31d7a7c4da77af7fdd0075aebb8df3d26ccb03bf71b6395992eae3", "a")) {
			assertEquals(SkinSource.Hash(h), ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"$h"}""")))
			val payload = readTexturesProperty(ModifyPlayerSkin.profileForHash(h).properties()["textures"].single())!!
			assertEquals("http://textures.minecraft.net/texture/$h", payload.byKind.values.single().link, "no padding")
		}
		assertEquals(60, "44958d7c69ae484c65f31347cdcc92c69f5406805b53652a75a8ed799df7".length)
		assertEquals(63, "4e31a387a31d7a7c4da77af7fdd0075aebb8df3d26ccb03bf71b6395992eae3".length)

		assertEquals(SkinSource.Default, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"0$hash"}""")))
		assertEquals(SkinSource.Default, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":" $hash "}""")))
	}

	@Test
	fun hashProfilePointsAtMojangTextureServer() {
		val profile = ModifyPlayerSkin.profileForHash(hash)
		val payload = readTexturesProperty(profile.properties()["textures"].single())!!
		val url = payload.byKind.values.single().link
		assertEquals("http://textures.minecraft.net/texture/$hash", url)
		assertEquals(profile.id(), ModifyPlayerSkin.profileForHash(hash).id(), "one hash, one cache key")
	}

	@Test
	fun pngIsTheFallbackForOldRepos() {
		assertEquals(SkinSource.Texture("neurepo:mobs/zealot.png"),
			ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin":"neurepo:mobs/zealot.png"}""")))
		assertEquals(SkinSource.Texture("neurepo:mobs/zealot.png"),
			ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"not a hash!","skin":"neurepo:mobs/zealot.png"}""")))
	}

	@Test
	fun missingOrBadHashGivesTheDefault() {
		assertEquals(SkinSource.Default, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":""}""")))
		assertEquals(SkinSource.Default, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":"zz"}""")))
		assertEquals(SkinSource.Default, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","skin_hash":null}""")))
		assertEquals(SkinSource.Keep, ModifyPlayerSkin.skinSource(mod("""{"type":"playerdata","slim":true}""")))
	}
}
