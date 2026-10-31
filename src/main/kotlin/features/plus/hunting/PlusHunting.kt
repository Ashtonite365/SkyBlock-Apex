// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.hunting

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.plus.findMatcher
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i

object PlusHunting {
	@Config
	object TConfig : ManagedConfig("hunting-extras", Category.FORAGING) {
		val hideFlyingCapsules by toggle("hide-flying-capsules") { false }
		val hideCapsulesOnGround by toggle("hide-capsules-on-ground") { false }
		val safariBiomeNames by toggle("safari-biome-names") { false }
		val pureReptileCounter by toggle("pure-reptile-counter") { false }
		val hud by position("hud", 120, 12) { Vector2i(5, 260) }
	}

	fun isHuntingIsland(island: SkyBlockIsland?): Boolean {
		if (island == null) return false
		if (island == SkyBlockIsland.GALATEA) return true
		val mode = island.serverMode
		return mode.startsWith("foraging") || "safari" in mode || "torrhus" in mode
	}

	private fun coreId(stack: ItemStack) = stack.view().repoItemId?.skyblockId

	fun hideCapsule(id: String?, close: Boolean): Boolean = when (id) {
		"CRITTER_CAPSULE" -> true
		"MASTERFUL_CRITTER_CAPSULE" -> close
		else -> false
	}

	fun shouldHideEntity(entity: Entity): Boolean {
		if (!isHuntingIsland(SkyBlockSession.skyblockIsland)) return false
		if (entity is ItemEntity && TConfig.hideCapsulesOnGround) return coreId(entity.item) == "CRITTER_CAPSULE"
		if (entity is Display.ItemDisplay && TConfig.hideFlyingCapsules) {
			val stack = entity.itemRenderState()?.itemStack() ?: return false
			val eye = Client.player?.eyePosition ?: return false
			return hideCapsule(coreId(stack), entity.distanceToSqr(eye) <= 9.0)
		}
		return false
	}

	val biomes = listOf(
		Vec3(-27.1, 66.0, 22.8) to "§2Forest Biome", Vec3(-25.5, 66.0, -23.2) to "§5Haunted Biome",
		Vec3(-73.3, 65.0, -23.4) to "§9Icy Biome", Vec3(-72.6, 65.5, 23.9) to "§6Cavern Biome",
	)
	private val safariCentre = Vec3(-50.0, 65.0, 0.0)

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.safariBiomeNames || !isHuntingIsland(SkyBlockSession.skyblockIsland)) return
		val self = Client.player ?: return
		if (self.position().distanceTo(safariCentre) > 25) return
		WorldDraw.drawInWorld(event) {
			biomes.filter { (pos, _) -> pos.distanceToSqr(self.position()) > 50 }.forEach { (pos, name) -> text(pos, Component.literal(name)) }
		}
	}

	private val pureReptile by PlusPatterns.group("attributeshards").pattern("pure-reptile-chat", "^PURE REPTILE")
	var pureReptiles = 0
		private set

	fun isPureReptile(msg: String): Boolean = pureReptile.findMatcher(msg) { Unit } != null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.pureReptileCounter) return
		if (isPureReptile(event.bareText)) pureReptiles += 1
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.pureReptileCounter || pureReptiles == 0) return
		event.drawPlusLines(TConfig.hud, listOf(Component.literal("§b§lPURE REPTILE§7: §e$pureReptiles")))
	}
}
