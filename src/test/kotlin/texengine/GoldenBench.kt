// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

object Bench {
	private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

	data class Result(val id: String, val case: String, val nsPerOp: Double, val bytesPerOp: Double, val ops: Int)

	fun run(id: String, case: String, ops: Int, body: () -> Any?): Result {
		var sink: Any? = null
		repeat(5) { sink = body() }
		val times = DoubleArray(10)
		val bytes = DoubleArray(10)
		val tid = Thread.currentThread().threadId()
		for (i in 0 until 10) {
			val b0 = threads.getThreadAllocatedBytes(tid)
			val t0 = System.nanoTime()
			sink = body()
			val t1 = System.nanoTime()
			val b1 = threads.getThreadAllocatedBytes(tid)
			times[i] = (t1 - t0).toDouble() / ops
			bytes[i] = (b1 - b0).toDouble() / ops
		}
		if (sink === Unit.javaClass) println("unreachable")
		times.sort(); bytes.sort()
		return Result(id, case, (times[4] + times[5]) / 2, (bytes[4] + bytes[5]) / 2, ops)
	}
}

object BenchPacks {
	val dir: Path by lazy {
		Path.of(System.getProperty("apex.benchDir") ?: (System.getProperty("apex.projectDir") + "/build/bench")).also { Files.createDirectories(it) }
	}

	fun pack(name: String, files: Map<String, String>): Path {
		val root = dir.resolve("packs").resolve(name)
		if (Files.exists(root)) root.toFile().deleteRecursively()
		for ((path, content) in files) {
			val file = root.resolve(path)
			Files.createDirectories(file.parent)
			Files.writeString(file, content)
		}
		Files.createDirectories(root)
		Files.writeString(root.resolve("pack.mcmeta"), """{"pack":{"pack_format":80,"description":"bench $name"}}""")
		return root
	}
}

@Tag("bench")
@ExtendWith(AutoBootstrapExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoldenBench {
	private val engine: GoldenEngine = ApexGolden
	private val results = mutableListOf<Bench.Result>()

	private fun record(r: Bench.Result) {
		results += r
		println("[bench] ${engine.id} ${r.id} ${r.case}: ${"%.1f".format(r.nsPerOp)} ns/op, ${"%.1f".format(r.bytesPerOp)} B/op")
	}

	@AfterAll
	fun write() {
		val out = BenchPacks.dir.resolve("results-${engine.id}.tsv")
		Files.writeString(out, results.joinToString("\n", postfix = "\n") { "${it.id}\t${it.case}\t${"%.1f".format(it.nsPerOp)}\t${"%.1f".format(it.bytesPerOp)}\t${it.ops}" })
	}

	@Test
	fun `B-SKULL skull texture id`() {
		TexConfig.reset()
		val urls = (0 until 200).map { "http://textures.minecraft.net/texture/%064x".format(it * 7919L + 1) }
		val heads = (0 until 10_000).map { urls[it % 200] }
		record(Bench.run("B-SKULL", "10k lookups, 200 textures (id derivation only)", heads.size) {
			var n = 0
			for (u in heads) if (engine.skullTextureId(u) != null) n++
			n
		})
	}

	@Test
	fun `B-TEXT text replacement`() {
		TexConfig.reset()
		val texts = (0 until 100_000).map {
			if (it % 5 == 0) Component.literal("Price: ${it} Coins") else Component.literal("Some GUI text $it")
		}
		for (files in listOf(0, 5, 50)) {
			val pack = BenchPacks.pack("text-$files", (0 until files).associate {
				"assets/firmskyblock/overrides/texts/t$it.json" to
					"""{"match":{"regex":".*Coins.*"},"replacements":[{"match":{"regex":"([0-9]+) Coins"},"replace":{"text":"C$it"}}]}"""
			})
			engine.loadTextReplacements(FixturePacks.managerAt(pack))
			record(Bench.run("B-TEXT", "$files files, 100k texts (20% match)", texts.size) {
				var n = 0
				for (t in texts) n += engine.replaceText(t).hashCode()
				n
			})
		}
		engine.loadTextReplacements(FixturePacks.manager())
	}

	private fun sbStacks(): List<ItemStack> = (0 until 1000).map {
		Stacks.sb(Items.PAPER, id = "ITEM_$it", name = Component.literal("Item $it"),
			lore = listOf(Component.literal("§7Line"), Component.literal("§6Ability: Thing $it"))) {
			putString("modifier", if (it % 3 == 0) "heroic" else "none")
			putString("petInfo", """{"type":"PET_${it % 10}","exp":${it * 1000},"tier":"EPIC","candyUsed":0}""")
		}
	}

	@Test
	fun `B-OVR override matching`() {
		TexConfig.reset()
		val stacks = sbStacks()
		for (count in listOf(1, 20, 200)) {
			val overrides = (0 until count).joinToString(",") {
				val pred = when (it % 4) {
					0 -> """{"apex:display_name":"Item $it"}"""
					1 -> """{"apex:lore":{"regex":"Ability: Thing ${it}"}}"""
					2 -> """{"apex:extra_attributes":{"path":"modifier","match":"heroic"},"apex:display_name":{"regex":"Item ${it}.*"}}"""
					else -> """{"apex:pet":{"id":"PET_${it % 10}","exp":">=${it * 1000}"}}"""
				}
				"""{"predicate":$pred,"model":"bench:m$it"}"""
			}
			val select = engine.legacySelector("""{"overrides":[$overrides]}""")
			record(Bench.run("B-OVR", "1k stacks x $count overrides", stacks.size) {
				var n = 0
				for (s in stacks) if (select(s) != null) n++
				n
			})
		}
	}

	@Test
	fun `B-ARM armor resolution`() {
		TexConfig.reset()
		val subs = (0 until 5).joinToString(",") { """{"predicate":{"apex:display_name":"Variant $it"},"model":"bench:v$it"}""" }
		val ids = listOf("BENCH_HELM", "BENCH_CHEST", "BENCH_LEGS", "BENCH_BOOTS")
		val pack = BenchPacks.pack("armor", ids.associate {
			"assets/firmskyblock/overrides/armor_models/${it.lowercase()}.json" to
				"""{"item_ids":["$it"],"layers":[{"identifier":"bench:${it.lowercase()}","tint":true}],"overrides":[$subs]}"""
		})
		engine.loadArmor(FixturePacks.managerAt(pack))
		val items = listOf(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)
		val slots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
		val originals = (0 until 100).flatMap { e -> (0 until 4).map { Stacks.sb(items[it], id = ids[it], name = Component.literal("Variant ${e % 7}")) } }
		record(Bench.run("B-ARM", "100 entities x 4 pieces, fresh copies", originals.size) {
			var n = 0
			for ((i, s) in originals.withIndex()) if (engine.armor(s.copy(), slots[i % 4]).assetId != null) n++
			n
		})
	}

	@Test
	fun `B-BLK block replacement`() {
		TexConfig.reset()
		val blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().toList().filter { it != Blocks.AIR }
		val section: List<BlockState> = (0 until 4096).map { blocks[(it * 31) % 64].defaultBlockState() }
		for ((replacements, areas) in listOf(0 to 0, 10 to 0, 10 to 3, 200 to 3, 200 to 20)) {
			val area = if (areas == 0) "" else ",\"area\":[" + (0 until areas).joinToString(",") { """{"min":[${it * 4},0,0],"max":[${it * 4 + 2},15,15]}""" } + "]"
			val repl = (0 until replacements).joinToString(",") {
				val id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blocks[it % blocks.size])
				"\"$id\":\"bench:b$it\""
			}
			val pack = BenchPacks.pack("blk-$replacements-$areas", mapOf("assets/firmskyblock/overrides/blocks/b.json" to """{"modes":"hub"$area,"replacements":{$repl}}"""))
			engine.loadBlocks(FixturePacks.managerAt(pack))
			val lookup = engine.blockSelector("hub")
			val case = "$replacements replacements, $areas areas"
			record(Bench.run("B-BLK", "$case, 16^3 section x 50", 4096 * 50) {
				var n = 0
				repeat(50) { s ->
					for (i in 0 until 4096) {
						if (lookup(section[i], BlockPos(i and 15, (i shr 4) and 15, (i shr 8) + s * 16)) != null) n++
					}
				}
				n
			})
			record(Bench.run("B-BLK", "$case, no position (far terrain path)", 4096 * 50) {
				var n = 0
				repeat(50) { for (i in 0 until 4096) if (lookup(section[i], null) != null) n++ }
				n
			})
		}
	}

	@Test
	fun `B-RLD reload parse`() {
		TexConfig.reset()
		val files = mutableMapOf<String, String>()
		for (i in 0 until 500) {
			files["assets/firmskyblock/overrides/item/o$i.json"] = """{"screen":"firmskyblock:s${i % 20}","model":"bench:o$i","predicate":{"apex:display_name":"N$i"}}"""
		}
		for (i in 0 until 20) files["assets/firmskyblock/filters/screen/s$i.json"] = """{"title":{"regex":"Screen $i.*"}}"""
		for (i in 0 until 200) files["assets/firmskyblock/overrides/blocks/b$i.json"] = """{"modes":"hub","replacements":{"minecraft:stone":"bench:b$i"}}"""
		for (i in 0 until 50) files["assets/firmskyblock/overrides/texts/t$i.json"] = """{"match":{"regex":".*X$i.*"},"replacements":[{"match":"X$i","replace":{"text":"Y"}}]}"""
		val manager = FixturePacks.managerAt(BenchPacks.pack("reload", files))
		val stack = Stacks.sb(Items.PAPER, name = Component.literal("N1"))
		record(Bench.run("B-RLD", "500 screen overrides + 200 block files + 50 text files", 1) {
			engine.loadBlocks(manager)
			engine.loadTextReplacements(manager)
			engine.itemOverride(manager, stack, Component.literal("Screen 1"), setOf())
		})
		engine.loadTextReplacements(FixturePacks.manager())
	}
}
