/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: small drawing helpers shared by the Ice Fill and Ice Path solvers (visual only).
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.util.render.WorldDraw

object PuzzleDraw {

	fun arrow(ctx: WorldDraw, from: Vec3, to: Vec3, colour: Int, lineWidth: Float = 4F) {
		val d = to.subtract(from)
		val len = d.horizontalDistance()
		if (len < 1e-6) return
		val ux = d.x / len; val uz = d.z / len
		val tip = Vec3(to.x - ux * 0.15, from.y, to.z - uz * 0.15)
		val back = 0.35; val side = 0.22
		val left = Vec3(tip.x - ux * back - uz * side, from.y, tip.z - uz * back + ux * side)
		val right = Vec3(tip.x - ux * back + uz * side, from.y, tip.z - uz * back - ux * side)
		ctx.line(from, tip, color = colour, lineWidth = lineWidth)
		ctx.line(left, tip, right, color = colour, lineWidth = lineWidth)
	}
}
