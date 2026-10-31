/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: alerts when the dungeon room you are in gets its white (cleared) or green (all secrets) check mark.
 * Reads the vanilla map item in hotbar slot 9 through DungeonMap's read-only grid / tick helpers. No network.
 */
package dev.ashtonite365.apex.features.kit.dungeon

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes

object RoomClear {

	fun cellOf(mx: Int, my: Int, grid: DungeonMap.Grid): Pair<Int, Int>? {
		val dx = (mx + 128) / 2 - grid.x
		val dy = (my + 128) / 2 - grid.y
		if (dx < 0 || dy < 0) return null
		val step = grid.step
		val inRoomX = dx % step < grid.room
		if (!inRoomX) return null
		if (dy % step >= grid.room) return null
		return dx / step to dy / step
	}

	fun alertFor(old: DungeonMap.Tick?, new: DungeonMap.Tick?, mode: DungeonExtras.ClearMode): String? {
		if (new == null || new == old || old == DungeonMap.Tick.GREEN) return null
		if (new == DungeonMap.Tick.GREEN) return if (mode != DungeonExtras.ClearMode.WHITE) "§aRoom Complete!" else null
		if (new == DungeonMap.Tick.WHITE) return if (mode != DungeonExtras.ClearMode.GREEN) "§fRoom Cleared!" else null
		return null
	}

	private val SILENT_ROOM_COLOURS = setOf(30, 82)

	private var previous: Map<Pair<Int, Int>, DungeonMap.Tick> = emptyMap()
	private var primed = false

	private fun forget() {
		primed = false
		previous = emptyMap()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		if (!DungeonExtras.TConfig.roomClear || !DungeonUtil.isInActiveDungeon) return forget()
		val player = Client.player ?: return
		val world = Client.level ?: return
		val map = player.inventory.getItem(8)
		if (!map.`is`(Items.FILLED_MAP)) return
		val saved = MapItem.getSavedData(map, world) ?: return
		val grid = DungeonMap.detectGrid(saved.colors) ?: return
		val ticks = DungeonMap.roomTicks(saved.colors, grid)
		val marker = saved.decorations.firstOrNull { it.type().value() == MapDecorationTypes.FRAME.value() }
		val cell = marker?.let { cellOf(it.x().toInt(), it.y().toInt(), grid) }
		if (primed && cell != null) {
			val px = grid.x + cell.first * grid.step + 1
			val py = grid.y + cell.second * grid.step + 1
			val colour = saved.colors[px + py * DungeonMap.SIZE].toInt() and 0xFF
			if (colour !in SILENT_ROOM_COLOURS) {
				val text = alertFor(previous[cell], ticks[cell], DungeonExtras.TConfig.roomClearMode)
				if (text != null) Alerts.show(Component.literal(text))
			}
		}
		previous = ticks
		primed = true
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = forget()
}
