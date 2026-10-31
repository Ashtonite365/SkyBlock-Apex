// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory.storageoverlay

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack

object StoragePaneCtm {
	private const val ROW = 9
	private const val SIZE = 18
	private const val PANE_SUFFIX = "_stained_glass_pane"

	const val UP = 1
	const val RIGHT = 2
	const val DOWN = 4
	const val LEFT = 8
	const val UP_LEFT = 16
	const val UP_RIGHT = 32
	const val DOWN_RIGHT = 64
	const val DOWN_LEFT = 128

	private val offsets = listOf(UP to (0 to -1), RIGHT to (1 to 0), DOWN to (0 to 1), LEFT to (-1 to 0),
		UP_LEFT to (-1 to -1), UP_RIGHT to (1 to -1), DOWN_RIGHT to (1 to 1), DOWN_LEFT to (-1 to 1))

	fun paneColour(stack: ItemStack): DyeColor? {
		if (stack.isEmpty) return null
		val id = BuiltInRegistries.ITEM.getKey(stack.item).path
		return if (id.endsWith(PANE_SUFFIX)) DyeColor.byName(id.dropLast(PANE_SUFFIX.length), null) else null
	}

	fun isPane(stack: ItemStack) = paneColour(stack) != null

	fun <T : Any> neighbours(cells: List<T?>, index: Int): Int {
		val own = cells.getOrNull(index) ?: return 0
		val column = index % ROW
		var bits = 0
		for ((bit, offset) in offsets) {
			val (dx, dy) = offset
			if (column + dx !in 0 until ROW) continue
			val other = index + dx + dy * ROW
			if (other >= 0 && cells.getOrNull(other) == own) bits = bits or bit
		}
		return bits
	}

	fun drawPanes(context: GuiGraphicsExtractor, stacks: List<ItemStack>, alpha: Float, slotX: (Int) -> Int, slotY: (Int) -> Int) {
		val colours = stacks.map(::paneColour)
		val opaque = (alpha * 255).toInt().coerceIn(0, 255)
		for ((index, colour) in colours.withIndex()) {
			colour ?: continue
			val rgb = colour.textureDiffuseColor and 0xFFFFFF
			drawFrame(context, slotX(index) - 1, slotY(index) - 1, neighbours(colours, index),
				fill = (opaque / 2 shl 24) or rgb, edge = (opaque shl 24) or rgb)
		}
	}

	private fun drawFrame(ctx: GuiGraphicsExtractor, x: Int, y: Int, joined: Int, fill: Int, edge: Int) {
		fun has(bit: Int) = joined and bit != 0
		ctx.fill(x, y, x + SIZE, y + SIZE, fill)
		if (!has(UP)) ctx.fill(x, y, x + SIZE, y + 1, edge)
		if (!has(DOWN)) ctx.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, edge)
		if (!has(LEFT)) ctx.fill(x, y, x + 1, y + SIZE, edge)
		if (!has(RIGHT)) ctx.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, edge)
		if (has(UP) && has(LEFT) && !has(UP_LEFT)) ctx.fill(x, y, x + 1, y + 1, edge)
		if (has(UP) && has(RIGHT) && !has(UP_RIGHT)) ctx.fill(x + SIZE - 1, y, x + SIZE, y + 1, edge)
		if (has(DOWN) && has(LEFT) && !has(DOWN_LEFT)) ctx.fill(x, y + SIZE - 1, x + 1, y + SIZE, edge)
		if (has(DOWN) && has(RIGHT) && !has(DOWN_RIGHT)) ctx.fill(x + SIZE - 1, y + SIZE - 1, x + SIZE, y + SIZE, edge)
	}
}
