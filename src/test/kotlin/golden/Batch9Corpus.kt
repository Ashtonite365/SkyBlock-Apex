// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.auction.PriceHistory
import kotlin.random.Random

object Batch9Graph {
	class Case(val points: List<PriceHistory.Point>, val range: Int, val now: Long, val left: Int, val top: Int, val mice: List<Int>)

	val cases: List<Case> by lazy {
		val r = Random(901)
		List(400) { i ->
			val n = when (i % 10) { 0 -> 0; 1 -> 1; 2 -> 2; else -> r.nextInt(2, 40) }
			var t = r.nextLong(1_700_000_000, 1_800_000_000)
			val flat = r.nextInt(6) == 0
			val base = listOf(1.0, 250.0, 1e6, 3e9, 0.1).random(r)
			val pts = List(n) {
				t += when (r.nextInt(8)) { 0 -> 0; 1 -> -r.nextLong(0, 5000); else -> r.nextLong(1, 40_000) }
				val buy = if (flat) base else base * r.nextDouble(0.2, 3.0)
				val sell = if (r.nextInt(3) == 0) null else if (flat) base else base * r.nextDouble(0.1, 2.0)
				PriceHistory.Point(t, buy, sell)
			}
			val last = pts.maxOfOrNull { it.t } ?: t
			val now = last + listOf(0L, 10L, 86_000L, 200_000L, 700_000L, 5_000_000L).random(r)
			val left = r.nextInt(-20, 400)
			val top = r.nextInt(-20, 300)
			val mice = List(6) { left + r.nextInt(30, 310) }
			Case(pts, r.nextInt(4), now, left, top, mice)
		}
	}

	val clicks: List<Pair<Double, Double>> by lazy {
		val r = Random(902)
		List(1500) { Pair(r.nextDouble(180.0, 300.0), r.nextDouble(0.0, 20.0)) } +
			listOf(192.0 to 4.0, 196.0 to 4.0, 220.0 to 15.99, 272.0 to 16.0, 296.0 to 4.0, 295.999 to 4.0)
	}
}

object Batch9Overlays {
	val vars = dev.ashtonite365.apex.util.math.Calculator.VariableProvider { name ->
		mapOf("HYPERION" to 1.2e9, "ENCHANTED_DIAMOND" to 1234.5, "COAL" to 2.0)[name]?.let { java.math.BigDecimal.valueOf(it) }
	}
	val searches: List<String> by lazy {
		val r = Random(903)
		val parts = listOf("1", "2.5", "+", "-", "*", "/", "(", ")", "k", "m", "HYPERION", "COAL", "ENCHANTED_DIAMOND", "x", " ", "!", "^", "1e3", "0")
		listOf("", "!", "!1", "!1+1", "1+1", "!HYPERION*2", "HYPERION", "!coal", "!(1+2)*3", "!1/0", "!  ", "!!1+1") +
			List(500) { (if (r.nextInt(3) > 0) "!" else "") + List(r.nextInt(1, 7)) { parts.random(r) }.joinToString("") } +
			Random(918).let { w ->
				val atoms = listOf("1", "2.5", "12k", "3m", "0.5b", "HYPERION", "COAL", "ENCHANTED_DIAMOND", "(4-1)", "1e3", "7")
				List(300) { (if (w.nextInt(8) > 0) "!" else "") + List(w.nextInt(1, 4)) { atoms.random(w) }.joinToString(listOf("+", "-", "*", "/", " * ").random(w)) }
			}
	}
	val trophyRaw: List<Map<String, List<Int>>> by lazy {
		val r = Random(904)
		List(60) { List(r.nextInt(0, 6)) { "FISH${r.nextInt(8)}" to List(r.nextInt(0, 7)) { r.nextInt(-2, 500) } }.toMap() }
	}
	class Stack(val coreId: String?, val name: String, val count: Int)
	val tallies: List<List<Stack>> by lazy {
		val r = Random(905)
		val tiers = listOf("_BRONZE", "_SILVER", "_GOLD", "_DIAMOND", "_PLATINUM", "")
		List(300) { List(r.nextInt(0, 30)) {
			val core = if (r.nextInt(6) == 0) null else "FISH${r.nextInt(8)}" + tiers.random(r)
			Stack(core, listOf("§9Blobfish", "§fBlobfish", "§6Lavahorse", "Golden Fish", "§9Blobfish§r").random(r), r.nextInt(1, 65))
		} }
	}
	val coinNames: List<String> by lazy {
		val r = Random(906)
		listOf("", "coins", "1 coins", "1,000 coins", "1.5M coins", "2b coins", "3K coins", "1.2.3 coins", ",, coins", "12 coin", "12  coins", "x 5k coins y", "5kcoins", "5 k coins", "١٢ coins") +
			List(300) { List(r.nextInt(0, 8)) { "0123456789.,kKmMbBx ".random(r) }.joinToString("") + listOf(" coins", " coins!", " Coins", "").random(r) }
	}
	class Offer(val id: String?, val name: String, val count: Int)
	val sides: List<List<Offer>> by lazy {
		val r = Random(907)
		List(400) { List(r.nextInt(0, 16)) {
			when (r.nextInt(4)) {
				0 -> Offer(null, coinNames.random(r), 1)
				1 -> Offer(listOf("HYPERION", "COAL", "UNKNOWN", "ENCHANTED_DIAMOND").random(r), "Thing", r.nextInt(1, 65))
				2 -> Offer(listOf("COAL", "HYPERION").random(r), "5k coins", r.nextInt(1, 65))
				else -> Offer(null, "Dirt", r.nextInt(1, 65))
			}
		} }
	}
	val prices = mapOf("HYPERION" to 1.2e9, "ENCHANTED_DIAMOND" to 1234.5, "COAL" to 2.0)
}

object Batch9Timers {
	val instants: List<Long> by lazy {
		val r = Random(908)

		listOf(1710054000000L, 1710050400000L, 1710046800000L, 1730613600000L, 1730610000000L, 1730617200000L, 0L, 1700000000000L, 1700006400000L) +
			List(400) { r.nextLong(1_600_000_000_000L, 1_900_000_000_000L) }
	}

	val waits: List<Triple<Int, Long?, Long>> by lazy {
		val r = Random(909)
		List(1500) {
			val now = instants.random(r)
			val off: Long? = if (r.nextInt(8) == 0) null else when (r.nextInt(4)) {
				0 -> -r.nextLong(0, 3 * 86_400_000L); 1 -> -r.nextLong(0, 5 * 3_600_000L); 2 -> r.nextLong(0, 86_400_000L); else -> listOf(0L, -1L, -14_400_000L, -172_800_000L, -14_399_999L).random(r)
			}
			Triple(r.nextInt(9), off, now)
		}
	}
	val chats = listOf("", "Puzzler gave you 1000 Mithril Powder for solving the puzzle!", "Puzzler gave you  for solving the puzzle!",
		"[NPC] Fetchur: thanks thats probably what i needed", "[NPC] Fetchur: come back another time, maybe tmrw", "[NPC] Fetchur: hi",
		"You've earned 2,000 Mithril Powder from mining your first Mithril Ore of the day!", "You've earned 1 Gemstone Powder from mining your first Gemstone of the day!",
		"Yum! You gain +10 Strength for 48 hours!", "Big Yum! You refresh +5 Speed for 48 hours!", "Yum! You eat cake for 48 hours!",
		"INFUSED! Used one of your free Rift charges!", "xx INFUSED! Used one of your free Rift charges! yy", "puzzler gave you x for solving the puzzle!",
		"Puzzler gave you\nx for solving the puzzle!", "Puzzler gave you x for solving the puzzle!You've earned y Mithril Powder from mining your first Mithril Ore of the day!")
	val menus: List<List<Any?>> by lazy {
		val titles = listOf("Experimentation Table", "Superpairs Rewards", "Dimensional Infusion", "Other", null, "experimentation table")
		val lores = listOf(emptyList(), listOf("Experiments on cooldown!"), listOf("Experiments on cooldown!", "x"), listOf("Click", "FREE! (Every 4 hours)"), listOf("§aFREE! (Every 4 hours) now"))
		val names = listOf(null, "Superpairs", "superpairs", "Superpairs ")
		titles.flatMap { t -> lores.flatMap { a -> names.flatMap { n -> lores.map { b -> listOf(t, a, n, b) } } } }
	}
	val footers = listOf("", "God Potion", "Active Effects\nGod Potion: 1d 2h!", "  God Potion:  12m 3s  \nGod Potion: other", "You have a God Potion active! 5h",
		"x\r\nGod Potion! !: 1h:\n", "No effects", "god potion: 1h", "A\u2028God Potion: 3m", "God PotionGod Potion: 2")
	val tabs: List<List<Pair<String, String>>> by lazy {
		val r = Random(910)
		val names = listOf("Faction Quests:", "  Faction Quests", "Paul's Quest", "Fetch Netherrack", "Kill Ghasts", "", "  ", "Diana thing", "Slay 5 Blazes", "Info:")
		List(300) { List(r.nextInt(0, 14)) {
			val n = names.random(r)
			val f = listOf(" §a", " §c", "§a", " §a§l", "").random(r) + n
			n to f
		} }
	}
}

object Batch9Bag {
	val titles = listOf("Accessory Bag", "Accessory Bag (1/3)", "Accessory Bag (12/12)", "Accessory Bag ()", "Accessory Bag (1/)", "Accessory Bag (/2)",
		"Accessory Bag (1/2/3)", "Accessory Bag (a/2)", "Accessory Bag (١/2)", "Accessory Bag(1/2)", "Accessory Bag (1/2) ", "Accessory Bag (1/2)x",
		"accessory bag", "Accessory Bags", "Accessory Bag  (1/2)", "Accessory Bag (01/2)", "Accessory Bag (1 /2)", "", "Accessory Bag (1/2)\n", "Accessory Bag\n")
	val lore: List<List<String>> by lazy {
		val r = Random(911)
		val heads = listOf("Strength", "Crit Damage", "Health", "Magic Find", "speed", "Sea Creature Chance", "Bonus Attack Speed", "1Str", "", "Ferocity ")
		List(300) { List(r.nextInt(0, 8)) {
			val n = List(r.nextInt(0, 6)) { "0123456789,..".random(r) }.joinToString("")
			heads.random(r) + listOf(": +", ": ", ":+", ": -", ": ++").random(r) + n + listOf("", "%", "% (+2)", " HP", "\nx").random(r)
		} }
	}
	val ids = List(16) { "ACC$it" }
	val rarities = listOf(null) + dev.ashtonite365.apex.util.skyblock.Rarity.entries
	val upgrades: Map<String, List<String>> by lazy {
		val r = Random(912)
		ids.filter { r.nextInt(3) == 0 }.associateWith { List(r.nextInt(0, 3)) { ids.random(r) } }
	}
	val prices: Map<String, Double> by lazy { val r = Random(913); (ids + "EXTRA1" + "EXTRA2").filter { r.nextInt(4) > 0 }.associateWith { Math.rint(r.nextDouble(0.0, 5e7)) } }
	val ignored = setOf("ACC3", "EXTRA2")

	val scans: List<List<Pair<String, dev.ashtonite365.apex.features.accessories.AccessoryBagHelper.Accessory?>>> by lazy {
		val r = Random(914)
		val stats = listOf(mapOf(), mapOf("Strength" to 1.0), mapOf("Strength" to 2.0, "Health" to 5.5), mapOf("Crit Damage" to 1.0))
		List(250) { List(r.nextInt(0, 40)) {
			val key = "${r.nextInt(1, 4)}:${r.nextInt(0, 12)}"
			val e = if (r.nextInt(5) == 0) null else dev.ashtonite365.apex.features.accessories.AccessoryBagHelper.Accessory(
				ids.random(r), rarities.random(r), listOf(null, "hurtful", "itchy", "strong").random(r), stats.random(r))
			key to e
		} }
	}
	val scrolls: List<List<Int>> by lazy { val r = Random(915); List(600) { listOf(r.nextInt(-50, 400), r.nextInt(1, 300), r.nextInt(0, 900), r.nextInt(-10, 700)) } }
}

object Batch9Chest {
	data class S(val name: String, val lore: List<String>, val id: String? = null, val enchants: Map<String, Int> = mapOf(), val count: Int = 1)

	private fun mk(s: S): net.minecraft.world.item.ItemStack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST, s.count).apply {
		set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(s.name))
		set(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore(s.lore.map { net.minecraft.network.chat.Component.literal(it) }))
		if (s.id != null) set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(net.minecraft.nbt.CompoundTag().apply {
			putString("id", s.id)
			if (s.enchants.isNotEmpty()) put("enchantments", net.minecraft.nbt.CompoundTag().apply { s.enchants.forEach { (k, v) -> putInt(k, v) } })
		}))
	}

	fun stacks(slots: List<S?>) = slots.map { it?.let(::mk) ?: net.minecraft.world.item.ItemStack.EMPTY }

	private val loot = listOf("Undead Essence x12", "Wither Essence x3", "Wither Essence x1,000", "Enchanted Book (Feather Falling VI)", "Enchanted Book (Rejuvenate I)",
		"Adaptive Helmet", "Dungeon Chest Key", "Hyperion", "Spirit Bow", "Gold Essence", "Diamond Essence x0", "Undead Essence x", "Recombobulator 3000", "")
	private val costs = listOf("FREE", "100,000 Coins", "250,000 Coins", "1,000,000 Coins", "5 Coins", "Dungeon Chest Key", "Dungeon Chest Key x2", "Requires a Dungeon Chest Key",
		"Infernal Kuudra Key", "Infernal Kuudra Key x3", "Burning Kuudra Key x99999999999", "Requires an Infernal Kuudra Key", "", "Click to open!", "Already opened!", "1,2 Coins")

	val croesus: List<Pair<List<S?>, Boolean>> by lazy {
		val r = Random(916)
		List(160) {
			val slots = MutableList<S?>(36) { null }
			repeat(r.nextInt(0, 7)) {
				val slot = r.nextInt(0, 36)
				slots[slot] = when (r.nextInt(6)) {
					0 -> S(listOf("Go Back", "Close", "Paper").random(r), listOf("To Croesus"))
					1 -> S("Wood", listOf("Contents") + List(r.nextInt(0, 5)) { loot.random(r) } + listOf("", "Already opened!"))
					else -> S(listOf("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock").random(r),
						listOf(if (r.nextInt(12) == 0) "Contentz" else "Contents") + List(r.nextInt(0, 6)) { loot.random(r) } + listOf("", "Cost") + List(r.nextInt(0, 4)) { costs.random(r) })
				}
			}
			slots to (r.nextInt(3) == 0)
		}
	}

	val rewards: List<Pair<String, List<S?>>> by lazy {
		val r = Random(917)
		val reward = listOf(
			{ S("Adaptive Helmet", listOf("Gear Score: 920"), "ADAPTIVE_HELMET") },
			{ S("Adaptive Helmet", listOf(), "STARRED_ADAPTIVE_HELMET") },
			{ S("Adaptive Helmet", listOf()) },
			{ S("Enchanted Book", listOf("Combinable in Anvil"), "ENCHANTED_BOOK", mapOf("feather_falling" to 6)) },
			{ S("Enchanted Book", listOf("Combinable in Anvil"), "ENCHANTED_BOOK", mapOf("protection" to 6, "growth" to 6)) },
			{ S("Enchanted Book", listOf("Combinable in Anvil")) },
			{ S("Undead Essence x21", listOf("Undead Essence can be used to")) },
			{ S("Wither Essence x12", listOf(), "ESSENCE_WITHER") },
			{ S("Dungeon Chest Key", listOf(), "DUNGEON_CHEST_KEY") },
			{ S("Hyperion", listOf("§6Soulbound"), "HYPERION") },
			{ S("Hyperion", listOf(), "HYPERION") },
			{ S("Spirit Bone x3", listOf(), "SPIRIT_BONE", count = 2) },
			{ S("Mystery", listOf(), "UNKNOWN_THING", count = 5) },
			{ S("", listOf(), "") },
		)
		List(200) {
			val slots = MutableList<S?>(54) { null }
			for (i in 10 until 17) if (r.nextInt(3) > 0) slots[i] = reward.random(r)()
			if (r.nextInt(10) > 0) slots[31] = S(if (r.nextInt(15) == 0) "Open Chest" else "Open Reward Chest",
				listOf("Contents") + List(r.nextInt(0, 4)) { loot.random(r) } + (if (r.nextBoolean()) listOf("", "Cost") else listOf("")) + List(r.nextInt(0, 3)) { costs.random(r) })
			if (r.nextBoolean()) slots[50] = S("Reroll Chest", if (r.nextBoolean()) listOf("You already rerolled a chest!") else listOf("Bring a Kismet Feather"))
			listOf("Emerald", "Master Mode Catacombs - Floor VII", "Bedrock", "Paid Chest").random(r) to slots
		}
	}

	val lines = loot + costs + listOf("Enchanted Book (Ultimate Wise I)", "Shiny Essence x2", "Ice Essence x1", "Crimson Essence x4")

	private fun status(sell: Double, buy: Double) = dev.ashtonite365.apex.repo.HypixelStaticData.BazaarStatus(sell, 1, 1, 1, buy, 1, 1, 1)
	private val bazaar = mapOf(
		"ESSENCE_WITHER" to status(2000.0, 2100.0), "ESSENCE_UNDEAD" to status(900.0, 1000.0), "ESSENCE_GOLD" to status(3000.0, 3200.0),
		"DUNGEON_CHEST_KEY" to status(50000.0, 75000.0), "KISMET_FEATHER" to status(1.5e6, 1.7e6), "ENCHANTMENT_FEATHER_FALLING_6" to status(120000.0, 150000.0),
		"ESSENCE_ICE" to status(1.0, 2.0),
	)
	private val lbin = mapOf("ADAPTIVE_HELMET" to 40000.0, "STARRED_ADAPTIVE_HELMET" to 900000.0, "HYPERION" to 1.1e9, "SPIRIT_BONE" to 77.0, "ESSENCE_UNDEAD" to 5.0)

	fun <T> withMarket(block: () -> T): T {
		val h = dev.ashtonite365.apex.repo.HypixelStaticData
		val f = h.javaClass.getDeclaredField("bazaarData").apply { isAccessible = true }
		val oldBz = f.get(h); val oldLb = h.lowestBin
		f.set(h, bazaar.map { (k, v) -> dev.ashtonite365.apex.util.ItemId.BazaarProduct(k) to dev.ashtonite365.apex.repo.HypixelStaticData.BazaarData(dev.ashtonite365.apex.util.ItemId.BazaarProduct(k), v) }.toMap())
		h.lowestBin = lbin.mapKeys { dev.ashtonite365.apex.util.ItemId(it.key) }
		try { return block() } finally { f.set(h, oldBz); h.lowestBin = oldLb }
	}

	val combos = listOf(listOf(true, true, true, true, true), listOf(false, false, false, false, false), listOf(true, false, true, false, true), listOf(false, true, false, true, false))

	fun <T> withToggles(c: List<Boolean>, block: () -> T): T {
		val dc = dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.TConfig
		val kc = dev.ashtonite365.apex.features.kuudra.KuudraProfit.TConfig
		if (runCatching { dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.data }.isFailure) dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.explicitDefaultLoad()
		listOf(dev.ashtonite365.apex.features.items.ItemCustomizer.TConfig, dc, kc).forEach { cfg -> if (cfg.sortedOptions.any { it._actualValue == null }) cfg.explicitDefaultLoad() }
		@Suppress("UNCHECKED_CAST")
		fun opt(cfg: dev.ashtonite365.apex.util.data.ManagedConfig, id: String) = cfg.sortedOptions.first { it.propertyName == id } as dev.ashtonite365.apex.gui.config.ManagedOption<Any>
		val targets = listOf(opt(dc, "include-essence"), opt(dc, "include-chest-key"), opt(dc, "include-kismet"), opt(dc, "croesus-sort-by-profit"), opt(kc, "include-key"))
		val old = targets.map { it._actualValue }
		targets.zip(c).forEach { (o, v) -> o._actualValue = v }
		try { return block() } finally { targets.zip(old).forEach { (o, v) -> o._actualValue = v } }
	}

	fun show(c: dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.ChestValue) =
		"${c.name.string}@${c.slot} cost=${c.cost} value=${c.value} profit=${c.profit} miss=${c.missingPrices} [" + c.items.joinToString(";") { "${it.label.string}=${it.value}" } + "]"
}
