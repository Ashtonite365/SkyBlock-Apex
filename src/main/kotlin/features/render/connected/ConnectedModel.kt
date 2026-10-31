/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, the wrapped block model. Near terrain (through the Fabric renderer API) remaps UVs
 * in a quad transform at emit time, with the tile chosen by [ConnectCore.decide]. Apex Far Terrain never draws
 * connected textures: it meshes [ConnectedModel.original] (see [FarPlain]), and inside [FarPlain.meshing] this model
 * emits its original quads unchanged. CPU-side only: any GPU, any shader.
 */
package dev.ashtonite365.apex.features.render.connected

import java.util.function.Predicate
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

class TextureTiles(val id: String, val plane: Int, val rects: FloatArray, val sprites: Array<TextureAtlasSprite?>, val present: BooleanArray) {
	fun contains(u: Float, v: Float): Boolean = u >= rects[0] && u <= rects[2] && v >= rects[1] && v <= rects[3]
	fun has(tile: Int): Boolean = present[tile]

	fun remapU(tile: Int, u: Float): Float {
		val o = tile * 4
		return rects[o] + (u - rects[0]) / (rects[2] - rects[0]) * (rects[o + 2] - rects[o])
	}

	fun remapV(tile: Int, v: Float): Float {
		val o = tile * 4
		return rects[o + 1] + (v - rects[1]) / (rects[3] - rects[1]) * (rects[o + 3] - rects[o + 1])
	}

	companion object {
		fun of(id: String, plane: Int, base: TextureAtlasSprite, tiles: Array<TextureAtlasSprite?>): TextureTiles {
			val n = ConnectTiles.TILE_COUNT
			val rects = FloatArray(n * 4)
			val sprites = arrayOfNulls<TextureAtlasSprite>(n)
			val present = BooleanArray(n)
			for (t in 0 until n) {
				val s = if (t == 0) base else tiles[t] ?: continue
				rects[t * 4] = s.u0; rects[t * 4 + 1] = s.v0; rects[t * 4 + 2] = s.u1; rects[t * 4 + 3] = s.v1
				sprites[t] = s
				present[t] = true
			}
			return TextureTiles(id, plane, rects, sprites, present)
		}
	}
}

class ConnectedModel(wrapped: BlockStateModel, val rule: ConnectRule, val textures: Array<TextureTiles>) : WrapperBlockStateModel(wrapped) {

	val original: BlockStateModel get() = wrapped

	fun textureAt(u: Float, v: Float): TextureTiles? {
		for (t in textures) if (t.contains(u, v)) return t
		return null
	}

	fun nearRemap(cache: NeighbourCache, state: BlockState, face: Direction, xyzuv: FloatArray, geom: QuadGeom = QuadGeom()): Int {
		var u = 0f; var v = 0f
		for (i in 0 until 4) { u += xyzuv[i * 5 + 3]; v += xyzuv[i * 5 + 4] }
		val tiles = textureAt(u * 0.25f, v * 0.25f)
		val tile = ConnectCore.decide(cache, rule, state, tiles?.plane ?: -1, geom.set(face, xyzuv))
		if (tile == ConnectCore.DROP || tile == ConnectCore.KEEP) return tile
		if (tiles == null || !tiles.has(tile)) return ConnectCore.KEEP
		for (i in 0 until 4) {
			xyzuv[i * 5 + 3] = tiles.remapU(tile, xyzuv[i * 5 + 3])
			xyzuv[i * 5 + 4] = tiles.remapV(tile, xyzuv[i * 5 + 4])
		}
		return tile
	}

	override fun emitQuads(emitter: QuadEmitter, level: BlockAndTintGetter, pos: BlockPos, state: BlockState, random: RandomSource, cullTest: Predicate<Direction?>) {
		if (FarPlain.active()) {

			FarPlain.plainBlocks.increment()
			wrapped.emitQuads(emitter, level, pos, state, random, cullTest)
			return
		}
		if (!ConnectedTextures.enabled(rule.family)) {
			wrapped.emitQuads(emitter, level, pos, state, random, cullTest)
			return
		}
		val t = NEAR.get()
		ConnectedDiag.nearBlocks.increment()
		t.begin(this, level, pos, state)
		emitter.pushTransform(t)
		try {
			wrapped.emitQuads(emitter, level, pos, state, random, cullTest)
		} finally {
			emitter.popTransform()
			t.end()
		}
	}

	override fun createGeometryKey(level: BlockAndTintGetter, pos: BlockPos, state: BlockState, random: RandomSource): Any? =
		if (FarPlain.active()) wrapped.createGeometryKey(level, pos, state, random) else null

	private class NearTransform : QuadTransform {
		private val cache = NeighbourCache()
		private val xyzuv = FloatArray(20)
		private val geom = QuadGeom()
		private val mpos = BlockPos.MutableBlockPos()
		private var level: BlockAndTintGetter? = null
		private val lookup = StateLookup { x, y, z -> level!!.getBlockState(mpos.set(x, y, z)) }
		private var model: ConnectedModel? = null
		private var state: BlockState = NeighbourCache.AIR

		fun begin(model: ConnectedModel, level: BlockAndTintGetter, pos: BlockPos, state: BlockState) {
			this.model = model; this.level = level; this.state = state
			cache.begin(lookup, pos.x, pos.y, pos.z)
		}

		fun end() { model = null; level = null }

		override fun transform(q: MutableQuadView): Boolean {
			val m = model ?: return true
			val face = q.nominalFace() ?: return true
			for (i in 0 until 4) {
				xyzuv[i * 5] = q.x(i); xyzuv[i * 5 + 1] = q.y(i); xyzuv[i * 5 + 2] = q.z(i)
				xyzuv[i * 5 + 3] = q.u(i); xyzuv[i * 5 + 4] = q.v(i)
			}
			val tile = m.nearRemap(cache, state, face, xyzuv, geom)
			if (tile == ConnectCore.DROP) return false
			if (tile == ConnectCore.KEEP) return true
			for (i in 0 until 4) q.uv(i, xyzuv[i * 5 + 3], xyzuv[i * 5 + 4])
			ConnectedDiag.nearRemapped.increment()
			return true
		}
	}

	companion object {
		private val NEAR = ThreadLocal.withInitial { NearTransform() }
	}
}
