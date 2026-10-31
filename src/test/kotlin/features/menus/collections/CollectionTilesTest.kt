// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CollectionTilesTest {
	private fun parse(n: String) = RealDumpFixtures.load(n).let { CollectionsParser.parse(it.title, it.slots) }!!
	private val w = { s: String -> s.replace(Regex("§."), "").length * 6 }

	@Test fun tierIconIsARealStack() {
		val p = parse("coal-collection") as CollPage.Item
		assertEquals(4, p.headSlot)
		val pane = p.tiers.map { CollectionTiles.tier(it, p.item, p.headSlot) { "lime_stained_glass_pane" } }
		assertTrue(pane.all { it.iconSlot == 4 })
		val own = p.tiers.map { CollectionTiles.tier(it, p.item, p.headSlot) { "coal" } }
		assertEquals(p.tiers.map { it.slot }, own.map { it.iconSlot })

		assertTrue(p.tiers.map { CollectionTiles.tier(it, p.item, p.headSlot) { null } }.all { it.iconSlot == 4 })
	}

	@Test fun numeralsAndFitting() {
		val p = parse("glowstone-dust-collection") as CollPage.Item
		val tiles = p.tiers.map { CollectionTiles.tier(it, p.item, p.headSlot) { null } }
		assertEquals(listOf("I", "II", "III", "IV", "V", "VI", "VII").map { "Tier $it" }, tiles.map { it.title })
		val v = tiles[4]
		assertEquals("§e72.4% · 3,622/5,000", v.sub)
		assertEquals(listOf("§e3,622/5,000", "§e72.4%", "§ein progress"), v.subAlts)
		assertEquals("§e72.4%", TileText.pick(listOf(v.sub!!) + v.subAlts, 60, w))
		assertEquals(v.sub, TileText.pick(listOf(v.sub!!) + v.subAlts, 200, w))
		assertTrue(v.tip!!.first().contains("Glowstone Dust V")); assertTrue(v.tip!!.any { it.contains("Enchanted Glowstone Recipe") })

		val cat = parse("mining-collections") as CollPage.Category
		val coal = CollectionTiles.item(cat.items.first { it.base == "Coal" })
		assertEquals("Coal", coal.title); assertEquals("X", coal.badge)
		assertTrue(TileText.titleRoom(80, w("§6X")) in 1..80)
	}

	@Test fun lockedTierBars() {
		val p = parse("glowstone-dust-collection") as CollPage.Item
		val tiles = p.tiers.associateBy({ it.tier }, { CollectionTiles.tier(it, p.item, p.headSlot) { null } })
		val vi = tiles[6]!!; assertEquals(CollectionTiles.DIM_BAR, vi.barColour); assertEquals("§7locked · 36.2%", vi.sub)
		assertNull(tiles[5]!!.barColour); assertEquals(1.0, tiles[1]!!.fraction)
		val noLore = CollTile(30, "Thing III", "Thing", 3, TileState.LOCKED, null, null, emptyList(), emptyList())
		val t = CollectionTiles.tier(noLore, "Thing", 4) { null }
		assertNull(t.fraction); assertEquals("§7locked", t.sub)
	}

	@Test fun lockedCollectionsReachable() {
		val farming = parse("farming-collections") as CollPage.Category
		assertEquals("Farming", farming.category); assertEquals(17.0 to 20.0, farming.header!!.unlocked!!.let { it.current to it.max })
		assertEquals(20, farming.items.size)
		assertEquals(listOf("Moonflower", "Sunflower", "Wild Rose"), farming.items.filter { it.state == TileState.LOCKED }.map { it.name })
		assertEquals(17, farming.items.count { it.state != TileState.LOCKED })
		val mining = parse("mining-collections") as CollPage.Category
		assertEquals(listOf("Glacite", "Tungsten", "Umber"), mining.items.filter { it.state == TileState.LOCKED }.map { it.name })
		for (page in listOf(farming, mining)) {
			val groups = CollectionsParser.groups(page.items)
			assertEquals(page.items.size, groups.sumOf { it.second.size })
			assertEquals("Locked", groups.last().first); assertEquals(3, groups.last().second.size)
			assertTrue(groups.last().second.all { CollectionTiles.item(it).sub == "§cnot found yet" && CollectionTiles.item(it).fraction == null })
			val cols = TileLayout.cols(470 - 12 - 4, 112)
			val content = TileLayout.height(groups.map { it.second.size }, cols, 30)
			val visible = (280 - 24) - 50
			val maxScroll = TileLayout.maxScroll(content, visible)

			val lockedTop = TileLayout.height(groups.dropLast(1).map { it.second.size }, cols, 30)
			assertTrue(lockedTop + TileLayout.LABEL_H + 30 > visible, "locked group needs scrolling: top=$lockedTop")
			assertTrue(maxScroll > 0 && content - maxScroll <= visible, "content=$content max=$maxScroll")
		}

		val pork = CollectionTiles.item(farming.items.first { it.base == "Raw Porkchop" })
		assertEquals("Raw Porkchop", pork.title); assertEquals("III", pork.badge)
		assertEquals("42.3% · 423/1,000", pork.sub); assertEquals(listOf("423/1,000", "42.3%"), pork.subAlts)
	}

	@Test fun realFarmingTierPages() {
		val expect = mapOf(
			"leather-collection" to Triple(11, 11, null), "cactus-collection" to Triple(9, 4, 5), "carrot-collection" to Triple(9, 7, 8),
			"pumpkin-collection" to Triple(11, 3, 4), "raw-porkchop-collection" to Triple(9, 3, 4))
		for ((n, e) in expect) {
			val p = parse(n) as CollPage.Item
			assertEquals(e.first, p.maxTier, n); assertEquals(e.second, p.unlockedTiers, n); assertEquals(e.third, p.next?.tier, n)
			assertEquals(4, p.headSlot, n)
			val tiles = p.tiers.map { CollectionTiles.tier(it, p.item, p.headSlot) { "red_stained_glass_pane" } }
			assertTrue(tiles.all { it.iconSlot == 4 }, n)
			assertEquals(p.tiers.map { "Tier " + it.name.substringAfterLast(' ') }, tiles.map { it.title }, n)
			p.tiers.zip(tiles).forEach { (t, tile) ->
				when (t.state) {
					TileState.UNLOCKED -> { assertEquals(1.0, tile.fraction); assertNull(tile.barColour) }
					TileState.IN_PROGRESS -> { assertNull(tile.barColour); assertEquals(t.progress!!.fraction, tile.fraction); assertTrue(tile.sub!!.startsWith("§e")) }
					TileState.LOCKED -> { assertEquals(CollectionTiles.DIM_BAR, tile.barColour); assertTrue(tile.sub!!.startsWith("§7locked")) }
					else -> fail("$n ${t.name} ${t.state}")
				}
			}
		}
		val pork = parse("raw-porkchop-collection") as CollPage.Item
		val iv = CollectionTiles.tier(pork.tiers.first { it.tier == 4 }, pork.item, pork.headSlot) { null }
		assertEquals("§e42.3% · 423/1,000", iv.sub); assertEquals("Tier IV", iv.title)
		assertTrue(iv.tip!!.first() == "§fRaw Porkchop IV" && iv.tip!!.any { it.contains("+1,000 Farming Experience") })
		assertEquals("§7locked · 0.8%", CollectionTiles.tier(pork.tiers.last(), pork.item, pork.headSlot) { null }.sub)
	}

	@Test fun tileContentFollowsWidth() {
		val base = TileContent.of(112, 30)
		assertEquals(22, base.textX); assertEquals(87, base.textW); assertEquals(87, base.barW)
		val wide = TileContent.of(300, 30); assertTrue(wide.textW > 260 && wide.textX + wide.textW <= 300)
		assertFalse(TileContent.of(30, 30).showTitle); assertFalse(TileContent.of(112, 16).showSub)
	}
}
