// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.ConnectCore
import dev.ashtonite365.apex.features.render.connected.FarConnect
import dev.ashtonite365.apex.features.render.connected.FarPlain
import dev.ashtonite365.apex.features.render.connected.StateLookup
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import java.lang.reflect.Proxy
import java.util.function.Predicate
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import dev.ashtonite365.apex.features.render.connected.ConnectedDiag
import dev.ashtonite365.apex.features.render.connected.ConnectedModel
import dev.ashtonite365.apex.features.render.connected.ConnectedTextures
import dev.ashtonite365.apex.features.render.connected.ConnectedTilesSource
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import net.minecraft.client.model.geom.EntityModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.SpriteLoader
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList
import net.minecraft.client.renderer.texture.atlas.SpriteSources
import net.minecraft.client.resources.model.BlockStateModelLoader
import net.minecraft.client.resources.model.ClientItemInfoLoader
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.client.resources.model.ModelManager
import net.minecraft.client.resources.model.ResolvedModel
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.client.resources.model.sprite.MaterialBaker
import net.minecraft.client.resources.model.sprite.SpriteGetter
import net.minecraft.core.Direction
import net.minecraft.data.AtlasIds
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class ConnectedLoadPathTest {
	companion object {
		private val direct = Executor { it.run() }

		private fun pack(id: String, root: Path): PackResources =
			PathPackResources(PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty()), root)

		private val roots: List<Path> by lazy {
			val urls = ConnectedLoadPathTest::class.java.classLoader.getResources("assets/minecraft/atlases/blocks.json").toList()
			val paths = urls.map { url ->
				val s = url.toString()
				if (s.startsWith("jar:")) {
					val jar = URI.create(s.substringBefore("!/"))
					val fs = runCatching { FileSystems.newFileSystem(jar, emptyMap<String, Any>()) }.getOrElse { FileSystems.getFileSystem(jar) }
					fs.getPath("/")
				} else Path.of(url.toURI()).parent.parent.parent.parent
			}.distinct()
			val (vanilla, mods) = paths.partition { Files.exists(it.resolve("assets/minecraft/blockstates/glass.json")) }
			require(vanilla.isNotEmpty()) { "vanilla client assets not on the test classpath: $urls" }
			require(mods.any { Files.exists(it.resolve("assets/apex")) }) { "Apex resources not on the test classpath: $urls" }
			vanilla.take(1) + mods.filter { Files.exists(it.resolve("assets/apex")) }
		}

		fun manager(extra: List<PackResources> = emptyList()): ResourceManager {

			SpriteSources.bootstrap()
			ConnectedTextures.registerSpriteSource()
			val packs = ArrayList<PackResources>()
			packs += pack("vanilla", roots[0])
			for ((i, r) in roots.drop(1).withIndex()) {
				packs += pack("apex$i", r)
				val builtin = r.resolve("resourcepacks/hypixel_skyblock")
				if (Files.isDirectory(builtin)) packs += pack("apex_hypixel_skyblock", builtin)
			}
			packs += extra
			return MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs)
		}

		fun stitch(rm: ResourceManager): SpriteLoader.Preparations {
			val loader = SpriteResourceLoader.create(emptySet())
			val contents = SpriteSourceList.load(rm, AtlasIds.BLOCKS).list(rm).mapNotNull { it.get(loader) } + MissingTextureAtlasSprite.create()
			val w = contents.sumOf { it.width() }
			val h = contents.maxOf { it.height() }
			var x = 0
			val regions = HashMap<Identifier, TextureAtlasSprite>()
			for (c in contents) {
				regions[c.name()] = object : TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, c, w, h, x, 0, 0) {}
				x += c.width()
			}
			val missing = regions.getValue(MissingTextureAtlasSprite.getLocation())
			return SpriteLoader.Preparations(w, h, 0, missing, regions, java.util.concurrent.CompletableFuture.completedFuture(null))
		}

		private val vanillaRm by lazy { ConnectedTextures.registerSpriteSource(); manager() }
		private val vanillaAtlas by lazy { stitch(vanillaRm) }

		private val baked: Map<BlockState, BlockStateModel> by lazy {
			val rm = vanillaRm
			val loadModels = ModelManager::class.java.getDeclaredMethod("loadBlockModels", ResourceManager::class.java, Executor::class.java).apply { isAccessible = true }
			@Suppress("UNCHECKED_CAST")
			val unbaked = (loadModels.invoke(null, rm, direct) as java.util.concurrent.CompletableFuture<Map<Identifier, UnbakedModel>>).join()
			val states = BlockStateModelLoader.loadBlockStates(rm, direct).join().models().filterKeys { ConnectRules.of(it.block) != null }
			val discover = ModelManager::class.java.declaredMethods.first { it.name == "discoverModelDependencies" }.apply { isAccessible = true }
			val resolved = discover.invoke(null, unbaked, BlockStateModelLoader.LoadedModels(states), ClientItemInfoLoader.LoadedClientInfos(emptyMap()))
			val rc = resolved.javaClass
			@Suppress("UNCHECKED_CAST")
			val models = rc.getMethod("models").apply { isAccessible = true }.invoke(resolved) as Map<Identifier, ResolvedModel>
			val missing = rc.getMethod("missing").apply { isAccessible = true }.invoke(resolved) as ResolvedModel
			val atlas = vanillaAtlas

			val ctor = ModelBakery::class.java.constructors.first { it.parameterCount == 7 }
			val bakery = ctor.newInstance(EntityModelSet.EMPTY, SpriteGetter { atlas.missing() }, null, states, emptyMap<Identifier, Any>(), models, missing) as ModelBakery
			bakery.bakeModels(MaterialBaker(atlas, atlas), direct).join().blockStateModels()
		}

		private fun png(path: Path, size: Int) {
			val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
			for (y in 0 until size) for (x in 0 until size) {
				val edge = x == 0 || y == 0 || x == size - 1 || y == size - 1
				img.setRGB(x, y, if (edge) 0xFFFFFFFF.toInt() else 0x20A0C0FF)
			}
			Files.createDirectories(path.parent)
			ImageIO.write(img, "png", path.toFile())
		}
	}

	@Test
	fun togglesDefaultOn() {
		ConnectedTextures.TConfig.explicitDefaultLoad()
		assertTrue(ConnectedTextures.TConfig.connectedGlass)
		assertTrue(ConnectedTextures.TConfig.connectedBlocks)
	}

	@Test
	fun connectedFarTerrainDefaultsOffAndFarTerrainThenStaysPlain() {
		ConnectedTextures.TConfig.explicitDefaultLoad()
		assertFalse(ConnectedTextures.TConfig.connectedFarTerrain)
		ConnectedTextures.sync()
		assertFalse(ConnectedTextures.farEnabled())
		assertEquals(0, ConnectedTextures.farSignature())

		assertNull(FarConnect.forBuild(StateLookup { _, _, _ -> throw AssertionError("neighbour read with the setting off") }) { error("model lookup") })
	}

	@Test
	fun farSignatureFollowsTheFamilies() {
		try {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = false)
			assertEquals(0, ConnectedTextures.farSignature())
			ConnectedTextures.setForTest(glass = true, blocks = true, far = true)
			val both = ConnectedTextures.farSignature()
			ConnectedTextures.setForTest(glass = true, blocks = false, far = true)
			val glassOnly = ConnectedTextures.farSignature()
			assertTrue(both != 0 && glassOnly != 0 && both != glassOnly)
		} finally {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = false)
		}
	}

	private fun connectedGold(): Pair<BlockState, ConnectedModel> {
		val atlas = vanillaAtlas
		val gold = net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()
		ConnectedDiag.reloadStarted()
		val m = ConnectedTextures.Wrapper(MaterialBaker(atlas, atlas)).wrap(baked.getValue(gold), gold) as ConnectedModel
		return gold to m
	}

	private class RecordedLookup(val inner: MapLookup, var cx: Int = 0, var cy: Int = 0, var cz: Int = 0) : StateLookup {
		var reads = 0
		override fun state(x: Int, y: Int, z: Int): BlockState {
			reads++
			assertTrue(kotlin.math.abs(x - cx) <= 2 && kotlin.math.abs(y - cy) <= 2 && kotlin.math.abs(z - cz) <= 2, "far read $x,$y,$z too far from $cx,$cy,$cz")
			return inner.state(x, y, z)
		}
	}

	@Test
	fun connectedFarTerrainOnRetilesPlainQuadsFromRecordedBlocks() {
		val (gold, model) = connectedGold()
		val plain = baked.getValue(gold)
		try {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = true)

			val rec = RecordedLookup(MapLookup().apply { set(0, 64, 0, gold); set(1, 64, 0, gold); set(10, 64, 10, gold) }, cy = 64)
			val far = FarConnect.forBuild(rec) { if (it === gold) model else null }
			assertNotNull(far)
			far!!
			far.begin(gold, 0, 64, 0)
			val north = quads(plain, Direction.NORTH).single()
			val out = far.remap(north)
			assertNotNull(out)
			assertNotSame(north, out, "north face of the west block did not join east")
			val tiles = model.textures.single()
			val uv = xyzuv(out!!)
			val near = xyzuv(north)
			val nearTile = model.nearRemap(cacheAt(rec.inner, 0, 64, 0), gold, Direction.NORTH, near)
			assertTrue(nearTile > 0)
			for (i in 0 until 4) {
				assertEquals(near[i * 5 + 3], uv[i * 5 + 3], "far u = near u at vertex $i")
				assertEquals(near[i * 5 + 4], uv[i * 5 + 4], "far v = near v at vertex $i")
				assertEquals(north.position(i), out.position(i))
			}
			assertSame(north.materialInfo(), out.materialInfo())
			assertTrue(tiles.sprites[nearTile]!!.contents().name().toString().startsWith("apex:block/connected/minecraft/gold_block/"))
			assertTrue(rec.reads > 0)

			val upQuad = quads(plain, Direction.UP).single()
			assertNotSame(upQuad, far.remap(upQuad), "top face joins east too")

			rec.cx = 10; rec.cz = 10
			far.begin(gold, 10, 64, 10)
			assertSame(north, far.remap(north))

			ConnectedTextures.setForTest(glass = true, blocks = false, far = true)
			rec.cx = 0; rec.cz = 0
			val far2 = FarConnect.forBuild(rec) { if (it === gold) model else null }!!
			val before = rec.reads
			far2.begin(gold, 0, 64, 0)
			assertSame(north, far2.remap(north))
			assertEquals(before, rec.reads, "neighbours read for a family that is off")
		} finally {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = false)
		}
	}

	@Test
	fun connectedFarTerrainIsSafeOnManyMeshThreadsAtOnce() {
		val (gold, model) = connectedGold()
		val plain = baked.getValue(gold)
		val faces = Direction.entries.map { quads(plain, it).single() }
		try {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = true)
			val world = MapLookup()
			for (x in 0 until 8) for (z in 0 until 8) world.set(x, 64, z, gold)
			fun run(): List<FloatArray> {
				val far = FarConnect.forBuild(world) { if (it === gold) model else null }!!
				val out = ArrayList<FloatArray>()
				for (x in 0 until 8) for (z in 0 until 8) {
					far.begin(gold, x, 64, z)
					for (q in faces) out.add(xyzuv(far.remap(q) ?: q))
				}
				return out
			}
			val expected = run()
			val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
			try {
				val results = (0 until 8).map { pool.submit<List<FloatArray>> { (0 until 20).map { run() }.last() } }.map { it.get() }
				for (r in results) for (i in expected.indices) assertArrayEquals(expected[i], r[i], 0f, "quad $i")
			} finally {
				pool.shutdownNow()
			}
		} finally {
			ConnectedTextures.setForTest(glass = true, blocks = true, far = false)
		}
	}

	@Test
	fun atlasDefinitionStacksOurSourceWithVanilla() {
		val list = SpriteSourceList.load(vanillaRm, AtlasIds.BLOCKS)
		val field = SpriteSourceList::class.java.getDeclaredField("sources").apply { isAccessible = true }
		val sources = field.get(list) as List<*>
		assertTrue(sources.any { it === ConnectedTilesSource }, "apex:connected_tiles is not in the stacked block atlas definition: $sources")
		assertTrue(sources.size > 1, "vanilla sources lost: $sources")
	}

	@Test
	fun realStitchHasEveryTileSprite() {
		val atlas = vanillaAtlas
		var expected = 0
		for ((tex, plane) in ConnectRules.textures) {
			assertTrue(atlas.regions().containsKey(Identifier.parse(tex)), "base $tex missing")
			for (t in ConnectTiles.reachable(plane)) {
				if (t == 0) continue
				expected++
				val id = ConnectedTilesSource.tileId(tex, t)
				assertTrue(atlas.regions().containsKey(id), "tile sprite $id was not stitched")
			}
		}
		assertTrue(expected > 0)
		assertEquals(0, ConnectedDiag.spritesFailed.get(), "tile sprites failed")
		assertEquals(ConnectRules.textures.size, ConnectedTilesSource.generated.size)
	}

	@Test
	fun realBakeThenWrapGivesConnectedModelsForEveryRuleState() {
		val atlas = vanillaAtlas
		val models = baked
		val ruleStates = models.keys.filter { ConnectRules.of(it.block) != null }
		assertTrue(ruleStates.size >= ConnectRules.blockIds.size, "baked ${ruleStates.size} rule states")
		ConnectedDiag.reloadStarted()
		val w = ConnectedTextures.Wrapper(MaterialBaker(atlas, atlas))
		val wrapped = ruleStates.associateWith { w.wrap(models.getValue(it), it) }
		val notWrapped = wrapped.filterValues { it !is ConnectedModel }.keys
		assertTrue(notWrapped.isEmpty(), "not wrapped: ${notWrapped.take(5)}; problems ${ConnectedDiag.problems}")
		assertEquals(ConnectedDiag.texturesResolved.get(), ConnectRules.textures.size, "problems ${ConnectedDiag.problems}")
		assertTrue(ConnectedDiag.problems.isEmpty(), "problems ${ConnectedDiag.problems}")

		val glass = ruleStates.first { it.block == net.minecraft.world.level.block.Blocks.GLASS }
		val model = wrapped.getValue(glass) as ConnectedModel
		val lookup = MapLookup().apply { set(0, 64, 0, glass); set(1, 64, 0, glass) }
		val parts = ArrayList<BlockStateModelPart>()
		model.collectParts(RandomSource.create(42L), parts)
		val north = parts.flatMap { it.getQuads(Direction.NORTH) }.single()
		val plainUv = xyzuv(north)
		val tiles = model.textureAt((0 until 4).sumOf { plainUv[it * 5 + 3].toDouble() }.toFloat() / 4, (0 until 4).sumOf { plainUv[it * 5 + 4].toDouble() }.toFloat() / 4)
			?: error("glass north face is not a connecting texture")
		val xyzuv = xyzuv(north)
		val tile = model.nearRemap(cacheAt(lookup, 0, 64, 0), glass, Direction.NORTH, xyzuv)
		assertTrue(tile > 0, "north face did not join east: $tile")
		val sprite = tiles.sprites[tile]?.contents()?.name()?.toString() ?: error("tile $tile has no sprite")
		assertTrue(sprite.startsWith("apex:block/connected/minecraft/glass/"), "north face tile sprite $sprite")
		val r = tiles.rects
		for (i in 0 until 4) {
			val u = xyzuv[i * 5 + 3]; val v = xyzuv[i * 5 + 4]
			assertTrue(u >= r[tile * 4] - 1e-6f && u <= r[tile * 4 + 2] + 1e-6f && v >= r[tile * 4 + 1] - 1e-6f && v <= r[tile * 4 + 3] + 1e-6f, "vertex $i uv $u,$v not in tile $tile")
		}

		val alone = MapLookup().apply { set(0, 64, 0, glass) }
		assertEquals(ConnectCore.KEEP, model.nearRemap(cacheAt(alone, 0, 64, 0), glass, Direction.NORTH, xyzuv(north)))
	}

	private fun xyzuv(q: BakedQuad) = FloatArray(20).also { a ->
		for (i in 0 until 4) {
			val p = q.position(i)
			a[i * 5] = p.x(); a[i * 5 + 1] = p.y(); a[i * 5 + 2] = p.z()
			a[i * 5 + 3] = UVPair.unpackU(q.packedUV(i)); a[i * 5 + 4] = UVPair.unpackV(q.packedUV(i))
		}
	}

	private fun quads(m: BlockStateModel, d: Direction): List<BakedQuad> {
		val parts = ArrayList<BlockStateModelPart>()
		m.collectParts(RandomSource.create(42L), parts)
		return parts.flatMap { it.getQuads(d) }
	}

	private fun connectedGlass(): Pair<BlockState, ConnectedModel> {
		val atlas = vanillaAtlas
		val glass = net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()
		ConnectedDiag.reloadStarted()
		val m = ConnectedTextures.Wrapper(MaterialBaker(atlas, atlas)).wrap(baked.getValue(glass), glass) as ConnectedModel
		return glass to m
	}

	private fun noReads(): BlockAndTintGetter = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BlockAndTintGetter::class.java)) { _, method, _ ->
		throw AssertionError("far terrain read the world: ${method.name}")
	} as BlockAndTintGetter

	private fun emitter(pushed: MutableList<Any?>): QuadEmitter = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(QuadEmitter::class.java)) { proxy, method, args ->
		when (method.name) {
			"pushTransform" -> { pushed.add(args?.get(0)); null }
			"popTransform" -> null
			"hashCode" -> System.identityHashCode(proxy)
			"equals" -> proxy === args?.get(0)
			"toString" -> "test emitter"
			else -> throw AssertionError("unexpected emitter call ${method.name}")
		}
	} as QuadEmitter

	@Test
	fun farTerrainMeshesTwoAdjacentGlassBlocksWithThePlainModel() {
		val (glass, model) = connectedGlass()
		val plain = baked.getValue(glass)

		val far = FarPlain.model(model)
		assertSame(plain, far)
		assertFalse(far is ConnectedModel)

		val nearNorth = quads(model, Direction.NORTH).single()
		for (d in Direction.entries) {
			val a = quads(far, d)
			val b = quads(plain, d)
			assertEquals(b.size, a.size, "$d")
			for (i in a.indices) {
				assertSame(b[i], a[i], "$d quad $i")
				assertEquals("minecraft:block/glass", a[i].materialInfo().sprite().contents().name().toString())
			}
		}

		val lookup = MapLookup().apply { set(0, 64, 0, glass); set(1, 64, 0, glass) }
		val near = xyzuv(nearNorth)
		assertTrue(model.nearRemap(cacheAt(lookup, 0, 64, 0), glass, Direction.NORTH, near) > 0)
		val farUv = xyzuv(quads(far, Direction.NORTH).single())
		assertFalse((0 until 4).all { farUv[it * 5 + 3] == near[it * 5 + 3] && farUv[it * 5 + 4] == near[it * 5 + 4] }, "far UVs were remapped")
	}

	@Test
	fun farTerrainLooksThroughOtherWrappersAndNeverConnects() {
		val (glass, model) = connectedGlass()
		val plain = baked.getValue(glass)
		var innerEmits = 0

		val counting = object : WrapperBlockStateModel(plain) {
			override fun emitQuads(emitter: QuadEmitter, level: BlockAndTintGetter, pos: BlockPos, state: BlockState, random: RandomSource, cullTest: Predicate<Direction?>) {
				innerEmits++
			}
		}
		val ours = ConnectedModel(counting, model.rule, model.textures)
		val foreign = object : WrapperBlockStateModel(ours) {}

		assertSame(foreign, FarPlain.model(foreign))
		assertSame(counting, FarPlain.model(ours))
		val pushed = ArrayList<Any?>()
		val pos = BlockPos(0, 64, 0)
		assertFalse(FarPlain.active())
		FarPlain.enter()
		try {
			assertTrue(FarPlain.active())
			foreign.emitQuads(emitter(pushed), noReads(), pos, glass, RandomSource.create(1L), { false })

			assertEquals(plain.createGeometryKey(noReads(), pos, glass, RandomSource.create(1L)), ours.createGeometryKey(noReads(), pos, glass, RandomSource.create(1L)))
		} finally {
			FarPlain.exit()
		}
		assertFalse(FarPlain.active())
		assertEquals(1, innerEmits, "plain model not emitted")
		assertTrue(pushed.isEmpty(), "far terrain pushed a connected-texture transform: $pushed")
		assertEquals(null, ours.createGeometryKey(noReads(), pos, glass, RandomSource.create(1L)))

		val level = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BlockAndTintGetter::class.java)) { _, method, args ->
			if (method.name == "getBlockState") {
				val p = args!![0] as BlockPos
				if (p.y == 64 && p.z == 0 && (p.x == 0 || p.x == 1)) glass else net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
			} else throw AssertionError("unexpected ${method.name}")
		} as BlockAndTintGetter
		assertTrue(ConnectedTextures.enabled(model.rule.family), "connected glass is off")
		foreign.emitQuads(emitter(pushed), level, pos, glass, RandomSource.create(1L), { false })
		assertEquals(2, innerEmits)
		assertEquals(1, pushed.size, "near terrain did not push its transform")
	}

	@Test
	fun serverPackWithItsOwnAtlasFileAndBiggerGlassStillGetsTiles() {
		val dir = Files.createTempDirectory("apex-connglass-pack")
		try {
			Files.createDirectories(dir.resolve("assets/minecraft/atlases"))
			Files.writeString(dir.resolve("assets/minecraft/atlases/blocks.json"), """{"sources":[{"type":"minecraft:directory","source":"block","prefix":"block/"}]}""")
			png(dir.resolve("assets/minecraft/textures/block/glass.png"), 32)
			Files.writeString(dir.resolve("pack.mcmeta"), """{"pack":{"description":"test","pack_format":1}}""")
			val rm = manager(listOf(pack("server", dir)))
			val atlas = stitch(rm)
			val tile = atlas.regions()[ConnectedTilesSource.tileId("minecraft:block/glass", 1)] ?: error("no glass tile with a server pack")
			assertEquals(32, tile.contents().width())
		} finally {
			dir.toFile().deleteRecursively()
		}
	}
}
