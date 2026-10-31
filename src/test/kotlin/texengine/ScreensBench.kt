// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.nio.file.Files
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("bench")
@ExtendWith(AutoBootstrapExtension::class)
class ScreensBench {
	private val engines = listOf<GoldenEngine>(ApexGolden)

	private fun textPack(files: Int) = BenchPacks.pack("scr-text-$files", (0 until files).associate {
		"assets/firmskyblock/overrides/texts/t$it.json" to
			"""{"match":{"regex":".*Coins.*"},"replacements":[{"match":{"regex":"([0-9]+) Coins"},"replace":{"text":"C$it"}}]}"""
	})

	private fun layoutPack(layouts: Int) = BenchPacks.pack("scr-layout-$layouts", (0 until layouts).associate {
		"assets/firmskyblock/overrides/screen_layout/l${"%04d".format(it)}.json" to
			"""{"predicates":{"label":{"regex":"Menu $it( .*)?"}},"background":{"texture":"bench:bg$it.png","x":0,"y":0,"width":176,"height":166},
			"slots":[{"index":0,"x":8,"y":8},{"index":1,"x":26,"y":8}],"containerTitle":{"x":8,"y":6,"align":"center"}}"""
	})

	private val colorPack = BenchPacks.pack("scr-colors", mapOf("assets/firmskyblock/overrides/text_colors.json" to
		"""{"defaultColor":4210752,"overrides":[${(0 until 20).joinToString(",") { """{"predicate":"Menu $it","override":$it}""" }}]}"""))

	@Test
	fun `B-SCR screens and text`() {
		TexConfig.reset()
		val results = mutableListOf<Pair<String, Bench.Result>>()
		val texts = (0 until 100_000).map {
			if (it % 5 == 0) Component.literal("Price: $it Coins") else Component.literal("Some GUI text $it")
		}
		val styled = (0 until 10_000).map {
			Component.literal("Lore line ").withStyle(ChatFormatting.GRAY).append(Component.literal("$it Coins").withStyle(ChatFormatting.GOLD))
		}
		val packs = listOf(0, 5, 50).associateWith { FixturePacks.managerAt(textPack(it)) }
		val layouts = FixturePacks.managerAt(layoutPack(200))
		val colors = FixturePacks.managerAt(colorPack)
		val sameTitles = listOf(Component.literal("Menu 7"), Component.literal("Inventory"))
		val freshTitles = (0 until 1000).map { Component.literal("Menu ${it % 40}") }
		for (engine in engines) {
			for ((files, pack) in packs) {
				engine.loadTextReplacements(pack)
				results += engine.id to Bench.run("B-TEXT", "$files files, 100k texts (20% match)", texts.size) {
					var n = 0
					for (t in texts) n += engine.replaceText(t).hashCode()
					n
				}
			}
			engine.loadTextReplacements(packs.getValue(5))
			results += engine.id to Bench.run("B-TEXT", "5 files, 10k two-part styled lines (all match)", styled.size) {
				var n = 0
				for (t in styled) n += engine.replaceText(t).hashCode()
				n
			}

			assertEquals("Price: C0", engine.replaceText(Component.literal("Price: 10 Coins")).string, engine.id)
			results += engine.id to Bench.run("B-TXT-RLD", "reload: 50 text files", 1) { engine.loadTextReplacements(packs.getValue(50)); 0 }
			engine.loadTextReplacements(packs.getValue(0))

			engine.loadTextColors(colors)
			assertEquals(7, engine.textColor(Component.literal("Menu 7"), 1))
			results += engine.id to Bench.run("B-COL", "20 overrides, same 2 title components per frame", 2) {
				engine.textColor(sameTitles[0], 1) + engine.textColor(sameTitles[1], 1)
			}
			results += engine.id to Bench.run("B-COL", "20 overrides, 1k new title components", freshTitles.size) {
				var n = 0
				for (t in freshTitles) n += engine.textColor(t, 1)
				n
			}
			engine.loadTextColors(FixturePacks.manager())

			engine.loadScreenLayouts(layouts)
			assertEquals(8, engine.activeLayout(Component.literal("Menu 150"))?.slotMoves?.first()?.second)
			val first = Component.literal("Menu 0")
			val last = Component.literal("Menu 199")
			val none = Component.literal("Something else")
			results += engine.id to Bench.run("B-SCR", "200 layouts, screen change, first layout matches", 1) { engine.activeLayout(first)?.slotMoves?.size ?: 0 }
			results += engine.id to Bench.run("B-SCR", "200 layouts, screen change, last layout matches", 1) { engine.activeLayout(last)?.slotMoves?.size ?: 0 }
			results += engine.id to Bench.run("B-SCR", "200 layouts, screen change, no layout matches", 1) { engine.activeLayout(none)?.slotMoves?.size ?: 0 }
			results += engine.id to Bench.run("B-SCR-RLD", "reload: 200 layout files", 1) { engine.loadScreenLayouts(layouts); 0 }
			engine.loadScreenLayouts(FixturePacks.manager())
		}
		val report = StringBuilder("B-TEXT / B-COL / B-SCR / reloads (engine, case, ns/op, B/op)\n")
		for ((engine, r) in results) report.append(String.format("%-9s %-10s %-52s %14.1f ns/op %14.1f B/op%n", engine, r.id, r.case, r.nsPerOp, r.bytesPerOp))
		println(report)
		Files.writeString(BenchPacks.dir.resolve("b-scr.txt"), report.toString())
	}
}
