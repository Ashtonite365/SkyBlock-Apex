// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.garden

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenHelpers
import dev.ashtonite365.apex.features.garden.GardenHelpers.ShoppingListCache
import dev.ashtonite365.apex.features.garden.GardenHelpers.Visitors
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemId

class GardenShoppingListHudTest {
	private val ids = mapOf(
		"Enchanted Hay Bale" to "ENCHANTED_HAY_BALE", "Enchanted Cookie" to "ENCHANTED_COOKIE",
		"Enchanted Baked Potato" to "ENCHANTED_BAKED_POTATO", "Jacob's Ticket" to "JACOBS_TICKET", "Wheat" to "WHEAT",
		"Enchanted Sugar Cane" to "ENCHANTED_SUGAR_CANE",
	)
	private var prices = mapOf("ENCHANTED_HAY_BALE" to 1312.4, "ENCHANTED_COOKIE" to 6_200.0, "ENCHANTED_BAKED_POTATO" to 1_234_567.0,
		"WHEAT" to 6.0, "ENCHANTED_SUGAR_CANE" to 152_000.0)
	private val lookup: (String) -> ItemId? = { ids[it]?.let(::ItemId) }
	private val price: (ItemId) -> Double? = { prices[it.skyblockId] }

	private fun reference(data: Visitors): List<Component> {
		val list = GardenHelpers.shoppingList(data)
		if (list.isEmpty()) return emptyList()
		var total = 0.0
		val lines = mutableListOf<Component>(Component.literal("§aVisitor shopping list §7(${data.wanted.size})"))
		for ((name, amount) in list) {
			val price = lookup(name)?.let(price)?.times(amount)
			price?.let { total += it }
			lines.add(Component.literal("§f${ApexFormatters.withThousands(amount.toLong())}x $name §6" + (price?.let { ApexFormatters.compactNumber(it) } ?: "?")))
		}
		lines.add(Component.literal("§eTotal: §6" + ApexFormatters.compactNumber(total)))
		return lines
	}

	private fun visitors(vararg v: Pair<String, Map<String, Int>>) = Visitors(v.associate { (k, m) -> k to m.toMutableMap() }.toMutableMap())

	private val samples = listOf(
		visitors(),
		visitors("Jacob" to mapOf("Enchanted Hay Bale" to 3)),
		visitors("Jacob" to mapOf("Enchanted Hay Bale" to 3, "Enchanted Cookie" to 1),
			"Anita" to mapOf("Enchanted Hay Bale" to 2, "Mystery Thing" to 7),
			"Duke" to mapOf("Jacob's Ticket" to 12, "Enchanted Baked Potato" to 1_500),
			"Liam" to mapOf("Wheat" to 1_234_567, "Enchanted Sugar Cane" to 64)),
		visitors("Empty" to mapOf()),
	)

	private fun cache(counter: IntArray = IntArray(1), gen: () -> Any? = { "g" }, bz: () -> Any? = { prices }, lb: () -> Any? = { "lb" }) =
		ShoppingListCache({ counter[0]++; lookup(it) }, price, gen, bz, lb)

	@Test
	fun linesIdenticalToTheOldPerFrameBuild() {
		for (s in samples) {
			assertEquals(reference(s), GardenHelpers.shoppingListLines(s, lookup, price))
			assertEquals(reference(s), cache().lines(s, 0))
			assertEquals(reference(s).map { it.string }, cache().lines(s, 0).map { it.string })
		}
		val big = samples[2]
		val text = cache().lines(big, 0).map { it.string }
		assertEquals("§aVisitor shopping list §7(4)", text.first())
		assertTrue(text.contains("§f7x Mystery Thing §6?"))
		assertTrue(text.contains("§f12x Jacob's Ticket §6?"))
		assertEquals(emptyList<Component>(), cache().lines(samples[0], 0))
	}

	@Test
	fun cachedUntilATriggerChanges() {
		val calls = IntArray(1)
		var gen: Any = Any()
		var bz: Any = Any()
		var lb: Any = Any()
		val c = cache(calls, { gen }, { bz }, { lb })
		var data = samples[2].copy(wanted = samples[2].wanted.mapValues { it.value.toMutableMap() }.toMutableMap())
		val first = c.lines(data, 0)
		repeat(100) { assertSame(first, c.lines(data, 0)) }
		assertEquals(1, c.rebuilds)
		val lookupsAfterFirst = calls[0]

		c.lines(data, 1); assertEquals(2, c.rebuilds)

		data.wanted["New"] = mutableMapOf("Wheat" to 1); assertEquals(reference(data), c.lines(data, 1)); assertEquals(3, c.rebuilds)
		data.wanted.remove("New"); c.lines(data, 1); assertEquals(4, c.rebuilds)

		data = samples[2].copy(wanted = samples[2].wanted.mapValues { it.value.toMutableMap() }.toMutableMap())
		c.lines(data, 1); assertEquals(5, c.rebuilds)

		c.invalidate(); c.lines(data, 1); assertEquals(6, c.rebuilds)

		prices = prices + ("ENCHANTED_COOKIE" to 7_000.0); bz = Any()
		assertEquals(reference(data), c.lines(data, 1)); assertEquals(7, c.rebuilds)
		lb = Any(); c.lines(data, 1); assertEquals(8, c.rebuilds)

		assertEquals(lookupsAfterFirst, calls[0])

		gen = Any(); c.lines(data, 1); assertEquals(9, c.rebuilds)
		assertEquals(lookupsAfterFirst * 2, calls[0])
		repeat(10) { c.lines(data, 1) }
		assertEquals(9, c.rebuilds)
	}

	@Test
	fun cachedFrameCostsMicroseconds() {
		val data = samples[2]
		val slowLookup: (String) -> ItemId? = { Thread.sleep(1); lookup(it) }
		val c = ShoppingListCache(slowLookup, price, { "g" }, { "bz" }, { "lb" })
		c.lines(data, 0)
		repeat(20_000) { c.lines(data, 0) }
		val n = 200_000
		val t = System.nanoTime()
		repeat(n) { c.lines(data, 0) }
		val usPerFrame = (System.nanoTime() - t) / 1000.0 / n
		println("[gardenhud] cached shopping-list frame: %.3f µs".format(usPerFrame))
		assertEquals(1, c.rebuilds)
		assertTrue(usPerFrame < 20.0, "cached frame took $usPerFrame µs")
	}
}
