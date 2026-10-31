// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.PlusRepoData
import dev.ashtonite365.apex.util.render.WorldDraw
import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.Base64
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

object PlusThunderSparks {
	@Config
	object TConfig : ManagedConfig("thunder-sparks", Category.FISHING) {
		val highlight by riskyToggle("highlight", RiskLevel.GREY_AREA)
		val colour by colour("colour") { ChromaColour.fromStaticRGB(255, 255, 255, 120) }
	}

	private val TEXTURE_URL = Regex("textures\\.minecraft\\.net/texture/([0-9a-fA-F]+)")
	private val SKULLS = MapSerializer(String.serializer(), String.serializer())
	private const val SEARCH_RADIUS = 40.0
	private const val LABEL_DISTANCE = 10

	private fun repad(b64: String): String {
		val bare = b64.trimEnd('=')
		val missing = (4 - bare.length % 4) % 4
		return bare + "=".repeat(missing)
	}

	fun textureHash(base64: String?): String? {
		if (base64.isNullOrEmpty()) return null
		val json = try {
			Base64.getDecoder().decode(repad(base64)).decodeToString()
		} catch (e: Throwable) {
			return null
		}
		return TEXTURE_URL.find(json)?.groupValues?.get(1)?.lowercase()
	}

	fun sparkHashFrom(skullsJson: String?): String? {
		if (skullsJson == null) return null
		val value = try { PlusRepoData.parse(skullsJson, SKULLS)["THUNDER_SPARK"] } catch (e: Throwable) { null }
		return value?.let(::textureHash)
	}

	private fun sparkHash(): String? = PlusRepoData.loadRepo("constants/Skulls.json", SKULLS)?.get("THUNDER_SPARK")?.let(::textureHash)

	private fun headHash(stand: ArmorStand): String? {
		val profile = stand.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE)?.partialProfile() ?: return null
		return textureHash(profile.properties().get("textures").firstOrNull()?.value)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.highlight || SkyBlockSession.skyblockIsland != SkyBlockIsland.CRIMSON_ISLE) return
		val target = sparkHash() ?: return
		val player = Client.player ?: return
		val world = Client.level ?: return
		val sparks = world.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(SEARCH_RADIUS)) { headHash(it) == target }
		if (sparks.isEmpty()) return
		val colour = TConfig.colour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			for (spark in sparks) {
				val at = spark.position()
				box(AABB(at.x - 0.25, at.y, at.z - 0.25, at.x + 0.25, at.y + 0.5, at.z + 0.25), colour)
				if (at.distanceTo(player.position()) < LABEL_DISTANCE) text(at.add(0.0, 1.5, 0.0), Component.literal("Thunder Spark"))
			}
		}
	}
}
