/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: a counter that goes up whenever the block models the game draws are swapped outside a resource reload
 * (a texture pack's block replacements switching on an island change). Renderers that keep their own meshes (far
 * terrain) compare it with the value they built with and re-mesh when it moved.
 */
package dev.ashtonite365.apex.util.render

import java.util.concurrent.atomic.AtomicInteger

object BlockModelChanges {
	private val counter = AtomicInteger()

	val generation: Int get() = counter.get()

	fun bump() {
		counter.incrementAndGet()
	}
}
