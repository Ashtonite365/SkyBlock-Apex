// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.render

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.Base64
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object RenderHides {
	@Config
	object TConfig : ManagedConfig("render-hides", Category.RENDER) {
		val hideFallingBlocks by toggle("hide-falling-blocks") { false }
		val hideLightning by toggle("hide-lightning") { false }
		val hideDeathAnimation by toggle("hide-death-animation") { false }
		val hideExplosionParticles by toggle("hide-explosion-particles") { false }
		val hideArcherPassive by toggle("hide-archer-passive") { false }
		val hideHealerFairy by toggle("hide-healer-fairy") { false }
		val hideSoulWeaver by toggle("hide-soul-weaver") { false }
		val hideTentacleHead by toggle("hide-tentacle-head") { false }
	}

	object Logic {
		const val TENTACLE = "37d83674926b89714e6b5a55470501c04b066dd87bf6c335cddc6e60a1a1a5f5"
		const val HEALER_FAIRY = "2eedcffc6a11a3834a28849cc316af7a2752a376d536cf84039cf79108b167ae"
		const val SOUL_WEAVER = "2f24ed6875304fa4a1f0c785b2cb6a6a72563e9f3e24ea55e18178452119aa66"

		private val textureId = Regex("textures\\.minecraft\\.net/texture/([0-9a-f]+)")

		private fun JsonElement.child(key: String): JsonElement = asJsonObject.get(key)

		fun skinHash(base64: String): String? {
			val url = runCatching {
				val decoded = Base64.getDecoder().decode(base64).toString(Charsets.UTF_8)
				JsonParser.parseString(decoded).child("textures").child("SKIN").child("url").asString
			}.getOrNull() ?: return null
			return textureId.find(url)?.let { it.groupValues[1] }
		}

		fun hiddenHead(hash: String?, fairy: Boolean, weaver: Boolean, tentacle: Boolean, mainHand: Boolean): Boolean {
			if (hash == null) return false
			return if (mainHand) hash == HEALER_FAIRY && fairy
			else (hash == SOUL_WEAVER && weaver) || (hash == TENTACLE && tentacle)
		}
	}

	private fun headTexture(stack: ItemStack): String? {
		if (stack.isEmpty || stack.item != Items.PLAYER_HEAD) return null
		val property = stack.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull() ?: return null
		return Logic.skinHash(property.value)
	}

	private fun anyEntityHide(c: TConfig) = c.hideFallingBlocks || c.hideLightning || c.hideDeathAnimation ||
		c.hideArcherPassive || c.hideHealerFairy || c.hideSoulWeaver || c.hideTentacleHead

	private fun hiddenMob(mob: LivingEntity, c: TConfig, inDungeon: Boolean): Boolean {
		if (c.hideDeathAnimation && mob.isDeadOrDying) return true
		if (!inDungeon) return false
		if (!c.hideHealerFairy && !c.hideSoulWeaver && !c.hideTentacleHead) return false
		val hand = headTexture(mob.getItemBySlot(EquipmentSlot.MAINHAND))
		if (Logic.hiddenHead(hand, c.hideHealerFairy, c.hideSoulWeaver, c.hideTentacleHead, true)) return true
		val helmet = headTexture(mob.getItemBySlot(EquipmentSlot.HEAD))
		return Logic.hiddenHead(helmet, c.hideHealerFairy, c.hideSoulWeaver, c.hideTentacleHead, false)
	}

	private fun hidden(entity: Entity, c: TConfig, inDungeon: Boolean): Boolean = when (entity) {
		is FallingBlockEntity -> c.hideFallingBlocks
		is LightningBolt -> c.hideLightning
		is ItemEntity -> c.hideArcherPassive && inDungeon && entity.item.item == Items.BONE_MEAL
		is Player -> false
		is LivingEntity -> hiddenMob(entity, c, inDungeon)
		else -> false
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!anyEntityHide(TConfig)) return
		val world = Client.level ?: return
		val inDungeon = DungeonUtil.isInDungeonIsland
		val me = Client.player
		val doomed = world.entitiesForRendering().filter { it !== me && hidden(it, TConfig, inDungeon) }
		for (e in doomed) e.remove(Entity.RemovalReason.DISCARDED)
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.hideExplosionParticles) return
		val type = event.particleEffect.type
		if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) event.cancel()
	}
}
