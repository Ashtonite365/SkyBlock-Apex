package dev.ashtonite365.apex.test.repo

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.BoxContents
import dev.ashtonite365.apex.repo.BoxContentsLayout

class BoxContentsTest {
	private fun fixture(n: String) = BoxContents.parse(JsonParser.parseString(javaClass.getResource("/apex-fixtures/contents/$n.json")!!.readText()))

	@Test fun jerryBoxGolden() {
		val b = fixture("JERRY_BOX_GOLDEN")
		assertEquals("JERRY_BOX_GOLDEN", b.box); assertEquals(124.0, b.totalWeight); assertEquals(5, b.drops.size)
		val (coins, talisman, gold, xp, unknown) = b.drops
		assertEquals(BoxContents.Type.COINS, coins.type)
		assertEquals(listOf("§6100,000–500,000 Coins", "§7Chance: §e48.39%", "§7Weight 60/124"), BoxContents.dropLines(coins, b))
		assertEquals(2.0 / 124, BoxContents.chanceOf(talisman, b))
		assertTrue("§7Weight 2/124" in BoxContents.dropLines(talisman, b))
		assertEquals(listOf("§7Amount: §f8–32", "§7Chance: §e25%", "§8stack size varies"), BoxContents.dropLines(gold, b))
		assertEquals(listOf("§32,000 Combat XP", "§7Chance: §e?"), BoxContents.dropLines(xp, b))
		assertNull(BoxContents.chanceOf(unknown, b)); assertEquals("?", BoxContents.pct(null))

		assertEquals(listOf(BoxContentsLayout.Drops(b.drops)), BoxContentsLayout.lines(b))
	}

	@Test fun diceSixRollsGroupedAndPaged() {
		val b = fixture("DICE_6_ROLLS")
		val rows = BoxContents.rows(b)
		assertEquals((1..6).map { "Roll $it" }, rows.map { it.first!!.name })
		assertTrue(rows.all { it.second.size == 5 })
		assertEquals("Roll 1 §7(16.67%)", BoxContents.groupLabel(rows[0].first!!))
		assertTrue(BoxContents.dropLines(rows[0].second[0], b).contains("§7Chance: §e20%"))
		val pages = BoxContentsLayout.pages(b, 106)
		assertTrue(pages.size > 1)
		assertEquals(BoxContentsLayout.lines(b), pages.flatten())
		assertTrue(pages.all { p -> p.sumOf { it.h } <= 106 })
	}

	@Test fun badRecordsThrowSoTheLoaderSkipsThem() {
		assertThrows(Exception::class.java) { BoxContents.parse(JsonParser.parseString("""{"drops":[]}""")) }
		assertThrows(Exception::class.java) { BoxContents.parse(JsonParser.parseString("""{"box":"X","drops":[{"type":"coins","chance":4}]}""")) }
	}

	@Test fun nullIdOrSkillSkipsOnlyThatDrop() {
		val b = BoxContents.parse(JsonParser.parseString("""{"box":"GREEN_GIFT","drops":[
			{"type":"item","id":null,"note":"not in Apex repo","chance":0.1},
			{"type":"item","id":"ENCHANTED_GOLD"},
			{"type":"skill_xp","skill":null,"min":100,"max":500},
			{"type":"skill_xp","skill":"COMBAT","min":10},
			{"type":"coins","min":1000}]}"""))
		assertEquals("GREEN_GIFT", b.box)
		assertEquals(listOf("ENCHANTED_GOLD", null, null), b.drops.map { it.id })
		assertEquals(listOf(BoxContents.Type.ITEM, BoxContents.Type.SKILL_XP, BoxContents.Type.COINS), b.drops.map { it.type })
		assertEquals(listOf("not in Apex repo (10%)", "unknown skill XP"), b.unresolved)
		assertEquals(listOf("§8? not in Apex repo (10%)", "§8? unknown skill XP"),
			BoxContentsLayout.lines(b).filterIsInstance<BoxContentsLayout.Label>().map { it.text })
		val empty = BoxContents.parse(JsonParser.parseString("""{"box":"Y","drops":[{"type":"item"}]}"""))
		assertEquals(0, empty.drops.size); assertEquals(listOf("unknown item"), empty.unresolved)
		assertEquals(1, BoxContentsLayout.pages(empty, 100).size)
	}

	@Test fun everyGeneratedBoxLoads() {

		val dir = java.io.File(javaClass.getResource("/apex-fixtures/contents-generated")!!.toURI())
		val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
		assertEquals(27, files.size)
		val boxes = files.map { BoxContents.parse(JsonParser.parseString(it.readText())) }
		assertEquals(27, boxes.map { it.box }.toSet().size)
		assertEquals(17, boxes.count { it.unresolved.isNotEmpty() })

		assertTrue(boxes.all { BoxContentsLayout.lines(it).isNotEmpty() })
	}
}
