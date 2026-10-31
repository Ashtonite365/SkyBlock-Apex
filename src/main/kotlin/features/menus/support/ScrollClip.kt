// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import dev.ashtonite365.apex.features.menus.MenuBounds

object ScrollClip {

	const val STEP = 12

	const val BAR_GAP = 3

	fun maxScroll(contentBottom: Int, floorY: Int) = maxOf(0, contentBottom - floorY)

	fun clamp(offset: Int, contentBottom: Int, floorY: Int) = offset.coerceIn(0, maxScroll(contentBottom, floorY))

	fun scrolled(offset: Int, amount: Double, contentBottom: Int, floorY: Int, step: Int = STEP) =
		clamp(offset - (amount * step).toInt(), contentBottom, floorY)

	fun visibleSpan(ty: Int, th: Int, topY: Int, floorY: Int): Pair<Int, Int> {
		val a = maxOf(ty, topY); val b = minOf(ty + th, floorY)
		return if (b > a) a to b else a to a
	}

	fun clip(r: MenuBounds, view: MenuBounds): MenuBounds? {
		val x0 = maxOf(r.x, view.x); val y0 = maxOf(r.y, view.y)
		val x1 = minOf(r.x + r.w, view.x + view.w); val y1 = minOf(r.y + r.h, view.y + view.h)
		return if (x1 > x0 && y1 > y0) MenuBounds(x0, y0, x1 - x0, y1 - y0) else null
	}

	fun inside(mx: Int, my: Int, r: MenuBounds) = mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h

	fun hit(mx: Int, my: Int, r: MenuBounds, view: MenuBounds?) = inside(mx, my, r) && (view == null || inside(mx, my, view))
}

class ScrollView(private val keyOf: (() -> String?)? = null) {

	private var wanted = 0
	private var boundKey: String? = null
	var offset = 0; private set
	var view = MenuBounds(0, 0, 0, 0); private set
	var contentHeight = 0; private set
	val floor get() = view.y + view.h
	val contentBottom get() = view.y + contentHeight
	val max get() = ScrollClip.maxScroll(contentBottom, floor)

	private fun syncKey() {
		val k = runCatching { keyOf?.invoke() }.getOrNull() ?: return
		if (k != boundKey) { boundKey = k; wanted = dev.ashtonite365.apex.util.ScrollMemory.get(k) }
	}

	private fun remember() { boundKey?.let { dev.ashtonite365.apex.util.ScrollMemory.put(it, wanted) } }

	fun layout(view: MenuBounds, contentHeight: Int) {
		syncKey()
		this.view = view; this.contentHeight = contentHeight
		offset = ScrollClip.clamp(wanted, contentBottom, floor)
	}

	fun scroll(amount: Double): Boolean {
		syncKey()
		if (max == 0) return false
		offset = ScrollClip.scrolled(offset, amount, contentBottom, floor)
		wanted = offset
		remember()
		return true
	}

	fun reset() { offset = 0; wanted = 0; remember() }
}
