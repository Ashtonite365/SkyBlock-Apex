// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.util.render.WaypointType
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object PlusTrophyHelpers {
	@Config
	object TConfig : ManagedConfig("trophy-fishing", Category.FISHING) {
		val geyserBox by toggle("geyser-box") { false }
		val geyserBoxColour by colour("geyser-box-colour") { ChromaColour.fromStaticRGB(255, 0, 255, 80) }
		val geyserOnlyWithRod by toggle("geyser-only-with-rod") { true }
		val hideGeyserParticles by toggle("hide-geyser-particles") { false }
		val odgerWaypoint by toggle("odger-waypoint") { false }
	}

	private var geyser: Vec3? = null
	val ODGER: BlockPos = BlockPos(-373, 207, -808)
	private const val LAVA_Y = 118.0

	fun isGeyserParticle(cloud: Boolean, count: Int, speed: Float, ox: Float, oy: Float, oz: Float): Boolean {
		if (!cloud || count != 15) return false
		return speed == 0.05f && ox == 0.1f && oy == 0.6f && oz == 0.1f
	}

	fun geyserBox(g: Vec3) = AABB(g.x - 2, LAVA_Y - 0.1, g.z - 2, g.x + 2, LAVA_Y - 0.09, g.z + 2)

	fun isTrophyLore(lines: List<String>): Boolean {
		for (line in lines) if (line.trim().endsWith("TROPHY FISH")) return true
		return false
	}

	private fun onCrimson() = SkyBlockSession.skyblockIsland == SkyBlockIsland.CRIMSON_ISLE

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!onCrimson()) return
		if (!TConfig.geyserBox && !TConfig.hideGeyserParticles) return
		val off = event.offset
		val cloud = event.particleEffect.type == ParticleTypes.CLOUD
		if (!isGeyserParticle(cloud, event.count, event.speed, off.x, off.y, off.z)) return
		val at = event.position
		geyser = at
		if (!TConfig.hideGeyserParticles) return
		val bobber = Client.player?.fishing?.position() ?: return
		if (bobber.distanceTo(event.position) < 3 && bobber.distanceTo(at) < 3) event.cancel()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		geyser = null
	}

	private var hasTrophy = false

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.odgerWaypoint || !onCrimson() || event.tickCount % 10 != 0) return
		val inv = Client.player?.inventory ?: return
		var found = false
		for (slot in 0 until inv.containerSize) {
			val stack = inv.getItem(slot)
			if (stack.isEmpty) continue
			if (isTrophyLore(stack.view().loreAccordingToNbt.map { it.bareText })) { found = true; break }
		}
		hasTrophy = found
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!onCrimson()) return
		val player = Client.player ?: return
		val rodInHand = player.mainHandItem.item == Items.FISHING_ROD
		val at = geyser
		val showBox = TConfig.geyserBox && at != null && (rodInHand || !TConfig.geyserOnlyWithRod) &&
			Vec3(at.x, player.y, at.z).distanceTo(player.position()) <= 96
		if (showBox) WorldDraw.drawInWorld(event) { box(geyserBox(at!!), TConfig.geyserBoxColour.getEffectiveColourRGB()) }
		if (TConfig.odgerWaypoint && hasTrophy && !rodInHand) {
			WorldDraw.drawInWorld(event, WaypointType.FISHING) {
				block(ODGER, 0x60FFFFFF)
				text(Vec3.atCenterOf(ODGER).add(0.0, 1.5, 0.0), Component.literal("Odger"))
			}
		}
	}
}
