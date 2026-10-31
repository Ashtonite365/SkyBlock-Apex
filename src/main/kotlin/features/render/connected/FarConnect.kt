/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures in Apex Far Terrain (Render setting "Connected textures in far terrain", off by
 * default). The far mesher always tessellates a block's plain model; when the setting is on, each plain quad of a
 * connecting block is given its tile here, with neighbours read from far terrain's own recorded blocks (never the live
 * world). One instance per region build, used only by the thread that runs the build: no shared mutable state.
 */
package dev.ashtonite365.apex.features.render.connected

import java.util.IdentityHashMap
import java.util.concurrent.atomic.LongAdder
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.world.level.block.state.BlockState

class FarConnect(lookup: StateLookup, private val models: (BlockState) -> BlockStateModel?) {
	private val cache = NeighbourCache()
	private val lookup = lookup
	private val geom = QuadGeom()
	private val xyzuv = FloatArray(20)
	private val connected = IdentityHashMap<BlockState, Any>()
	private var model: ConnectedModel? = null
	private var state: BlockState = NeighbourCache.AIR

	fun begin(state: BlockState, x: Int, y: Int, z: Int) {
		val m = modelOf(state)
		model = if (m != null && ConnectedTextures.enabled(m.rule.family)) m else null
		this.state = state
		if (model != null) {
			cache.begin(lookup, x, y, z)
			blocks.increment()
		}
	}

	private fun modelOf(state: BlockState): ConnectedModel? {
		if (ConnectRules.of(state.block) == null) return null
		val m = connected.getOrPut(state) { ConnectedDiag.findConnected(models(state)) ?: NONE }
		return m as? ConnectedModel
	}

	fun remap(quad: BakedQuad): BakedQuad? {
		val m = model ?: return quad
		for (i in 0 until 4) {
			val p = quad.position(i)
			val uv = quad.packedUV(i)
			xyzuv[i * 5] = p.x(); xyzuv[i * 5 + 1] = p.y(); xyzuv[i * 5 + 2] = p.z()
			xyzuv[i * 5 + 3] = UVPair.unpackU(uv); xyzuv[i * 5 + 4] = UVPair.unpackV(uv)
		}
		val tile = m.nearRemap(cache, state, quad.direction(), xyzuv, geom)
		if (tile == ConnectCore.DROP) return null
		if (tile == ConnectCore.KEEP) return quad
		remapped.increment()
		return BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			UVPair.pack(xyzuv[3], xyzuv[4]), UVPair.pack(xyzuv[8], xyzuv[9]),
			UVPair.pack(xyzuv[13], xyzuv[14]), UVPair.pack(xyzuv[18], xyzuv[19]),
			quad.direction(), quad.materialInfo(),
		)
	}

	companion object {
		private val NONE = Any()

		val blocks = LongAdder()
		val remapped = LongAdder()

		@JvmStatic
		fun forBuild(lookup: StateLookup, models: (BlockState) -> BlockStateModel?): FarConnect? =
			if (ConnectedTextures.farEnabled()) FarConnect(lookup, models) else null
	}
}
