/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: clean-room Apex implementation of the dungeon key and door helpers.
 * Key Highlight (⚠, off by default) boxes the floating Wither/Blood key; Key Spawn Alert shows a local alert when one
 * appears. Nearest Locked Door (⚠, off by default) boxes the next still-locked wither door (coal) or blood door
 * (red terracotta) reachable from the rooms you can walk to ([DoorLogic.nextDoor]), found by probing the fixed door
 * slots of the 32-block room grid. With Key Path (⚠) it also draws a walking route around walls to that door while
 * you hold its key (the shared A*, [BlockPathPlanner], limited to the rooms on the route). A door is latched as open
 * the moment it opens (chat line or falling-block animation) and is never drawn again, so the vanilla opening
 * animation renders normally. Throttled "[ApexDoorPath]" log lines explain a missing route from latest.log.
 */
package dev.ashtonite365.apex.features.kit.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.events.ProcessChatEvent
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import dev.ashtonite365.apex.features.world.BlockPathPlanner
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object KeyHighlight {
	enum class Key(val label: String, val code: Char) { WITHER("Wither Key", '8'), BLOOD("Blood Key", 'c') }

	fun keyOf(name: String): Key? {
		val shown = name.trim()
		return Key.entries.find { k -> k.label == shown }
	}

	private class SeenKey(val key: Key, val stand: ArmorStand)

	private var seenKey: SeenKey? = null

	private fun findKey(level: Level, player: Player): SeenKey? {
		val stands = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(64.0)) { it.hasCustomName() }
		for (stand in stands) {
			val key = keyOf(stand.customName!!.bareText) ?: continue
			return SeenKey(key, stand)
		}
		return null
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		val cfg = DungeonExtras.TConfig
		val wanted = cfg.keyHighlight || cfg.keySpawnAlert
		if (!wanted || !DungeonUtil.isInActiveDungeon) {
			seenKey = null
			return
		}
		val player = Client.player ?: return
		val level = Client.level ?: return
		if (seenKey?.stand?.isAlive == false) seenKey = null
		if (seenKey != null) return
		val found = findKey(level, player) ?: return
		seenKey = found
		if (cfg.keySpawnAlert) Alerts.show(Component.literal("§${found.key.code}${found.key.label}§7 spawned!"))
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val cfg = DungeonExtras.TConfig
		if (!cfg.keyHighlight) return
		val seen = seenKey ?: return
		if (!seen.stand.isAlive) return
		val rgb = when (seen.key) {
			Key.WITHER -> cfg.witherKeyColour
			Key.BLOOD -> cfg.bloodKeyColour
		}.getEffectiveColourRGB()
		val at = seen.stand.position()
		val area = AABB(at.x - 0.5, at.y + 1.0, at.z - 0.5, at.x + 0.5, at.y + 2.0, at.z + 0.5)
		WorldDraw.drawInWorld(event) { box(area, rgb) }
	}

	private var slots: Map<DoorLogic.Slot, DoorLogic.SlotState> = emptyMap()

	private val opened = HashSet<DoorLogic.Slot>()
	private var target: DoorLogic.Door? = null
	private var probeNextTick = false
	val keys = DoorLogic.KeyState()

	private val planner = BlockPathPlanner(
		nodeCap = 30_000, maxDist = 200, replanDistSq = 2.0, minTicksBetween = 10, cellsPerTick = 32_768,
		straightFallback = false, keepLastFound = true, drawHeight = 0.1, maxSpan = 260, maxVolume = 1_500_000,
		goalRadius = 2.5,
	)

	private object PathLog {
		private val logger = org.slf4j.LoggerFactory.getLogger("Apex/ApexDoorPath")
		var lastKey: String? = null
		private var lastAt = 0L

		fun write(key: String, text: () -> String) {
			val now = System.currentTimeMillis()
			val quiet = if (key == lastKey) 15_000 else 1_000
			if (now - lastAt < quiet) return
			lastKey = key
			lastAt = now
			logger.info("[ApexDoorPath] {}", text())
		}
	}

	fun doorBox(slot: DoorLogic.Slot): AABB {
		val bottom = DoorLogic.DOOR_Y.first.toDouble()
		val top = DoorLogic.DOOR_Y.last + 1.0
		val x = slot.x.toDouble()
		val z = slot.z.toDouble()
		return if (slot.east) AABB(x, bottom, z - 1, x + 1, top, z + 2)
		else AABB(x - 1, bottom, z, x + 2, top, z + 1)
	}

	private fun latchOpen(slot: DoorLogic.Slot) {
		if (!opened.add(slot)) return
		slots = slots + (slot to DoorLogic.SlotState.OPEN)
		DungeonMap.blockDoors = DoorLogic.mapDoors(slots)
		if (target?.slot != slot) return
		target = null
		planner.clear()
	}

	private fun probe(level: Level, x: Int, y: Int, z: Int): DoorLogic.Probe {
		val pos = BlockPos(x, y, z)
		if (!level.isLoaded(pos)) return DoorLogic.Probe.UNLOADED
		val state = level.getBlockState(pos)
		return when {
			state.`is`(Blocks.COAL_BLOCK) -> DoorLogic.Probe.COAL
			state.`is`(Blocks.DYED_TERRACOTTA.red) -> DoorLogic.Probe.BLOOD
			state.isAir -> DoorLogic.Probe.AIR

			state.getCollisionShape(level, pos).isEmpty -> DoorLogic.Probe.PASSABLE
			else -> DoorLogic.Probe.SOLID
		}
	}

	private fun contextOf(level: Level, slot: DoorLogic.Slot): DoorLogic.SlotContext {
		val a = Pair(slot.a.i, slot.a.j)
		val b = Pair(slot.b.i, slot.b.j)
		val fairy = listOf(a, b).any { DungeonRoomTracker.roomTypeAt(level, it) == "FAIRY" }
		return DoorLogic.SlotContext(fairyBeyond = fairy, mapPixel = DungeonMap.doorPixel(a, b))
	}

	private fun whyNoPath(held: DoorLogic.DoorType?, pathOn: Boolean, door: DoorLogic.Door?, hasArea: Boolean, outcome: Any?): String = when {
		held == null -> "no key held"
		!pathOn -> "key path setting off"
		door == null -> "no target door"
		door.type != held -> "next door is ${door.type}, key is $held"
		!hasArea -> "no room route to the door"
		else -> outcome?.toString() ?: "planning"
	}

	@Subscribe
	fun onDoorTick(event: TickEvent) {
		val cfg = DungeonExtras.TConfig
		if (!cfg.nearestDoorHighlight || !DungeonUtil.isInActiveDungeon) {
			target = null

			DungeonMap.blockDoors = emptyMap()
			DungeonMap.scannedConnections = emptySet()
			DungeonMap.noCoalDoors = emptySet()
			planner.clear()
			if (DungeonUtil.isInDungeonIsland) PathLog.write("gate ${cfg.nearestDoorHighlight}") {
				"off: highlight=${cfg.nearestDoorHighlight} path=${cfg.nearestDoorPath} activeDungeon=${DungeonUtil.isInActiveDungeon}"
			}
			return
		}
		val player = Client.player ?: return
		val level = Client.level ?: return
		if (probeNextTick || event.tickCount % 20 == 0) {
			probeNextTick = false
			slots = DoorLogic.scan({ x, y, z -> probe(level, x, y, z) }, slots, opened) { slot -> contextOf(level, slot) }
			DungeonMap.blockDoors = DoorLogic.mapDoors(slots)
			DungeonMap.scannedConnections = slots.filterValues { it == DoorLogic.SlotState.OPEN || it == DoorLogic.SlotState.WITHER || it == DoorLogic.SlotState.BLOOD }
				.keys.mapTo(HashSet()) { DungeonMap.DoorKey(it.a.i, it.a.j, it.east) }

			DungeonMap.noCoalDoors = slots.filterValues { it == DoorLogic.SlotState.OPEN || it == DoorLogic.SlotState.WALL }
				.keys.mapTo(HashSet()) { DungeonMap.DoorKey(it.a.i, it.a.j, it.east) }
		}

		if (event.tickCount % 2 == 0) {
			DoorLogic.lockedDoors(slots)
				.filter { level.getEntitiesOfClass(FallingBlockEntity::class.java, doorBox(it.slot).inflate(0.5)).isNotEmpty() }
				.forEach { latchOpen(it.slot) }
		}
		val held = keys.held()
		val next = DoorLogic.nextDoor(slots, player.x, player.z, held)
		target = next
		val walkTo = if (cfg.nearestDoorPath && held != null && next != null && next.type == held) next else null
		val area = if (walkTo == null) null else DoorLogic.routeArea(slots, player.x, player.z, walkTo)
		val outcome = if (walkTo != null && area != null) planner.lastOutcome else null
		val stateKey = listOf(held, next, walkTo != null, area != null, outcome?.found, outcome?.reason?.substringBefore(" (")).joinToString("|")
		PathLog.write(stateKey) {
			val why = whyNoPath(held, cfg.nearestDoorPath, next, area != null, outcome)
			"held=$held feet=${player.blockPosition().toShortString()} ${DoorLogic.diagnose(slots, player.x, player.z, held)} path: $why"
		}
		if (walkTo == null || area == null) {
			planner.clear()
			return
		}
		val feet = player.blockPosition()
		val low = minOf(feet.y, DoorLogic.FLOOR_Y) - 12
		val high = maxOf(feet.y, DoorLogic.DOOR_Y.last) + 12
		val goal = BlockPos(walkTo.slot.x, DoorLogic.FEET_Y, walkTo.slot.z)
		val bounds = BlockPathPlanner.Box(area.minX, low, area.minZ, area.maxX, high, area.maxZ)
		planner.update(feet, goal, BlockPathPlanner.MinecraftSource(level), maxOf(low, level.minY), minOf(high, level.maxY), bounds)
	}

	private fun distSq(slot: DoorLogic.Slot, p: Player): Double {
		val dx = slot.x + 0.5 - p.x
		val dz = slot.z + 0.5 - p.z
		return dx * dx + dz * dz
	}

	@Subscribe
	fun onDoorChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		val line = event.bareText.trim()
		keys.onChat(line)
		val openedType = DoorLogic.openedByChat(line)
		if (openedType == DoorLogic.DoorType.BLOOD) {
			for (door in DoorLogic.lockedDoors(slots)) if (door.type == DoorLogic.DoorType.BLOOD) latchOpen(door.slot)
		} else if (openedType == DoorLogic.DoorType.WITHER) {

			val who = line.substringBefore(" opened a WITHER door!").substringAfterLast(' ')
			val opener: Player? = Client.level?.players()?.find { it.gameProfile.name == who } ?: Client.player
			if (opener != null) {
				val closest = DoorLogic.lockedDoors(slots).filter { it.type == DoorLogic.DoorType.WITHER }.minByOrNull { distSq(it.slot, opener) }
				if (closest != null && distSq(closest.slot, opener) <= 64.0) latchOpen(closest.slot)
			}
		}
		if (openedType != null) probeNextTick = true
		if (keys.held() == null) planner.clear()
	}

	@Subscribe
	fun onDoorRender(event: WorldRenderLastEvent) {
		val cfg = DungeonExtras.TConfig
		if (!cfg.nearestDoorHighlight) return
		val door = target ?: return
		if (door.slot in opened) return
		val doorRgb = (if (door.type == DoorLogic.DoorType.WITHER) cfg.witherDoorColour else cfg.bloodDoorColour).getEffectiveColourRGB()
		val route = planner.result?.let { if (it.found) it.points else null }
		WorldDraw.drawInWorld(event) {
			box(doorBox(door.slot), doorRgb)
			if (route != null && route.size >= 2) line(route, color = cfg.doorPathColour.getEffectiveColourRGB(), lineWidth = 4F)
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		seenKey = null
		slots = emptyMap()
		opened.clear()
		DungeonMap.blockDoors = emptyMap()
		DungeonMap.scannedConnections = emptySet()
		DungeonMap.noCoalDoors = emptySet()
		target = null
		probeNextTick = false
		PathLog.lastKey = null
		planner.clear()
		keys.reset()
	}
}
