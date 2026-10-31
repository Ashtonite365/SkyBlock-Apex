package dev.ashtonite365.apex.test.util.mc

import com.mojang.authlib.minecraft.MinecraftProfileTexture
import java.util.Base64
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.gui.entity.ModifyPlayerSkin
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.SkinPayload
import dev.ashtonite365.apex.util.mc.TextureRef
import dev.ashtonite365.apex.util.mc.applyEncodedSkin
import dev.ashtonite365.apex.util.mc.readTexturesProperty
import dev.ashtonite365.apex.util.mc.texturesPropertyOf

@ExtendWith(AutoBootstrapExtension::class)
class TwoPlayerSkinTest {
	private val alice = UUID.fromString("11111111-2222-3333-4444-555555555555")
	private val bob = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")
	private fun url(h: String) = "http://textures.minecraft.net/texture/$h"

	private fun encoded(owner: UUID, hash: String): String {
		val props = texturesPropertyOf(SkinPayload(mapOf(MinecraftProfileTexture.Type.SKIN to TextureRef(url(hash))), owner, owner.toString().take(8)))
		return props["textures"].single().value()
	}

	private fun headTexture(stack: ItemStack): Pair<UUID, String> {
		val profile = stack.get(DataComponents.PROFILE)!!.partialProfile()
		val payload = readTexturesProperty(profile.properties()["textures"].single())!!
		return profile.id() to payload.byKind.getValue(MinecraftProfileTexture.Type.SKIN).link
	}

	@Test
	fun twoPlayersHeadsResolveToDifferentTextures() {
		val a = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(alice, encoded(alice, "aaaa")) }
		val b = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(bob, encoded(bob, "bbbb")) }
		val (aId, aUrl) = headTexture(a)
		val (bId, bUrl) = headTexture(b)
		assertEquals(alice, aId); assertEquals(bob, bId)
		assertEquals(url("aaaa"), aUrl); assertEquals(url("bbbb"), bUrl)
		assertNotEquals(aUrl, bUrl)
	}

	@Test
	fun unpaddedTexturesStillResolvePerPlayer() {
		val raw = { o: UUID, h: String -> Base64.getEncoder().withoutPadding().encodeToString(Base64.getDecoder().decode(encoded(o, h))) }
		val a = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(alice, raw(alice, "a1")) }
		val b = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(bob, raw(bob, "b22")) }
		assertNotEquals(headTexture(a), headTexture(b))
	}

	@Test
	fun skinProfilesForTwoSkinsHaveDistinctCacheKeys() {
		val a = ModifyPlayerSkin.profileForHash("aaaa")
		val b = ModifyPlayerSkin.profileForHash("bbbb")
		assertNotEquals(a.id(), b.id(), "SkinManager caches by (profile id, textures property)")
		assertNotEquals(a.properties()["textures"].single().value(), b.properties()["textures"].single().value())
	}

	@Test
	fun twoTeammatesGetTheirOwnMapMarkers() {
		val markers = listOf(10.0 to 10.0, 40.0 to 10.0)
		val pairs = DungeonMap.pairMarkers(markers, listOf(40.5 to 10.0, 10.5 to 10.0))
		assertEquals(mapOf(0 to 1, 1 to 0), pairs)
	}
}
