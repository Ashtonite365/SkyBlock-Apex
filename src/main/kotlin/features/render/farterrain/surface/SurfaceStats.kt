/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, debug-mode diagnostics (pure Kotlin, unit-tested).
 * Counters are cheap atomics that are always updated; the overlay, periodic log lines and the dump file only exist
 * while Debug Mode is on (see SurfaceDebug).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SurfaceStats {

	@Volatile var islandLabel: String = "-"
	@Volatile var islandLoadMs = 0.0
	@Volatile var islandLoadChunks = 0
	@Volatile var islandLoadRegions = 0
	@Volatile var lastRegionLoad: String = "-"
	val regionLoads = AtomicInteger()
	@Volatile var regionLoadMsMax = 0.0

	val recordedThisVisit = AtomicInteger()
	val pendingCaptures = AtomicInteger()
	val changedThisVisit = AtomicInteger()
	val recaptureForEdges = AtomicInteger()

	val meshQueue = AtomicInteger()
	val meshBuilds = AtomicInteger()
	@Volatile var meshLastMs = 0.0
	@Volatile var meshMaxMs = 0.0
	private val meshTotalNs = AtomicLong()
	private val workerBusyNs = AtomicLong()
	@Volatile private var workerWindowStart = System.nanoTime()
	@Volatile var workerUsage = 0.0

	@Volatile var drawCalls = 0

	@Volatile var drawRuns = 0
	@Volatile var multiDraw = false
	@Volatile var uploadsLastFrame = 0
	@Volatile var uploadBytesLastFrame = 0L
	@Volatile var uploadsWaiting = 0
	@Volatile var regionsDrawn = 0
	@Volatile var facesDrawn = 0L

	@Volatile var facesCulled = 0L

	@Volatile var facesOffScreen = 0L
	@Volatile var chunksOffScreen = 0
	@Volatile var verticesDrawn = 0L
	@Volatile var regionsResident = 0
	@Volatile var vramBytes = 0L
	@Volatile var ramBytes = 0L
	@Volatile var drawPath: String = "-"

	@Volatile var drawMode: String = "-"

	@Volatile var arenaBytes = 0L
	@Volatile var arenaUsedBytes = 0L
	@Volatile var arenaHoles = 0
	@Volatile var arenaFragmentation = 0.0
	@Volatile var arenaGrowths = 0

	@Volatile var arenaRefused = 0

	@Volatile var frameDataWrites = 0L
	@Volatile var frameDataReuses = 0L

	@Volatile var handoffHeld = 0

	@Volatile var handoffGame = 0
	@Volatile var handoffBoth = 0
	@Volatile var handoffNotOwned = 0
	@Volatile var handoffNoNeighbours = 0
	@Volatile var handoffNotBuilt = 0
	@Volatile var handoffWaiting = 0

	@Volatile var handoffNotDrawn = 0

	@Volatile var handoffDoubleDrawn = 0

	@Volatile var gameAreaExtent = 0
	@Volatile var gameAreaLoadedRadius = 0
	@Volatile var gameAreaDrawnReach = 0
	@Volatile var gameAreaRadius = 0

	@Volatile var drawnMode = "-"
	@Volatile var drawnSections = 0

	@Volatile var handoffNs = 0L

	@Volatile var handoffMaxNs = 0L
	private var handoffWindowMax = 0L
	private var handoffWindowStart = 0L

	fun onHandoffFrame(ns: Long, nowMs: Long) {
		handoffNs = ns
		if (ns > handoffWindowMax) handoffWindowMax = ns
		if (nowMs - handoffWindowStart >= 1000) { handoffMaxNs = handoffWindowMax; handoffWindowMax = 0; handoffWindowStart = nowMs }
		if (ns > handoffMaxNs) handoffMaxNs = ns
	}

	val cacheHits = AtomicInteger()
	val cacheMisses = AtomicInteger()
	val cacheChanged = AtomicInteger()

	val refined = AtomicInteger()

	@Volatile var particleRuns = 0
	@Volatile var particlesSpawned = 0
	val saves = AtomicInteger()
	val savedBytes = AtomicLong()

	private val events = ArrayDeque<String>()
	private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")

	@Synchronized
	fun event(line: String) {
		events.addLast(timeFormat.format(Date()) + " " + line)
		while (events.size > MAX_EVENTS) events.removeFirst()
	}

	@Synchronized
	fun recentEvents(): List<String> = events.toList()

	fun onPut(r: SurfaceStore.PutResult) {
		when (r) {
			SurfaceStore.PutResult.NEW -> cacheMisses.incrementAndGet()
			SurfaceStore.PutResult.CHANGED -> { cacheChanged.incrementAndGet(); changedThisVisit.incrementAndGet() }
			SurfaceStore.PutResult.SAME -> cacheHits.incrementAndGet()
			SurfaceStore.PutResult.REFINED -> { cacheHits.incrementAndGet(); refined.incrementAndGet() }
		}
	}

	fun onRegionLoaded(name: String, chunks: Int, ms: Double) {
		regionLoads.incrementAndGet()
		if (ms > regionLoadMsMax) regionLoadMsMax = ms
		lastRegionLoad = "%s %d ch %.1f ms".format(name, chunks, ms)
		event("disk load region $name: $chunks chunks in ${"%.1f".format(ms)} ms")
	}

	fun onIslandLoaded(path: String, chunks: Int, regions: Int, ms: Double) {
		islandLoadMs = ms; islandLoadChunks = chunks; islandLoadRegions = regions
		event("disk load island $path: $chunks chunks, $regions regions in ${"%.1f".format(ms)} ms")
	}

	fun onSaved(name: String, chunks: Int, bytes: Long) {
		saves.incrementAndGet(); savedBytes.addAndGet(bytes)
		event("save region $name: $chunks chunks, $bytes B")
	}

	fun onMeshBuilt(region: String, ms: Double, faces: Int) {
		meshBuilds.incrementAndGet()
		meshLastMs = ms
		if (ms > meshMaxMs) meshMaxMs = ms
		meshTotalNs.addAndGet((ms * 1_000_000).toLong())
		event("mesh region $region: $faces faces in ${"%.2f".format(ms)} ms")
	}

	fun meshAvgMs(): Double = meshBuilds.get().let { if (it == 0) 0.0 else meshTotalNs.get() / 1_000_000.0 / it }

	fun onWorkerBusy(ns: Long) {
		workerBusyNs.addAndGet(ns)
		val now = System.nanoTime()
		val window = now - workerWindowStart
		if (window > 1_000_000_000L) {
			workerUsage = workerBusyNs.getAndSet(0).toDouble() / window
			workerWindowStart = now
		}
	}

	fun resetVisit(label: String) {
		islandLabel = label
		recordedThisVisit.set(0); changedThisVisit.set(0); recaptureForEdges.set(0)
		islandLoadMs = 0.0; islandLoadChunks = 0; islandLoadRegions = 0
		event("visit $label")
	}

	fun lines(): List<String> = listOf(
		"Apex Far Terrain [debug] island: $islandLabel",
		"disk: island %.0f ms (%d ch / %d reg), regions loaded %d, max %.1f ms, last %s".format(islandLoadMs, islandLoadChunks, islandLoadRegions, regionLoads.get(), regionLoadMsMax, lastRegionLoad),
		"record: %d this visit, %d pending, %d re-recorded (changed), %d edge re-captures".format(recordedThisVisit.get(), pendingCaptures.get(), changedThisVisit.get(), recaptureForEdges.get()),
		"mesh: last %.2f ms, avg %.2f ms, max %.2f ms, %d built, queue %d, worker %.0f%%".format(meshLastMs, meshAvgMs(), meshMaxMs, meshBuilds.get(), meshQueue.get(), workerUsage * 100),
		"draw [%s]: %d calls%s (%d ranges), %d regions, %d faces (%d culled facing away, %d off screen in %d chunks), %d vertices, %d chunks in hand-off wait".format(drawPath, drawCalls, if (multiDraw) " multi-draw" else "", drawRuns, regionsDrawn, facesDrawn, facesCulled, facesOffScreen, chunksOffScreen, verticesDrawn, handoffHeld),
		"hand-off columns: %d game only, %d both; far draws %d not game's, %d missing neighbour, %d not built, %d waiting, %d not drawn by the game".format(handoffGame, handoffBoth, handoffNotOwned, handoffNoNeighbours, handoffNotBuilt, handoffWaiting, handoffNotDrawn),
		"hand-off source: %s, %d sections drawn; %d columns drawn twice".format(drawnMode, drawnSections, handoffDoubleDrawn),
		"game area (measured): loaded %d chunks out, loaded disc %d, drawn reach %d, radius %d".format(gameAreaExtent, gameAreaLoadedRadius, gameAreaDrawnReach, gameAreaRadius),
		"hand-off cpu: %.3f ms last frame, %.3f ms max (1 s)".format(handoffNs / 1e6, handoffMaxNs / 1e6),
		"memory: VRAM %s in %d regions, RAM %s".format(mib(vramBytes), regionsResident, mib(ramBytes)),
		"shared buffer [%s]: %s of %s used, %d holes (%.0f%% of free space scattered), %d growths, %d own buffers; frame data %d written, %d reused".format(drawMode, mib(arenaUsedBytes), mib(arenaBytes), arenaHoles, arenaFragmentation * 100, arenaGrowths, arenaRefused, frameDataWrites, frameDataReuses),
		"upload: %d regions (%s) last frame, %d waiting".format(uploadsLastFrame, mib(uploadBytesLastFrame), uploadsWaiting),
		"cache: %d hits (%d refined), %d misses, %d changed; saves %d (%s)".format(cacheHits.get(), refined.get(), cacheMisses.get(), cacheChanged.get(), saves.get(), mib(savedBytes.get())),
		"particles: %d far animations, %d particles last tick".format(particleRuns, particlesSpawned),
	)

	fun dump(header: List<String> = emptyList()): String = buildString {
		header.forEach { appendLine(it) }
		appendLine("== current ==")
		lines().forEach { appendLine(it) }
		appendLine("== recent events (oldest first) ==")
		recentEvents().forEach { appendLine(it) }
	}

	companion object {
		const val MAX_EVENTS = 2000
		fun mib(b: Long): String = "%.1f MiB".format(b / 1048576.0)
	}
}
