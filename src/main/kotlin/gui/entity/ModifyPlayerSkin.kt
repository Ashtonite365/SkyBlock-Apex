package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import java.util.UUID
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.TextureRef
import dev.ashtonite365.apex.util.mc.SkinPayload
import dev.ashtonite365.apex.util.mc.texturesPropertyOf

object ModifyPlayerSkin : EntityModifier {

	val playerModelPartIndex: Map<String, PlayerModelPart> = PlayerModelPart.entries.associateBy(PlayerModelPart::getId)

	private fun texture(json: JsonElement): ClientAsset.ResourceTexture {
		val id = Identifier.parse(json.asString)
		return ClientAsset.ResourceTexture(id, id)
	}

	sealed interface SkinSource {

		data class Hash(val hash: String) : SkinSource

		data class Texture(val id: String) : SkinSource

		data object Default : SkinSource

		data object Keep : SkinSource
	}

	const val TEXTURE_URL = "http://textures.minecraft.net/texture/"

	private val HASH = Regex("[0-9a-fA-F]{1,64}")

	fun skinSource(info: JsonObject): SkinSource {
		val hash = (info["skin_hash"] as? JsonPrimitive)?.takeIf { it.isString }?.asString
		if (hash != null && HASH.matches(hash)) return SkinSource.Hash(hash)
		val png = (info["skin"] as? JsonPrimitive)?.takeIf { it.isString }?.asString
		if (png != null) return SkinSource.Texture(png)
		return if (info.has("skin_hash")) SkinSource.Default else SkinSource.Keep
	}

	fun profileForHash(hash: String): GameProfile {
		val uuid = UUID.nameUUIDFromBytes("apex-mob-skin:$hash".toByteArray())
		val payload = SkinPayload(
			byKind = mapOf(MinecraftProfileTexture.Type.SKIN to TextureRef(TEXTURE_URL + hash)),
			ownerId = uuid,
			ownerName = "ApexMob",
		)
		return GameProfile(uuid, "ApexMob", texturesPropertyOf(payload))
	}

	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val player = requireNotNull(entity as? GuiPlayer) { "Failed requirement." }
		val now = player.skinTextures
		val cape = info["cape"]?.let(::texture) ?: now.cape
		val body = when (val source = skinSource(info)) {
			is SkinSource.Hash -> {
				player.hashSkin = Client.game.skinManager.createLookup(profileForHash(source.hash), false)
				now.body
			}
			is SkinSource.Texture -> {
				player.hashSkin = null
				texture(JsonPrimitive(source.id))
			}
			SkinSource.Default -> {
				player.hashSkin = null
				DefaultPlayerSkin.get(player.uuid).body
			}
			SkinSource.Keep -> now.body
		}
		val model = info["slim"]?.let { if (it.asBoolean) PlayerModelType.SLIM else PlayerModelType.WIDE } ?: now.model
		info["parts"]?.let { parts ->
			val shown = layersShown(player.entityData.get(Avatar.DATA_PLAYER_MODE_CUSTOMISATION).toInt(), parts)
			player.entityData.set(Player.DATA_PLAYER_MODE_CUSTOMISATION, shown.toByte())
		}
		player.skinTextures = PlayerSkin(body, cape, null, model, true)
		return player
	}

	private fun layersShown(bits: Int, parts: JsonElement): Int {
		if (parts is JsonPrimitive && parts.isBoolean) return if (parts.asBoolean) -1 else 0
		var result = bits
		for ((key, wanted) in parts.asJsonObject.entrySet()) {
			val mask = playerModelPartIndex[key]!!.mask
			result = if (wanted.asBoolean) result and mask.inv() else result or mask
		}
		return result
	}
}
