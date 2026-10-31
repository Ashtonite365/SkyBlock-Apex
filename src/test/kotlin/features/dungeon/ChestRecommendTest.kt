package dev.ashtonite365.apex.features.dungeon

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.ChestValue
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.PricedItem
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class ChestRecommendTest {
	private fun chest(name: String, slot: Int, loot: List<Double?>, coins: Double, key: Double = 0.0, openable: Boolean = true) =
		ChestValue(Component.literal(name), slot, loot.map { PricedItem(Component.literal("x"), it) }, coins + key, openable, keyCost = key)

	@Test fun allNegativeRecommendsNothing() {

		val chests = listOf(chest("Gold", 12, listOf(0.0), 100_000.0, key = 122_000.0),
			chest("Diamond", 13, listOf(101_000.0), 250_000.0, key = 122_000.0),
			chest("Emerald", 14, listOf(61_000.0), 500_000.0, key = 122_000.0))
		assertTrue(chests.all { it.profit < 0 })
		assertNull(DungeonChestProfit.bestChest(chests))
	}

	@Test fun picksMostProfitablePositiveOpenableChest() {
		val chests = listOf(chest("Gold", 12, listOf(150_000.0), 100_000.0),
			chest("Diamond", 13, listOf(900_000.0), 250_000.0, openable = false),
			chest("Emerald", 14, listOf(700_000.0), 500_000.0))
		assertEquals(14, DungeonChestProfit.bestChest(chests)?.slot)
	}

	@Test fun zeroProfitIsNotRecommended() {
		assertNull(DungeonChestProfit.bestChest(listOf(chest("Gold", 12, listOf(100_000.0), 100_000.0))))
	}

	@Test fun profitIsLootMinusOpenMinusKey() {
		val c = chest("Gold", 12, listOf(50_000.0, 30_000.0, null), 100_000.0, key = 122_000.0)
		assertEquals(80_000.0, c.value)
		assertEquals(100_000.0, c.coinCost)
		assertEquals(80_000.0 - 100_000.0 - 122_000.0, c.profit)
	}

	@Test fun lossNeverWorseThanOpenCostPlusKey() {
		val c = chest("Gold", 12, listOf(-5_000_000.0, 10.0), 100_000.0, key = 122_000.0)
		assertEquals(10.0 - 222_000.0, c.profit)
		assertTrue(c.profit >= -(c.coinCost + c.keyCost))
	}

	@Test fun openedOrBlockedChestsAreNotOpenable() {
		assertTrue(DungeonChestProfit.isOpenable(listOf("Contents", "Cost", "100,000 Coins", "Click to open!")))
		assertEquals(false, DungeonChestProfit.isOpenable(listOf("Contents", "Already opened!")))
		assertEquals(false, DungeonChestProfit.isOpenable(listOf("Contents", "Can't open another chest!")))
	}

	@Test fun panelShowsBreakdownWithKeyOnlyWhenListed() {
		val rows = DungeonChestProfit.panelRows(listOf(chest("Gold", 12, listOf(0.0), 100_000.0, key = 122_000.0), chest("Wood", 11, listOf(5_000.0), 0.0)))
		assertEquals(4, rows.size)
		assertTrue(rows[1].left.contains("open") && rows[1].left.contains("key"), rows[1].left)
		assertTrue(!rows[3].left.contains("key"), rows[3].left)
		assertTrue(rows[0].right.startsWith("§c-"), rows[0].right)
	}
}
