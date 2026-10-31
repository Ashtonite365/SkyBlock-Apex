// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.apex.ApexBlocks
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.SingleVariant
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class ApexBlocksTest {
	private var rebuilds = 0
	private val before = ApexBlocks.onTableChanged

	@BeforeEach
	fun setUp() {
		TexConfig.reset()
		ApexGolden.id
		rebuilds = 0
		ApexBlocks.onTableChanged = { rebuilds++ }
		ApexBlocks.useIsland(null)
		ApexBlocks.use(ApexBlocks.Loaded.EMPTY)
		rebuilds = 0
	}

	@AfterEach
	fun tearDown() {
		ApexBlocks.useIsland(null)
		ApexBlocks.use(ApexBlocks.Loaded.EMPTY)
		ApexBlocks.onTableChanged = before
		TexConfig.reset()
	}

	private fun id(s: String) = Identifier.parse(s)
	private val stone = Blocks.STONE.defaultBlockState()
	private val dirt = Blocks.DIRT.defaultBlockState()

	private fun pack(name: String, vararg files: Pair<String, String>): Path {
		val root = Path.of("texengine-test-packs").toAbsolutePath().resolve(name)
		if (Files.exists(root)) root.toFile().deleteRecursively()
		Files.createDirectories(root)
		Files.writeString(root.resolve("pack.mcmeta"), """{"pack":{"pack_format":1,"description":""}}""")
		for ((path, content) in files) {
			val f = root.resolve(path)
			Files.createDirectories(f.parent)
			Files.writeString(f, content)
		}
		return root
	}

	private fun parsed(vararg files: Pair<String, String>) =
		ApexBlocks.parse(FixturePacks.managerAt(pack("blocks-" + files.joinToString().hashCode().toUInt(),
			*files.map { (n, c) -> "assets/firmskyblock/overrides/blocks/$n" to c }.toTypedArray())))

	private fun model(): BlockStateModel = Fakes.alloc(SingleVariant::class.java)

	private fun fixtures() = ApexBlocks.parse(FixturePacks.manager("blocks"))

	@Test
	fun `G-BLK-4 config off - no island table, switching it back on needs an island update`() {
		ApexBlocks.use(ApexBlocks.build(fixtures(), null))
		TexConfig.with("block-overrides", false) {
			ApexBlocks.useIsland("hub")
			assertNull(ApexBlocks.entryFor(stone, BlockPos.ZERO))
			assertEquals(0, rebuilds)
		}
		ApexBlocks.useIsland("hub")
		assertEquals(id("firmskyblock:fancy_stone"), ApexBlocks.entryFor(stone, BlockPos.ZERO)?.replacement)
		assertEquals(1, rebuilds)
	}

	@Test
	fun `G-BLK-8 a rebuild is asked for only when the island's table changes`() {
		ApexBlocks.use(ApexBlocks.build(fixtures(), null))
		ApexBlocks.useIsland("hub")
		assertEquals(1, rebuilds)
		ApexBlocks.useIsland("hub")
		assertEquals(1, rebuilds, "same island again")
		ApexBlocks.useIsland("mining_3")
		assertEquals(2, rebuilds, "island without replacements")
		ApexBlocks.useIsland(null)
		assertEquals(2, rebuilds, "no replacements before or after")
		ApexBlocks.useIsland("hub")
		assertEquals(3, rebuilds)

		ApexBlocks.use(ApexBlocks.build(fixtures(), null))
		ApexBlocks.useIsland("hub")
		assertEquals(4, rebuilds)

		ApexBlocks.use(ApexBlocks.Loaded.EMPTY)
		ApexBlocks.useIsland("hub")
		assertEquals(5, rebuilds)
		assertNull(ApexBlocks.entryFor(stone, BlockPos.ZERO))
	}

	@Test
	fun `islands with the same entries share one table`() {
		val p = parsed("a.json" to """{"modes":["hub","dynamic"],"replacements":{"minecraft:stone":"x:s"}}""",
			"b.json" to """{"modes":["dynamic","hub"],"replacements":{"minecraft:dirt":"x:d"}}""",
			"c.json" to """{"modes":["winter"],"replacements":{"minecraft:dirt":"x:d"}}""")
		val loaded = ApexBlocks.build(p, null)
		assertSame(loaded.byMode["hub"], loaded.byMode["dynamic"])
		assertTrue(loaded.byMode["hub"] !== loaded.byMode["winter"])
		ApexBlocks.use(loaded)
		ApexBlocks.useIsland("hub")
		ApexBlocks.useIsland("dynamic")
		assertEquals(1, rebuilds, "hub -> dynamic draws the same, no rebuild")
	}

	@Test
	fun `the renderer gets the baked model, every state of the block`() {
		val fancy = model()
		val p = parsed("a.json" to """{"modes":"hub","replacements":{"minecraft:smooth_stone_slab":"x:fancy"}}""")
		ApexBlocks.use(ApexBlocks.build(p, ApexBlocks.Baked(mapOf(id("x:fancy") to fancy), mapOf())))
		ApexBlocks.useIsland("hub")
		for (state in Blocks.SMOOTH_STONE_SLAB.stateDefinition.possibleStates) {
			assertSame(fancy, ApexBlocks.modelFor(state, BlockPos(1, 2, 3)), state.toString())
			assertSame(fancy, ApexBlocks.modelFor(state, null), state.toString())
		}
		assertNull(ApexBlocks.modelFor(stone, null))
		assertNull(ApexBlocks.modelFor(Blocks.AIR.defaultBlockState(), null))
	}

	@Test
	fun `per-state models - a state the replacement's blockstate file does not cover keeps the original model`() {
		val bottom = model()
		val slab = Blocks.SMOOTH_STONE_SLAB
		val p = parsed("a.json" to """{"modes":"hub","replacements":{"minecraft:smooth_stone_slab":"x:fancy"}}""")
		val bottomState = slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
		val topState = slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
		val perState = mapOf(ApexBlocks.StateMapKey(slab, id("x:fancy")) to mapOf(bottomState to bottom))
		ApexBlocks.use(ApexBlocks.build(p, ApexBlocks.Baked(mapOf(id("x:fancy") to model()), perState)))
		ApexBlocks.useIsland("hub")
		assertSame(bottom, ApexBlocks.modelFor(bottomState, BlockPos.ZERO))
		assertNull(ApexBlocks.modelFor(topState, BlockPos.ZERO))
		assertNotNull(ApexBlocks.entryFor(topState, BlockPos.ZERO), "selected, but no model: original drawn (SPEC §2.7.2 quirk)")
	}

	@Test
	fun `a failed bake leaves the replacement out, not the reload`() {
		val p = parsed("a.json" to """{"modes":"hub","replacements":{"minecraft:stone":"x:missing"}}""")
		ApexBlocks.use(ApexBlocks.build(p, ApexBlocks.Baked(mapOf(), mapOf())))
		ApexBlocks.useIsland("hub")
		assertNull(ApexBlocks.modelFor(stone, BlockPos.ZERO))
	}

	@Test
	fun `replacement object without sound is read`() {
		val p = parsed("a.json" to """{"modes":"hub","replacements":{"minecraft:stone":{"block":"x:s"},"minecraft:dirt":{"block":"x:d","sound":"x:snd"}}}""")
		assertEquals(listOf(id("x:s") to null, id("x:d") to id("x:snd")), p.entries.map { it.replacement to it.sound })
	}

	@Test
	fun `broken file skipped whole, the others load`() {
		val p = parsed("a.json" to """{"modes":"hub","replacements":{"minecraft:stone":"x:s"}}""",
			"b.json" to """{"modes":"hub","area":[{"min":[0,0]}],"replacements":{"minecraft:dirt":"x:d"}}""",
			"c.json" to """{"modes":"hub","replacements":{"minecraft:sand":"x:sand"}""")
		assertEquals(listOf("minecraft:stone"), p.entries.map { net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(it.block).toString() })
	}

	@Test
	fun `fallback scope is per thread`() {
		ApexBlocks.use(ApexBlocks.build(fixtures(), null))
		ApexBlocks.useIsland("hub")
		ApexBlocks.enterFallback()
		try {
			assertNull(ApexBlocks.entryFor(stone, null))
			val other = CompletableFuture.supplyAsync { ApexBlocks.entryFor(stone, null)?.replacement }.get(10, TimeUnit.SECONDS)
			assertEquals(id("firmskyblock:fancy_stone"), other)
		} finally {
			ApexBlocks.exitFallback()
		}
		assertEquals(id("firmskyblock:fancy_stone"), ApexBlocks.entryFor(stone, null)?.replacement)
	}

	@Test
	fun `G-RLD-2 the reload applies only after the replacement models are baked`() {
		val manager = FixturePacks.manager("blocks")
		ApexBlocks.startEarly(manager, Runnable::run)
		val prepared = CompletableFuture.supplyAsync { ApexBlocks.Listener.preparePublic(manager) }
		Thread.sleep(200)
		assertFalse(prepared.isDone, "prepare must wait for baking")

		val fancy = model()
		ApexBlocks.reload.baked.complete(ApexBlocks.Baked(mapOf(id("firmskyblock:fancy_stone") to fancy), mapOf()))
		val loaded = prepared.get(10, TimeUnit.SECONDS)
		ApexBlocks.use(loaded)
		ApexBlocks.useIsland("hub")
		assertSame(fancy, ApexBlocks.modelFor(stone, BlockPos.ZERO))
		ApexBlocks.reload = ApexBlocks.Reload(CompletableFuture.completedFuture(ApexBlocks.Parsed.EMPTY)).also { it.baked.complete(ApexBlocks.Baked(mapOf(), mapOf())) }
	}

	@Test
	fun `chunk-builder threads see one whole table at a time while islands switch`() {
		val hubModel = model()
		val p = parsed("a.json" to """{"modes":"hub","area":[{"min":[0,0,0],"max":[15,255,15]}],"replacements":{"minecraft:stone":"x:hub"}}""",
			"b.json" to """{"modes":"winter","replacements":{"minecraft:stone":"x:winter","minecraft:dirt":"x:winter"}}""")
		val winterModel = model()
		ApexBlocks.use(ApexBlocks.build(p, ApexBlocks.Baked(mapOf(id("x:hub") to hubModel, id("x:winter") to winterModel), mapOf())))
		val stop = java.util.concurrent.atomic.AtomicBoolean(false)
		val error = AtomicReference<Throwable?>()
		val started = CountDownLatch(4)
		val workers = (0 until 4).map { t ->
			Thread {
				val pos = BlockPos.MutableBlockPos()
				started.countDown()
				try {
					var i = 0
					while (!stop.get()) {
						pos.set(i and 31, i and 255, (i shr 3) and 31)
						val m = ApexBlocks.modelFor(if (i and 1 == 0) stone else dirt, pos)
						if (m != null && m !== hubModel && m !== winterModel) error("foreign model")
						if (m === hubModel && (pos.x > 15 || pos.z > 15)) error("hub area ignored at $pos")
						i++
					}
				} catch (e: Throwable) {
					error.compareAndSet(null, e)
				}
			}.also { it.name = "chunk-builder-test-$t"; it.start() }
		}
		started.await()
		repeat(20_000) { ApexBlocks.useIsland(if (it % 3 == 0) "hub" else if (it % 3 == 1) "winter" else null) }
		stop.set(true)
		workers.forEach { it.join(10_000) }
		assertNull(error.get())
	}

	@Test
	fun `a lookup allocates nothing`() {
		val p = parsed("a.json" to """{"modes":"hub","area":[{"min":[0,0,0],"max":[3,3,3]},{"min":[8,0,0],"max":[11,3,3]},{"min":[0,0,8],"max":[3,3,11]}],"replacements":{"minecraft:stone":"x:s"}}""",
			"b.json" to """{"modes":"hub","replacements":{"minecraft:dirt":"x:d"}}""")
		ApexBlocks.use(ApexBlocks.build(p, ApexBlocks.Baked(mapOf(id("x:s") to model(), id("x:d") to model()), mapOf())))
		ApexBlocks.useIsland("hub")
		val states = arrayOf(stone, dirt, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState())
		val pos = BlockPos.MutableBlockPos()
		val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		fun run(): Int {
			var n = 0
			for (i in 0 until 1_000_000) {
				pos.set(i and 15, (i shr 4) and 15, (i shr 8) and 15)
				ApexBlocks.enterFallback()
				if (ApexBlocks.modelFor(states[i and 3], null) != null) n++
				ApexBlocks.exitFallback()
				if (ApexBlocks.modelFor(states[i and 3], pos) != null) n++
				if (ApexBlocks.modelFor(states[i and 3], null) != null) n++
			}
			return n
		}
		repeat(3) { run() }
		val tid = Thread.currentThread().threadId()
		val start = mx.getThreadAllocatedBytes(tid)
		val n = run()
		val bytes = mx.getThreadAllocatedBytes(tid) - start
		assertTrue(n > 0)
		assertTrue(bytes < 64 * 1024, "allocated $bytes bytes for 3M lookups")
	}
}
