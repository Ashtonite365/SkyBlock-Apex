// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.ashtonite365.apex.features.garden.GardenGreenhouse
import dev.ashtonite365.apex.features.garden.GardenJacobExtras
import dev.ashtonite365.apex.features.garden.GardenPestTimers
import dev.ashtonite365.apex.features.garden.GardenPlotMenu
import dev.ashtonite365.apex.features.garden.GardenProgress
import dev.ashtonite365.apex.features.garden.GardenTools
import dev.ashtonite365.apex.features.garden.GardenVisitorExtras
import kotlin.random.Random

object P2aCorpus {
	private val r = Random(0x2A2A)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	private val numerals = listOf("I", "IV", "V", "IX", "XIV", "XV", "XX", "XLIX", "L", "C", "IC", "IIII", "MC", "15", "0", "-2", "", "xv", "XXV", "LXXX")
	private val cropWords = GardenProgress.Crop.entries.flatMap { listOf(it.display, it.display.uppercase(), it.name, " ${it.display.lowercase()} ") } +
		listOf("Melon Slice", "melon slice", "Cocoa", "Wheat Seeds", "", " ", "Sugar  Cane", "Nether Warts")
	val menuNames = List(900) {
		when (r.nextInt(5)) {
			0 -> cropWords.pick()
			1 -> "${cropWords.pick()} ${numerals.pick()}"
			2 -> " ${cropWords.pick()} ${numerals.pick()} "
			3 -> numerals.pick()
			else -> "${numerals.pick()} ${cropWords.pick()}"
		}
	}
	val tools = GardenProgress.Crop.entries.flatMap { it.toolPrefixes } .flatMap { listOf(it, it + "_2", it + "_3", it.lowercase(), it.drop(2)) } + listOf("", "DIAMOND_HOE", "MELON_DICER_3")

	private val stepPool = listOf(-5L, 0L, 1L, 2L, 7L, 30L, 100L, 1000L, 5_000L, 25_000L, 100_000L, 1_000_000L, Long.MAX_VALUE / 4)
	fun table(): List<Long> = List(r.nextInt(0, 9)) { if (r.nextInt(5) == 0) stepPool.pick() else r.nextLong(1, 200_000) }
	val tables = List(250) { table() }
	private val totalPool = listOf(-1L, 0L, 1L, 99L, 100L, 1_000_000L, Long.MAX_VALUE, Long.MIN_VALUE, 3_000_000_000L)
	fun total(): Long = if (r.nextInt(4) == 0) totalPool.pick() else r.nextLong(0, 3_000_000)
	val milestoneInputs = List(1500) { Triple(r.nextInt(tables.size), total(), r.nextBoolean()) }
	val goalInputs = List(700) { Triple(r.nextInt(tables.size), total(), r.nextInt(-2, 60)) }

	private val keys = GardenProgress.Crop.entries.map { it.name } + listOf("wheat", "Sugar Cane", "nether wart", "CACTI", "", "MELON ")
	private val values = listOf("5", "50", "0", "-3", "1.5k", "500k", "2m", "abc", "", "12=3", " 7 ", "1,000", "3.2M", "99999999999")
	val assignTexts = List(500) {
		List(r.nextInt(0, 6)) {
			when (r.nextInt(6)) {
				0 -> keys.pick()
				1 -> "${keys.pick()}=${values.pick()}=x"
				else -> "${if (r.nextBoolean()) " " else ""}${keys.pick()}${if (r.nextBoolean()) " = " else "="}${values.pick()}"
			}
		}.joinToString(if (r.nextBoolean()) "," else ", ")
	}
	val romanInputs = numerals + List(300) { List(r.nextInt(0, 7)) { "IVXLCM1a ".random(r) }.joinToString("") }

	private val loreBits = listOf("Total: 1,234,567", "  Total: 0", "Total: ,,", "Total: 99999999999999999999", "Progress: 1.5k/2k",
		"Progress to Tier XV: 12,345/100,000", "3m/5m", "abc", "", "  10/20", "Total:12", "1.2.3k/4", "Total: 12 (rank)", "5K/10K")
	val menuLore = List(600) { Triple(r.nextInt(tables.size), r.nextInt(-1, 12), List(r.nextInt(0, 4)) { loreBits.pick() }) }

	private val ctr = listOf(null, 0L, 5L, 100L, 1000L, -4L, Long.MAX_VALUE)
	val counterPairs = List(300) { (ctr.pick() to ctr.pick()) to (ctr.pick() to ctr.pick()) }

	val bestInputs = List(400) {
		val totals = GardenProgress.Crop.entries.filter { r.nextInt(3) > 0 }.associate { it.name to total() } +
			(if (r.nextInt(5) == 0) mapOf("NOT_A_CROP" to 5L) else emptyMap())
		totals to GardenProgress.Crop.entries.associateWith { tables.pick() }
	}
	val ffInputs = List(300) { Triple(listOf(0L, 1L, 100_000L, 500_000L, 1_500_000L, 3_000_000L, -9L).pick(), GardenProgress.Crop.entries.pick(), listOf(20.0, 18.5, 0.0, 1.0, -1.0, 40.0).pick()) }
	val timeInputs = List(400) { listOf(-5L, 0L, 59L, 1000L, 123_456L, 9_000_000L, Long.MAX_VALUE).pick() to listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.5, 60.0, 1234.5, 90000.0).pick() }

	fun garden(): JsonObject = JsonObject().apply {
		if (r.nextInt(6) == 0) return@apply
		val up = JsonObject()
		for (k in listOf("speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction", "weird")) {
			if (r.nextInt(4) == 0) continue
			val levels = JsonObject()
			for (lv in 1..r.nextInt(1, 6)) {
				val e = JsonObject()
				if (r.nextInt(5) > 0) e.add("items", JsonObject().apply { repeat(r.nextInt(0, 4)) { addProperty(listOf("wheat", "enchanted_hay_bale", "box_of_seeds", "Compost").pick() + it, r.nextInt(1, 600)) } })
				if (r.nextInt(4) > 0) e.addProperty("copper", r.nextInt(0, 5000))
				if (r.nextInt(30) == 0) e.add("copper", JsonArray())
				levels.add("$lv", e)
			}
			if (r.nextInt(20) == 0) levels.addProperty("2", "bad")
			up.add(k, levels)
		}
		add("composter_upgrades", up)
	}
	val gardens = List(80) { garden() }
	val upgradeInputs = List(600) { Triple(r.nextInt(gardens.size + 1), listOf("speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction", "weird", "x").pick(), r.nextInt(-1, 7)) }

	private val tabPool = listOf("Visitors: (3)", " Visitors: (5) ", "Visitors: (99999999999)", "Visitors: ()", "Visitors (2)", "Garden Level: XII", "", "Visitors: (0) Next", "visitors: (1)")
	val visitorTabs = List(200) { List(r.nextInt(0, 4)) { tabPool.pick() } }

	private val pestPool = listOf(" Cooldown: READY", " Cooldown: 1m 58s", " Cooldown: 29m", " Cooldown: 2h 3m 4s", " Cooldown: MAX PESTS", "Cooldown: ",
		" Cooldown: 5s", " Cooldown: 12m 5", " Alive: 8", " Alive: 8 pests", " Alive: 80", " Alive: 3", " Bonus: INACTIVE", " Bonus: +200☘ 5m 2s",
		" Bonus: +60☘ 29m", " Bonus: +99999999999☘ 1m", " Bonus: +50☘", " Bonus: +", " Bonus Pest Chance: ൠ70", " Bonus Pest Chance: ൠ12.5",
		" Bonus Pest Chance: ൠ1,200", " Bonus Pest Chance: ൠ.", "Pests:", " Plots: 1, 2", "", " Bonus: +100☘ 59s")
	val pestLists = List(900) { List(r.nextInt(0, 6)) { pestPool.pick() } }
	val spores = listOf("VERY RARE CROP! Burrowing Spores", "VERY RARE CROP! ✿ Burrowing Spores (x2)", "RARE CROP! Burrowing Spores", "VERY RARE CROP! Burrowing", "") +
		List(40) { "VERY RARE CROP! " + listOf("", "x ", "Burrowing Spores", "Spores").pick() + listOf("", "!", " Burrowing Spores").pick() }
	private val tabTimes = listOf("1m 58s", "29m", "2h 3m", "5s", "0s", "1H 2M", "abc", "", "12m 5", "3h", "59s", "1m", "100m 100s", "9999999999999h")
	val tabEndInputs = List(1200) {
		val now = 1_700_000_000_000L + r.nextLong(0, 1_000_000)
		val cur = when (r.nextInt(4)) { 0 -> null; 1 -> now + r.nextLong(-200_000, 7_200_000); else -> now + r.nextLong(-70_000, 3_700_000) }
		Triple(tabTimes.pick(), now, cur)
	}
	val formatInputs = listOf(-5L, 0L, 1L, 999L, 1000L, 1001L, 59_999L, 60_000L, 3_599_999L, 3_600_000L, 86_400_000L, Long.MAX_VALUE - 1000) + List(300) { r.nextLong(-1000, 20_000_000) }

	val pestSequences = List(160) { List(r.nextInt(1, 14)) { Triple(r.nextLong(0, 120_000), pestLists.pick(), r.nextInt(5)) } }

	val rarityMaps = listOf(emptyMap(), mapOf("Jacob" to "common", "Rhys" to "legendary", "Spaceman" to "special", "Odawa" to "very_special", "Duke" to "weird", "Lazy Miner" to "mythic", "Bob" to "rare", "Zed" to "epic", "Arg" to "uncommon"))
	val visitorNames = listOf("Jacob", "Rhys", "Spaceman", "Odawa", "Duke", "Lazy Miner", "Bob", "Zed", "Arg", "Nobody", "")
	private val rewardPool = listOf("+20 Copper", "+1,200 Farming XP", "+5k Farming Experience", "+3.5k Garden Experience", "+2 Bits", "Fine Flour x3",
		"+2x Gold Essence", "Jungle Key", "  +12 Copper  ", "+1.2.3 Copper", "x5", "+ Copper", "Something x", "+10 Mithril Powder", "+1,000 Gemstone Powder",
		"Overgrown Grass", "Copper", "+7 Bits", "+12k Copper", "Dedication IV", "+1x Pet Cake")
	val rewards = rewardPool + List(300) { rewardPool.pick().let { if (r.nextBoolean()) " $it" else it } }
	private val chatPool = rewardPool + listOf("OFFER ACCEPTED with Jacob (UNCOMMON)", "OFFER ACCEPTED with Lazy Miner (LEGENDARY)", "OFFER ACCEPTED with X (rare)",
		"OFFER ACCEPTED with Y (MYTHIC)", "  OFFER ACCEPTED with Odawa (SPECIAL)  ", "REWARDS", "+20 Copper Coins", "+abc Bits", "+1.5k Bits")
	val statSequences = List(200) { List(r.nextInt(0, 20)) { chatPool.pick() } }
	val amounts = listOf("1,250", "2.5k", "k", "", " 7 ", "1e3", "NaN", "abc", "12.9", "-4", "1,2,3k", "3K") + List(60) { "${r.nextInt(0, 99999)}${listOf("", "k", ".5", ".5k").pick()}" }
	val statusInputs = List(300) {
		val items = listOf("Wheat", "Carrot", "Fine Flour", "Box of Seeds")
		val wanted = if (r.nextInt(5) == 0) null else items.filter { r.nextBoolean() }.associateWith { r.nextInt(0, 400) }
		wanted to items.filter { r.nextBoolean() }.associateWith { r.nextInt(0, 400) }
	}

	private val plotTitles = listOf("Plot - 1", "Plot - Gibbon", " Plot - Bar ", "Plot -", "The Barn", "Plot - ", "")
	private val plotLore = listOf("This plot has 5 Pests!", "This plot has 1 Pest!", "ൠ This plot has 2 nasty Pests! ൠ", "This plot has x Pests!", "Cost:", "Cost: 5 Compost",
		"Pasting in progress: 3s", "Click to teleport!", "", "This plot has 99999999999 Pests!")
	val plotInputs = List(500) { Triple(plotTitles.pick(), List(r.nextInt(0, 5)) { plotLore.pick() }, Triple(r.nextInt(-1, 26), listOf(null, -1, 0, 5, 12, 24).pick(), listOf(null, 0L, 1L, 30L, 4_000_000_000L).pick())) }
	private val costPool = listOf("Cost", "Cost:", " Cost: ", "Costs", "Cost: 4", "Compost x2", "Enchanted Hay Bale x1,024", "Wheat", "  Jacob's Ticket x3  ", "123", "", "Dirt x", "Box Of Seeds x05")
	val costInputs = List(400) { List(r.nextInt(0, 5)) { costPool.pick() } }

	val gainInputs = List(600) {
		val inc = listOf(1, 3, 10, 50, 100, 0, -1).pick()
		listOf(r.nextDouble(0.0, 2_000_000.0), r.nextDouble(0.0, 2_000_000.0), r.nextDouble(0.0, 120.0), inc.toDouble(), if (r.nextBoolean()) 1.0 else 0.0)
	}
	private val months = listOf("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn",
		"Early Winter", "Winter", "Late Winter", "Mid Winter", "spring", "")
	val dateNames = List(500) { "${months.pick()} ${r.nextInt(0, 33)}${listOf("st", "nd", "rd", "th", "", "x").pick()}, Year ${listOf(r.nextInt(0, 500).toString(), "", "99999999999999999999").pick()}" .let { if (r.nextInt(5) == 0) " $it " else it } }
	private val medalPool = listOf("You placed in the GOLD bracket!", "  You placed in the DIAMOND bracket!  ", "You placed in the gold bracket!", "Contest boosted by Finnegan!", "You placed in the  bracket!", "x")
	val medalLore = List(200) { List(r.nextInt(0, 4)) { medalPool.pick() } }
	val cropNames = listOf("Wheat", " sugar cane ", "Nether Wart", "Cocoa Beans", "", "mUsHrOoM")

	val ringInputs = List(250) {
		val x = r.nextDouble(-200.0, 200.0); val z = r.nextDouble(-200.0, 200.0)
		val radius = when (r.nextInt(4)) {
			0 -> r.nextDouble(0.0, 32.0)
			1 -> GardenGreenhouse.distanceFromVolume(r.nextFloat())
			else -> { val bx = kotlin.math.floor(x) + 0.5 + r.nextInt(-20, 21); val bz = kotlin.math.floor(z) + 0.5 + r.nextInt(-20, 21); kotlin.math.hypot(bx - x, bz - z) }
		}
		Triple(if (r.nextInt(10) == 0) kotlin.math.floor(x) + 0.5 else x, z, radius)
	}
	val volumes = listOf(0f, 1f, 0.5f, -1f, 2f, Float.NaN, 0.123f) + List(40) { r.nextFloat() }
	private val diagLore = listOf("Status: Harvestable", "Status: Growing", "Drops: 3 Wheat", "Rewards: 1 Copper", "Drops:", "This crop has enough water to", "This crop doesn't need water!", "  Status: Harvestable", "")
	val diagInputs = List(300) { List(r.nextInt(0, 5)) { diagLore.pick() } }

	private fun pestReset() {
		GardenPestTimers.cooldownEnd = null; GardenPestTimers.ready = false; GardenPestTimers.maxPests = false; GardenPestTimers.cooldownSeen = false
		GardenPestTimers.lastSpawn = null; GardenPestTimers.spawnTimes.clear(); GardenPestTimers.bonusFortune = 0; GardenPestTimers.bonusEnd = null
		GardenPestTimers.bonusPestChance = null
	}

	private fun pestState() = GardenPestTimers.run { "$cooldownEnd,$ready,$maxPests,$cooldownSeen,$lastSpawn,$spawnTimes,$bonusFortune,$bonusEnd,$bonusPestChance,${averageSpawn()}" }

	fun cases(): List<Pair<String, String>> {
		val cfg = GardenTools.TConfig
		if (cfg.sortedOptions.any { it._actualValue == null }) cfg.explicitDefaultLoad()
		val out = mutableListOf<Pair<String, String>>()
		fun add(k: String, f: () -> Any?) { out += k to Golden.run { f().toString() } }
		val P = GardenProgress

		for ((i, n) in menuNames.withIndex()) add("menu|$i") { "${GardenProgress.Crop.fromMenuName(n)} ${GardenProgress.Crop.byDisplay(n)}" }
		for ((i, t) in tools.withIndex()) add("tool|$i") { GardenProgress.Crop.forTool(t) }
		for ((i, m) in milestoneInputs.withIndex()) add("ms|$i") { P.milestoneFromTotal(tables[m.first], m.second, m.third) }
		for ((i, g) in goalInputs.withIndex()) add("goal|$i") { P.cropsToTier(tables[g.first], g.second, g.third) }
		for ((i, t) in assignTexts.withIndex()) add("assign|$i") { "${P.parseGoals(t)} ${P.parseThresholds(t)}" }
		for ((i, s) in romanInputs.withIndex()) add("roman|$i") { P.roman(s) }
		for ((i, m) in menuLore.withIndex()) add("total|$i") { P.totalFromMenu(tables[m.first], m.second, m.third) }
		for ((i, c) in counterPairs.withIndex()) add("ctr|$i") { P.counterDelta(c.first, c.second) }
		for ((i, b) in bestInputs.withIndex()) add("best|$i") { P.bestCrop(b.first) { b.second.getValue(it) } }
		for ((i, f) in ffInputs.withIndex()) add("ff|$i") { P.ffNeeded(f.first, f.second, f.third).toRawBits() }
		for ((i, t) in timeInputs.withIndex()) add("time|$i") { P.timeLeft(t.first, t.second) }
		for ((i, u) in upgradeInputs.withIndex()) add("up|$i") { P.upgradeCost(gardens.getOrNull(u.first), u.second, u.third) }
		for ((i, t) in visitorTabs.withIndex()) add("vis|$i") { P.visitorCount(t) }
		add("milestoneTable") { gardens.take(5).map { g -> GardenProgress.Crop.entries.map { P.milestoneTable(g, it) } } + P.milestoneTable(null, GardenProgress.Crop.WHEAT) }

		val T = GardenPestTimers
		for ((i, l) in pestLists.withIndex()) add("pest|$i") { "${T.parseCooldown(l)} ${T.parseBonus(l)} ${T.parseBonusPestChance(l)}" }
		for ((i, s) in spores.withIndex()) add("spores|$i") { T.isBurrowingSpores(s) }
		for ((i, e) in tabEndInputs.withIndex()) add("tabend|$i") { T.tabEndTime(e.first, e.second, e.third) }
		for ((i, ms) in formatInputs.withIndex()) add("fmt|$i") { T.format(ms) }
		val pestOn = mapOf("pest-spawn-timer" to true, "pest-bonus-display" to true, "bonus-pest-chance-display" to true)
		for ((i, seq) in pestSequences.withIndex()) add("pseq|$i") { P2Opts.with(cfg, if (i % 4 == 0) emptyMap() else if (i % 4 == 3) pestOn + ("pest-custom-cooldown" to true) else pestOn) {
			pestReset()
			var now = 1_700_000_000_000L
			seq.joinToString(" ; ") { (dt, lines, op) ->
				now += dt
				val r1 = Golden.run {
					when (op) { 0 -> T.onPestSpawn(now); 1 -> T.onCropProgress(now); else -> T.update(lines, now) }
					pestState()
				}
				r1 + " | " + Golden.run { T.hudLines(now).joinToString("/") { it.string } }
			}
		} }

		val V = GardenVisitorExtras
		for ((mi, m) in rarityMaps.withIndex()) for ((ni, n) in visitorNames.withIndex()) add("colour|$mi|$ni") { V.colouredName(n, m) }
		for ((i, s) in rewards.withIndex()) add("reward|$i") { V.compactReward(s) }
		for ((i, s) in amounts.withIndex()) add("amount|$i") { V.parseAmount(s) }
		for ((i, seq) in statSequences.withIndex()) add("stats|$i") {
			val st = GardenVisitorExtras.Stats()
			val counted = seq.map { V.record(st, it) }
			"$counted $st ${V.statLines(st)}"
		}
		for (on in listOf(false, true)) P2Opts.with(cfg, mapOf("visitor-colour-names" to on)) {
			for ((i, s) in statSequences.take(40).withIndex()) add("compact|$on|$i") { V.compactMessage(visitorNames[i % visitorNames.size], s.map(V::compactReward)) }
		}
		for ((i, s) in statusInputs.withIndex()) add("status|$i") { V.status(s.first, s.second) }

		val M = GardenPlotMenu
		for (id in -3..30) add("slotOf|$id") { M.slotOf(id) }
		for (slot in -10..60) add("plotAt|$slot") { M.plotAtSlot(slot) }
		for ((i, p) in plotInputs.withIndex()) add("plot|$i") {
			val info = M.readPlot(p.first, p.second)
			"$info ${M.status(info, p.third.first, p.third.second, p.third.third)}"
		}
		for ((i, c) in costInputs.withIndex()) add("cost|$i") { M.costLine(c) }

		val J = GardenJacobExtras
		for ((i, c) in cropNames.withIndex()) add("cropKey|$i") { J.cropKey(c) }
		for ((i, g) in gainInputs.withIndex()) add("gain|$i") { J.gainMessage(g[0], g[1], g[2], cropNames[i % cropNames.size], g[3].toInt(), g[4] > 0.5) }
		for ((i, d) in dateNames.withIndex()) add("date|$i") { J.contestStart(d)?.let { "$it ${J.realTimeLine(it)}" } }
		for ((i, l) in medalLore.withIndex()) add("medal|$i") { J.medal(l) }

		val G = GardenGreenhouse
		for ((i, c) in ringInputs.withIndex()) add("ring|$i") { G.candidatesAt(c.first, c.second, c.third).joinToString(",") { "${it.x}:${it.y}:${it.z}" } }
		for ((i, v) in volumes.withIndex()) add("vol|$i") { G.distanceFromVolume(v).toRawBits() }
		for ((i, l) in diagInputs.withIndex()) add("diag|$i") { "${G.harvestStatus(l)} ${G.hasEnoughWater(l)}" }
		return out
	}
}
