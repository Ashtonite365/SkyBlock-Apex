// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import dev.ashtonite365.apex.features.menus.MenuBounds

object StyledChestLayout {
	const val PITCH = 18
	const val GROW = 1.25f
	const val TOP_BAR = 20
	const val PAD = 8
	const val INV_GAP = 6
	const val GUTTER = 2

	const val INV_W = 9 * PITCH
	const val INV_H = 10 + 4 * PITCH + 4

	data class Layout(
		val frame: MenuBounds, val scale: Float, val gridX: Int, val gridY: Int, val rows: Int,
		val inventory: Pair<Int, Int>?,
	) {

		fun cellScreen(i: Int): MenuBounds =
			MenuBounds(gridX + (i % 9) * pitch, gridY + (i / 9) * pitch, cellSize, cellSize)
		val cellSize get() = (PITCH * scale).toInt()
		val pitch get() = cellSize + if (scale != 1f) GUTTER else 0

		val cellScale get() = cellSize / PITCH.toFloat()
	}

	fun build(screenW: Int, screenH: Int, rows: Int, withInventory: Boolean): Layout {
		val r = rows.coerceIn(1, 6)
		val scale = if (withInventory) 1f else GROW
		val cell = (PITCH * scale).toInt(); val pitch = cell + if (scale != 1f) GUTTER else 0
		val gridW = 9 * pitch - (pitch - cell); val gridH = r * pitch - (pitch - cell)
		val innerW = maxOf(gridW, if (withInventory) INV_W else 0)
		val w = innerW + 2 * PAD
		val h = TOP_BAR + gridH + PAD + if (withInventory) INV_GAP + INV_H + PAD / 2 else 0
		val fw = minOf(screenW - 8, w); val fh = minOf(screenH - 8, h)
		val fx = (screenW - fw) / 2; val fy = (screenH - fh) / 2
		val gx = fx + (fw - gridW) / 2; val gy = fy + TOP_BAR
		val inv = if (withInventory) (fx + (fw - INV_W) / 2) to (gy + gridH + INV_GAP) else null
		return Layout(MenuBounds(fx, fy, fw, fh), scale, gx, gy, r, inv)
	}

	private val INVENTORY_WORDS = listOf(
		"sell", "storage", "backpack", "ender chest", "trade", "salvage", "reforge", "anvil", "enchant item",
		"create auction", "create bin auction", "auction house", "bazaar", "craft", "sack", "accessory bag",
		"potion bag", "quiver", "fishing bag", "personal compactor", "deletor", "rune", "gemstone", "upgrade item",
		"convert", "essence", "pets", "wardrobe", "museum", "minion", "chest", "hopper", "furnace", "shop",
	)
	fun usesInventory(title: String): Boolean {
		val t = title.lowercase()
		return INVENTORY_WORDS.any { t.contains(it) }
	}
}

object WardrobeLayout {

	const val ITEM_FILL = 0.72f

	const val BUTTON = 26

	const val BUTTON_ROW = 4 + BUTTON + 2

	const val ARMOUR_SHRINK = 0.82f

	const val INVENTORY_SCALE = 1.3f

	fun frame(screenW: Int, screenH: Int): MenuBounds {
		val fw = minOf(screenW - 12, 460); val fh = minOf(screenH - 12, 340)
		return MenuBounds((screenW - fw) / 2, (screenH - fh) / 2, fw, fh)
	}

	data class Armour(val scale: Float, val gridX: Int, val gridY: Int, val cell: Int) {

		val pitch get() = cell + if (cell != 18) StyledChestLayout.GUTTER else 0
		fun cellAt(row: Int, col: Int) = gridX + col * pitch to gridY + row * pitch

		fun buttonAt(col: Int) = gridX + col * pitch + (cell - BUTTON) / 2 to gridY + 4 * pitch + 4

		val itemScale get() = cell * ITEM_FILL / 16f

		fun itemPos(fx: Int, fy: Int) = fx + Math.round((cell - 16 * itemScale) / 2) to fy + Math.round((cell - 16 * itemScale) / 2)
		val width get() = 9 * pitch - (pitch - cell)
		val height get() = 4 * pitch - (pitch - cell)
	}

	fun armour(x: Int, w: Int, top: Int, bottom: Int): Armour {
		val availH = bottom - top - BUTTON_ROW
		val g = StyledChestLayout.GUTTER
		val scale = (minOf((w - 16 - 8 * g) / 162f, (availH - 4 * g) / 72f) * ARMOUR_SHRINK).coerceIn(1f, 3f)
		val cell = (18 * scale).toInt()
		val pitch = cell + if (cell != 18) g else 0
		return Armour(cell / 18f, x + (w - (9 * pitch - (pitch - cell))) / 2, top, cell)
	}
}
