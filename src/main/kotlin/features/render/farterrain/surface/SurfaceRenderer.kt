/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, GPU side (render thread only).
 *
 * Every region's vertices live in one shared GPU buffer ([SurfaceVertexArena]: one range per region, its layers back
 * to back; a region that does not fit even the largest allowed buffer gets a buffer of its own). Each frame: upload a
 * few finished region meshes ([SurfaceUploadQueue]: a per-frame budget, the regions near the camera first), pick the
 * regions inside the far distance and the view frustum, cut out the chunks vanilla is drawing itself
 * ([SurfaceDrawRuns]) and draw the rest with Minecraft's standard pipeline API (BLOCK vertex format, sequential quad
 * index buffer, plain GLSL 330). A region layer's ranges are cached between frames ([SurfaceRegionDrawCache]).
 *
 * Draw paths, best first:
 *  1. Shared buffer, indirect ([SurfaceIndirect]): where the game draws its own terrain with indirect multi-draw, each
 *     layer's ranges from ALL drawn regions go out in one call (a command list in a GPU buffer; each region's offset
 *     from the camera is a per-instance attribute). The per-frame data (offsets, commands) is only written when it
 *     changed ([SurfaceFrameStream]).
 *  2. Per region layer, one multi-draw submission where the device has it (OpenGL: glMultiDrawElementsBaseVertex),
 *  3. else one draw per range with the state set once per region layer.
 * Paths 2 and 3 draw from the same shared buffer (the region's slice of it) with a transform per region, as before.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.vertex.VertexFormat
import dev.ashtonite365.apex.Apex
import java.nio.ByteBuffer
import java.util.EnumMap
import java.util.Optional
import java.util.OptionalDouble
import java.util.concurrent.ConcurrentLinkedQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.lwjgl.PointerBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.DynamicGpuData
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

object SurfaceVertexFormats {

	val TERRAIN: VertexFormat = DefaultVertexFormat.BLOCK
	const val TERRAIN_BYTES = 28

	val REGION_INSTANCE: VertexFormat = VertexFormat.builder(1).addAttribute("RegionOffset", GpuFormat.RGB32_FLOAT).build()
}

object SurfacePipelines {
	private fun base(name: String) = RenderPipeline.builder()
		.withLocation(Apex.identifier("pipeline/far_surface_$name"))
		.withBindGroupLayout(BindGroupLayouts.GLOBALS)
		.withBindGroupLayout(BindGroupLayouts.FOG)
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withVertexBinding(0, DefaultVertexFormat.BLOCK)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withVertexShader(Apex.identifier("surfacelod/terrain"))
		.withFragmentShader(Apex.identifier("surfacelod/terrain"))

	val SOLID: RenderPipeline = base("solid")
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.build()
	val CUTOUT: RenderPipeline = base("cutout")
		.withShaderDefine("ALPHA_CUTOUT", 0.5f)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.build()
	val TRANSLUCENT: RenderPipeline = base("translucent")
		.withShaderDefine("ALPHA_CUTOUT", 0.1f)
		.withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build()

	fun of(layer: ChunkSectionLayer) = when (layer) {
		ChunkSectionLayer.SOLID -> SOLID
		ChunkSectionLayer.CUTOUT -> CUTOUT
		ChunkSectionLayer.TRANSLUCENT -> TRANSLUCENT
	}

	private fun shared(name: String) = base(name)
		.withVertexBinding(1, SurfaceVertexFormats.REGION_INSTANCE)
		.withShaderDefine("REGION_INSTANCED")

	val SOLID_SHARED: RenderPipeline = shared("solid_shared")
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.build()
	val CUTOUT_SHARED: RenderPipeline = shared("cutout_shared")
		.withShaderDefine("ALPHA_CUTOUT", 0.5f)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.build()
	val TRANSLUCENT_SHARED: RenderPipeline = shared("translucent_shared")
		.withShaderDefine("ALPHA_CUTOUT", 0.1f)
		.withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build()

	fun sharedOf(layer: ChunkSectionLayer) = when (layer) {
		ChunkSectionLayer.SOLID -> SOLID_SHARED
		ChunkSectionLayer.CUTOUT -> CUTOUT_SHARED
		ChunkSectionLayer.TRANSLUCENT -> TRANSLUCENT_SHARED
	}
}

class SurfaceGpuRegion(
	val rx: Int, val rz: Int, val minY: Int, val maxY: Int, val faces: Int, val animated: SurfaceAnimatedBlocks?,

	val slotMinY: IntArray = IntArray(SurfaceRegions.SLOTS) { minY },
	val slotMaxY: IntArray = IntArray(SurfaceRegions.SLOTS) { maxY },

	val uploadId: Long = nextUploadId(),
) {
	val starts = EnumMap<ChunkSectionLayer, IntArray>(ChunkSectionLayer::class.java)
	val counts = EnumMap<ChunkSectionLayer, IntArray>(ChunkSectionLayer::class.java)

	val planes = EnumMap<ChunkSectionLayer, FloatArray>(ChunkSectionLayer::class.java)

	internal val layerStarts = arrayOfNulls<IntArray>(LAYERS)
	internal val layerCounts = arrayOfNulls<IntArray>(LAYERS)
	internal val layerPlanes = arrayOfNulls<FloatArray>(LAYERS)

	internal val layerBase = IntArray(LAYERS)
	internal val layerUnits = IntArray(LAYERS)

	var arenaStart = -1
	var units = 0

	var ownBuffer: GpuBuffer? = null

	private val slices = arrayOfNulls<GpuBufferSlice>(LAYERS)
	private var slicesOf: GpuBuffer? = null

	fun layerSlice(li: Int, arenaBuffer: GpuBuffer?): GpuBufferSlice? {
		if (layerUnits[li] == 0) return null
		val own = ownBuffer
		val b = own ?: (if (arenaStart >= 0) arenaBuffer else null) ?: return null
		if (b !== slicesOf) { slices.fill(null); slicesOf = b }
		slices[li]?.let { return it }
		val first = (if (own != null) 0 else arenaStart) + layerBase[li]
		val s = b.slice(first.toLong() * SurfaceVertexFormats.TERRAIN_BYTES, layerUnits[li].toLong() * SurfaceVertexFormats.TERRAIN_BYTES)
		slices[li] = s
		return s
	}

	var slotMask = 0L
		private set

	val drawCache = SurfaceRegionDrawCache(uploadId, LAYERS)

	val bounds: AABB = run {
		val bx = rx * SurfaceRegions.SIZE * 16.0
		val bz = rz * SurfaceRegions.SIZE * 16.0
		val size = SurfaceRegions.SIZE * 16.0
		AABB(bx - 1, minY - 1.0, bz - 1, bx + size + 1, maxY + 1.0, bz + size + 1)
	}

	internal val offset = Vector3f()
	internal var transform: DynamicGpuData.Transform? = null

	fun finishUpload() {
		for ((layer, a) in starts) layerStarts[layer.ordinal] = a
		for ((layer, a) in counts) layerCounts[layer.ordinal] = a
		for ((layer, a) in planes) layerPlanes[layer.ordinal] = a
		slotMask = SurfaceDrawPlan.slotMask(counts.values)
	}

	fun hasSlot(slot: Int): Boolean = (slotMask ushr slot) and 1L != 0L
	var bytes = 0L

	fun close() {
		ownBuffer?.let { runCatching { it.close() } }
		ownBuffer = null
		slices.fill(null)
		slicesOf = null
	}

	companion object {
		val LAYERS = ChunkSectionLayer.entries.size
		private var uploads = 0L
		fun nextUploadId(): Long = ++uploads
	}
}

object SurfaceRenderer {

	class Pending(val key: Long, val mesh: SurfaceRegionMesh?, val generation: Int)

	private val pending = ConcurrentLinkedQueue<Pending>()
	private val regions = HashMap<Long, SurfaceGpuRegion>()
	private var residentBytes = 0L

	@Volatile
	var generation = 0
		private set

	var stats: SurfaceStats? = null

	private class Visible : SurfaceDrawOrder.Item {
		lateinit var region: SurfaceGpuRegion
		lateinit var transform: GpuBufferSlice

		var instance = 0
		val plans = arrayOfNulls<SurfaceDrawPlan>(SurfaceGpuRegion.LAYERS)
		override var distSq = 0.0
	}

	private val visiblePool = ArrayList<Visible>()

	private val visible = ArrayList<Visible>()
	private val visibleFar = ArrayList<Visible>()
	private val sortScratch = ArrayList<Visible>()
	private var frameReady = false
	private var drewInPass = false
	private var frameDrawCalls = 0
	private var frameRuns = 0
	private var frameVertices = 0L

	@Volatile
	var farClip = 0f

	@JvmStatic
	fun depthFar(vanilla: Float): Float = if (farClip > vanilla) farClip else vanilla

	fun submit(key: Long, mesh: SurfaceRegionMesh?, gen: Int) { pending.add(Pending(key, mesh, gen)) }

	fun clear() { generation++ }

	private var clearedGeneration = 0

	fun chunkRegion(cx: Int, cz: Int): SurfaceGpuRegion? {
		val r = regions[SurfaceIndex.key(Math.floorDiv(cx, SurfaceRegions.SIZE), Math.floorDiv(cz, SurfaceRegions.SIZE))] ?: return null
		val slot = Math.floorMod(cx, SurfaceRegions.SIZE) + Math.floorMod(cz, SurfaceRegions.SIZE) * SurfaceRegions.SIZE
		return if (r.hasSlot(slot)) r else null
	}

	private var uploadedThisFrame = false

	val frames = SurfaceFrameSchedule()

	private val uploads = SurfaceUploadQueue<SurfaceRegionMesh> { it.close() }
	private var uploadFrame = 0L
	private var focusCcx = 0
	private var focusCcz = 0
	private var focusReach = 0
	private var budget = SurfaceUploadQueue.Budget()
	private var frameUploads = 0
	private var frameUploadBytes = 0L

	fun uploadFocus(ccx: Int, ccz: Int, urgentReachChunks: Int) {
		focusCcx = ccx; focusCcz = ccz
		if (urgentReachChunks != focusReach) { focusReach = urgentReachChunks; budget = SurfaceUploadQueue.Budget(urgentReachChunks = urgentReachChunks) }
	}

	private val isResident: (Long) -> Boolean = { regions.containsKey(it) }
	private val nanoClock: () -> Long = { System.nanoTime() }
	private val removeRegion: (Long) -> Unit = { k -> regions.remove(k)?.let { residentBytes -= it.bytes; release(it) } }
	private val uploadEntry: (SurfaceUploadQueue.Entry<SurfaceRegionMesh>, SurfaceUploadQueue.Kind) -> Long = { e, kind ->
		val b = upload(e.key, e.mesh!!)
		frameUploads++
		frameUploadBytes += b
		b
	}

	fun uploadsBeforeHandoff() {
		if (uploadedThisFrame || !frames.canWrite()) return
		drainUploads(true)
		uploadedThisFrame = true
	}

	private fun drainUploads(upload: Boolean) {
		val gen = generation
		if (clearedGeneration != gen) {
			regions.values.forEach { release(it) }
			regions.clear()
			residentBytes = 0
			clearedGeneration = gen

			closeShared()
		}
		while (true) {
			val p = pending.poll() ?: break
			val mesh = p.mesh
			if (p.generation != gen) { mesh?.close(); continue }
			uploads.offer(p.key, SurfaceIndex.keyX(p.key), SurfaceIndex.keyZ(p.key), mesh, p.generation, mesh?.let(::meshBytes) ?: 0L, uploadFrame)
		}
		if (upload) {
			frameUploads = 0; frameUploadBytes = 0

			uploads.drain(uploadFrame++, gen, focusCcx, focusCcz, budget, isResident, nanoClock, removeRegion, uploadEntry)
		}
		stats?.let { s ->
			s.vramBytes = residentBytes
			s.regionsResident = regions.size
			s.uploadsLastFrame = frameUploads
			s.uploadBytesLastFrame = frameUploadBytes
			s.uploadsWaiting = uploads.size
		}
	}

	private fun meshBytes(mesh: SurfaceRegionMesh): Long {
		var b = 0L
		for (data in mesh.layers.values) b += data.vertexBuffer().remaining()
		return b
	}

	private class GpuIo(private val usage: Int) : SurfaceBuffers<GpuBuffer> {
		override fun create(label: String, bytes: Long): GpuBuffer = RenderSystem.getDevice().createBuffer({ label }, usage, bytes)
		override fun write(buffer: GpuBuffer, offset: Long, data: ByteBuffer) {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(offset, data.remaining().toLong()), data)
		}
		override fun copy(from: GpuBuffer, to: GpuBuffer, bytes: Long) {
			RenderSystem.getDevice().createCommandEncoder().copyToBuffer(from.slice(0, bytes), to.slice(0, bytes))
		}
		override fun close(buffer: GpuBuffer) { buffer.close() }
	}

	private var arena: SurfaceVertexArena<GpuBuffer>? = null
	private val instanceStream = SurfaceFrameStream(GpuIo(GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST), "Apex far terrain region offsets")
	private val commandStream = SurfaceFrameStream(GpuIo(GpuBuffer.USAGE_INDIRECT_PARAMETERS or GpuBuffer.USAGE_COPY_DST), "Apex far terrain draw commands")
	private val instances = SurfaceInstances()
	private val commands = SurfaceCommandList(SurfaceGpuRegion.LAYERS)
	private val layerMaxIndices = IntArray(SurfaceGpuRegion.LAYERS)

	private fun arena(): SurfaceVertexArena<GpuBuffer> = arena ?: SurfaceVertexArena(
		GpuIo(GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_COPY_SRC),
		SurfaceVertexFormats.TERRAIN_BYTES, ARENA_STEP_BYTES, arenaMaxBytes(),
	).also { arena = it }

	private fun arenaMaxBytes(): Long {
		val device = try { RenderSystem.getDevice().getDeviceInfo().limits().maxMemoryAllocationSize() } catch (_: Throwable) { 0L }
		return if (device > 0) minOf(ARENA_MAX_BYTES, device) else ARENA_MAX_BYTES
	}

	private fun release(r: SurfaceGpuRegion) {
		if (r.arenaStart >= 0) { arena?.free(r.arenaStart, r.units); r.arenaStart = -1 }
		r.close()
	}

	private fun closeShared() {
		arena?.close()
		arena = null
		instanceStream.close()
		commandStream.close()
	}

	private fun upload(key: Long, mesh: SurfaceRegionMesh): Long {
		try {
			val r = SurfaceGpuRegion(mesh.rx, mesh.rz, mesh.minY, mesh.maxY, mesh.faces, mesh.animated, mesh.slotMinY, mesh.slotMaxY)
			var units = 0
			for ((layer, data) in mesh.layers) {
				val vb = data.vertexBuffer()
				val n = vb.remaining() / SurfaceVertexFormats.TERRAIN_BYTES
				check(n * SurfaceVertexFormats.TERRAIN_BYTES == vb.remaining()) { "far terrain mesh is not whole vertices" }
				r.bytes += vb.remaining()
				r.layerBase[layer.ordinal] = units
				r.layerUnits[layer.ordinal] = n
				units += n
				r.starts[layer] = mesh.starts[layer] ?: IntArray(SurfaceRegions.SLOTS)
				r.counts[layer] = mesh.counts[layer] ?: IntArray(SurfaceRegions.SLOTS)
				mesh.planes[layer]?.let { r.planes[layer] = it }
			}
			r.units = units
			if (units > 0) {
				try {
					val a = arena()
					val at = a.allocate(units)
					if (at >= 0) {
						r.arenaStart = at
						for ((layer, data) in mesh.layers) if (r.layerUnits[layer.ordinal] > 0) a.write(at + r.layerBase[layer.ordinal], data.vertexBuffer())
					} else {

						val io = GpuIo(GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST)
						val own = io.create("Apex far surface ${mesh.rx},${mesh.rz}", units.toLong() * SurfaceVertexFormats.TERRAIN_BYTES)
						r.ownBuffer = own
						for ((layer, data) in mesh.layers) if (r.layerUnits[layer.ordinal] > 0) io.write(own, r.layerBase[layer.ordinal].toLong() * SurfaceVertexFormats.TERRAIN_BYTES, data.vertexBuffer())
					}
				} catch (t: Throwable) {
					release(r)
					throw t
				}
			}
			r.finishUpload()
			residentBytes += r.bytes
			regions.put(key, r)?.let { residentBytes -= it.bytes; release(it) }
			return r.bytes
		} finally {
			mesh.close()
		}
	}

	val evicted = ConcurrentLinkedQueue<Long>()

	@Volatile
	var keepDistSq = Long.MAX_VALUE
		private set

	private fun evictOverBudget(ccx: Int, ccz: Int) {
		val total = residentBytes
		if (regions.size <= MAX_RESIDENT_REGIONS && total <= MAX_VRAM_BYTES) { keepDistSq = Long.MAX_VALUE; return }
		val entries = regions.entries.map { (k, r) -> SurfaceBudget.Entry(k, regionDistSq(r.rx, r.rz, ccx, ccz), r.bytes) }
		val drop = SurfaceBudget.toDrop(entries, MAX_RESIDENT_REGIONS, MAX_VRAM_BYTES).toHashSet()
		for (k in drop) { regions.remove(k)?.let { residentBytes -= it.bytes; release(it) }; evicted.add(k) }
		keepDistSq = entries.filter { it.key !in drop }.maxOfOrNull { it.distSq } ?: Long.MAX_VALUE
	}

	fun regionDistSq(rx: Int, rz: Int, ccx: Int, ccz: Int): Long {
		val dx = (rx * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - ccx).toLong()
		val dz = (rz * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - ccz).toLong()
		return dx * dx + dz * dz
	}

	val drawnRegionKeys = LongOpenHashSet()

	fun animatedIn(key: Long): SurfaceAnimatedBlocks? = regions[key]?.animated

	@Volatile
	var effectiveFarChunks = 0
		private set

	private val indexStorage = object : SurfaceIndexStorage {
		override fun hasStorage(indexCount: Int) = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).hasStorage(indexCount)
		override fun ensure(indexCount: Int) { RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount) }
	}

	@Volatile
	var faceCulling = true

	@Volatile
	var chunkFrustumCulling = true

	private val skipSlot = BooleanArray(SurfaceRegions.SLOTS)

	private val offScreenSlot = BooleanArray(SurfaceRegions.SLOTS)
	private val chunkFrustum = SurfaceChunkFrustum()
	private var frameCulledFaces = 0L
	private var frameOffScreenFaces = 0L
	private var frameOffScreenChunks = 0

	private val view = Matrix4f()
	private val fade = Matrix4f()
	private val white = Vector4f(1f, 1f, 1f, 1f)
	private var transformArray = arrayOfNulls<DynamicGpuData.Transform>(0)

	private var indirect = false
	private var indirectFailed = false
	private var indirectMaxDraws = -1
	private var instanceSlice: GpuBufferSlice? = null
	private var commandBuffer: GpuBuffer? = null
	private var sharedTransform: GpuBufferSlice? = null
	private var sharedTransformData: DynamicGpuData.Transform? = null
	private val zeroOffset = Vector3f()
	private var frameIndirectRegions = 0

	private var multiDrawFailed = false
	private var multiDraw = false
	private var offsets: PointerBuffer? = null

	fun prepareFrame(cam: CameraRenderState, farChunks: Int, envScale: Float, chunkFrustumSafe: Boolean = false, skipChunk: (cx: Int, cz: Int, minY: Int, maxY: Int) -> Boolean) {
		frameReady = false
		frameCulledFaces = 0
		frameOffScreenFaces = 0
		frameOffScreenChunks = 0
		drewInPass = false
		visible.clear()
		visibleFar.clear()
		drawnRegionKeys.clear()
		frameDrawCalls = 0
		frameRuns = 0
		frameVertices = 0
		indirect = false

		if (!frames.canWrite()) {
			if (errors.firstTime(IllegalStateException("prepareFrame inside a render pass"))) Apex.logger.warn("[Apex Far Terrain] frame preparation reached inside a render pass; skipping far terrain this frame")
			return
		}
		uploadsBeforeHandoff()
		uploadedThisFrame = false
		val camPos = cam.pos ?: return
		val camX = camPos.x
		val camY = camPos.y
		val camZ = camPos.z
		val ccx = SurfaceHandoff.chunkOf(camX)
		val ccz = SurfaceHandoff.chunkOf(camZ)
		evictOverBudget(ccx, ccz)
		if (regions.isEmpty()) { effectiveFarChunks = 0; return }
		val unlimited = farChunks >= UNLIMITED

		val farthest = SurfaceFarDistance.effective(farChunks, if (!unlimited) 0L else regions.values.maxOf { regionDistSq(it.rx, it.rz, ccx, ccz) })
		effectiveFarChunks = farthest
		val frustum = cam.cullFrustum

		val perChunk = SurfaceChunkFrustum.use(chunkFrustumCulling, chunkFrustumSafe) &&
			chunkFrustum.set(cam.projectionMatrix, cam.viewRotationMatrix)

		fade.identity().m30(SurfaceFarDistance.fadeStart(farthest)).m31(SurfaceFarDistance.fadeEnd(farthest)).m32(envScale)
		view.set(cam.viewRotationMatrix)
		var maxIndices = 0
		var allInArena = true
		val reach = farthest + SurfaceRegions.SIZE
		for ((key, r) in regions) {
			val bx = r.rx * SurfaceRegions.SIZE * 16.0
			val bz = r.rz * SurfaceRegions.SIZE * 16.0
			val size = SurfaceRegions.SIZE * 16.0
			if (!unlimited && regionDistSq(r.rx, r.rz, ccx, ccz) > reach.toLong() * reach) continue

			if (frustum != null && !frustum.isVisible(r.bounds)) continue
			var anySlot = false
			for (slot in 0 until SurfaceRegions.SLOTS) {

				if (!r.hasSlot(slot)) { skipSlot[slot] = true; continue }
				val cx = r.rx * SurfaceRegions.SIZE + (slot % SurfaceRegions.SIZE)
				val cz = r.rz * SurfaceRegions.SIZE + (slot / SurfaceRegions.SIZE)
				val dx = (cx - ccx).toLong()
				val dz = (cz - ccz).toLong()
				val skip = (!unlimited && dx * dx + dz * dz > farChunks.toLong() * farChunks) || skipChunk(cx, cz, r.minY, r.maxY)
				skipSlot[slot] = skip
				if (!skip) anySlot = true
			}
			if (!anySlot) continue

			val skipMask = SurfaceDrawPlan.maskOf(skipSlot)
			val offScreenMask = if (perChunk && markOffScreen(r, camX, camY, camZ)) SurfaceDrawPlan.maskOf(offScreenSlot) else 0L

			val lx = camX - bx
			val lz = camZ - bz
			val v = if (visiblePool.size > visible.size) visiblePool[visible.size] else Visible().also { visiblePool.add(it) }
			var anyLayer = false
			for (li in 0 until SurfaceGpuRegion.LAYERS) {
				v.plans[li] = null
				val start = r.layerStarts[li] ?: continue
				val count = r.layerCounts[li] ?: continue
				val mask = if (faceCulling) SurfaceFaceCull.visibleMask(r.layerPlanes[li], lx, camY, lz) else SurfaceFaceCull.ALL
				val plan = r.drawCache.plan(li, start, count, SurfaceRegions.SLOTS, mask, skipMask, offScreenMask)
				frameCulledFaces += plan.culledFaces
				frameOffScreenFaces += plan.offScreenFaces
				if (plan.runCount == 0) continue
				v.plans[li] = plan
				anyLayer = true
				if (plan.maxIndices > maxIndices) maxIndices = plan.maxIndices
			}
			if (!anyLayer) continue
			val cdx = bx + size / 2 - camX
			val cdz = bz + size / 2 - camZ
			v.region = r
			v.distSq = cdx * cdx + cdz * cdz
			visible.add(v)
			drawnRegionKeys.add(key)
			r.offset.set((bx - camX).toFloat(), (-camY).toFloat(), (bz - camZ).toFloat())
			if (r.transform == null) r.transform = DynamicGpuData.Transform(view, white, r.offset, fade)
			if (r.arenaStart < 0) allInArena = false
		}
		if (visible.isEmpty()) return

		SurfaceDrawOrder.sortAscending(visible, sortScratch)
		SurfaceDrawOrder.descendingOf(visible, visibleFar)

		SurfaceIndexPlan.prepare(indexStorage, maxIndices)

		indirect = SurfaceIndirect.allowed(gameUsesIndirect(), indirectMaxDraws(), indirectFailed, allInArena) &&
			indexStorage.hasStorage(maxIndices) && prepareIndirect()
		if (!indirect) {

			if (transformArray.size != visible.size) transformArray = arrayOfNulls(visible.size)
			for (i in visible.indices) transformArray[i] = visible[i].region.transform
			@Suppress("UNCHECKED_CAST")
			val slices = RenderSystem.getDynamicUniforms().writeTransforms(*(transformArray as Array<DynamicGpuData.Transform>))
			for (i in visible.indices) visible[i].transform = slices[i]
		}

		multiDraw = SurfaceDrawMode.multiDrawAllowed(deviceHasMultiDraw(), multiDrawFailed)

		frameReady = try {
			resolveTextures()
		} catch (t: Throwable) {
			if (errors.firstTime(t)) Apex.logger.error("[Apex Far Terrain] texture load failed; skipping far terrain this frame (logged once per cause)", t)
			false
		}
	}

	private fun gameUsesIndirect(): Boolean =
		try { Minecraft.getInstance().levelRenderer.isChunkRenderingUsingMultiDrawIndirect() } catch (_: Throwable) { false }

	private fun indirectMaxDraws(): Int {
		if (indirectMaxDraws < 0) indirectMaxDraws = try {
			val info = RenderSystem.getDevice().getDeviceInfo()
			val f = info.features()
			if (f.drawIndirect() && f.multiDrawIndirect() && f.nonZeroFirstInstance()) info.limits().maxDrawIndirectDrawCount() else 0
		} catch (_: Throwable) { 0 }
		return indirectMaxDraws
	}

	private fun prepareIndirect(): Boolean {
		try {
			instances.clear()
			for (i in visible.indices) {
				val v = visible[i]
				val o = v.region.offset
				v.instance = instances.add(o.x, o.y, o.z)
			}
			commands.clear()
			for (layer in ChunkSectionLayer.entries) {
				val li = layer.ordinal
				val list = if (layer in TRANSLUCENT) visibleFar else visible
				commands.beginLayer(li)
				var maxIdx = 0
				for (i in list.indices) {
					val v = list[i]
					val plan = v.plans[li] ?: continue
					if (plan.drawCount == 0) continue
					val r = v.region
					commands.add(plan, r.arenaStart + r.layerBase[li], v.instance)
					if (plan.maxIndices > maxIdx) maxIdx = plan.maxIndices
				}
				commands.endLayer()
				layerMaxIndices[li] = maxIdx
			}
			if (commands.count == 0) return false
			val inst = instanceStream.submit(instances.data, instances.bytes)
			instanceSlice = inst.slice(0, instances.bytes.toLong())
			commandBuffer = commandStream.submit(commands.data, commands.bytes)
			val t = sharedTransformData ?: DynamicGpuData.Transform(view, white, zeroOffset, fade).also { sharedTransformData = it }
			sharedTransform = RenderSystem.getDynamicUniforms().writeTransforms(t)[0]
			frameIndirectRegions = visible.size
			return true
		} catch (t: Throwable) {
			indirectFailed = true
			if (errors.firstTime(t)) Apex.logger.warn("[Apex Far Terrain] shared-buffer indirect drawing failed; drawing per region from now on", t)
			return false
		}
	}

	private fun markOffScreen(r: SurfaceGpuRegion, camX: Double, camY: Double, camZ: Double): Boolean {
		val n = chunkFrustum.markRegion(r.rx, r.rz, r.minY, r.maxY, r.slotMinY, r.slotMaxY, skipSlot, offScreenSlot, camX, camY, camZ)
		frameOffScreenChunks += n
		return n > 0
	}

	private val textures = SurfacePassTextures<String, GpuTextureView>(listOf(TEX_ATLAS, TEX_LIGHTMAP))

	private fun resolveTextures(): Boolean {
		val mc = Minecraft.getInstance()
		return textures.prepare { key ->
			when (key) {
				TEX_ATLAS -> mc.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).textureView
				TEX_LIGHTMAP -> mc.gameRenderer.lightmap()
				else -> null
			}
		}
	}

	private fun bind(pass: RenderPass): Boolean {
		val atlas = textures.inPass(TEX_ATLAS) ?: return false
		val light = textures.inPass(TEX_LIGHTMAP) ?: return false
		val cache = RenderSystem.getSamplerCache()
		pass.setUniform("Sampler0", atlas, cache.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
		pass.setUniform("Sampler2", light, cache.getClampToEdge(FilterMode.LINEAR))
		return true
	}

	private const val TEX_ATLAS = "blocks"
	private const val TEX_LIGHTMAP = "lightmap"

	val errors = SurfaceErrorLog()

	private fun deviceHasMultiDraw(): Boolean =
		SurfaceDrawMode.deviceHasMultiDraw { RenderSystem.getDevice().getDeviceInfo().features() }

	private fun zeroOffsets(n: Int): PointerBuffer {
		var o = offsets
		if (o == null || o.capacity() < n) { o = PointerBuffer.allocateDirect(maxOf(n, 64, (o?.capacity() ?: 0) * 2)); offsets = o }
		o.position(0).limit(n)
		return o
	}

	private fun drawIndirect(pass: RenderPass, layers: Array<ChunkSectionLayer>): Boolean {
		val arenaBuffer = arena?.buffer ?: return false
		val inst = instanceSlice ?: return false
		val cmds = commandBuffer ?: return false
		val transform = sharedTransform ?: return false
		val indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
		val maxPerCall = indirectMaxDraws()
		for (layer in layers) {
			val li = layer.ordinal
			var left = commands.layerCount[li]
			if (left == 0) continue
			pass.setPipeline(RenderSystem.getCompiledPipeline(SurfacePipelines.sharedOf(layer)))
			if (!bind(pass)) { frameReady = false; return true }
			pass.setIndexBuffer(indices.getBuffer(layerMaxIndices[li]), indices.type())
			pass.setUniform("DynamicTransforms", transform)
			pass.setVertexBuffer(0, arenaBuffer.slice())
			pass.setVertexBuffer(1, inst)
			var first = commands.layerFirst[li]
			while (left > 0) {
				val k = minOf(left, maxPerCall)
				pass.drawIndexedIndirect(cmds.slice(first.toLong() * SurfaceIndirect.COMMAND_BYTES, k.toLong() * SurfaceIndirect.COMMAND_BYTES), k)
				frameDrawCalls++
				first += k
				left -= k
			}
			frameRuns += commands.layerCount[li]
		}
		return true
	}

	private fun draw(pass: RenderPass, layers: Array<ChunkSectionLayer>, backToFront: Boolean) {
		if (!frameReady || visible.isEmpty()) return
		try {
			if (indirect) {
				val done = try {
					drawIndirect(pass, layers)
				} catch (e: UnsupportedOperationException) {
					false.also { if (errors.firstTime(e)) Apex.logger.warn("[Apex Far Terrain] indirect multi-draw refused by the backend; drawing per region from next frame", e) }
				} catch (e: IllegalStateException) {
					false.also { if (errors.firstTime(e)) Apex.logger.warn("[Apex Far Terrain] indirect multi-draw rejected; drawing per region from next frame", e) }
				} catch (e: IllegalArgumentException) {
					false.also { if (errors.firstTime(e)) Apex.logger.warn("[Apex Far Terrain] indirect multi-draw rejected; drawing per region from next frame", e) }
				}
				if (!done) { indirectFailed = true; indirect = false; frameReady = false }
				if (done) for (layer in layers) frameVertices += layerVertices(layer.ordinal)
				return
			}
			val arenaBuffer = arena?.buffer
			val indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
			val list = if (backToFront) visibleFar else visible
			for (layer in layers) {
				val li = layer.ordinal
				var pipelineSet = false
				for (i in list.indices) {
					val v = list[i]
					val slice = v.region.layerSlice(li, arenaBuffer) ?: continue
					val plan = v.plans[li] ?: continue
					val n = plan.drawCount
					if (n == 0) continue
					if (SurfaceDrawMode.select(multiDraw, indices.hasStorage(plan.maxIndices)) == SurfaceDrawMode.Mode.MULTI_DRAW) {

						if (!pipelineSet) {
							pass.setPipeline(RenderSystem.getCompiledPipeline(SurfacePipelines.of(layer)))
							if (!bind(pass)) { frameReady = false; return }
							pipelineSet = true
						}
						pass.setIndexBuffer(indices.getBuffer(plan.maxIndices), indices.type())
						pass.setUniform("DynamicTransforms", v.transform)
						pass.setVertexBuffer(0, slice)
						val drawn = try {
							pass.multiDrawIndexed(zeroOffsets(n), plan.indexCounts, plan.baseVertices, n)
							true
						} catch (e: UnsupportedOperationException) {

							multiDrawFailed = true
							multiDraw = false
							if (errors.firstTime(e)) Apex.logger.warn("[Apex Far Terrain] multi-draw refused by the backend; drawing per range", e)
							false
						}
						if (drawn) {
							frameDrawCalls++
							frameRuns += n
							frameVertices += plan.drawnVertices
							continue
						}
					}

					var bound = false
					val counts = plan.indexCounts
					val bases = plan.baseVertices
					for (r in 0 until n) {
						val count = counts.get(r)
						if (!indices.hasStorage(count)) continue
						if (!pipelineSet) {
							pass.setPipeline(RenderSystem.getCompiledPipeline(SurfacePipelines.of(layer)))
							if (!bind(pass)) { frameReady = false; return }
							pipelineSet = true
						}
						if (!bound) {
							pass.setIndexBuffer(indices.getBuffer(count), indices.type())
							pass.setUniform("DynamicTransforms", v.transform)
							pass.setVertexBuffer(0, slice)
							bound = true
						}
						pass.drawIndexed(count, 1, 0, bases.get(r), 0)
						frameDrawCalls++
						frameRuns++
						frameVertices += count / 6 * 4
					}
				}
			}
		} catch (t: Throwable) {
			frameReady = false
			if (errors.firstTime(t)) Apex.logger.error("[Apex Far Terrain] draw failed; skipping far terrain this frame (logged once per cause)", t)
		}
	}

	private fun layerVertices(li: Int): Long {
		var n = 0L
		for (i in visible.indices) n += visible[i].plans[li]?.drawnVertices ?: 0L
		return n
	}

	fun drawOpaqueInPass(pass: RenderPass) {
		if (!frameReady) return
		drewInPass = true
		draw(pass, OPAQUE, false)
	}

	fun drawTranslucentInPass(pass: RenderPass) {
		if (!frameReady || !drewInPass) return
		draw(pass, TRANSLUCENT, true)
		translucentDone = true
	}

	private var translucentDone = false

	fun endFrame() {
		if (frameReady && visible.isNotEmpty()) {
			val needOpaque = !drewInPass
			val needTranslucent = !translucentDone
			if (needOpaque || needTranslucent) {
				ownPass(needOpaque, needTranslucent)
			}
			stats?.let { s ->
				s.drawPath = if (needOpaque) "own pass" else if (needTranslucent) "in-pass + own translucent" else "in-pass"
			}
		}
		stats?.let { s ->
			s.drawCalls = frameDrawCalls
			s.drawRuns = frameRuns
			s.multiDraw = multiDraw
			s.drawMode = if (!frameReady) "-" else if (indirect) "shared buffer, one call per layer" else if (multiDraw) "multi-draw per region" else "per range"
			val a = arena
			s.arenaBytes = a?.capacityBytes ?: 0L
			s.arenaUsedBytes = a?.usedBytes ?: 0L
			s.arenaHoles = a?.freeBlocks ?: 0
			s.arenaFragmentation = a?.fragmentation() ?: 0.0
			s.arenaGrowths = a?.growths ?: 0
			s.arenaRefused = a?.refused ?: 0
			s.frameDataWrites = instanceStream.writes + commandStream.writes
			s.frameDataReuses = instanceStream.reuses + commandStream.reuses
			s.verticesDrawn = frameVertices
			s.facesDrawn = frameVertices / 4
			s.facesCulled = if (frameReady) frameCulledFaces else 0
			s.facesOffScreen = if (frameReady) frameOffScreenFaces else 0
			s.chunksOffScreen = if (frameReady) frameOffScreenChunks else 0
			s.regionsDrawn = if (frameReady) visible.size else 0
		}
		frameReady = false
		drewInPass = false
		translucentDone = false
		visible.clear()
		visibleFar.clear()
	}

	private fun ownPass(opaque: Boolean, translucent: Boolean) {
		val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
		val color = target.colorTextureView ?: return
		RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			{ "Apex far surface" }, color, Optional.empty(),
			if (target.hasDepth()) target.depthTextureView else null, OptionalDouble.empty(),
		).use { pass ->
			RenderSystem.bindDefaultUniforms(pass)
			if (opaque) draw(pass, OPAQUE, false)
			if (translucent) draw(pass, TRANSLUCENT, true)
		}
	}

	fun idle() {
		frameReady = false
		visible.clear()
		visibleFar.clear()
		if (!frames.canWrite()) return
		if (clearedGeneration != generation || pending.isNotEmpty()) drainUploads(false)
		uploads.closeAll()
		while (true) { val p = pending.poll() ?: break; p.mesh?.close() }
		SurfaceStaging.clear()
		uploadedThisFrame = false
		if (regions.isEmpty()) closeShared()
	}

	private val OPAQUE = arrayOf(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT)
	private val TRANSLUCENT = arrayOf(ChunkSectionLayer.TRANSLUCENT)
	private const val MAX_RESIDENT_REGIONS = 400
	private const val MAX_VRAM_BYTES = 768L shl 20

	private const val ARENA_STEP_BYTES = 32L shl 20
	private const val ARENA_MAX_BYTES = 1L shl 30
	const val UNLIMITED = Int.MAX_VALUE
}
