// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.dungeon.secrets.MapLayout
import dev.ashtonite365.apex.features.museum.CheapestList
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch10GoldenTest {
	@Test
	fun map() {
		val maps = Batch10Maps.maps
		var grids = 0
		var marked = 0
		val out = Golden.load("batch10-map").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"grid" -> DungeonMap.detectGrid(maps[p[1].toInt()].colors).also { if (it != null) grids++ }.toString()
					"ticks" -> { val m = maps[p[1].toInt()]; DungeonMap.roomTicks(m.colors, DungeonMap.detectGrid(m.colors)!!).also { if (it.isNotEmpty()) marked++ }
						.entries.joinToString(",") { "${it.key.first}:${it.key.second}=${it.value}" } }
					"layout" -> { val m = maps[p[1].toInt()]; Batch10Maps.digest(DungeonMap.layoutPixels(m.colors, DungeonMap.detectGrid(m.colors)!!, m.ticks, m.failed, m.showFailed, m.failArgb)) }
					"layoutdef" -> { val m = maps[p[1].toInt()]; Batch10Maps.digest(DungeonMap.layoutPixels(m.colors, DungeonMap.detectGrid(m.colors)!!)) }
					"disp" -> { val m = maps[p[1].toInt()]; val g = DungeonMap.detectGrid(m.colors)!!; val cell = p[2].toInt() to p[3].toInt()
						"${DungeonMap.displayTick(m.colors, g, cell, m.failed, m.showFailed)}/${DungeonMap.displayTick(m.colors, g, cell, m.failed, !m.showFailed)}" }
					"door" -> { val m = maps[p[1].toInt()]; val g = DungeonMap.detectGrid(m.colors)!!; val col = p[2].toInt(); val row = p[3].toInt()
						val f = MapLayout.MapFrame(0, 0, g.step, g.room)
						"${DungeonMap.doorPixelOf(m.colors, f, col to row, col + 1 to row)}/${DungeonMap.doorPixelOf(m.colors, f, col to row, col to row + 1)}/" +
							DungeonMap.doorStripColour(m.colors, g.room, g.x + col * g.step, g.y + row * g.step, true) }
					"raw" -> { val m = maps[p[1].toInt()]; Batch10Maps.digest(DungeonMap.rawPixels(m.colors, p[2].toBoolean(), DungeonMap.detectGrid(m.colors), m.failed, m.failArgb)) }
					"glyph" -> DungeonMap.glyphOf(DungeonMap.Tick.valueOf(p[1])).joinToString("/")
					"draw" -> { val pts = mutableListOf<String>()
						DungeonMap.drawTick({ x, y, a -> pts += "$x,$y,$a" }, p[2].toInt(), p[3].toInt(), DungeonMap.Tick.valueOf(p[1])); pts.joinToString(" ") }
					"angle" -> "${DungeonMap.headAngle(Batch10Maps.yaws[p[1].toInt()])}"
					"pip" -> { val y = Batch10Maps.yaws[p[1].toInt()]
						"${DungeonMap.pipOffset(y, DungeonMap.PIP_RADIUS)} ${DungeonMap.pipOffset(y, 3.5F)} ${DungeonMap.pipPixel(false, y)} ${DungeonMap.pipPixel(true, y)}" }
					"rot" -> { val rot = p[1].toInt(); "${DungeonMap.decorationYaw(rot)} ${DungeonMap.teammateYaw(null, rot)} ${DungeonMap.teammateYaw(12.5F, rot)}" }
					"pair" -> Batch10Maps.pairings[p[1].toInt()].let { DungeonMap.pairMarkers(it.first, it.second) }.toString()
					"packet" -> DungeonMap.packetMarkers(listOf(1, 2, 3, 2, 5), { it == 2 }, p[1].toBoolean()).toString()
					else -> error(k)
				}
			}
		}

		Golden.verify("batch10-map", out.filter { !it.first.startsWith("layout") }, minCases = 8000) { !it.startsWith("layout") }
		assertTrue(grids > 150, "grids=$grids")
		assertTrue(marked > 60, "marked=$marked")
	}

	@Test
	fun museum() {
		val rows = Batch10MuseumShow.rows
		val B = Batch10Museum
		var priced = 0
		var listed = 0
		val countData = MuseumHelper.Data()
		dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.explicitDefaultLoad()
		val expected = Golden.load("batch10-museum")
		val out = expected.map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"row" -> rows[p[1].toInt()].also { if (it.price != null) priced++ }.let(Batch10MuseumShow::show)
					"sort" -> MuseumHelper.listedOnly(B.groups[p[1].toInt()].map { rows[it] }, MuseumHelper.Sort.valueOf(p[2])).map { rows.indexOf(it) }.toString()
					"piece" -> MuseumHelper.piece(ItemId(p[1]), { B.bin[it.skyblockId] }, { B.bid[it.skyblockId] }, { B.stale[it.skyblockId] },
						{ B.bazaar[it.skyblockId] == true }, { B.lowConf[it.skyblockId] }, { B.noLive[it.skyblockId] == true }).toString()
					"live" -> "${MuseumHelper.liveBin(B.bin[p[1]], B.stale[p[2]])}"
					"ahq" -> MuseumHelper.ahQuote(B.groups[p[1].toInt()].map { ItemId(B.ids[it % 24]) }, { B.bin[it.skyblockId] }, { B.bid[it.skyblockId] }).toString()
					"ahq2" -> MuseumHelper.ahQuote(B.groups[p[1].toInt()].take(3).map { ItemId(B.ids[it % 24]) }, { B.binDense[it.skyblockId] }, { B.bidDense[it.skyblockId] }).toString()
					"covered" -> B.donations[p[1].toInt()].let { MuseumHelper.coveredIds(Batch10MuseumShow.data(it), it.child) }.toString()
					"missing" -> {
						val d = B.donations[p[1].toInt()]
						MuseumHelper.missingFrom(Batch10MuseumShow.data(d), d.child, p[2].takeUnless { it == "null" }, MuseumHelper.Sort.valueOf(p[3])) { n, i, c, l ->
							MuseumHelper.Missing(n, i, c, l, { B.bin[it.skyblockId] }, { null }, { B.stale[it.skyblockId] })
						}.also { if (it.isNotEmpty()) listed++ }.joinToString(";") { "${it.name}/${it.category}/${it.lowerTiers}/${it.ids}/${it.price}" }
					}
					"title" -> B.titles[p[1].toInt()].let { "${MuseumHelper.categoryOf(it)}|${MuseumHelper.isOverviewTitle(it)}" }
					"set" -> MuseumHelper.armorKey(p[1]) + " " + MuseumHelper.setPieces(B.museumJson, p[1])
					"status" -> MuseumHelper.status(B.stack(B.stacks[p[1].toInt()])).toString()
					"page" -> {
						val i = p[1].toInt()
						val map = LinkedHashMap<String, MuseumHelper.Entry>()
						if (i % 3 == 0) map["Lapis Pickaxe"] = MuseumHelper.Entry(listOf("KEEP"), i % 2 == 0)
						if (i % 5 == 0) map["Helm"] = MuseumHelper.Entry(listOf(), true)
						val stacks = B.pages[i].map { it?.let(B::stack) }
						val ids = { n: String, c: String -> listOf("ID:$n@$c") }
						val n1 = MuseumHelper.recordPage(map, "Cat$i", stacks, ids)
						val n2 = MuseumHelper.recordPage(map, "Cat$i", stacks.reversed(), ids)
						"$n1 $n2 $map"
					}
					"count" -> {
						val i = p[1].toInt()
						val name = listOf("§aCombat", "combat ", "Mining", "museum", "§lSpecial Items").random(kotlin.random.Random(i))
						val dn = i % 7; val tot = if (i % 4 == 0) null else 10 + i % 3
						"${MuseumHelper.MuseumCounts.record(countData, name, dn, tot, i.toLong())} ${countData.counts} ${MuseumHelper.MuseumCounts.key(name)}"
					}
					"shown" -> {
						val i = p[1].toInt()
						val name = listOf("§aCombat", "combat ", "Mining", "museum", "§lSpecial Items").random(kotlin.random.Random(i))
						val sh = MuseumHelper.MuseumCounts.shown(name, if (i % 3 == 0) i else null, if (i % 2 == 0) i + 1 else null, if (i % 5 == 0) null else countData)
						"$sh ${sh?.fraction} ${MuseumHelper.MuseumCounts.label(sh)}"
					}
					"frac" -> listOf(MuseumHelper.MuseumCounts.Shown(5, 0, true), MuseumHelper.MuseumCounts.Shown(15, 10, false), MuseumHelper.MuseumCounts.Shown(-1, 10, false),
						MuseumHelper.MuseumCounts.Shown(3, null, true)).first { it.toString() == p[1] }.let { "${it.fraction} ${MuseumHelper.MuseumCounts.label(it)}" }
					"trunc" -> { val tr = CheapestList.truncate(p[1], p[2].toInt()) { it.length * 6 }; "$tr|${CheapestList.tooltip(p[1], tr)}" }
					"scroll" -> { val (total, vis, sc) = p.drop(1).map { it.toInt() }
						"${CheapestList.clamp(sc, total, vis)} ${CheapestList.maxScroll(total, vis)} ${CheapestList.shown(sc, total, vis)}" }
					"vis" -> "${CheapestList.visible(p[1].toInt())}"
					"labels" -> MuseumHelper.Sort.valueOf(p[1]).let { MuseumHelper.sortLabel(it) + "/" + MuseumHelper.nextLabel(it) }
					else -> error(k)
				}
			}
		}
		Golden.verify("batch10-museum", out, minCases = 3000)
		assertTrue(priced >= 100, "priced=$priced")
		assertTrue(listed > 150, "listed=$listed")
	}
}
