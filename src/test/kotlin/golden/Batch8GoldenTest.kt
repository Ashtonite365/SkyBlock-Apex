// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.enchanting.ExperimentSolvers
import dev.ashtonite365.apex.features.minions.MinionHelper
import dev.ashtonite365.apex.features.auction.BinWarning
import dev.ashtonite365.apex.features.auction.AuctionTools
import dev.ashtonite365.apex.features.auction.SignSearchScreen
import dev.ashtonite365.apex.features.items.ItemCustomizer
import dev.ashtonite365.apex.util.ItemId
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.misc.AbiphoneHelper
import dev.ashtonite365.apex.features.misc.AbiphoneHelper.Click
import dev.ashtonite365.apex.features.enchanting.ExperimentSolvers.Cell
import dev.ashtonite365.apex.features.enchanting.ExperimentSolvers.Paint
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch8GoldenTest {
	private fun cell(c: Batch8Corpus.C) = Cell(c.path, c.name, c.count, c.empty)

	private fun ops(p: Paint?, remembered: Cell?): String {
		if (p == null) return ""
		val ops = mutableListOf<String>()
		p.box?.let { ops += "box:" + Integer.toHexString(it) }
		if (p.reveal && remembered != null) ops += "item:${remembered.path}/${remembered.name}"
		p.label?.let { ops += "num:$it" }
		return ops.joinToString(",")
	}

	@Test
	fun experiments() {
		val sessions = Batch8Corpus.sessions()
		var green = 0
		var revealed = 0
		val out = Golden.load("batch8-experiments").map { (k, _) ->
			k to Golden.run {
				val p = k.split('|')
				if (p[0] == "title") return@run ExperimentSolvers.solverFor(k.substringAfter('|')).toString()
				error(k)
			}
		}.toMutableList()

		var idx = out.indexOfFirst { it.first.startsWith("ev|") }
		for ((i, s) in sessions.withIndex()) {
			val round = ExperimentSolvers.SequencerRound()
			val pairs = ExperimentSolvers.PairsMemory<Cell>()
			var cells: List<Cell> = emptyList()
			for ((e, ev) in s.events.withIndex()) {
				val key = "ev|$i|$e"
				out[idx] = key to Golden.run {
					when (ev) {
						is Batch8Corpus.Tick -> {
							cells = ev.cells.map(::cell)
							if (s.superpairs) pairs.observe(cells) { cells[it] } else round.observe(cells)
						}
						is Batch8Corpus.Click -> if (ev.slot in cells.indices && !s.superpairs) round.clicked(ev.slot)
					}
					val paints = cells.indices.mapNotNull { slot ->
						val paint = if (s.superpairs) pairs.paint(cells[slot], slot, s.reveal) else round.paint(cells, slot, s.showNext, s.numbers)
						ops(paint, pairs.seen[slot]?.second).takeIf { it.isNotEmpty() }?.let { "$slot=$it" }
					}
					green += paints.count { "box:8000ff00" in it }
					revealed += paints.count { "item:" in it }
					val allowed = if (s.superpairs) pairs.allowed(cells) else round.allowed(cells)
					"allowed=" + (allowed?.sorted()?.joinToString(",") ?: "null") + " order=" + round.order +
						" pos=" + round.position + " | " + paints.joinToString(";")
				}
				idx++
			}
		}
		assertEquals(out.size, idx)
		Golden.verify("batch8-experiments", out, minCases = 2000)
		assertTrue(green > 300 && revealed > 100, "fixture exercises highlights: green=$green revealed=$revealed")
	}

	@Test
	fun gateKeepsSwitchedOffGamesVanilla() {
		for (s in ExperimentSolvers.Solver.entries) for (u in listOf(true, false)) for (sp in listOf(true, false)) {
			val want = when (s) {
				ExperimentSolvers.Solver.ULTRASEQUENCER -> if (u) s else ExperimentSolvers.Solver.NONE
				ExperimentSolvers.Solver.SUPERPAIRS -> if (sp) s else ExperimentSolvers.Solver.NONE
				ExperimentSolvers.Solver.NONE -> s
			}
			assertEquals(want, ExperimentSolvers.gate(s, u, sp))
		}
	}

	@Test
	fun minions() {
		val repo = Batch8Minions.repo
		val cat = MinionHelper.Catalogue(repo.tiers, repo.names::get, repo.recipes::get, repo.prices::get)
		val menus = Batch8Minions.menus
		val datas = HashMap<Int, MinionHelper.Data>()
		var changes = 0
		val out = Golden.load("batch8-minions").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"base" -> cat.baseFor(k.substringAfter('|')).toString()
					"cost" -> cat.costOf(p[1], p[2].takeIf { it != "null" }).toString()
					"next" -> cat.nextUpgrades(Batch8Minions.crafted[p[1].toInt()]).joinToString(";") { "${it.id.skyblockId}/${it.name}/${it.cost}" }
					"need" -> MinionHelper.moreNeeded(k.substringAfter('|').trim()).toString()
					"menu" -> {
						val data = datas.getOrPut(p[1].toInt()) { MinionHelper.Data() }
						val look = menus[p[1].toInt()][p[2].toInt()].map { MinionHelper.MenuItem(it.name, it.lore) }
						val changed = MinionHelper.readMenu(look, data, cat::baseFor)
						if (changed) changes++
						"$changed ${data.crafted.sorted()} ${data.neededForNextSlot}"
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("batch8-minions", out, minCases = 600)
		assertTrue(changes > 50, "menu reads that changed data: $changes")
	}

	@Test
	fun abiphone() {
		val cases = Batch8Abiphone.cases
		val kinds = HashMap<String, Int>()
		val out = Golden.load("batch8-abiphone").map { (k, _) ->
			k to Golden.run {
				val c = cases[k.substringAfter('|').toInt()]
				val where = AbiphoneHelper.where(c.title, c.sky)
				val sw = AbiphoneHelper.Switches(c.fav, c.only, c.req, c.markers)
				val contact = where == AbiphoneHelper.Where.PHONE && AbiphoneHelper.isContactLore(c.lore)
				val entry = c.entry?.let { AbiphoneHelper.Contact(it.requirement, it.island, it.x, it.y, it.z) }
				val lines = mutableListOf("<0>", "<1>")
				for (a in AbiphoneHelper.tooltipLines(where, sw, c.name, contact, c.stars, entry, c.marked)) {
					val text = a.line.string + "#" + a.line.style.color
					if (a.index != null) lines.add(a.index, text) else lines.add(text)
				}
				val click = when (val d = AbiphoneHelper.decideClick(where, sw, c.name, contact, c.shift, c.stars, entry, c.marked)) {
					Click.Pass -> "pass"
					Click.Swallow -> "protect"
					is Click.Star -> (if (d.starred) "added " else "removed ") + d.name + " protect"
					Click.Unmark -> "unmark"
					is Click.Mark -> "mark ${d.name} ${d.at.x},${d.at.y},${d.at.z} ${d.island} warp=${d.warp}"
				}
				kinds.merge(click.substringBefore(' '), 1, Int::plus)
				val (before, after) = AbiphoneHelper.tint(where, sw, c.name, contact, c.stars)
				lines.joinToString(" / ") + " || " + click + " || " + (before?.let(Integer::toHexString) ?: "-") + " " + (after?.let(Integer::toHexString) ?: "-")
			}
		}
		Golden.verify("batch8-abiphone", out, minCases = 4000)
		for (kind in listOf("pass", "protect", "added", "removed", "unmark", "mark")) assertTrue((kinds[kind] ?: 0) > 2, "click kind $kind: ${kinds[kind]}")
	}

	@Test
	fun bin() {
		val labels = Batch8Bin.labels
		val grid = Batch8Bin.grid
		var warned = 0
		var parsed = 0
		val out = Golden.load("batch8-bin").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"sell" -> BinWarning.sellPrice(labels[p[1].toInt()]).also { if (it != null) parsed++ }.toString()
					"buy" -> BinWarning.buyPrice(labels[p[1].toInt()]).also { if (it != null) parsed++ }.toString()
					"warn" -> {
						val g = grid[p[1].toInt()]
						val a = BinWarning.shouldWarn(g[0] as Long, g[1] as Double, g[2] as Int, g[3] as Int, g[4] as Boolean)
						if (a) warned++
						"$a ${BinWarning.shouldWarnBuy(g[0] as Long, g[1] as Double, g[3] as Int)}"
					}
					"hold" -> {
						val hold = BinWarning.Hold<String>()
						var n = 0
						val log = mutableListOf<String>()
						for (e in Batch8Bin.scripts[p[1].toInt()].split(' ')) when (e[0]) {
							'j' -> log += "j" + hold.judge { if (e[1] == 'W') "w${n++}" else null }
							'y' -> hold.release({ e[1] == 'O' }) { w -> log += "send $w:" + hold.judge { "w${n++}" } }
							'n', 'c' -> hold.drop()
						}
						log.joinToString(",") + " | " + hold.held
					}
					"btn" -> {
						val w = p[1].toInt(); val h = p[2].toInt()
						BinWarning.button(w, h, w / 2 + p[3].toInt() + 0.5, h / 2 + p[4].toDouble()).toString()
					}
					"text" -> {
						val i = p[1].toInt()
						val g = grid[i]
						BinWarning.dialogText("Item $i", g[0] as Long, g[1] as Double, g[4] as Boolean).joinToString(" / ") { (t, dy, c) ->
							(if (t is Component) t.string else t.toString()) + "@$dy#" + Integer.toHexString(c)
						}
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("batch8-bin", out, minCases = 3000)
		assertTrue(warned > 300 && parsed > 50, "warned=$warned parsed=$parsed")
	}

	@Test
	fun auction() {
		val out = Golden.load("batch8-auction").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"split" -> AuctionTools.splitIntoSignLines(Batch8Auction.searches[p[1].toInt()]).joinToString("¦")
					"kind" -> AuctionTools.searchKind(Batch8Auction.signs[p[1].toInt()], Batch8Auction.titles[p[2].toInt()]).toString()
					"push" -> {
						val list = mutableListOf<String>()
						Batch8Auction.pushes[p[1].toInt()].joinToString(" / ") { (search, max) ->
							if (AuctionTools.pushSearch(list, search, max)) list.joinToString("+") else "skip"
						}
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("batch8-auction", out, minCases = 700)
	}

	@Test
	fun search() {
		val byId = Batch8Search.items.associateBy { it.id }
		val items = Batch8Search.items.map { ItemId(it.id) to it.display }
		val out = Golden.load("batch8-search").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"rank" -> SignSearchScreen.rank(Batch8Search.queries[p[1].toInt()], items) { id, name ->
						val item = byId.getValue(id.skyblockId)
						if (p[2].toBoolean()) item.bazaar?.let { SignSearchScreen.Quote(it.first, (it.second + it.second / 3).toDouble()) }
						else SignSearchScreen.auctionQuote(item.bazaar != null, item.lowestBin, name)
					}.joinToString(";") { "${it.label}/${it.id?.skyblockId}/${it.price}" }
					"row" -> SignSearchScreen.rowAt(p[3].toDouble(), p[4].toDouble(), 50, 80, p[1].toInt(), p[2].toInt()).toString()
					"scroll" -> SignSearchScreen.scrolled(p[1].toInt(), p[2].toDouble(), p[3].toInt()).toString()
					else -> error(k)
				}
			}
		}
		Golden.verify("batch8-search", out, minCases = 500)
		assertTrue(out.count { it.first.startsWith("rank") && it.second.length > 50 } > 15)
	}

	@Test
	fun custom() {
		val out = Golden.load("batch8-custom").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"name" -> {
						val n = Batch8Custom.names[p[1].toInt()]
						val edited = ItemCustomizer.edited(ItemCustomizer.Customization(glint = true), n, "")
						ItemCustomizer.parseName(n).string + "¦" + (edited.name ?: "<null>")
					}
					"colour" -> ItemCustomizer.parseColour(Batch8Custom.colours[p[1].toInt()]).toString()
					"text" -> if (p[1] == "null") ItemCustomizer.colourText(null)
					else p[1].toInt().let { ItemCustomizer.colourText(it) + " " + (it or 0xFF000000.toInt()) }
					"glint" -> ItemCustomizer.nextGlint(p[1].toBooleanStrictOrNull()).toString()
					else -> error(k)
				}
			}
		}
		Golden.verify("batch8-custom", out, minCases = 1300)
	}
}
