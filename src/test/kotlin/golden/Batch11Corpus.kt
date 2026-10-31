// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.random.Random

object Batch11Garden {
	val names = listOf("Wheat", "Enchanted Hay Bale", "Jack o' Lantern", "Box of Seeds", "Enchanted Carrot", "Mutant Nether Wart",
		"Cactus", "Red Mushroom", "Sugar Cane", "Pumpkin", "Melon", "Potato", "Cocoa Beans", "Fine Flour", "X", "Big-Tree Sapling")
	private val priceValues = listOf(1.5, 0.0, -5.0, Double.NaN, 1e9, 123456.789, 3.0, 999.0, 2.5e5, 0.01)
	val prices: Map<String, Double> = Random(1101).let { r -> names.filter { r.nextInt(4) > 0 }.associateWith { priceValues.random(r) } }
	val priceByName: (String) -> Double? = { prices[it] }
	val trade: (Int) -> Pair<String, Double>? = { c -> if (c % 7 == 0) null else "§aSkyMart Item ${c % 5}" to c * 41.25 }

	private val reqLines = listOf(" Wheat", " Wheat x1,234", "Enchanted Hay Bale x2", " Jack o' Lantern x10", "Box of Seeds x", " Cactus x1,2,3",
		"  Wheat x3", " Wheat x99999999999", " Fine Flour x64", "Mutant Nether Wart x0", " Unknown Thing x5", "Sugar Cane x1 ", " Potato9 x2",
		" Melon\n", " Pumpkin x١٢", "Big-Tree Sapling x7", " X", "", "   ", "§aWheat x3", " Cocoa Beans x,", "Red Mushroom x5x", "Ãpple x1")
	private val copperLines = listOf("+12 Copper", " +1,234 Copper", "Rewards: +5 Copper and +10 Copper", "+ 5 Copper", "+12Copper",
		"Garden Experience +400, +25 Copper!", "+99999999999 Copper", "+0 Copper", "Copper +5", "+3 Copper\n", "+٣ Copper", "+7 copper", "+14 Copper Coins")
	private val header = listOf("Items Required:", "  Items Required:  ", "Items required:", "Items Required")
	private val other = listOf("Rewards:", "", "§7Click to accept", "Visitor Offer", "+2 Farming XP", " ", "Items Required:")

	val tooltips: List<List<String>> by lazy {
		val r = Random(1102)
		List(400) {
			buildList {
				repeat(r.nextInt(0, 3)) { add(other.random(r)) }
				if (r.nextInt(6) > 0) add(header.random(r))
				repeat(r.nextInt(0, 5)) { add(if (r.nextInt(3) == 0) names.random(r) + " x" + r.nextInt(1, 3000) else reqLines.random(r)) }
				if (r.nextBoolean()) add("")
				repeat(r.nextInt(0, 4)) { add(if (r.nextBoolean()) copperLines.random(r) else other.random(r)) }
				if (r.nextInt(4) == 0) add("+" + r.nextInt(1, 500) + " Copper")
			}
		}
	}
	val singleLines: List<String> get() = reqLines + copperLines + header + other + names

	data class Table(val json: JsonObject, val prices: Map<String, Double?>)
	val tables: List<Table> by lazy {
		val r = Random(1103)
		List(120) { t ->
			val o = JsonObject(); val pr = mutableMapOf<String, Double?>()
			repeat(r.nextInt(0, 9)) { k ->
				val id = "ITEM_${r.nextInt(30)}"
				val e = JsonObject()
				when (r.nextInt(8)) { 0 -> {}; 1 -> e.addProperty("currency", "bits"); 2 -> if (t % 9 == 0) e.add("currency", JsonNull.INSTANCE) else e.addProperty("currency", "copper"); else -> e.addProperty("currency", "copper") }
				when (r.nextInt(8)) { 0 -> {}; 1 -> e.addProperty("price", 0); 2 -> e.addProperty("price", -10); 3 -> e.add("price", JsonPrimitive("25")); else -> e.addProperty("price", r.nextInt(1, 2000)) }
				if (r.nextBoolean()) e.addProperty("display", "§9Display $k")
				o.add(id, e)
				pr[id] = listOf(null, 0.0, Double.NaN, 1e6, r.nextDouble(1.0, 5e6), 50.0, 50.0).random(r)
			}
			if (t % 17 == 0) o.add("BROKEN", JsonPrimitive(5))
			Table(o, pr)
		}
	}
	val coppers = listOf(0, 1, 25, 70, 1234, -3)

	val sequences: List<List<Pair<Long, Long?>>> by lazy {
		val r = Random(1104)
		List(300) {
			var v = r.nextLong(0, 1_000_000)
			List(r.nextInt(1, 40)) {
				val dt = listOf(0L, 1000L, 1000L, 1000L, 5000L, 30_000L, 60_000L, 61_000L, -500L).random(r)
				val reading: Long? = when (r.nextInt(12)) { 0 -> null; 1 -> v - r.nextLong(1, 100); 2 -> v; else -> v + r.nextLong(0, 400) }
				if (reading != null) v = reading
				dt to reading
			}
		}
	}

	val pests = listOf("§6§lYUCK! §2Pest §ehas spawned in Plot - 3!", "YUCK! 2 Pests have spawned in Plot - 12!", "Pests have spawned in Plot - A!",
		"A Pest has spawned in Plot - 4", "YUCK! Pests have spawned in Plot -  5!", "SPests have spawned in Plot - 1!", "3 Pests has spawned in Plot - 9!x",
		"Pest has spawned in Plot - Barn Skin!", "", "Pests have spawned in Plot - 7!\n", "Pests have spawned in Plot - 7! extra")
}
