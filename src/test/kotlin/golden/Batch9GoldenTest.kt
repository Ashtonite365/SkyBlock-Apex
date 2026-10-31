// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.auction.PriceGraphScreen
import dev.ashtonite365.apex.features.inventory.InventoryOverlays
import dev.ashtonite365.apex.features.skills.TimersOverlay
import dev.ashtonite365.apex.features.accessories.AccessoryBagHelper
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.ApexFormatters
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch9GoldenTest {
	@Test
	fun graph() {
		var drawn = 0
		val out = Golden.load("batch9-graph").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"g" -> {
						val c = Batch9Graph.cases[p[1].toInt()]
						val shown = PriceGraphScreen.visible(c.points, PriceGraphScreen.Range.entries[c.range], c.now)
						if (shown.size < 2) return@run "few ${shown.size}"
						val chart = PriceGraphScreen.Chart(shown, c.left + 50, c.left + PriceGraphScreen.W - 8, c.top + 24, c.top + PriceGraphScreen.H - 22)
						fun cols(pick: (dev.ashtonite365.apex.features.auction.PriceHistory.Point) -> Double?) =
							chart.line(pick).joinToString("") { "${it.x}:${it.top}:${it.bottom}," }.also { if (it.isNotEmpty()) drawn++ } + " / "
						val near = c.mice.joinToString(",") { mx -> val n = chart.nearest(mx); "${n.t}@${chart.xOf(n.t)}" }
						"${chart.start}..${chart.end} ${ApexFormatters.compactNumber(chart.high)}/${ApexFormatters.compactNumber(chart.low)} " +
							cols { it.buy } + cols { it.sell } + " near=$near"
					}
					"hit" -> {
						val (mx, my) = Batch9Graph.clicks[p[1].toInt()]
						PriceGraphScreen.rangeHit(mx, my, 0, 0)?.ordinal?.toString() ?: "none"
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("batch9-graph", out, minCases = 1900)
		assertTrue(drawn > 300, "drawn=$drawn")
	}

	@Test
	fun overlays() {
		val table = buildMap { Batch9Overlays.trophyRaw.forEach { putAll(InventoryOverlays.trophyTable(it)) } }
		var answered = 0
		val prices = { id: ItemId -> Batch9Overlays.prices[id.skyblockId] }
		fun offers(l: List<Batch9Overlays.Offer>) = l.map { InventoryOverlays.Offer(it.id?.let(::ItemId), it.name, it.count) }
		val sides = Batch9Overlays.sides
		val out = Golden.load("batch9-overlays").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"calc" -> InventoryOverlays.searchResult(Batch9Overlays.searches[p[1].toInt()], InventoryOverlays.CalcMode.valueOf(p[2]), Batch9Overlays.vars)
						.also { if (it != null) answered++ }.toString()
					"table" -> InventoryOverlays.trophyTable(Batch9Overlays.trophyRaw[p[1].toInt()]).toSortedMap().toString()
					"trophy" -> {
						val stacks = Batch9Overlays.tallies[p[1].toInt()].map { InventoryOverlays.Held(it.coreId, it.name, it.count) }
						val entries = InventoryOverlays.tally(stacks, table)
						InventoryOverlays.trophyPanel(entries, p[2].toBoolean()).joinToString("\n") + " || " + entries.sumOf { it.value }
					}
					"coin" -> InventoryOverlays.sideTotal(offers(listOf(Batch9Overlays.Offer(null, Batch9Overlays.coinNames[p[1].toInt()], 1))), prices).toString()
					"trade" -> {
						val i = p[1].toInt()
						InventoryOverlays.tradePanel(InventoryOverlays.sideTotal(offers(sides[i]), prices), InventoryOverlays.sideTotal(offers(sides[(i + 1) % sides.size]), prices)).joinToString("\n")
					}
					"kat" -> InventoryOverlays.katLines(p[1], p[2].takeIf { it != "null" }).joinToString("\n")
					else -> error(k)
				}
			}
		}
		Golden.verify("batch9-overlays", out, minCases = 3200)
		assertTrue(answered > 100, "answered=$answered")
	}

	@Test
	fun timers() {
		val tasks = TimersOverlay.Task.entries
		var waiting = 0
		val out = Golden.load("batch9-timers").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"reset" -> (TimersOverlay.Task.valueOf(p[2]).reset as? TimersOverlay.Reset.Daily)
						?.let { TimersOverlay.resetBefore(it, Batch9Timers.instants[p[1].toInt()]) }.toString()
					"wait" -> {
						val (ti, off, now) = Batch9Timers.waits[p[1].toInt()]
						val left = TimersOverlay.waitFor(tasks[ti], off?.let { now + it }, now)
						if (left != null) waiting++
						"$left | " + listOf(0, 3, 5, 7).joinToString(" ; ") { q -> TimersOverlay.statusText(tasks[ti], left, q) }
					}
					"chat" -> TimersOverlay.completedBy(Batch9Timers.chats[p[1].toInt()].trim()).joinToString(",") { it.name }
					"menu" -> {
						val m = Batch9Timers.menus[p[1].toInt()]
						@Suppress("UNCHECKED_CAST")
						when (TimersOverlay.menuSignal(m[0] as String?, m[1] as List<String>, m[2] as String?, m[3] as List<String>)) {
							TimersOverlay.MenuSignal.EXPERIMENTS_DONE -> "EXP"
							TimersOverlay.MenuSignal.INFUSION_FREE -> "INF"
							TimersOverlay.MenuSignal.NOTHING -> "-"
						}
					}
					"god" -> TimersOverlay.godPotionIn(Batch9Timers.footers[p[1].toInt()]).toString()
					"quests" -> TimersOverlay.finishedQuests(Batch9Timers.tabs[p[1].toInt()].map { TimersOverlay.TabLine(it.first, it.second) }).joinToString("|")
					else -> error(k)
				}
			}
		}
		Golden.verify("batch9-timers", out, minCases = 6000)
		assertTrue(waiting > 400, "waiting=$waiting")
	}

	@Test
	fun bag() {
		val ups = AccessoryBagHelper.Upgrades { Batch9Bag.upgrades[it] }
		val all = (Batch9Bag.ids + "EXTRA1" + "EXTRA2").map(::ItemId)
		var nonEmpty = 0
		val datas = Batch9Bag.scans.map { script ->
			val d = AccessoryBagHelper.Data()
			d to script.map { (k, e) -> AccessoryBagHelper.record(d, k, e) }
		}
		val out = Golden.load("batch9-bag").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"page" -> AccessoryBagHelper.pageOf(Batch9Bag.titles[p[1].toInt()].trim()).toString()
					"stats" -> AccessoryBagHelper.statsFrom(Batch9Bag.lore[p[1].toInt()].map { it.trim() }).toString()
					"scan" -> datas[p[1].toInt()].let { (d, ch) -> ch.joinToString("") { if (it) "1" else "0" } + " " + d.slots.toSortedMap().toString() }
					"rows" -> {
						val i = p[1].toInt()
						val d = datas[i].first
						AccessoryBagHelper.rows(AccessoryBagHelper.Tab.valueOf(p[2]), d, ups, { "N_$it" },
							{ AccessoryBagHelper.missing(d, all, Batch9Bag.ignored, ups) { Batch9Bag.prices[it.skyblockId] } }, 4 + i % 9)
							.also { if (it.isNotEmpty()) nonEmpty++ }
							.joinToString(" ¦ ") { "${it.first}=${it.second}" }
					}
					"bar" -> {
						val (y, h, contentH, scroll) = Batch9Bag.scrolls[p[1].toInt()]
						(AccessoryBagHelper.scrollBar(y, h, contentH, scroll)?.let { "${it.first}+${it.second}" } ?: "-") + " " + AccessoryBagHelper.rowVisible(scroll, y, h)
					}
					"mp" -> AccessoryBagHelper.magicalPower(if (p[1] == "null") null else dev.ashtonite365.apex.util.skyblock.Rarity.valueOf(p[1])).toString()
					else -> error(k)
				}
			}
		}
		Golden.verify("batch9-bag", out, minCases = 2400)
		assertTrue(nonEmpty > 700, "nonEmpty=$nonEmpty")
	}

	@Test
	fun chests() {
		var priced = 0
		val expected = Golden.load("batch9-chests")
		val out = Batch9Chest.withMarket {
			Batch9Chest.combos.withIndex().flatMap { (ci, combo) ->
				Batch9Chest.withToggles(combo) {
					expected.filter { it.first.split('|')[1] == ci.toString() }.map { (k, _) ->
						val p = k.split('|')
						k to Golden.run {
							when (p[0]) {
								"croesus" -> {
									val sc = Batch9Chest.croesus[p[2].toInt()]
									val chests = DungeonChestProfit.croesusChests(Batch9Chest.stacks(sc.first), sc.second)
									if (chests.isNotEmpty()) priced++
									chests.joinToString(" || ") {
										Batch9Chest.show(it) + " :: " + DungeonChestProfit.profitLabel(it.profit, it.missingPrices) + " :: " +
											DungeonChestProfit.tooltip(it, 0.0).joinToString("\\n") { c -> c.string }
									} + " best=" + chests.maxByOrNull { it.profit }?.slot
								}
								"reward" -> {
									val rc = Batch9Chest.rewards[p[2].toInt()]
									val (chest, kismet) = DungeonChestProfit.parseRewardChest(Component.literal(rc.first), Batch9Chest.stacks(rc.second)) ?: return@run "null"
									if (chest.items.isNotEmpty()) priced++
									Batch9Chest.show(chest) + " kismet=$kismet :: " + DungeonChestProfit.tooltip(chest, kismet).joinToString("\\n") { it.string } +
										" :: " + DungeonChestProfit.profitLabel(chest.copy(cost = chest.cost + kismet).profit, chest.missingPrices)
								}
								"line" -> DungeonChestProfit.priceLoreLine(Component.literal(Batch9Chest.lines[p[2].toInt()]), p[3].toBoolean())
									?.let { "${it.label.string}=${it.value}" }.toString()
								else -> error(k)
							}
						}
					}
				}
			}
		}
		Golden.verify("batch9-chests", out, minCases = 1700)
		assertTrue(priced > 600, "priced=$priced")
	}
}
