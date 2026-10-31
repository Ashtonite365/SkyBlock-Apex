// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.nio.file.Files
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("bench")
@ExtendWith(AutoBootstrapExtension::class)
class ArmorBench {
	private val engines = listOf<GoldenEngine>(ApexGolden)
	private val items = listOf(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)
	private val slots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

	private fun armorPack(name: String, sets: Int, variants: Int) = BenchPacks.pack(name, buildMap {
		val subs = (0 until variants).joinToString(",") { """{"predicate":{"apex:display_name":"Variant $it"},"model":"bench:v$it"}""" }
		for (s in 0 until sets) for (piece in listOf("HELM", "CHEST", "LEGS", "BOOTS")) {
			val id = "BENCH_${s}_$piece"
			put("assets/firmskyblock/overrides/armor_models/${id.lowercase()}.json",
				"""{"item_ids":["$id"],"layers":[{"identifier":"bench:${id.lowercase()}","tint":true}],"overrides":[$subs]}""")
		}
	})

	@Test
	fun `B-ARM armour resolution and reload`() {
		TexConfig.reset()
		val results = mutableListOf<Pair<String, Bench.Result>>()
		val small = FixturePacks.managerAt(armorPack("arm-1x5", 1, 5))
		val big = FixturePacks.managerAt(armorPack("arm-125x5", 125, 5))
		val originals = (0 until 100).flatMap { e ->
			(0 until 4).map { Stacks.sb(items[it], id = "BENCH_${e % 125}_${listOf("HELM", "CHEST", "LEGS", "BOOTS")[it]}", name = Component.literal("Variant ${e % 7}")) }
		}
		val nonSb = (0 until 400).map { Stacks.sb(items[it % 4]) }
		for (engine in engines) {
			engine.loadArmor(big)
			val expected = originals.count { engine.armor(it, slots[originals.indexOf(it) % 4]).assetId != null }
			assertEquals(originals.size, expected, "every bench stack resolves on ${engine.id}")
			results += engine.id to Bench.run("B-ARM", "100 entities x 4 pieces, 500 files, fresh copies", originals.size) {
				var n = 0
				for ((i, s) in originals.withIndex()) if (engine.armor(s.copy(), slots[i % 4]).assetId != null) n++
				n
			}
			results += engine.id to Bench.run("B-ARM", "100 entities x 4 pieces, 500 files, same stacks", originals.size) {
				var n = 0
				for ((i, s) in originals.withIndex()) if (engine.armor(s, slots[i % 4]).assetId != null) n++
				n
			}
			results += engine.id to Bench.run("B-ARM", "400 non-SkyBlock pieces (vanilla armour)", nonSb.size) {
				var n = 0
				for ((i, s) in nonSb.withIndex()) if (engine.armor(s, slots[i % 4]).assetId != null) n++
				n
			}
			results += engine.id to Bench.run("B-ARM-RLD", "reload: 4 files, 5 variants each", 1) { engine.loadArmor(small); 0 }
			results += engine.id to Bench.run("B-ARM-RLD", "reload: 500 files, 5 variants each", 1) { engine.loadArmor(big); 0 }
		}
		val report = StringBuilder("B-ARM / B-ARM-RLD (engine, case, ns/op, B/op)\n")
		for ((engine, r) in results) report.append(String.format("%-9s %-10s %-52s %14.1f ns/op %14.1f B/op%n", engine, r.id, r.case, r.nsPerOp, r.bytesPerOp))
		println(report)
		Files.writeString(BenchPacks.dir.resolve("b-arm.txt"), report.toString())
	}
}
