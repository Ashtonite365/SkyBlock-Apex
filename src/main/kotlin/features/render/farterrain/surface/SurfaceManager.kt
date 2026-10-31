/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, settings + per-island sessions + capture/mesh orchestration + debug mode.
 *
 * Main thread: works out which island store is active ([SurfaceIslandKeys]), records loaded chunks' surfaces.
 * Worker thread: stores surfaces, loads/flushes region files, meshes regions. Render thread: [SurfaceRenderer].
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import dev.ashtonite365.apex.util.render.BlockModelChanges
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.render.connected.ConnectedTextures
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.chunk.CompiledSectionMesh
import net.minecraft.client.renderer.chunk.SectionMesh
import net.minecraft.util.Util
import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.chunk.EmptyLevelChunk
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.scores.DisplaySlot
import org.joml.Vector2i

object SurfaceManager {
	@Config
	object TConfig : ManagedConfig("apex-far-terrain", Category.RENDER) {

		val enabled by toggle("enabled") { true }

		val unlimitedDistance by toggle("unlimited-distance") { true }

		val distance by integer("distance", 8, 1024) { 128 }

		val faceCulling by toggle("block-face-culling") { true }

		val chunkFrustumCulling by toggle("per-chunk-frustum-culling") { true }

		val particleDistance by integer("particle-distance", 0, 256) { 64 }

		val debug by toggle("debug") { false }

		val debugChunkMap by toggle("debug-chunk-map") { true }
		val debugHud by position("debug-hud", 220, 120) { Vector2i(4, 60) }
		val dumpDebug by button("dump-debug") { dumpDebugLog() }
	}

	private val log get() = Apex.logger
	private const val TAG = "[Apex Far Terrain]"

	val blockedByOtherLodMod: Boolean by lazy { OtherLodMods { id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id) }.loaded }

	val active: Boolean get() = TConfig.enabled && !blockedByOtherLodMod

	val stats = SurfaceStats()

	@Volatile private var connectionEpoch = 0L
	@Volatile private var locationEpoch = -1L
	@Volatile private var gametype: String? = null
	@Volatile private var mode: String? = null
	@Volatile private var sidebarTitle: String? = null

	private class Session(val key: SurfaceIslandKey, val level: ClientLevel, val store: SurfaceStore, val generation: Int) {
		@Volatile var loaded = false
		val firstSeen = HashMap<Long, Long>()
		val capturedAt = HashMap<Long, Long>()
		val pending: MutableSet<Long> = ConcurrentHashMap.newKeySet()
		val meshQueued: MutableSet<Long> = ConcurrentHashMap.newKeySet()
	}

	@Volatile private var session: Session? = null
	private val worker = Executors.newSingleThreadScheduledExecutor { r ->
		Thread(r, "Apex-FarSurface-Worker").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 2 }
	}

	init { SurfaceRenderer.stats = stats }

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ClientPlayConnectionEvents.JOIN.register { _, _, _ -> connectionEpoch++ }
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> Client.onClientThread { endSession("disconnect") } }
		ClientChunkEvents.CHUNK_LOAD.register { level, chunk -> onLoad(level, chunk) }
		ClientChunkEvents.CHUNK_UNLOAD.register { level, chunk -> onUnload(level, chunk) }

		LevelRenderEvents.START_MAIN.register { _ -> SurfaceRenderer.frames.passOpened() }
		LevelRenderEvents.END_MAIN.register { _ ->

			if (SurfaceRenderer.frames.endMain()) prepareFrameNow()
			if (active) SurfaceRenderer.endFrame()
			SurfaceRenderer.frames.frameDone()
			dropHandoffFrame()
		}
		worker.scheduleWithFixedDelay({ flushNow("periodic") }, 60, 60, TimeUnit.SECONDS)
		Runtime.getRuntime().addShutdownHook(Thread { runCatching { session?.store?.flush() } })
	}

	@JvmStatic
	fun beforeMainPass() {
		if (SurfaceRenderer.frames.beforeMainPass()) prepareFrameNow()
	}

	private fun prepareFrameNow() {
		if (!active || session == null) { SurfaceRenderer.idle(); SurfaceFog.fadeEnd = 0f; dropHandoffFrame(); return }
		val level = Client.game.level ?: return
		val levelState = Client.game.gameRenderer.gameRenderState().levelRenderState
		val cam = levelState.cameraRenderState
		val pos = cam.pos ?: return

		val t0 = System.nanoTime()
		val frame = handoffFrame ?: HandoffFrame(level, pos.x, pos.y, pos.z, holdBack = false,
			mode = collectDrawn(Util.getMillis(), null, pos.x, pos.y, pos.z), frustum = cam.cullFrustum).also { handoffFrame = it }
		frame.spentNs += System.nanoTime() - t0

		SurfaceRenderer.faceCulling = TConfig.faceCulling
		uploadFocus(level, pos.x, pos.z)
		SurfaceRenderer.chunkFrustumCulling = TConfig.chunkFrustumCulling
		val ent = cam.entityRenderState
		val player = levelState.playerRenderState
		val chunkFrustumSafe = SurfaceChunkFrustum.safeFrame(
			ent.hurtTime, ent.deathTime, ent.isDeadOrDying,
			player.portalEffectIntensity, player.nauseaEffectIntensity,
			cam.isFrustumCaptured, cam.isPanoramicMode,
		)
		SurfaceRenderer.prepareFrame(cam, farChunksSetting(), SurfaceFog.envScale, chunkFrustumSafe) { x, z, _, _ -> !frame.apexDraws(x, z) }
		stats.onHandoffFrame(frame.spentNs, frame.now)
		frame.publish(stats)

		SurfaceFog.fadeStart = SurfaceFarDistance.fadeStart(SurfaceRenderer.effectiveFarChunks)
		SurfaceFog.fadeEnd = SurfaceFarDistance.fadeEnd(SurfaceRenderer.effectiveFarChunks)
	}

	fun farChunksSetting(): Int = SurfaceFarDistance.setting(TConfig.unlimitedDistance, TConfig.distance)

	val cameraMotion = SurfaceCameraMotion()
	private var motionLevel: ClientLevel? = null

	private val gameArea = SurfaceGameArea()
	private var handoffFrame: HandoffFrame? = null
	private val heldMeshes: MutableSet<SectionMesh> = Collections.newSetFromMap(IdentityHashMap())
	private var pruneAt = 0L
	private var pruneCcx = Int.MIN_VALUE
	private var pruneCcz = Int.MIN_VALUE

	private fun dropHandoffFrame() { handoffFrame = null; heldMeshes.clear() }

	private val drawnList = SurfaceDrawnList()

	private val handoff = SurfaceHandoffDecider(drawn = drawnList)

	private val drawnSources = SurfaceDrawnSources(
		isModLoaded = { id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id) },
		loadOptional = SurfaceDrawnSources.serviceLoader(SurfaceManager::class.java.classLoader),
		warn = { msg, t -> if (t == null) log.warn("$TAG $msg") else log.warn("$TAG $msg", t) },
	)

	private fun collectDrawn(now: Long, vanillaList: List<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection>?,
		camX: Double, camY: Double, camZ: Double): SurfaceDrawnMode {
		drawnList.begin(now, camX, camY, camZ)

		if (vanillaList != null) {
			for (sec in vanillaList) {
				val node = sec.sectionNode
				drawnList.drawn(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node))
			}
			return SurfaceDrawnMode.VANILLA.also { stats.drawnMode = it.label; stats.drawnSections = drawnList.frameSections }
		}
		val mode = if (drawnSources.collect(drawnList)) SurfaceDrawnMode.RENDERER else SurfaceDrawnMode.PREDICTION
		stats.drawnMode = mode.label
		stats.drawnSections = drawnList.frameSections
		return mode
	}

	@JvmStatic
	fun beforeSectionDrawList() {
		beforeSectionDrawListTimed()
	}

	private fun uploadFocus(level: ClientLevel, x: Double, z: Double) {
		val ccx = SurfaceHandoff.chunkOf(x); val ccz = SurfaceHandoff.chunkOf(z)
		val chunks = level.chunkSource
		gameArea.refresh(level, ccx, ccz) { cx, cz -> chunks.hasChunk(cx, cz) }
		SurfaceRenderer.uploadFocus(ccx, ccz, SurfaceGameArea.urgentReach(gameArea.extent))
	}

	private fun beforeSectionDrawListTimed() {
		heldMeshes.clear()
		handoffFrame = null
		if (!active || session == null) return
		val level = Client.game.level ?: return

		val cam = Client.game.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos ?: return
		uploadFocus(level, cam.x, cam.z)
		SurfaceRenderer.uploadsBeforeHandoff()
		val t0 = System.nanoTime()
		val list = Client.game.levelRenderer.visibleSections()

		val mode = collectDrawn(Util.getMillis(), list, cam.x, cam.y, cam.z)
		val frustum = Client.game.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.cullFrustum
		val frame = HandoffFrame(level, cam.x, cam.y, cam.z, holdBack = true, mode = mode, frustum = frustum)
		handoffFrame = frame
		var held = 0
		for (sec in list) {
			val node = sec.sectionNode
			val x = SectionPos.x(node); val z = SectionPos.z(node)
			if (frame.gameDraws(x, z)) continue
			heldMeshes.add(sec.sectionMesh.get())
			if (frame.grid.markHeld(x, z)) held++
		}
		stats.handoffHeld = held
		frame.spentNs = System.nanoTime() - t0
	}

	@JvmStatic
	fun holdBack(mesh: SectionMesh): Boolean = heldMeshes.isNotEmpty() && heldMeshes.contains(mesh)

	private class HandoffFrame(val level: ClientLevel, camX: Double, camY: Double, camZ: Double, holdBack: Boolean, val mode: SurfaceDrawnMode,
		private val frustum: net.minecraft.client.renderer.culling.Frustum?) : SurfaceHandoffDecider.Game {
		val mc = Client.game
		val lr = mc.levelRenderer
		val viewArea = lr.viewArea()
		val dispatcher = lr.sectionRenderDispatcher()
		val vanillaPath = mode == SurfaceDrawnMode.VANILLA && viewArea != null && dispatcher != null
		val now = Util.getMillis()
		val fadeMs = Util.toMillis(mc.options.chunkSectionFadeInTime().get())
		val ccx = SurfaceHandoff.chunkOf(camX)
		val ccz = SurfaceHandoff.chunkOf(camZ)
		val grid: SurfaceHandoffGrid
		val decider: SurfaceHandoffDecider.Frame
		val bp = BlockPos.MutableBlockPos()
		private val chunks = level.chunkSource
		private val minY = level.minY.toDouble()
		private val maxY = (level.maxY + 1).toDouble()

		init {

			if (motionLevel !== level) { cameraMotion.reset(); motionLevel = level }
			cameraMotion.frame(camX, camZ, now)
			val predict = if (cameraMotion.reliable(now)) SurfaceHandoffGeometry.Prediction(camX, camZ, cameraMotion.vx, cameraMotion.vz) else null

			gameArea.refresh(level, ccx, ccz) { x, z -> chunks.hasChunk(x, z) }
			gameArea.drawnFrame(drawnList.frameReach(), now)
			grid = SurfaceHandoffGrid(ccx, ccz, gameArea.extent)

			decider = handoff.Frame(mode, camX, camY, camZ, now, SurfaceFog.gameFogEnd.toDouble(), gameArea.radius(now), predict, holdBack, this)

			if (now >= pruneAt || ccx != pruneCcx || ccz != pruneCcz) {
				pruneAt = now + 5000; pruneCcx = ccx; pruneCcz = ccz
				val keep = grid.radius
				handoff.prune { k -> Math.abs(SurfaceIndex.keyX(k) - ccx) <= keep && Math.abs(SurfaceIndex.keyZ(k) - ccz) <= keep }
			}
		}

		fun gameDraws(x: Int, z: Int): Boolean = decision(x, z) and SurfaceHandoffOrder.GAME != 0
		fun apexDraws(x: Int, z: Int): Boolean = decision(x, z) and SurfaceHandoffOrder.APEX != 0

		var spentNs = 0L

		fun publish(st: SurfaceStats) {
			val f = decider
			st.handoffGame = f.gameOnly; st.handoffBoth = f.both; st.handoffNotOwned = f.notOwned
			st.handoffNoNeighbours = f.noNeighbours; st.handoffNotBuilt = f.notBuilt; st.handoffWaiting = f.waiting
			st.handoffNotDrawn = f.notDrawn
			st.handoffDoubleDrawn = f.doubleDrawn
			st.gameAreaExtent = gameArea.extent; st.gameAreaLoadedRadius = gameArea.loadedRadius
			st.gameAreaDrawnReach = gameArea.drawnReach(now); st.gameAreaRadius = f.radius
		}

		fun decision(x: Int, z: Int): Int {

			if (!grid.inside(x, z)) return SurfaceHandoffGrid.OUTSIDE
			val c = grid.get(x, z)
			if (c != 0) return c
			val t0 = System.nanoTime()
			val region = SurfaceRenderer.chunkRegion(x, z)

			val slot = SurfaceRegions.slot(x, z)
			val yLo = region?.slotMinY?.get(slot) ?: 0
			val yHi = region?.slotMaxY?.get(slot) ?: 0
			val minS = level.minSectionY; val maxS = level.maxSectionY
			val lo = if (region == null) minS else (yLo shr 4).coerceIn(minS, maxS)
			val hi = if (region == null) maxS else ((yHi - 1) shr 4).coerceIn(lo, maxS)
			val d = decider.decide(x, z, region != null, yLo, yHi, lo, hi)
			grid.put(x, z, d)
			spentNs += System.nanoTime() - t0
			return d
		}

		override fun loaded(x: Int, z: Int): Boolean = chunks.hasChunk(x, z)

		override fun inView(x: Int, z: Int): Boolean {
			val f = frustum ?: return true
			return f.isVisible(net.minecraft.world.phys.AABB(x * 16.0, minY, z * 16.0, x * 16.0 + 16.0, maxY, z * 16.0 + 16.0))
		}

		override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {
			if (!settled) {

				var sy = hi
				while (sy >= lo) {
					bp.set(x shl 4, sy shl 4, z shl 4)
					if (!lr.isSectionCompiledAndVisible(bp, fadeMs)) {
						if (sy == hi) { p.topBuilt = false; break }
						p.allBuilt = false
					}
					sy--
				}
			} else if (mode == SurfaceDrawnMode.PREDICTION) {

				bp.set(x shl 4, hi shl 4, z shl 4)
				if (!lr.isSectionCompiledAndVisible(bp, fadeMs)) { p.present = false; return }
			}
			if (!vanillaPath) return

			dispatcher!!.lock()
			try {
				var signature = 0L
				for (sy in lo..hi) {
					bp.set(x shl 4, sy shl 4, z shl 4)
					val rs = viewArea!!.getRenderSectionAt(bp)

					if (rs == null || rs.sectionNode != SectionPos.asLong(x, sy, z)) { p.present = false; break }
					val mesh = rs.sectionMesh.get()
					if (mesh === CompiledSectionMesh.UNCOMPILED) {

						if (sy == hi) { p.present = false; break }
						p.allBuilt = false
						continue
					}
					signature = signature * 31 + System.identityHashCode(mesh)
					if (settled || !p.topBuilt || mesh === CompiledSectionMesh.EMPTY || !mesh.hasRenderableLayers()) continue

					var drawable = true
					for (layer in ChunkSectionLayer.entries) {
						val draw = mesh.getSectionDraw(layer) ?: continue
						val slice = dispatcher.getRenderSectionSlice(mesh, layer)
						if (slice == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) { drawable = false; break }
					}
					if (!drawable) { if (sy == hi) p.topBuilt = false else p.allBuilt = false; continue }
					if (rs.getVisibility(now, fadeMs) < 1f) p.fadeDone = false
				}
				p.signature = signature
			} finally {
				dispatcher.unlock()
			}
		}
	}

	@Subscribe
	fun onLocation(event: SkyblockServerUpdateEvent) {
		val l = event.currentLocation
		gametype = l?.gametype
		mode = l?.mode
		locationEpoch = if (l == null) -1L else connectionEpoch
	}

	private fun place(level: ClientLevel): SurfacePlace {
		val mc = Client.game
		val server = mc.currentServer?.ip
		val sp = if (server == null) mc.singleplayerServer?.worldData?.levelName ?: "world" else null
		return SurfacePlace(
			server = server,
			singleplayerWorld = sp,
			onHypixel = server != null && (SkyBlockSession.inHypixel || server.lowercase().contains("hypixel.net")),
			locationFresh = locationEpoch == connectionEpoch,
			gametype = gametype,
			mode = mode,
			ownership = SurfaceIslandKeys.ownershipFromSidebarTitle(sidebarTitle),
			profileId = SkyBlockSession.profileId,
			dimension = level.dimension().identifier().toString(),
			connection = connectionEpoch,
		)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val level = Client.game.level
		sidebarTitle = level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.displayName?.bareText
		SurfaceRenderer.farClip = if (active) SurfaceFarDistance.farClip(TConfig.unlimitedDistance, TConfig.distance, SurfaceRenderer.effectiveFarChunks) else 0f
		if (!active || level == null) { endSession(if (level == null) "no level" else "off"); return }
		if (event.tickCount % 10 == 0) {
			val key = SurfaceIslandKeys.key(place(level))
			val cur = session
			if (key == null) { if (cur != null) endSession("location changing"); return }
			if (cur == null || cur.key.id != key.id || cur.level !== level) startSession(key, level)
		}
		Client.player?.let {
			playerChunk = SurfaceIndex.key(it.blockX shr 4, it.blockZ shr 4)
		} ?: cameraMotion.reset()
		capture(level)
		requeueEvicted()
		remeshForSettings()
		if (session != null) {
			SurfaceParticles.tick(level, TConfig.particleDistance)
			stats.particleRuns = SurfaceParticles.lastRun
			stats.particlesSpawned = SurfaceParticles.lastSpawned
		}
		if (TConfig.debug && event.tickCount % 200 == 0) stats.lines().forEach { log.info("$TAG [debug] $it") }
	}

	private fun startSession(key: SurfaceIslandKey, level: ClientLevel) {
		endSession("switch")
		remeshAll.clear()
		SurfaceRenderer.clear()
		val folder = key.path?.let { Client.game.gameDirectory.resolve("apex").resolve("far-terrain-surface").resolve(it) }
		val s = Session(key, level, SurfaceStore(folder, stats), SurfaceRenderer.generation)
		session = s

		run {
			val now = System.currentTimeMillis()
			for (k in gameArea.loadedChunks(level)) {
				val cx = SurfaceIndex.keyX(k)
				val cz = SurfaceIndex.keyZ(k)
				if (SurfaceCapture.loadedChunk(level, cx, cz) != null) s.firstSeen[k] = now
			}
		}
		stats.resetVisit(key.label)
		stats.event("arrive ${key.id} -> ${folder?.path ?: "(memory only)"}")
		worker.execute {
			try {
				s.store.load()
			} catch (e: Throwable) {
				log.error("$TAG could not load ${folder?.path}", e)
			}
			s.loaded = true
			val regions = s.store.keys.map { SurfaceIndex.key(SurfaceIndex.keyX(it) shr 3, SurfaceIndex.keyZ(it) shr 3) }.toSet()
			for (r in regions) queueMesh(s, SurfaceIndex.keyX(r), SurfaceIndex.keyZ(r))
		}
	}

	private fun endSession(why: String) {
		val s = session ?: return
		session = null
		remeshAll.clear()
		SurfaceRenderer.clear()
		stats.event("leave ${s.key.id} ($why): ${s.store.chunkCount} chunks")
		worker.execute {
			runCatching { s.store.flush() }.onFailure { log.warn("$TAG flush failed", it) }
			stats.ramBytes = 0
		}
	}

	private fun flushNow(why: String) {
		val s = session ?: return
		if (!s.loaded) return
		if (s.store.hasDirty()) runCatching { s.store.flush() }.onFailure { log.warn("$TAG $why flush failed", it) }
		evictIfOverBudget(s)
	}

	private val evictedRegions = HashSet<Long>()

	private fun requeueEvicted() {
		val s = session ?: return
		while (true) evictedRegions.add(SurfaceRenderer.evicted.poll() ?: break)
		if (evictedRegions.isEmpty()) return
		val keep = SurfaceRenderer.keepDistSq
		val pcx = SurfaceIndex.keyX(playerChunk)
		val pcz = SurfaceIndex.keyZ(playerChunk)
		val it = evictedRegions.iterator()
		while (it.hasNext()) {
			val rk = it.next()
			if (SurfaceRenderer.regionDistSq(SurfaceIndex.keyX(rk), SurfaceIndex.keyZ(rk), pcx, pcz) < keep) {
				it.remove()
				queueMesh(s, SurfaceIndex.keyX(rk), SurfaceIndex.keyZ(rk))
			}
		}
	}

	private val remeshAll = SurfaceRemeshAll()

	private fun remeshForSettings() {
		val s = session
		val connected = ConnectedTextures.farSignature()
		val sig = connected * 31 + BlockModelChanges.generation
		if (remeshAll.changed(sig) && s != null && s.loaded) {
			worker.execute {
				if (session !== s) return@execute
				val regions = SurfaceRemeshAll.regionsNearestFirst(s.store.keys.toList(), SurfaceIndex.keyX(playerChunk), SurfaceIndex.keyZ(playerChunk))
				remeshAll.replace(regions)
				stats.event("far terrain models changed (connected textures ${if (connected == 0) "off" else "on"}, block replacements #${BlockModelChanges.generation}): re-meshing ${regions.size} regions")
			}
		}
		if (s == null || !s.loaded) return
		for (rk in remeshAll.take(stats.meshQueue.get())) queueMesh(s, SurfaceIndex.keyX(rk), SurfaceIndex.keyZ(rk))
	}

	@Volatile private var playerChunk: Long = 0L

	private fun evictIfOverBudget(s: Session) {
		if (s.store.chunkCount <= RAM_BUDGET_CHUNKS) return
		val px = SurfaceIndex.keyX(playerChunk)
		val pz = SurfaceIndex.keyZ(playerChunk)
		val entries = s.store.keys.map { k ->
			val dx = (SurfaceIndex.keyX(k) - px).toLong(); val dz = (SurfaceIndex.keyZ(k) - pz).toLong()
			SurfaceBudget.Entry(k, dx * dx + dz * dz, 1L)
		}
		val drop = SurfaceBudget.toDrop(entries, RAM_BUDGET_CHUNKS, Long.MAX_VALUE).toHashSet()
		val n = s.store.evict { c -> c.key !in drop }
		if (n > 0) {
			stats.ramBytes = s.store.estimatedBytes()
			stats.event("evicted $n far chunks from memory (kept on disk)")
		}
	}

	private fun onLoad(level: ClientLevel, chunk: LevelChunk) {
		if (chunk !is EmptyLevelChunk) gameArea.chunkLoaded(level, chunk.pos.x(), chunk.pos.z())
		val s = session ?: return
		if (s.level !== level || chunk is EmptyLevelChunk) return
		s.firstSeen[SurfaceIndex.key(chunk.pos.x(), chunk.pos.z())] = System.currentTimeMillis()
	}

	private fun onUnload(level: ClientLevel, chunk: LevelChunk) {
		gameArea.chunkUnloaded(level, chunk.pos.x(), chunk.pos.z())
		val s = session ?: return
		if (!active || s.level !== level || !s.loaded || chunk is EmptyLevelChunk) return
		val k = SurfaceIndex.key(chunk.pos.x(), chunk.pos.z())
		val seen = s.firstSeen.remove(k) ?: return
		val now = System.currentTimeMillis()
		if (now - seen < SETTLE_MS) return
		submitCapture(s, level, chunk, now)
	}

	private fun capture(level: ClientLevel) {
		val s = session ?: return
		if (!s.loaded) return
		stats.pendingCaptures.set(s.pending.size)
		if (s.pending.size > MAX_PENDING) return
		val now = System.currentTimeMillis()
		var budget = CHUNKS_PER_TICK
		val it = s.firstSeen.entries.iterator()
		while (it.hasNext() && budget > 0) {
			val (k, seen) = it.next()
			if (now - seen < SETTLE_MS) continue
			val last = s.capturedAt[k]
			val cx = SurfaceIndex.keyX(k)
			val cz = SurfaceIndex.keyZ(k)

			val edges = s.store.chunk(cx, cz)?.edges ?: SurfaceChunk.ALL_EDGES
			val needEdges = last != null && edges != SurfaceChunk.ALL_EDGES && now - last > EDGE_RETRY_MS &&
				SurfaceChurn.edgesNowAvailable(edges) { dx, dz -> SurfaceCapture.loadedChunk(level, cx + dx, cz + dz) != null }
			if (last != null && now - last < RECAPTURE_MS && !needEdges) continue
			val chunk = SurfaceCapture.loadedChunk(level, SurfaceIndex.keyX(k), SurfaceIndex.keyZ(k))
			if (chunk == null) { it.remove(); continue }
			if (needEdges) stats.recaptureForEdges.incrementAndGet()
			submitCapture(s, level, chunk, now)
			budget--
		}
	}

	private fun submitCapture(s: Session, level: ClientLevel, chunk: LevelChunk, now: Long) {
		val k = SurfaceIndex.key(chunk.pos.x(), chunk.pos.z())
		s.capturedAt[k] = now
		val surface = try { SurfaceCapture.capture(level, chunk, now) } catch (e: Throwable) { log.warn("$TAG capture failed", e); return }
		if (surface.isAllAir) return
		s.pending.add(k)
		worker.execute {
			try {
				if (session !== s) return@execute
				val r = s.store.put(surface)
				if (r == SurfaceStore.PutResult.SAME) return@execute
				stats.recordedThisVisit.incrementAndGet()
				if (r == SurfaceStore.PutResult.CHANGED) {
					val diff = s.store.lastDifference
					stats.event("changed chunk ${surface.x},${surface.z}: ${diff ?: "re-recorded"}")
					if (TConfig.debug) log.info("$TAG [debug] changed chunk ${surface.x},${surface.z}: ${diff ?: "?"}")
				}

				for (rk in SurfaceRegions.affectedRegions(surface.x, surface.z)) queueMesh(s, SurfaceIndex.keyX(rk), SurfaceIndex.keyZ(rk))
				stats.ramBytes = s.store.estimatedBytes()
			} catch (e: Throwable) {
				log.warn("$TAG store failed", e)
			} finally {
				s.pending.remove(k)
			}
		}
	}

	private fun queueMesh(s: Session, rx: Int, rz: Int) {
		val rk = SurfaceIndex.key(rx, rz)
		if (!s.meshQueued.add(rk)) return
		stats.meshQueue.incrementAndGet()
		worker.execute {
			s.meshQueued.remove(rk)
			stats.meshQueue.decrementAndGet()
			if (session !== s) return@execute
			val t0 = System.nanoTime()
			val mesh = try {
				SurfaceMesher.build(s.level, s.store, rx, rz)
			} catch (e: Throwable) {

				if (SurfaceRenderer.errors.firstTime(e)) log.error("$TAG mesh region $rx,$rz failed (logged once per cause)", e)
				null
			}
			val ns = System.nanoTime() - t0
			stats.onWorkerBusy(ns)
			stats.onMeshBuilt("$rx,$rz", ns / 1_000_000.0, mesh?.faces ?: 0)
			SurfaceRenderer.submit(rk, mesh, s.generation)
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.debug) return
		drawDebugHud(event)
	}

	private fun drawDebugHud(event: HudRenderEvent) {
		val lines = ArrayList<Component>()
		lines.add(Component.literal("§bApex Far Terrain §7" + when {
			blockedByOtherLodMod -> "§coff: another far-terrain (LOD) mod is installed"
			!TConfig.enabled -> "§eoff (setting)"
			session == null -> "§ewaiting for location"
			else -> "§aon, " + (if (TConfig.unlimitedDistance) "unlimited (${SurfaceRenderer.effectiveFarChunks} chunks recorded)" else "${TConfig.distance} chunks")
		}))
		stats.lines().forEach { lines.add(Component.literal("§7$it")) }
		if (TConfig.debugChunkMap) chunkMap().forEach { lines.add(Component.literal(it)) }
		event.drawHudLines(TConfig.debugHud, lines)
	}

	private fun chunkMap(): List<String> {
		val s = session ?: return emptyList()
		val p = Client.player ?: return emptyList()
		val pcx = p.blockX shr 4
		val pcz = p.blockZ shr 4
		val r = MAP_RADIUS
		return (-r..r).map { dz ->
			buildString {
				for (dx in -r..r) {
					val k = SurfaceIndex.key(pcx + dx, pcz + dz)
					append(when {
						dx == 0 && dz == 0 -> "§f+"
						k in s.pending -> "§e▒"
						s.store.has(pcx + dx, pcz + dz) -> "§a█"
						else -> "§8·"
					})
				}
			}
		}
	}

	fun dumpDebugLog(): File? {
		val dir = Client.game.gameDirectory.resolve("apex").resolve("far-terrain-surface").resolve("debug")
		return try {
			dir.mkdirs()
			val f = dir.resolve("dump-" + SimpleDateFormat("yyyyMMdd-HHmmss").format(Date()) + ".log")
			val s = session
			f.writeText(stats.dump(listOf(
				"enabled=${TConfig.enabled} unlimited=${TConfig.unlimitedDistance} distance=${TConfig.distance} effective=${SurfaceRenderer.effectiveFarChunks} faceCulling=${TConfig.faceCulling} chunkFrustumCulling=${TConfig.chunkFrustumCulling} particleDistance=${TConfig.particleDistance} blockedByOtherLodMod=$blockedByOtherLodMod",
				"island=${s?.key?.id ?: "-"} path=${s?.key?.path ?: "(memory only)"} chunks=${s?.store?.chunkCount ?: 0} disk=${s?.store?.diskBytes() ?: 0} B",
				"gameArea: loadedExtent=${gameArea.extent} loadedRadius=${gameArea.loadedRadius} drawnReach=${gameArea.drawnReach(Util.getMillis())} radius=${gameArea.radius(Util.getMillis())}",
			)))
			log.info("$TAG debug dump written to ${f.path}")
			Client.showChat(Component.literal("§bApex Far Terrain: §7debug log saved to ${f.path}"))
			f
		} catch (e: Throwable) {
			log.warn("$TAG debug dump failed", e); null
		}
	}

	private const val SETTLE_MS = 2_000L
	private const val RECAPTURE_MS = 120_000L
	private const val EDGE_RETRY_MS = 5_000L
	private const val CHUNKS_PER_TICK = 4
	private const val MAX_PENDING = 64
	private const val MAP_RADIUS = 8
	private const val RAM_BUDGET_CHUNKS = 16_384
}
