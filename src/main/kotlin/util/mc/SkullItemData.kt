package dev.ashtonite365.apex.util.mc

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.Base64Util.padToValidBase64
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer
import dev.ashtonite365.apex.util.json.InstantAsLongSerializer
import dev.ashtonite365.apex.util.trueOr

@Serializable
data class TextureRef(
	@SerialName("url") val link: String,
	@SerialName("metadata") val extras: Map<String, String> = mapOf(),
)

@Serializable
data class SkinPayload(
	@SerialName("textures") val byKind: Map<MinecraftProfileTexture.Type, TextureRef> = mapOf(),
	@SerialName("profileId") @Serializable(with = DashlessUUIDSerializer::class) val ownerId: UUID? = null,
	@SerialName("profileName") val ownerName: String? = null,
	@SerialName("isPublic") val publicSkin: Boolean = true,
	@SerialName("timestamp") @Serializable(with = InstantAsLongSerializer::class) val issuedAt: Instant = Instant.now(),
)

private const val TEXTURES_PROPERTY = "textures"

private const val ENCODED_HEAD_OWNER = "LameGuy123"
private const val URL_HEAD_OWNER = "nea89"

val placeholderHeadUuid: UUID = UUID.fromString("d3cb85e2-3075-48a1-b213-a9bfb62360c1")

private fun texturesOnly(encoded: String): PropertyMap =
	PropertyMap(ImmutableMultimap.of(TEXTURES_PROPERTY, Property(TEXTURES_PROPERTY, encoded)))

private fun resolvedProfile(id: UUID, name: String, properties: PropertyMap): ResolvableProfile =
	ResolvableProfile.createResolved(GameProfile(id, name, properties))

fun texturesPropertyOf(payload: SkinPayload): PropertyMap {
	val json = Apex.json.encodeToString(SkinPayload.serializer(), payload)
	return texturesOnly(Base64.getEncoder().encodeToString(json.toByteArray()))
}

fun ItemStack.applyEncodedSkin(owner: UUID, encoded: String) {
	set(DataComponents.PROFILE, resolvedProfile(owner, ENCODED_HEAD_OWNER, texturesOnly(encoded.padToValidBase64())))
}

fun skullWithSkin(owner: UUID, url: String): KeptStack = KeptStack.build(Items.PLAYER_HEAD) { applySkinUrl(owner, url) }

fun ItemEditor.applySkinUrl(owner: UUID, url: String) {
	val payload = SkinPayload(
		byKind = mapOf(MinecraftProfileTexture.Type.SKIN to TextureRef(url)),
		ownerId = owner,
		ownerName = URL_HEAD_OWNER,
	)
	set(DataComponents.PROFILE, resolvedProfile(owner, URL_HEAD_OWNER, texturesPropertyOf(payload)))
}

private fun trimExcessPadding(value: String): String = value.trimEnd('=')

fun readTexturesProperty(property: Property): SkinPayload? {
	trueOr(property.name() == TEXTURES_PROPERTY) { return null }
	return try {
		val json = String(Base64.getDecoder().decode(trimExcessPadding(property.value()).padToValidBase64()))
		Apex.json.decodeFromString(SkinPayload.serializer(), json)
	} catch (e: Exception) {
		failedDecode(e)
	}
}

private fun failedDecode(e: Exception): SkinPayload? {
	Apex.logger.debug("Ignoring unreadable skin textures property", e)
	return null
}
