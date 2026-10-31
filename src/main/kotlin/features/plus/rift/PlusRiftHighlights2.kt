// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId

object PlusRiftHighlights2 {
	@Config
	object TConfig : ManagedConfig("rift-mob-highlights", Category.RIFT) {
		val motesOrbs by toggle("motes-orbs") { false }
		val hideOrbParticles by toggle("hide-orb-particles") { false }
		val larvaOdonata by riskyToggle("larva-odonata", RiskLevel.GREY_AREA)
		val agaricus by toggle("agaricus-cap") { false }
	}

	private fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	val orbs = MotesOrbTracker()

	private fun nearVisibleOrb(at: Vec3) = orbs.visible().any { Vec3(it.x, it.y, it.z).distanceTo(at) < 3 }

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.motesOrbs || !inRift()) return
		if (event.particleEffect.type != ParticleTypes.ENTITY_EFFECT) return
		val at = event.position
		orbs.particle(at.x, at.y, at.z, System.currentTimeMillis())
		if (TConfig.hideOrbParticles && nearVisibleOrb(at)) event.cancel()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!(TConfig.motesOrbs && inRift())) return
		if (!MotesOrbTracker.pickupChat.matches(event.bareText.trim())) return
		val me = Client.player ?: return
		orbs.pickedUpNear(me.x, me.y, me.z)
	}

	private var mushroomPos: BlockPos? = null
	private var mushroomSince = 0L

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		orbs.orbs.clear()
		mushroomPos = null
	}

	private fun blockPath(pos: BlockPos): String? {
		val block = Client.level?.getBlockState(pos)?.block ?: return null
		return BuiltInRegistries.BLOCK.getKey(block).path
	}

	private fun lookedAtMushroom(): BlockPos? {
		val pos = (Minecraft.getInstance().hitResult as? BlockHitResult)?.blockPos ?: return null
		val path = blockPath(pos)
		return if (path == "brown_mushroom" || path == "red_mushroom") pos else null
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		orbs.update(System.currentTimeMillis())
		val holdingWand = TConfig.agaricus && inRift() && Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId == "FARMING_WAND"
		val target = if (holdingWand) lookedAtMushroom() else null
		if (target == null) {
			mushroomPos = null
			return
		}
		if (target != mushroomPos) {
			mushroomPos = target
			mushroomSince = System.currentTimeMillis()
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inRift()) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		WorldDraw.drawInWorld(event) {
			if (TConfig.motesOrbs) for (orb in orbs.visible()) {
				box(AABB.ofSize(Vec3(orb.x, orb.y, orb.z), 0.6, 0.6, 0.6), 0x80FF55FF.toInt())
				text(Vec3(orb.x, orb.y + 0.8, orb.z), Component.literal("§dMotes Orb"))
			}
			val skullSlot = if (TConfig.larvaOdonata) RiftRules.skullTarget(player.mainHandItem.view().repoItemId?.skyblockId) else null
			if (skullSlot != null) {
				val slot = if (skullSlot == RiftRules.SkullTarget.WORN) EquipmentSlot.HEAD else EquipmentSlot.MAINHAND
				for (stand in level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(16.0)) { true })
					if (stand.getItemBySlot(slot).item == Items.PLAYER_HEAD) box(stand.boundingBox.inflate(0.2), 0x6000FF00)
			}
			val cap = mushroomPos
			if (TConfig.agaricus && cap != null) {
				val path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cap).block).path
				RiftRules.agaricusLabel(path, System.currentTimeMillis() - mushroomSince)
					?.let { text(Vec3.atCenterOf(cap).add(0.0, 0.8, 0.0), Component.literal(it)) }
			}
		}
	}
}
