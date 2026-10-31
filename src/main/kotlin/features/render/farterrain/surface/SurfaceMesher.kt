/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, region mesher (far-terrain worker thread).
 *
 * Turns the stored surface of one 8x8-chunk region into vanilla BLOCK-format vertex buffers (one per layer) using the
 * game's own block-model and fluid tessellators, so every face uses the block's real atlas sprites, biome tint and
 * ambient occlusion. Only blocks with a visible face ([SurfaceFaces]) are tessellated, every quad passes
 * [SurfaceFaces.quadVisible] (hidden only when the neighbour's face completely covers it), and chunks are written in slot order with their vertex ranges recorded, so the renderer can leave out chunks that
 * vanilla is drawing itself. Each layer's quads are then grouped by the way they face ([SurfaceFaceCull]: six
 * directions plus "always"), chunks still in slot order inside every group, so the renderer can also leave out a whole
 * direction that faces away from the camera.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import dev.ashtonite365.apex.features.render.connected.FarConnect
import dev.ashtonite365.apex.features.render.connected.FarPlain
import dev.ashtonite365.apex.features.render.connected.StateLookup
import java.util.EnumMap
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.BlockModelLighter
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.client.renderer.block.ModelBlockRenderer
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.CardinalLighting
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.material.FluidState

class SurfaceBlockView(private val level: ClientLevel, private val world: SurfaceWorld, private val light: SurfaceLight) : BlockAndTintGetter {
	private val biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME)
	private val biomes = HashMap<String, Biome?>()
	private val fallbackBiome: Biome? by lazy { biomeRegistry.getValue(Biomes.PLAINS) }

	override fun getBlockState(pos: BlockPos): BlockState {
		val c = world.chunk(pos.x shr 4, pos.z shr 4) ?: return AIR
		val i = c.stateIndex(pos.x and 15, pos.y, pos.z and 15)
		return when (i) {
			SurfaceChunk.BELOW -> HIDDEN
			0 -> AIR
			else -> SurfaceStates.state(c.palette[i])
		}
	}

	override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
	override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
	override fun getHeight(): Int = level.height
	override fun getMinY(): Int = level.minY
	override fun getLightEngine(): LevelLightEngine = level.lightEngine
	override fun cardinalLighting(): CardinalLighting = level.cardinalLighting()
	override fun getBrightness(layer: LightLayer, pos: BlockPos): Int =
		if (layer == LightLayer.SKY) light.sky(pos.x, pos.y, pos.z) else light.block(pos.x, pos.y, pos.z)
	override fun getRawBrightness(pos: BlockPos, darkening: Int): Int =
		maxOf(light.sky(pos.x, pos.y, pos.z) - darkening, light.block(pos.x, pos.y, pos.z), 0)
	override fun canSeeSky(pos: BlockPos): Boolean = light.canSeeSky(pos.x, pos.y, pos.z)

	override fun getBlockTint(pos: BlockPos, resolver: ColorResolver): Int {
		val c = world.chunk(pos.x shr 4, pos.z shr 4)
		val name = c?.let { ch -> ch.biomes.getOrNull(ch.columnBiome[SurfaceIndex.col(pos.x and 15, pos.z and 15)]) }
		val biome = name?.let { biomes.getOrPut(it) { Identifier.tryParse(it)?.let { id -> biomeRegistry.getValue(id) } } } ?: fallbackBiome
			?: return -1
		return resolver.getColor(biome, pos.x.toDouble(), pos.z.toDouble())
	}

	companion object {
		val AIR: BlockState = Blocks.AIR.defaultBlockState()
		val HIDDEN: BlockState = Blocks.STONE.defaultBlockState()
	}
}

object GameLightProps : LightProps {
	override fun emission(state: String): Int = SurfaceStates.state(state).lightEmission
	override fun dampening(state: String): Int {
		val s = SurfaceStates.state(state)
		return if (s.isSolidRender) 15 else s.lightDampening
	}
}

class SurfaceAnimatedBlocks(val positions: LongArray, val states: Array<BlockState>) {
	val size: Int get() = positions.size

	companion object {
		private val blockAnimates = java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>()
		private val fluidAnimates = java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>()

		fun animates(state: BlockState): Boolean {
			val b = blockAnimates.computeIfAbsent(state.block.javaClass) { overrides(it, net.minecraft.world.level.block.Block::class.java, "animateTick", 4) }
			if (b) return true
			val f = state.fluidState
			if (f.isEmpty) return false
			return fluidAnimates.computeIfAbsent(f.type.javaClass) { overrides(it, net.minecraft.world.level.material.Fluid::class.java, "animateTick", 4) }
		}

		private fun overrides(cls: Class<*>, base: Class<*>, name: String, params: Int): Boolean {
			var c: Class<*>? = cls
			while (c != null && c != base) {
				if (c.declaredMethods.any { it.name == name && it.parameterCount == params }) return true
				c = c.superclass
			}
			return false
		}
	}
}

internal class LayerAcc {
	val counter = IntArray(SurfaceFaceCull.BUCKETS * SurfaceRegions.SLOTS)
	val planes = SurfaceFaceCull.newPlanes()
	var buckets = ByteArray(1024)
	var quads = 0
	private val xs = FloatArray(4)
	private val ys = FloatArray(4)
	private val zs = FloatArray(4)
	private var corner = 0

	fun addQuad(bucket: Int, slot: Int) {
		if (quads == buckets.size) buckets = buckets.copyOf(buckets.size * 2)
		buckets[quads++] = bucket.toByte()
		counter[bucket * SurfaceRegions.SLOTS + slot] += 4
	}

	fun blockQuad(quad: BakedQuad, x: Float, y: Float, z: Float, slot: Int) {
		for (i in 0 until 4) {
			val p = quad.position(i)
			xs[i] = p.x() + x; ys[i] = p.y() + y; zs[i] = p.z() + z
		}
		addQuad(SurfaceFaceCull.classify(xs, ys, zs, planes), slot)
	}

	fun vertex(x: Float, y: Float, z: Float, slot: Int) {
		xs[corner] = x; ys[corner] = y; zs[corner] = z
		if (++corner == 4) { corner = 0; addQuad(SurfaceFaceCull.classify(xs, ys, zs, planes), slot) }
	}

	val consistent: Boolean get() = corner == 0
}

private class Shifted(val d: VertexConsumer, val dx: Float, val dy: Float, val dz: Float, val acc: LayerAcc, val slot: Int) : VertexConsumer {
	override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer { acc.vertex(x + dx, y + dy, z + dz, slot); d.addVertex(x + dx, y + dy, z + dz); return this }
	override fun addVertex(x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, overlay: Int, light: Int, nx: Float, ny: Float, nz: Float) {
		acc.vertex(x + dx, y + dy, z + dz, slot)
		d.addVertex(x + dx, y + dy, z + dz, color, u, v, overlay, light, nx, ny, nz)
	}
	override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer { d.setColor(r, g, b, a); return this }
	override fun setColor(c: Int): VertexConsumer { d.setColor(c); return this }
	override fun setUv(u: Float, v: Float): VertexConsumer { d.setUv(u, v); return this }
	override fun setUv1(u: Int, v: Int): VertexConsumer { d.setUv1(u, v); return this }
	override fun setUv2(u: Int, v: Int): VertexConsumer { d.setUv2(u, v); return this }
	override fun setUv3(u: Float, v: Float): VertexConsumer { d.setUv3(u, v); return this }
	override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer { d.setNormal(x, y, z); return this }
	override fun setLineWidth(w: Float): VertexConsumer { d.setLineWidth(w); return this }
}

class SurfaceRegionMesh(
	val rx: Int, val rz: Int, val minY: Int, val maxY: Int,
	val layers: EnumMap<ChunkSectionLayer, MeshData>,

	val starts: EnumMap<ChunkSectionLayer, IntArray>,
	val counts: EnumMap<ChunkSectionLayer, IntArray>,
	val faces: Int,
	private val buffers: List<ByteBufferBuilder>,

	val animated: SurfaceAnimatedBlocks? = null,

	val slotMinY: IntArray = IntArray(SurfaceRegions.SLOTS) { minY },
	val slotMaxY: IntArray = IntArray(SurfaceRegions.SLOTS) { maxY },

	val planes: EnumMap<ChunkSectionLayer, FloatArray> = EnumMap(ChunkSectionLayer::class.java),
) : AutoCloseable {
	override fun close() {
		var ok = true
		layers.values.forEach { if (runCatching { it.close() }.isFailure) ok = false }

		buffers.forEach { SurfaceStaging.release(it, ok) }
	}
}

object SurfaceMesher {
	private const val EPS = 1e-4f

	fun onFace(quad: BakedQuad, d: Direction): Boolean {
		val target = if (d.axisDirection == Direction.AxisDirection.POSITIVE) 1f else 0f
		for (i in 0 until 4) {
			val p = quad.position(i)
			val v = when (d.axis) {
				Direction.Axis.X -> p.x()
				Direction.Axis.Y -> p.y()
				Direction.Axis.Z -> p.z()
			}
			if (kotlin.math.abs(v - target) > EPS) return false
		}
		return true
	}

	fun build(level: ClientLevel, world: SurfaceWorld, rx: Int, rz: Int): SurfaceRegionMesh? {
		val mc = Minecraft.getInstance()
		val opts = mc.options
		val ox0 = rx * SurfaceRegions.SIZE * 16
		val oz0 = rz * SurfaceRegions.SIZE * 16
		val light = SurfaceLight(world, GameLightProps, ox0 - 1, oz0 - 1, ox0 + SurfaceRegions.SIZE * 16, oz0 + SurfaceRegions.SIZE * 16)
		val view = SurfaceBlockView(level, world, light)
		val animPos = it.unimi.dsi.fastutil.longs.LongArrayList()
		val animStates = ArrayList<BlockState>()
		val modelSet = mc.modelManager.blockStateModelSet
		val renderer = ModelBlockRenderer(opts.ambientOcclusion().get(), true, mc.blockColors)
		val fluids = FluidRenderer(mc.modelManager.fluidStateModelSet)
		val cutoutLeaves = opts.cutoutLeaves().get()
		val ox = rx * SurfaceRegions.SIZE * 16
		val oz = rz * SurfaceRegions.SIZE * 16

		val buffers = ArrayList<ByteBufferBuilder>(3)
		val staging = EnumMap<ChunkSectionLayer, ByteBufferBuilder>(ChunkSectionLayer::class.java)
		val builders = EnumMap<ChunkSectionLayer, BufferBuilder>(ChunkSectionLayer::class.java)
		val accs = EnumMap<ChunkSectionLayer, LayerAcc>(ChunkSectionLayer::class.java)
		fun builder(layer: ChunkSectionLayer): BufferBuilder = builders.getOrPut(layer) {
			val bb = SurfaceStaging.acquire()
			buffers.add(bb)
			staging[layer] = bb
			BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK)
		}
		fun acc(layer: ChunkSectionLayer): LayerAcc = accs.getOrPut(layer) { LayerAcc() }

		var slot = 0
		var faces = 0

		var curX = 0
		var curY = 0
		var curZ = 0
		var curName = SurfaceChunk.AIR
		fun keep(quad: BakedQuad): Boolean {
			val d = quad.direction()
			return SurfaceFaces.quadVisible(world, SurfaceStates.classifier, SurfaceStates.faceShapes, curName, curX, curY, curZ, SurfaceDirections.bit(d), onFace(quad, d))
		}

		val lookPos = BlockPos.MutableBlockPos()
		val connect = FarConnect.forBuild(StateLookup { x, y, z -> view.getBlockState(lookPos.set(x, y, z)) }) { modelSet.get(it) }
		val out = BlockQuadOutput { x, y, z, plainQuad, inst ->
			if (!keep(plainQuad)) return@BlockQuadOutput
			val quad = if (connect == null) plainQuad else connect.remap(plainQuad) ?: return@BlockQuadOutput
			val layer = quad.materialInfo().layer()
			builder(layer).putBlockBakedQuad(x, y, z, quad, inst)
			acc(layer).blockQuad(quad, x, y, z, slot)
			faces++
		}
		val solidOut = BlockQuadOutput { x, y, z, plainQuad, inst ->
			if (!keep(plainQuad)) return@BlockQuadOutput
			val quad = if (connect == null) plainQuad else connect.remap(plainQuad) ?: return@BlockQuadOutput
			builder(ChunkSectionLayer.SOLID).putBlockBakedQuad(x, y, z, quad, inst)
			acc(ChunkSectionLayer.SOLID).blockQuad(quad, x, y, z, slot)
			faces++
		}
		val pos = BlockPos.MutableBlockPos()
		var minY = Int.MAX_VALUE
		var maxY = Int.MIN_VALUE
		val slotMinY = IntArray(SurfaceRegions.SLOTS) { Int.MAX_VALUE }
		val slotMaxY = IntArray(SurfaceRegions.SLOTS) { Int.MIN_VALUE }
		BlockModelLighter.enableCaching()

		FarPlain.enter()
		try {
			for (s in 0 until SurfaceRegions.SLOTS) {
				slot = s
				val cx = rx * SurfaceRegions.SIZE + (s % SurfaceRegions.SIZE)
				val cz = rz * SurfaceRegions.SIZE + (s / SurfaceRegions.SIZE)
				val chunk = world.chunk(cx, cz) ?: continue
				SurfaceFaces.forEachVisible(world, SurfaceStates.classifier, SurfaceStates.faceShapes, chunk) { wx, wy, wz, name, _ ->
					val state = SurfaceStates.state(name)
					if (state.isAir) return@forEachVisible
					pos.set(wx, wy, wz)
					if (SurfaceAnimatedBlocks.animates(state)) { animPos.add(pos.asLong()); animStates.add(state) }
					curX = wx; curY = wy; curZ = wz; curName = name
					val fluid = state.fluidState
					if (!fluid.isEmpty) {
						val dx = ((wx and 15.inv()) - ox).toFloat()
						val dy = (wy and 15.inv()).toFloat()
						val dz = ((wz and 15.inv()) - oz).toFloat()
						fluids.tesselate(view, pos, { layer -> Shifted(builder(layer), dx, dy, dz, acc(layer), s) }, state, fluid)
					}
					if (state.renderShape == RenderShape.MODEL) {
						connect?.begin(state, wx, wy, wz)
						renderer.tesselateBlock(
							if (ModelBlockRenderer.forceOpaque(cutoutLeaves, state)) solidOut else out,
							(wx - ox).toFloat(), wy.toFloat(), (wz - oz).toFloat(),
							view, pos, state, FarPlain.model(modelSet.get(state)), state.getSeed(pos),
						)
					}
					if (wy < minY) minY = wy
					if (wy + 1 > maxY) maxY = wy + 1
					if (wy < slotMinY[s]) slotMinY[s] = wy
					if (wy + 1 > slotMaxY[s]) slotMaxY[s] = wy + 1
				}
			}
		} catch (t: Throwable) {
			buffers.forEach { runCatching { it.close() } }
			throw t
		} finally {
			FarPlain.exit()
			BlockModelLighter.clearCache()
		}
		val layers = EnumMap<ChunkSectionLayer, MeshData>(ChunkSectionLayer::class.java)
		val starts = EnumMap<ChunkSectionLayer, IntArray>(ChunkSectionLayer::class.java)
		val counts = EnumMap<ChunkSectionLayer, IntArray>(ChunkSectionLayer::class.java)
		val planes = EnumMap<ChunkSectionLayer, FloatArray>(ChunkSectionLayer::class.java)
		val stride = DefaultVertexFormat.BLOCK.vertexSize
		val built = ArrayList<ByteBufferBuilder>(3)
		for ((layer, b) in builders) {
			val bb = staging.getValue(layer)
			val mesh = b.build()
			if (mesh == null) { SurfaceStaging.release(bb, ok = false); continue }
			built.add(bb)
			layers[layer] = mesh
			val a = acc(layer)
			var c = a.counter
			val vb = mesh.vertexBuffer()
			if (a.consistent && a.quads * 4 * stride == vb.remaining()) {

				SurfaceFaceCull.reorder(vb, stride, a.buckets, a.quads)
				planes[layer] = a.planes
			} else {

				val flat = IntArray(SurfaceFaceCull.BUCKETS * SurfaceRegions.SLOTS)
				for (bk in 0 until SurfaceFaceCull.BUCKETS) for (sl in 0 until SurfaceRegions.SLOTS)
					flat[SurfaceFaceCull.ALWAYS * SurfaceRegions.SLOTS + sl] += c[bk * SurfaceRegions.SLOTS + sl]
				c = flat
			}
			counts[layer] = c
			starts[layer] = IntArray(c.size).also { st -> var acc = 0; for (i in st.indices) { st[i] = acc; acc += c[i] } }
		}
		if (layers.isEmpty()) return null
		val animated = if (animPos.isEmpty) null else SurfaceAnimatedBlocks(animPos.toLongArray(), animStates.toTypedArray())
		return SurfaceRegionMesh(rx, rz, minY, maxY, layers, starts, counts, faces, built, animated, slotMinY, slotMaxY, planes)
	}
}
