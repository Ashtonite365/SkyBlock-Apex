// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.apex.ApexBlocks
import java.nio.file.Files
import net.minecraft.client.renderer.block.dispatch.SingleVariant
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("bench")
@ExtendWith(AutoBootstrapExtension::class)
class BlocksBench {
	private val apex = object {
		fun hook(state: BlockState, pos: BlockPos): Boolean {
			ApexBlocks.enterFallback()
			val inside = ApexBlocks.modelFor(state, null)
			ApexBlocks.exitFallback()
			return (inside ?: ApexBlocks.modelFor(state, pos)) != null
		}

		fun far(state: BlockState) = ApexBlocks.modelFor(state, null) != null
	}

	@Test
	fun `B-BLK blocks`() {
		TexConfig.reset()
		ApexGolden.id
		val before = ApexBlocks.onTableChanged
		ApexBlocks.onTableChanged = {}
		val blocks = BuiltInRegistries.BLOCK.stream().toList().filter { it != Blocks.AIR }
		val section: Array<BlockState> = Array(4096) { blocks[(it * 31) % 64].defaultBlockState() }
		val model = Fakes.alloc(SingleVariant::class.java)
		val results = mutableListOf<Pair<String, Bench.Result>>()

		val expectedHits = mapOf(
			"0 replacements, 0 areas" to 0,
			"10 replacements, 0 areas" to 32000, "10 replacements, 3 areas" to 256, "10 replacements, 20 areas" to 448,
			"200 replacements, 0 areas" to 204800, "200 replacements, 3 areas" to 2304, "200 replacements, 20 areas" to 3072,
		)
		val hits = HashMap<String, Int>()
		for (replacements in listOf(0, 10, 200)) for (areas in listOf(0, 3, 20)) {
			if (replacements == 0 && areas > 0) continue
			val area = if (areas == 0) "" else ",\"area\":[" + (0 until areas).joinToString(",") { """{"min":[${it * 4},0,0],"max":[${it * 4 + 2},15,15]}""" } + "]"
			val repl = (0 until replacements).joinToString(",") { "\"${BuiltInRegistries.BLOCK.getKey(blocks[it % blocks.size])}\":\"bench:b$it\"" }
			val manager = FixturePacks.managerAt(BenchPacks.pack("blk-$replacements-$areas", mapOf("assets/firmskyblock/overrides/blocks/b.json" to """{"modes":"hub"$area,"replacements":{$repl}}""")))
			val parsed = ApexBlocks.parse(manager)
			ApexBlocks.use(ApexBlocks.build(parsed, ApexBlocks.Baked(parsed.entries.associate { it.replacement to model }, mapOf())))
			ApexBlocks.useIsland("hub")
			val case = "$replacements replacements, $areas areas"
			run {
				val name = "apex"
				val path = apex
				val pos = BlockPos.MutableBlockPos()
				var hit = 0
				results += name to Bench.run("B-BLK-HOOK", "$case, 16^3 section x 50", 4096 * 50) {
					var n = 0
					for (s in 0 until 50) for (i in 0 until 4096) {
						pos.set(i and 15, (i shr 4) and 15, (i shr 8) + s * 16)
						if (path.hook(section[i], pos)) n++
					}
					hit = n
					n
				}
				hits[case] = hit
				results += name to Bench.run("B-BLK-FAR", "$case, no position", 4096 * 50) {
					var n = 0
					for (s in 0 until 50) for (i in 0 until 4096) if (path.far(section[i])) n++
					n
				}
			}
		}
		ApexBlocks.useIsland(null)
		ApexBlocks.use(ApexBlocks.Loaded.EMPTY)
		ApexBlocks.onTableChanged = before
		assertEquals(expectedHits, hits)
		val report = StringBuilder("B-BLK (engine, case, ns/op, B/op); one op = one block\n")
		for ((engine, r) in results) report.append(String.format("%-9s %-10s %-48s %10.2f ns/op %10.2f B/op%n", engine, r.id, r.case, r.nsPerOp, r.bytesPerOp))
		println(report)
		Files.writeString(BenchPacks.dir.resolve("b-blk.txt"), report.toString())
	}
}
