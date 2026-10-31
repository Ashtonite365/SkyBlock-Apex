/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: pack textures for player skulls by skin hash (SPEC §2.11).
 */
package dev.ashtonite365.apex.texengine.apex

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import dev.ashtonite365.apex.texengine.SkullTextures
import dev.ashtonite365.apex.util.mc.readTexturesProperty
import java.util.Optional
import java.util.WeakHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier

object ApexSkulls : SkullTextures {

	const val NAMESPACE = "firmskyblock"

	private val skinUrl = Regex("https?://textures\\.minecraft\\.net/texture/([0-9a-fA-F]+)")

	fun textureIdForUrl(url: String): Identifier? {
		val hash = skinUrl.matchEntire(url)?.groupValues?.get(1)?.lowercase() ?: return null
		return Identifier.fromNamespaceAndPath(NAMESPACE, "textures/placedskull/$hash.png")
	}

	fun textureIdFor(profile: GameProfile): Identifier? {
		synchronized(ids) { ids[profile]?.let { return it.orElse(null) } }
		val property = profile.properties()["textures"].firstOrNull()
		val url = property?.let(::readTexturesProperty)?.byKind?.get(MinecraftProfileTexture.Type.SKIN)?.link
		val id = url?.let(::textureIdForUrl)
		synchronized(ids) { ids[profile] = Optional.ofNullable(id) }
		return id
	}

	override fun renderTypeFor(profile: GameProfile): RenderType? {
		if (!settings.skullsEnabled) return null
		val id = textureIdFor(profile) ?: return null
		val exists = synchronized(present) {
			present.getOrPut(id) { Minecraft.getInstance().resourceManager.getResource(id).isPresent }
		}
		return if (exists) RenderTypes.entityTranslucent(id) else null
	}

	private val ids = WeakHashMap<GameProfile, Optional<Identifier>>()
	private val present = HashMap<Identifier, Boolean>()

	fun clearCache() {
		synchronized(present) { present.clear() }
		synchronized(ids) { ids.clear() }
	}
}
