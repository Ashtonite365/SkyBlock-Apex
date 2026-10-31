package dev.ashtonite365.apex.features.inventory.storageoverlay

import com.mojang.blaze3d.platform.InputConstants

object StorageClickLogic {

	data class PageRect<P>(val x: Int, val y: Int, val width: Int, val height: Int, val page: P)

	const val SELECTOR_SLOT = 18

	fun <P> pageToOpen(
		rects: List<PageRect<P>>, panel: IntArray, mouseX: Double, mouseY: Double, button: Int, openPage: P?,
	): P? {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) return null
		if (!inside(panel[0], panel[1], panel[2], panel[3], mouseX, mouseY)) return null
		val clicked = rects.firstOrNull { inside(it.x, it.y, it.width, it.height, mouseX, mouseY) }?.page ?: return null
		return clicked.takeUnless { it == openPage }
	}

	fun inside(x: Int, y: Int, w: Int, h: Int, mx: Double, my: Double): Boolean =
		mx >= x && my >= y && mx < x + w && my < y + h

	sealed interface Placement {

		data class Player(val index: Int) : Placement

		data class Selector(val index: Int) : Placement

		data object Elsewhere : Placement
	}

	fun placeSlot(isPlayerSlot: Boolean, containerSlot: Int, isPageHandler: Boolean): Placement = when {
		isPlayerSlot -> Placement.Player(containerSlot)
		isPageHandler && containerSlot in 0..8 -> Placement.Selector(containerSlot)
		else -> Placement.Elsewhere
	}

	fun selectorPosition(overviewX: Int, overviewY: Int, index: Int): Pair<Int, Int> =
		Pair(overviewX - SELECTOR_SLOT - 4, overviewY + 4 + index * SELECTOR_SLOT)
}
