/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): Pickobulus area helper and risky mining highlights (corpse and ladder
 * locators, ghost visibility, dropped items through walls). Inspired by, and feature
 * descriptions only. Original implementation, no code copied.
 */
package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import dev.ashtonite365.apex.annotations.Subscribe
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

object MiningRisky {
	@Config
	object TConfig : ManagedConfig("mining-risky", Category.MINING) {
		val pickobulusArea by toggle("pickobulus-area") { false }
		val corpseLocator by riskyToggle("corpse-locator", RiskLevel.RISKY)
		val ladderLocator by riskyToggle("ladder-locator", RiskLevel.RISKY)
		val ghostVisibility by riskyToggle("ghost-visibility", RiskLevel.RISKY)
		val itemsThroughWalls by riskyToggle("items-through-walls", RiskLevel.RISKY)
	}

	private val mines = setOf(SkyBlockIsland.DWARVEN_MINES, SkyBlockIsland.CRYSTAL_HOLLOWS, SkyBlockIsland.MINESHAFT)

	fun corpseType(helmetName: String): String? = when {
		helmetName.contains("Lapis Armor") -> "§9Lapis"
		helmetName.contains("Mineral") -> "§6Umber"
		helmetName.contains("Yog") -> "§7Tungsten"
		helmetName.contains("Vanguard") -> "§bVanguard"
		else -> null
	}

	private var ladders = listOf<BlockPos>()
	private var tick = 0

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.ladderLocator || SkyBlockSession.skyblockIsland != SkyBlockIsland.MINESHAFT) { ladders = emptyList(); return }
		if (tick++ % 40 != 0) return
		val level = Client.game.level ?: return
		val c = Client.player?.blockPosition() ?: return
		ladders = BlockPos.betweenClosed(c.offset(-40, -20, -40), c.offset(40, 20, 40))
			.filter { level.getBlockState(it).`is`(Blocks.LADDER) }.map { it.immutable() }
			.groupBy { it.x to it.z }.values.map { col -> col.minBy { it.y } }
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (SkyBlockSession.skyblockIsland !in mines) return
		val level = Client.game.level ?: return
		WorldDraw.drawInWorld(event) {
			if (TConfig.pickobulusArea) {
				val held = Client.player?.mainHandItem
				val target = (Client.game.hitResult as? BlockHitResult)?.blockPos
				if (held != null && target != null && held.view().loreAccordingToNbt.any { it.bareText.contains("Pickobulus") })
					box(AABB(target).inflate(2.0), 0x2000FFFF)
			}
			looks(WaypointType.CORPSES_LADDERS) { for (pos in ladders) waypoint(pos, Component.literal("§eLadder")) }
			for (entity in level.entitiesForRendering()) {
				when {
					TConfig.corpseLocator && entity is ArmorStand && SkyBlockSession.skyblockIsland == SkyBlockIsland.MINESHAFT -> {
						val type = corpseType(entity.getItemBySlot(EquipmentSlot.HEAD).hoverName.bareText) ?: continue
						looks(WaypointType.CORPSES_LADDERS) { waypoint(entity.blockPosition(), Component.literal("$type Corpse")) }
					}
					TConfig.ghostVisibility && entity is Creeper && entity.isInvisible && SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES ->
						box(entity.boundingBox, 0x60AA00FF)
					TConfig.itemsThroughWalls && entity is ItemEntity ->
						box(entity.boundingBox.inflate(0.2), 0x60FFFF00)
				}
			}
		}
	}
}
