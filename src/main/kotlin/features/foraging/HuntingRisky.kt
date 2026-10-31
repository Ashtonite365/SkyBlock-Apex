/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.foraging

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object HuntingRisky {
	@Config
	object TConfig : ManagedConfig("hunting-risky", Category.FORAGING) {
		val hiddenMobFinder by riskyToggle("hidden-mob-finder", RiskLevel.RISKY)
		val tracer by toggle("tracer") { true }
		val beaconSolver by riskyToggle("beacon-solver", RiskLevel.GREY_AREA)
		val templeSolver by riskyToggle("temple-solver", RiskLevel.GREY_AREA)
	}

	private val HIDDEN = listOf("Hideonleaf", "Invisibug", "Hideyho", "Birries", "Hideonsun")
	private var found: List<Pair<Int, String>> = emptyList()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		found = emptyList()
		if (!TConfig.hiddenMobFinder || !dev.ashtonite365.apex.features.plus.hunting.PlusHunting.isHuntingIsland(SkyBlockSession.skyblockIsland)) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		val out = mutableListOf<Pair<Int, String>>()

		for (stand in level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(48.0)) { it.customName != null }) {
			val name = stand.customName!!.bareText
			val mob = HIDDEN.firstOrNull { name.contains(it, ignoreCase = true) } ?: continue
			val body = level.getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.5, 3.0, 1.5)) { it !is ArmorStand && it !== player }
				.minByOrNull { it.distanceToSqr(stand) }
			out += (body ?: stand).id to mob
		}

		for (e in level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(48.0)) {
			it.isInvisible && it !is ArmorStand && it !is net.minecraft.world.entity.player.Player
		}) if (out.none { it.first == e.id }) out += e.id to "Hidden mob"
		found = out
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (found.isEmpty()) return
		val level = Client.level ?: return
		WorldDraw.drawInWorld(event, WaypointType.FORAGING) {
			for ((id, name) in found) {
				val e = level.getEntity(id) ?: continue
				markerBox(e.boundingBox.inflate(0.2), 0x8040FF40.toInt())
				waypoint(BlockPos.containing(e.position()), Component.literal("§a$name"))
				if (TConfig.tracer) tracer(e.position(), 0xFF40FF40.toInt())
			}
		}
	}

	private fun isBeacon() = Client.screenTitle?.let { it.contains("Beacon", true) || it.contains("Tune", true) } == true
	private fun lore(s: ItemStack) = s.view().loreAccordingToNbt.map { it.bareText }
	private val TARGET = Regex("(?:Target|Required|Current Target|Goal)[^:]*: (.+)", RegexOption.IGNORE_CASE)

	@Subscribe
	fun onBeaconSlot(event: SlotRenderEvents.Before) {
		if (!TConfig.beaconSolver || !isBeacon() || event.slot.container is Inventory) return
		val menu = Client.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> ?: return
		val targets = menu.menu.slots.filter { it.container !is Inventory }
			.flatMap { lore(it.item) }.mapNotNull { TARGET.find(it)?.groupValues?.get(1)?.trim() }.toSet()
		if (targets.isEmpty()) return
		val text = (lore(event.slot.item) + event.slot.item.hoverName.bareText).joinToString("\n")
		if (targets.any { t -> TARGET.find(text) == null && text.contains(t, true) })
			event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, 0x8000FF00.toInt())
	}

	private val sequence = mutableListOf<Int>()
	private var lastGlowing = emptySet<Int>()
	private var clicked = 0

	@Subscribe
	fun onScreen(event: ScreenChangeEvent) {
		sequence.clear(); lastGlowing = emptySet(); clicked = 0
	}

	@Subscribe
	fun onTempleTick(event: TickEvent) {
		if (!TConfig.templeSolver || Client.screenTitle?.contains("Temple", true) != true) return
		val menu = Client.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> ?: return
		val glowing = menu.menu.slots.filter { it.container !is Inventory && it.item.hasFoil() }.map { it.index }.toSet()
		for (i in glowing - lastGlowing) sequence += i
		lastGlowing = glowing
	}

	@Subscribe
	fun onTempleSlot(event: SlotRenderEvents.After) {
		if (!TConfig.templeSolver || sequence.isEmpty() || Client.screenTitle?.contains("Temple", true) != true) return
		val steps = sequence.withIndex().filter { it.value == event.slot.index }.map { it.index + 1 }
		if (steps.isEmpty()) return
		val t = steps.joinToString(",")
		event.context.text(Client.font, t, event.slot.x + 1, event.slot.y + 1, 0xFFFFFF55.toInt(), true)
	}
}
