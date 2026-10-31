// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuiltWait
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnMode
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFarDistance
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFog
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceGameArea
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffDecider
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGrid
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRenderer
import dev.ashtonite365.apex.init.MixinPlugin
import java.io.DataInputStream
import java.io.File
import net.minecraft.client.renderer.fog.FogData
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceRenderDistanceIndependenceTest {

	private class World(val server: Int, val drawnDisc: Int) {
		fun loaded(x: Int, z: Int) = Math.abs(x) <= server && Math.abs(z) <= server
		fun drawn(x: Int, z: Int) = SurfaceHandoff.inDisc(0, 0, drawnDisc, x, z) && SurfaceHandoff.neighboursLoaded(x, z, ::loaded)
	}

	private fun decisions(world: World, mode: SurfaceDrawnMode, @Suppress("UNUSED_PARAMETER") renderDistance: Int, frames: Int = 30): Triple<IntArray, Int, Int> {
		val drawn = SurfaceDrawnList()
		val handoff = SurfaceHandoffDecider(SurfaceHandoffTimer(), SurfaceBuiltWait(), drawn)
		val area = SurfaceGameArea()
		val level = Any()
		val box = 40
		val out = IntArray((2 * box + 1) * (2 * box + 1))
		var extent = 0; var radius = 0
		for (f in 0 until frames) {
			val now = f * 16L
			drawn.begin(now, 8.0, 70.0, 8.0)
			if (mode != SurfaceDrawnMode.PREDICTION) for (x in -box..box) for (z in -box..box) if (world.drawn(x, z)) for (y in 3..5) drawn.drawn(x, y, z)
			area.refresh(level, 0, 0, world::loaded)
			area.drawnFrame(drawn.frameReach(), now)
			val grid = SurfaceHandoffGrid(0, 0, area.extent)
			val game = object : SurfaceHandoffDecider.Game {
				override fun loaded(x: Int, z: Int) = world.loaded(x, z)
				override fun inView(x: Int, z: Int) = true
				override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {

					val built = world.drawn(x, z)
					if (settled) { if (mode == SurfaceDrawnMode.PREDICTION) p.present = built } else { p.topBuilt = built; p.allBuilt = built }
				}
			}
			val frame = handoff.Frame(mode, 8.0, 70.0, 8.0, now, Double.POSITIVE_INFINITY, area.radius(now), null, mode != SurfaceDrawnMode.RENDERER, game)
			var i = 0
			for (x in -box..box) for (z in -box..box) {
				out[i++] = if (grid.inside(x, z)) frame.decide(x, z, true, 48, 96, 3, 5) else SurfaceHandoffGrid.OUTSIDE
			}
			extent = area.extent; radius = frame.radius
		}
		return Triple(out, extent, radius)
	}

	@Test
	fun changingTheRenderDistanceAloneChangesNoDecision() {
		for (mode in SurfaceDrawnMode.entries) {
			val world = World(server = 14, drawnDisc = 10)
			val base = decisions(world, mode, renderDistance = 10)
			for (rd in 2..32) {
				val other = decisions(world, mode, renderDistance = rd)
				assertArrayEquals(base.first, other.first, "$mode: rd $rd")
				assertEquals(base.second, other.second, "$mode: hand-off box, rd $rd")
				assertEquals(base.third, other.third, "$mode: measured radius, rd $rd")
			}
		}
	}

	@Test
	fun everyGameRenderDistanceFrom2To32LeavesFarTerrainsDrawSetOutsideTheGamesChunksUnchanged() {

		for (mode in SurfaceDrawnMode.entries) for (rd in 2..32) {
			val world = World(server = 34, drawnDisc = rd)
			val (d, extent, _) = decisions(world, mode, renderDistance = rd)
			assertEquals(34, extent, "$mode rd $rd: hand-off box from the loaded chunks only")
			val box = 40
			var i = 0
			for (x in -box..box) for (z in -box..box) {
				val v = d[i++]
				if (!world.drawn(x, z)) assertTrue(v and SurfaceHandoffOrder.APEX != 0, "$mode rd $rd: far terrain draws $x,$z")
				else if (mode == SurfaceDrawnMode.VANILLA) assertEquals(SurfaceHandoffOrder.GAME, v, "$mode rd $rd: the game's own $x,$z")
				else assertTrue(v and SurfaceHandoffOrder.GAME != 0, "$mode rd $rd: the game keeps $x,$z")
			}
		}

		val a = SurfaceGameArea(); val level = Any()
		for (x in -5..5) for (z in -5..5) a.chunkLoaded(level, x, z)
		val seed = a.loadedChunks(level).toHashSet()
		for (rd in 2..32) {
			assertEquals(seed, a.loadedChunks(level).toHashSet())
			assertEquals(128, SurfaceFarDistance.effective(SurfaceFarDistance.setting(false, 128), 0))
			assertEquals(128 * 16 * 1.5f + 512f, SurfaceFarDistance.farClip(false, 128, 0))
		}
	}

	@Test
	fun ownershipFollowsTheDrawnListAndTheLoadedChunks() {

		val small = decisions(World(14, 6), SurfaceDrawnMode.VANILLA, 0).first
		val large = decisions(World(14, 12), SurfaceDrawnMode.VANILLA, 0).first
		val box = 40
		var i = 0
		var gameSmall = 0; var gameLarge = 0
		for (x in -box..box) for (z in -box..box) {
			val s = small[i]; val l = large[i]; i++
			assertEquals(World(14, 6).drawn(x, z), s == SurfaceHandoffOrder.GAME, "disc 6 at $x,$z")
			assertEquals(World(14, 12).drawn(x, z), l == SurfaceHandoffOrder.GAME, "disc 12 at $x,$z")
			if (s == SurfaceHandoffOrder.GAME) gameSmall++
			if (l == SurfaceHandoffOrder.GAME) gameLarge++
		}
		assertTrue(gameLarge > gameSmall * 3, "$gameSmall / $gameLarge")

		assertEquals(14, decisions(World(14, 6), SurfaceDrawnMode.VANILLA, 0).second)
		assertEquals(20, decisions(World(20, 6), SurfaceDrawnMode.VANILLA, 0).second)
	}

	@Test
	fun theGameStopsDrawingAColumnStillLoadedSoFarTerrainTakesItBackThatFrame() {
		val drawn = SurfaceDrawnList()
		val handoff = SurfaceHandoffDecider(SurfaceHandoffTimer(), SurfaceBuiltWait(), drawn)
		var drawing = true
		val game = object : SurfaceHandoffDecider.Game {
			override fun loaded(x: Int, z: Int) = true
			override fun inView(x: Int, z: Int) = true
			override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {}
		}
		var shownAt = -1L
		for (f in 0 until 40) {
			val now = f * 16L
			if (f == 30) drawing = false
			drawn.begin(now, 8.0, 70.0, 8.0)
			if (drawing) for (y in 3..5) drawn.drawn(5, y, 0)
			val frame = handoff.Frame(SurfaceDrawnMode.VANILLA, 8.0, 70.0, 8.0, now, Double.POSITIVE_INFINITY, 0, null, true, game)
			val d = frame.decide(5, 0, true, 48, 96, 3, 5)
			if (frame.lastShown && shownAt < 0) shownAt = now
			if (f == 30) assertTrue(frame.lastTakenBack, "taken back the frame the game stopped drawing it")
			if (f > 30) assertFalse(frame.lastShown, "the next hand-off starts over")
			if (f >= 30) assertEquals(SurfaceHandoffOrder.APEX, d, "frame $f: the game draws nothing there, far terrain does")
			else if (frame.lastShown) assertEquals(SurfaceHandoffOrder.GAME, d, "frame $f")
		}
		assertTrue(shownAt in SurfaceHandoffTimer.HOLD_MS..SurfaceHandoffTimer.HOLD_MS + 32, "the 100 ms hold stays ($shownAt)")
	}

	@Test
	fun gameAreaIsMeasuredFromLoadedChunksAndDrawnReach() {
		val a = SurfaceGameArea()
		val level = Any()
		val loaded = HashSet<Long>()
		fun load(x: Int, z: Int) { loaded.add(idx(x, z)); a.chunkLoaded(level, x, z) }
		for (x in -9..9) for (z in -9..9) load(x, z)
		load(40, 3)
		val isLoaded = { x: Int, z: Int -> idx(x, z) in loaded }
		a.refresh(level, 0, 0, isLoaded)
		assertEquals(40, a.extent)
		assertEquals(9, a.loadedRadius, "rounded disc of 9 inside the square of 9 (the nearest unloaded chunk is 10 out)")
		assertEquals(9, a.radius(0), "no drawn reach yet: the loaded disc")
		a.drawnFrame(6, 0)
		assertEquals(6, a.radius(10))
		a.drawnFrame(20, 100)
		assertEquals(9, a.radius(120), "never more than the loaded disc")
		assertEquals(0, a.drawnReach(10_000), "forgotten after the window")

		assertEquals(loaded, a.loadedChunks(level).toHashSet())

		loaded.remove(idx(40, 3))
		a.chunkUnloaded(level, 40, 3)
		a.refresh(level, 0, 0, isLoaded)
		assertEquals(9, a.extent)
		val other = Any()
		a.refresh(other, 0, 0) { _, _ -> false }
		assertEquals(0, a.extent)
		assertEquals(0, a.loadedChunks(level).size)
	}

	@Test
	fun drawnReachIsTheSmallestRadiusCoveringWhatWasDrawn() {
		val l = SurfaceDrawnList()
		l.begin(0, 8.0, 70.0, 8.0)
		assertEquals(0, l.frameReach())

		for (x in -8..8) for (z in -8..8)
			if (SurfaceHandoff.inDisc(0, 0, 7, x, z) && SurfaceHandoffGeometry.nearestSq(x, z, 8.0, 8.0) < 112.0 * 112.0) l.drawn(x, 4, z)
		assertEquals(7, l.frameReach())
		l.begin(16)
		l.drawn(30, 4, 0)
		assertEquals(0, l.frameReach())
		assertTrue(l.columnDrawnThisFrame(30, 0))
		assertFalse(l.columnDrawnThisFrame(0, 0))
		assertTrue(l.columnDrawnRecently(0, 0, 16))
		assertFalse(l.columnDrawnRecently(0, 0, 200))
	}

	@Test
	fun drawRangeClipAndFogComeOnlyFromFarTerrainsOwnSettings() {

		assertEquals(SurfaceRenderer.UNLIMITED, SurfaceFarDistance.setting(true, 128))
		assertEquals(128, SurfaceFarDistance.setting(false, 128))
		assertEquals(128, SurfaceFarDistance.effective(128, 0))
		assertEquals(16, SurfaceFarDistance.effective(SurfaceRenderer.UNLIMITED, 0), "unlimited, nothing resident")
		assertEquals(30 + 8, SurfaceFarDistance.effective(SurfaceRenderer.UNLIMITED, 900))
		assertEquals(128 * 16 * 1.5f + 512f, SurfaceFarDistance.farClip(false, 128, 999))
		assertEquals(38 * 16 * 1.5f + 512f, SurfaceFarDistance.farClip(true, 128, 38))
		assertEquals(38 * 16 * 0.8f, SurfaceFarDistance.fadeStart(38))
		assertEquals(38 * 16f, SurfaceFarDistance.fadeEnd(38))

		val fade = SurfaceFarDistance.fadeStart(128); val end = SurfaceFarDistance.fadeEnd(128)
		SurfaceFog.fadeStart = fade; SurfaceFog.fadeEnd = end
		try {
			var scale = Float.NaN
			for (rd in 2..32) {
				val fog = FogData()
				fog.renderDistanceEnd = rd * 16f; fog.renderDistanceStart = rd * 16f - minOf(64f, maxOf(4f, rd * 16f / 10f))
				fog.environmentalStart = 0f; fog.environmentalEnd = 1024f
				SurfaceFog.adjust(fog, true, null)
				assertEquals(fade, SurfaceFog.fadeStart); assertEquals(end, SurfaceFog.fadeEnd)
				assertEquals(fade, fog.renderDistanceStart, "rd $rd"); assertEquals(end, fog.renderDistanceEnd, "rd $rd")
				if (scale.isNaN()) scale = SurfaceFog.envScale else assertEquals(scale, SurfaceFog.envScale, "rd $rd")
				assertEquals(1024f, SurfaceFog.gameFogEnd, "the hand-off limit is the environmental fog end, not the render distance")
			}
		} finally {
			SurfaceFog.fadeStart = 0f; SurfaceFog.fadeEnd = 0f
			SurfaceFog.adjust(FogData(), false, null)
		}
	}

	private val forbiddenNames = setOf(
		"renderDistance", "getEffectiveRenderDistance", "effectiveRenderDistance", "serverRenderDistance",
		"setServerRenderDistance", "simulationDistance", "serverSimulationDistance", "serverChunkRadius",
		"updateViewRadius", "calculateStorageRange", "getViewDistance", "viewDistance", "lastViewDistance",
		"RENDER_DISTANCE_SHORT", "RENDER_DISTANCE_FAR", "RENDER_DISTANCE_REALLY_FAR", "RENDER_DISTANCE_EXTREME",
		"handleSetChunkCacheRadius", "handleSetSimulationDistance", "isInViewDistance", "getDepthFar",

		"renderDistanceStart", "renderDistanceEnd", "depthFar",
	)
	private val forbiddenClasses = listOf(
		"net/minecraft/server/level/ChunkTrackingView", "ClientboundSetChunkCacheRadiusPacket",
		"ClientboundSetSimulationDistancePacket", "net/minecraft/client/multiplayer/ClientChunkCache\$Storage",
	)

	private val allowed = mapOf(
		"SurfaceFog" to setOf("renderDistanceStart", "renderDistanceEnd"),
		"SurfaceFogMixin" to setOf("renderDistanceStart", "renderDistanceEnd"),
		"SurfaceCameraDepthFarMixin" to setOf("depthFar"),
		"SurfaceRenderer" to setOf("depthFar"),
	)

	private fun allowedFor(fileName: String): Set<String> = allowed[fileName.substringBefore('$').substringBefore('.')] ?: emptySet()

	private fun classRoot(c: Class<*>): File {
		val url = c.classLoader.getResource(c.name.replace('.', '/') + ".class") ?: error("no class file for ${c.name}")
		require(url.protocol == "file") { "expected a class directory, got $url" }
		var f = File(url.toURI())
		repeat(c.name.count { it == '.' } + 1) { f = f.parentFile }
		return f
	}

	private fun utf8Constants(bytes: ByteArray): List<String> {
		val inp = DataInputStream(bytes.inputStream())
		require(inp.readInt() == 0xCAFEBABE.toInt())
		inp.readUnsignedShort(); inp.readUnsignedShort()
		val count = inp.readUnsignedShort()
		val out = ArrayList<String>()
		var i = 1
		while (i < count) {
			when (val tag = inp.readUnsignedByte()) {
				1 -> out.add(inp.readUTF())
				3, 4 -> inp.skipBytes(4)
				5, 6 -> { inp.skipBytes(8); i++ }
				7, 8, 16, 19, 20 -> inp.skipBytes(2)
				9, 10, 11, 12, 17, 18 -> inp.skipBytes(4)
				15 -> inp.skipBytes(3)
				else -> error("bad constant tag $tag")
			}
			i++
		}
		return out
	}

	@Test
	fun noFarTerrainBytecodeReadsTheRenderDistance() {
		val kotlinRoot = classRoot(SurfaceDrawnList::class.java)
		val dirs = listOfNotNull(
			File(kotlinRoot, "dev/ashtonite365/apex/features/render/farterrain"),
			File(classRoot(MixinPlugin::class.java), "dev/ashtonite365/apex/mixins/surfacelod"),

			File(kotlinRoot.parentFile, "sodium/dev/ashtonite365/apex/compat/sodium").takeIf { it.isDirectory },
		)
		var checked = 0
		val hits = ArrayList<String>()
		var allowedSeen = 0
		for (dir in dirs) {
			assertTrue(dir.isDirectory, "$dir")
			dir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { f ->
				val ok = allowedFor(f.name)
				for (c in utf8Constants(f.readBytes())) {
					if (c in forbiddenNames) { if (c in ok) allowedSeen++ else hits.add("${f.relativeTo(dir)}: $c") }
					for (cls in forbiddenClasses) if (c.contains(cls)) hits.add("${f.relativeTo(dir)}: $c")
				}
				checked++
			}
		}
		assertTrue(checked > 50, "only $checked classes found")
		assertTrue(hits.isEmpty(), "render-distance reads in far-terrain bytecode:\n" + hits.joinToString("\n"))
		assertTrue(allowedSeen > 0, "the allow list is checked against real uses")
	}

	@Test
	fun noFarTerrainSourceReadsTheRenderDistance() {
		val roots = listOf("src/main/kotlin/features/render/farterrain", "src/main/java/dev/ashtonite365/apex/mixins/surfacelod", "src/compat/sodium")
			.map { File(projectRoot, it) }
		assertTrue(roots.all { it.isDirectory }, "source roots under $projectRoot")
		val word = Regex("[A-Za-z_][A-Za-z0-9_]*")
		val hits = ArrayList<String>()
		var files = 0
		for (root in roots) root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }.forEach { f ->
			files++
			val ok = allowedFor(f.name)
			f.readLines().forEachIndexed { n, line ->
				for (m in word.findAll(line)) if (m.value in forbiddenNames && m.value !in ok) hits.add("${f.path}:${n + 1}: ${m.value}")
				for (cls in forbiddenClasses) if (line.contains(cls.substringAfterLast('/').replace("\$", "."))) hits.add("${f.path}:${n + 1}: $cls")
			}
		}
		assertTrue(files > 30, "only $files source files")
		assertTrue(hits.isEmpty(), "render-distance reads in far-terrain source:\n" + hits.joinToString("\n"))
	}

	private val projectRoot: File by lazy {
		generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { File(it, "src/main/kotlin/features/render/farterrain").isDirectory }
	}

	private fun idx(x: Int, z: Int): Long = dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex.key(x, z)
}
