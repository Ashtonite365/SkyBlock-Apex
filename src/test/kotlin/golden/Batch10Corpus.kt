// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch10Maps {
	private const val N = 128
	private val roomIds = listOf(30, 63, 63, 63, 66, 62, 74, 82, 18, 85, 34, 119)
	private val doorIds = listOf(63, 82, 119, 18, 30, 85, 0, 2, 66)
	private val markIds = listOf(34, 30, 18, 63, 0, 119)

	class MapCase(val colors: ByteArray, val failed: Set<Pair<Int, Int>>, val ticks: Boolean, val showFailed: Boolean, val failArgb: Int)

	private fun gen(r: Random): ByteArray {
		val c = ByteArray(N * N)
		fun set(x: Int, y: Int, v: Int) { if (x in 0 until N && y in 0 until N) c[x + y * N] = v.toByte() }
		val room = listOf(16, 18, 14, 20, 15, 13, 21).random(r)
		val step = room + 4
		val ox = r.nextInt(0, step + 6) - 2
		val oy = r.nextInt(0, step + 6) - 2
		val cols = 6; val rows = 6
		val ids = Array(rows) { IntArray(cols) { if (r.nextInt(5) == 0) 0 else roomIds.random(r) } }
		if (r.nextInt(8) > 0) ids[r.nextInt(rows)][r.nextInt(cols)] = 30
		for (row in 0 until rows) for (col in 0 until cols) {
			val id = ids[row][col]; if (id == 0) continue
			val gx = ox + col * step; val gy = oy + row * step
			for (dy in 0 until room) for (dx in 0 until room) set(gx + dx, gy + dy, id)
			if (r.nextInt(3) == 0) {
				val m = markIds.random(r)
				for (dy in -3..3) for (dx in -3..3) if (r.nextInt(3) > 0) set(gx + room / 2 + dx, gy + room / 2 + dy, m)
				set(gx + room / 2, gy + room / 2, m)
			}
			if (col + 1 < cols && ids[row][col + 1] != 0 && r.nextBoolean()) {
				if (r.nextInt(4) == 0 && ids[row][col + 1] == id) for (dy in 0 until room) for (dx in 0 until 4) set(gx + room + dx, gy + dy, id)
				else { val d = doorIds.random(r); for (a in 0 until 4) for (b in -2..1) if (r.nextInt(6) > 0) set(gx + room + a, gy + room / 2 + b, d) }
			}
			if (row + 1 < rows && ids[row + 1][col] != 0 && r.nextBoolean()) {
				if (r.nextInt(4) == 0 && ids[row + 1][col] == id) for (dy in 0 until 4) for (dx in 0 until room) set(gx + dx, gy + room + dy, id)
				else { val d = doorIds.random(r); for (a in 0 until 4) for (b in -2..1) if (r.nextInt(6) > 0) set(gx + room / 2 + b, gy + room + a, d) }
			}
		}
		repeat(r.nextInt(0, 30)) { set(r.nextInt(N), r.nextInt(N), r.nextInt(256)) }
		return c
	}

	val maps: List<MapCase> by lazy {
		val r = Random(1001)
		List(260) {
			val colors = when (it % 13) {
				0 -> ByteArray(N * N) { r.nextInt(256).toByte() }
				1 -> ByteArray(N * N) { (if (r.nextInt(4) == 0) 30 else 0).toByte() }
				else -> gen(r)
			}
			MapCase(colors, List(r.nextInt(0, 4)) { r.nextInt(-1, 7) to r.nextInt(-1, 7) }.toSet(), r.nextInt(4) > 0, r.nextInt(4) > 0,
				listOf(0xFFFF5555.toInt(), 0x80FF00FF.toInt(), 0).random(r))
		}
	}

	val yaws: List<Float> by lazy { val r = Random(1002); listOf(0F, 90F, 180F, -180F, 270F, -90F, 359.9F, 360F, 720F, -0F, Float.NaN) + List(300) { r.nextFloat() * 1440F - 720F } }

	val pairings: List<Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>>> by lazy {
		val r = Random(1003)
		List(500) {
			val m = List(r.nextInt(0, 6)) { r.nextInt(0, 64) / 2.0 to r.nextInt(0, 64) / 2.0 }
			val e = List(r.nextInt(0, 6)) { (if (m.isNotEmpty() && r.nextBoolean()) m.random(r).let { p -> (p.first + r.nextDouble(-4.0, 4.0)) to (p.second + r.nextDouble(-4.0, 4.0)) } else r.nextDouble(0.0, 32.0) to r.nextDouble(0.0, 32.0)) }
			m to e
		}
	}

	fun digest(a: IntArray): String {
		val md = java.security.MessageDigest.getInstance("SHA-256")
		val bb = java.nio.ByteBuffer.allocate(a.size * 4); a.forEach { bb.putInt(it) }
		return md.digest(bb.array()).take(12).joinToString("") { "%02x".format(it) } + " nz=" + a.count { it != 0 }
	}
}

object Batch10Museum {
	private val r0 = Random(1010)
	val ids = List(24) { "ITEM_$it" }
	private fun table(seed: Int, p: Int, gen: (Random) -> Double?): Map<String, Double?> { val r = Random(seed); return ids.associateWith { if (r.nextInt(p) == 0) gen(r) else null } }
	private val prices = { r: Random -> listOf(0.0, -1.0, 1.0, 5e5, 1.25e6, r.nextDouble(1.0, 5e7), Double.NaN, 99.5).random(r) }
	val bin = table(1011, 2, prices)
	val bid = table(1012, 2, prices)
	val stale = table(1013, 3, prices)
	val lowConf = table(1014, 3, prices)
	val binDense = table(1023, 1, prices)
	val bidDense = table(1024, 2, prices)
	val bazaar = Random(1015).let { r -> ids.associateWith { r.nextInt(5) == 0 } }
	val noLive = Random(1016).let { r -> ids.associateWith { r.nextInt(5) == 0 } }
	val names = Random(1017).let { r -> ids.associateWith { listOf("Helmet", "Chestplate of the Very Long Name", "§6Boots", "Leggings  ", "Sword").random(r) + " $it" } }
	val rowNames = listOf("§fLapis Pickaxe", "Young Dragon Armor", "§5Very Long Museum Entry Name That Needs Cutting", "  Spaced  ", "§d§lSet", "Ice")

	fun <M> rows(make: (String, List<dev.ashtonite365.apex.util.ItemId>, String, Int, (dev.ashtonite365.apex.util.ItemId) -> Double?, (dev.ashtonite365.apex.util.ItemId) -> Double?,
	                    (dev.ashtonite365.apex.util.ItemId) -> Double?, (dev.ashtonite365.apex.util.ItemId) -> Boolean, (dev.ashtonite365.apex.util.ItemId) -> Double?,
	                    (dev.ashtonite365.apex.util.ItemId) -> Boolean, (dev.ashtonite365.apex.util.ItemId) -> String) -> M): List<M> {
		val r = Random(1018)
		return List(700) { i ->
			val n = listOf(0, 1, 1, 1, 2, 3, 4).random(r)
			make(rowNames.random(r) + i, List(n) { dev.ashtonite365.apex.util.ItemId(ids.random(r)) }, listOf("Combat", "Armor Sets").random(r), r.nextInt(0, 3),
				{ bin[it.skyblockId] }, { bid[it.skyblockId] }, { stale[it.skyblockId] }, { bazaar[it.skyblockId] == true }, { lowConf[it.skyblockId] }, { noLive[it.skyblockId] == true },
				{ names[it.skyblockId] ?: it.skyblockId })
		}
	}

	val groups: List<List<Int>> by lazy { val r = Random(1019); List(300) { List(r.nextInt(0, 9)) { r.nextInt(700) } } }

	fun fmt(d: Double) = if (d.isNaN()) "NaN" else "%.2f".format(java.util.Locale.ROOT, d)

	class Donations(val cats: List<Pair<String, List<Triple<String, List<String>, Boolean>>>>, val child: Map<String, String>)
	val donations: List<Donations> by lazy {
		val r = Random(1020)
		List(250) {
			val child = List(r.nextInt(0, 14)) { ids.random(r) to ids.random(r) }.toMap()
			Donations(List(r.nextInt(0, 4)) { c -> listOf("Combat", "Mining", "Armor Sets", "Special").random(r) + c to
				List(r.nextInt(0, 8)) { e -> Triple("Entry$e", List(listOf(0, 1, 1, 2).random(r)) { ids.random(r) }, r.nextInt(3) == 0) } }, child)
		}
	}

	val titles = listOf("Museum", "  Museum ", "Your Museum", "Museum ➜ Combat", "Museum ➜ Combat (2/3)", "Museum ➜ Combat(2/3)", "Museum ➜ Combat (2/3) ", "Museum ➜ Combat (a/3)",
		"Museum ➜ ", "museum ➜ Armor Sets (1/12)", "Bank ➜ Deposit", "Museum ➜ A ➜ B (1/2)", "Museum ➜ Special Items (３/4)", "Museum ➜ X (1/2)\n", "Museum➜Weapons", "MUSEUM ➜ Rarities ( 1/2)", "Museum ➜ (1/2)", "")

	val setNames = listOf("Young Dragon Armor", "Shadow Assassin's Armor", "Farm Suit", "Rancher's Boots Set", "Angler - Armor", "Something Equipment", "Super Set Armor",
		"  Lapis Armor  ", "Mineral Outfit", "Armor", "Fake")
	val museumJson: com.google.gson.JsonObject by lazy {
		com.google.gson.JsonParser.parseString("""{"set_exceptions":{"SHADOW_ASSASSIN":"SHADOW_ASSASSIN_SET","FAKE":"NOPE"},"sets_to_items":{"YOUNG_DRAGON":["YOUNG_DRAGON_HELMET","YOUNG_DRAGON_BOOTS"],
			"SHADOW_ASSASSIN_SET":["SA_HELM"],"FARM_ARMOR":["FARM_HAT"],"RANCHER'S_BOOTS":["RB"],"ANGLER":["ANGLER_HAT","ANGLER_X"],"LAPIS_ARMOR":["LAPIS_HELM"],"MINERAL":["M1","M2","M3"],"SUPER_SET":["S"]}}""").asJsonObject
	}

	data class St(val item: String, val name: String, val lore: List<String>)
	val stacks: List<St> by lazy {
		val r = Random(1021)
		val items = listOf("gray_dye", "lime_dye", "black_stained_glass_pane", "gray_stained_glass_pane", "arrow", "barrier", "diamond_sword", "air", "red_dye")
		val lore = listOf("Not Donated", "not donated yet", "Missing items", "Donated", "", "Click to donate")
		List(400) { St(items.random(r), listOf("Lapis Pickaxe", " Sword ", "Lapis Pickaxe", "Helm").random(r), List(r.nextInt(0, 3)) { lore.random(r) }) }
	}
	fun stack(s: St): net.minecraft.world.item.ItemStack {
		val item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(s.item))
		if (item == net.minecraft.world.item.Items.AIR) return net.minecraft.world.item.ItemStack.EMPTY
		return net.minecraft.world.item.ItemStack(item).apply {
			set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(s.name))
			set(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore(s.lore.map { net.minecraft.network.chat.Component.literal(it) }))
		}
	}

	val pages: List<List<St?>> by lazy { val r = Random(1022); List(120) { List(54) { if (r.nextInt(4) == 0) null else stacks.random(r) } } }

	val truncNames = listOf("", "a", "Short", "A Rather Long Name Indeed", "Trailing   space here", "§fColour", "ééééééééééé", "x y z w v u")
}

object Batch10MuseumShow {
	private val M = dev.ashtonite365.apex.features.museum.MuseumHelper
	val rows by lazy { Batch10Museum.rows { n, i, c, l, a, b, s, bz, lc, nl, nm -> dev.ashtonite365.apex.features.museum.MuseumHelper.Missing(n, i, c, l, a, b, s, bz, lc, nl, nm) } }
	fun show(row: dev.ashtonite365.apex.features.museum.MuseumHelper.Missing): String {
		val f = Batch10Museum::fmt
		val shown = dev.ashtonite365.apex.features.museum.CheapestList.truncate(row.name.replace(Regex("§."), "").trim(), 60) { it.length * 6 }
		return "q=${row.quote} p=${row.price} st=${row.staleQuote} lc=${row.lowConfQuote} nl=${row.noLive} bo=${row.bidOnly} bq=${row.bidQuote} se=${row.setEstimate} " +
			"mp=${row.missingPieces} pieces=${row.pieces} bp=${row.buyPiece} bn=${row.buyName()} est=${row.estimate} ev=${row.estimateValue} set=${row.isSet}" +
			" | ${M.priceText(row, f)} | ${M.rowTooltip(row, shown, f) { Batch10Museum.names[it.skyblockId] ?: "?" }} | ${M.rowTooltip(row, row.name, f)} | ${M.buyCommand(row)}"
	}
	fun data(d: Batch10Museum.Donations) = dev.ashtonite365.apex.features.museum.MuseumHelper.Data(
		d.cats.associateTo(LinkedHashMap()) { (c, es) -> c to es.associateTo(LinkedHashMap()) { (n, ids, don) -> n to dev.ashtonite365.apex.features.museum.MuseumHelper.Entry(ids, don) } })
}
