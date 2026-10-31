// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import kotlin.math.floor
import net.minecraft.world.phys.Vec3

object GardenPlots {
	@Config
	object TConfig : ManagedConfig("plus-plots", Category.GARDEN) {
		val toggleBordersKey by keyBindingWithDefaultUnbound("toggle-borders-key")
		val sprayonatorBorder by toggle("sprayonator-border") { false }
	}

	val plotMap = listOf(
		listOf(21, 13, 9, 14, 22),
		listOf(15, 5, 1, 6, 16),
		listOf(10, 2, 0, 3, 11),
		listOf(17, 7, 4, 8, 18),
		listOf(23, 19, 12, 20, 24),
	)
	const val PLOT_SIZE = 96
	private const val HALF_EXTENT = 240.0

	private val cellOf: Map<Int, Pair<Int, Int>> = buildMap {
		plotMap.forEachIndexed { row, ids -> ids.forEachIndexed { col, id -> putIfAbsent(id, col to row) } }
	}

	private fun cell(v: Double): Int? {
		if (v < -HALF_EXTENT || v > HALF_EXTENT) return null
		val raw = floor((v + HALF_EXTENT) / PLOT_SIZE).toInt()
		return raw.coerceIn(0, 4)
	}

	fun plotIdAt(x: Double, z: Double): Int? {
		val col = cell(x) ?: return null
		val row = cell(z) ?: return null
		return plotMap[row][col]
	}

	fun plotCorner(id: Int): Pair<Int, Int>? {
		val (col, row) = cellOf[id] ?: return null
		return (col - 2) * PLOT_SIZE - 48 to (row - 2) * PLOT_SIZE - 48
	}

	fun plotMiddle(id: Int, y: Double = 75.0): Vec3? {
		val (x, z) = plotCorner(id) ?: return null
		return Vec3(x + 48.0, y, z + 48.0)
	}

	fun currentPlotId(): Int? {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return null
		val player = Client.player ?: return null
		return plotIdAt(player.x, player.z)
	}

	var bordersShown = false

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (event.matches(TConfig.toggleBordersKey)) bordersShown = !bordersShown
	}

	fun holdingSprayonator(): Boolean {
		val id = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId ?: return false
		return id.endsWith("SPRAYONATOR")
	}

	fun WorldDraw.drawPlotBorder(id: Int, color: Int) {
		val (minX, minZ) = plotCorner(id) ?: return
		val bottom = 66.0
		val top = 102.0
		val x0 = minX.toDouble()
		val x1 = minX + 96.0
		val z0 = minZ.toDouble()
		val z1 = minZ + 96.0
		for (x in listOf(x0, x1)) for (z in listOf(z0, z1)) line(Vec3(x, bottom, z), Vec3(x, top, z), color = color, lineWidth = 3f)
		var y = bottom
		while (y <= top) {
			line(Vec3(x0, y, z0), Vec3(x1, y, z0), Vec3(x1, y, z1), Vec3(x0, y, z1), Vec3(x0, y, z0), color = color, lineWidth = 2f)
			y += 12.0
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val id = currentPlotId() ?: return
		val spraying = TConfig.sprayonatorBorder && holdingSprayonator()
		if (!spraying && !bordersShown) return
		val colour = if (spraying) 0xFFFFFF55.toInt() else 0xFF55FFFF.toInt()
		WorldDraw.drawInWorld(event) { drawPlotBorder(id, colour) }
	}
}
