/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: by default connected textures are not drawn in Apex Far Terrain. The region mesher meshes every block with its
 * plain, unconnected model: [model] takes our [ConnectedModel] off the top of a block's model, and while [meshing] runs
 * on the far-terrain worker thread, a [ConnectedModel] found deeper (inside another mod's model wrapper, which is kept
 * so its own behaviour stays) emits its original model's quads as they are. No tile choice, no UV remapping and no
 * neighbour reads happen here. With "Connected textures in far terrain" on, [FarConnect] retiles the plain quads
 * afterwards from far terrain's own recorded blocks.
 */
package dev.ashtonite365.apex.features.render.connected

import java.util.concurrent.atomic.LongAdder
import net.minecraft.client.renderer.block.dispatch.BlockStateModel

object FarPlain {
	private val depth: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(1) }

	val plainBlocks = LongAdder()

	@JvmStatic
	fun active(): Boolean = depth.get()[0] > 0

	inline fun <T> meshing(body: () -> T): T {
		enter()
		try {
			return body()
		} finally {
			exit()
		}
	}

	@JvmStatic fun enter() { depth.get()[0]++ }
	@JvmStatic fun exit() { depth.get()[0]-- }

	@JvmStatic
	fun model(model: BlockStateModel): BlockStateModel {
		var m = model
		repeat(8) {
			val c = m as? ConnectedModel ?: return m
			plainBlocks.increment()
			m = c.original
		}
		return m
	}
}
