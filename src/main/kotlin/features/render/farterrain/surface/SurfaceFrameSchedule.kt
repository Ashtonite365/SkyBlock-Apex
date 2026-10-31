/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, when a frame's GPU writes may happen (pure Kotlin, unit-tested).
 *
 * The game refuses any buffer write or copy while a render pass is open. The game's START_MAIN event fires inside its
 * opaque terrain pass, so far terrain's frame preparation (region uploads into the shared vertex buffer, its growth
 * copy, the indirect command / instance streams, the transform uniforms, index buffer growth) runs from a hook just
 * before the game opens its main pass ([beforeMainPass]). Inside the pass only the draws happen. When that hook did not
 * run (another renderer replaced the code path), the frame is prepared at the end of the main frame instead
 * ([endMain]), after the pass closed, and drawn in far terrain's own pass. [canWrite] is false while the game's pass
 * is known to be open: every GPU write path checks it and skips instead of crashing.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfaceFrameSchedule {

	var passOpen = false
		private set

	var prepared = false
		private set

	fun beforeMainPass(): Boolean {
		passOpen = false
		if (prepared) return false
		prepared = true
		return true
	}

	fun passOpened() { passOpen = true }

	fun endMain(): Boolean {
		passOpen = false
		if (prepared) return false
		prepared = true
		return true
	}

	fun frameDone() { prepared = false; passOpen = false }

	fun canWrite(): Boolean = !passOpen
}
