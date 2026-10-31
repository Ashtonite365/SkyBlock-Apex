// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.WikiApi
import dev.ashtonite365.apex.features.items.wiki.WikiDoc
import dev.ashtonite365.apex.features.items.wiki.WikiLayout
import dev.ashtonite365.apex.features.items.wiki.WikiLinks

class ItemWikiDocTest {
	private fun fixture(name: String) = javaClass.getResource("/apex-fixtures/item-wiki/$name")!!.readText()
	private fun page(name: String, asked: String) = WikiApi.fromResponse(200, fixture(name), asked) as WikiApi.Outcome.Page
	private val aote by lazy { page("parse-aspect-of-the-end.json", "Aspect of the End") }
	private val tater by lazy { page("parse-tater-helmet.json", "Tater Helmet") }

	@Test fun aspectOfTheEndParses() {
		assertEquals("Aspect of the End", aote.title)
		assertEquals(listOf("Obtaining", "Crafting", "Usage"), aote.sections.take(3).map { it.title })
		val p = WikiDoc.parse(aote.title, aote.html)
		val box = p.blocks.filterIsInstance<WikiDoc.Block.Infobox>().first()
		assertEquals("Aspect of the End", box.title)
		val rows = WikiFixtures.rows(box)
		assertEquals("RARE", rows["Rarity"]); assertEquals("Weapon", rows["Type"])
		val lead = p.blocks.filterIsInstance<WikiDoc.Block.Para>().first()
		assertTrue(lead.spans.joinToString("") { it.text }.startsWith("The Aspect of the End, frequently abbreviated as AOTE, is a RARE Sword"))
		assertTrue(lead.spans.filter { it.bold }.map { it.text }.containsAll(listOf("Aspect of the End", "AOTE")), "bold kept")
		assertTrue(lead.spans.any { it.href == "/w/Sword" }, "links kept")
		val heads = p.blocks.filterIsInstance<WikiDoc.Block.Heading>()
		assertTrue(heads.any { it.level == 2 && it.text == "Obtaining" && it.anchor == "Obtaining" })
		assertTrue(heads.any { it.level == 3 && it.anchor == "Etherwarp_Upgrade" })
		assertNotNull(p.indexOfAnchor("Obtaining")); assertNotNull(p.indexOfAnchor("Etherwarp Upgrade"))
		assertTrue(p.blocks.filterIsInstance<WikiDoc.Block.Table>().isNotEmpty(), "tables")
		assertTrue(p.blocks.filterIsInstance<WikiDoc.Block.Para>().any { it.bullet != null }, "lists")
		assertTrue(p.blocks.filterIsInstance<WikiDoc.Block.Para>().flatMap { it.spans }.any { it.color != null }, "colour")
		val all = WikiFixtures.text(p)
		assertFalse("[edit]" in all, "edit links dropped")
	}

	@Test fun taterHelmetRedirectsToTheSetPage() {
		assertEquals("Tater Armor", tater.title, "redirect followed")
		val p = WikiDoc.parse(tater.title, tater.html)
		val lead = p.blocks.filterIsInstance<WikiDoc.Block.Para>().first().spans.joinToString("") { it.text }
		assertTrue(lead.startsWith("Tater Armor is an UNCOMMON Armor set."), lead)
		val rows = WikiFixtures.rows(p.blocks.filterIsInstance<WikiDoc.Block.Infobox>().first())
		assertTrue(rows.containsKey("Type"), rows.keys.toString())
		assertTrue(rows.keys.any { it.startsWith("Rarit") }, rows.keys.toString())
		assertEquals("Crafting_2", tater.sections.first { it.anchor.endsWith("_2") }.anchor, "duplicate headings keep MediaWiki anchors")
	}

	@Test fun layoutStaysInsideTheWidthAndKeepsTheText() {
		val p = WikiDoc.parse(aote.title, aote.html)
		for (w in listOf(160, 300, 540)) {
			val laid = WikiLayout.layout(p, w, WikiFixtures.m)
			assertTrue(laid.height > 0)
			laid.ops.forEach { o -> assertTrue(o.x >= 0 && o.x + o.w <= w + 1, "$o overflows $w") }
			val laidText = laid.text().filterNot(Char::isWhitespace)
			val leadText = p.blocks.filterIsInstance<WikiDoc.Block.Para>().first().spans.joinToString("") { it.text }.filterNot(Char::isWhitespace)
			assertTrue(leadText in laidText, "lead paragraph intact at width $w")
		}
	}

	@Test fun longWordsSplitAndLinksMapBack() {
		val spans = listOf(WikiDoc.Span("Crafted from the "), WikiDoc.Span("Necron's Handle", "/w/Necron%27s_Handle"), WikiDoc.Span(" and averyveryverylongwordthatcannotfitononeline."))
		val page = WikiDoc.Page("T", listOf(WikiDoc.Block.Para(spans)))
		val laid = WikiLayout.layout(page, 60, WikiFixtures.m, title = null)
		val texts = laid.ops.filterIsInstance<WikiLayout.Text>()
		assertEquals(spans.joinToString("") { it.text }.replace(" ", ""), texts.joinToString("") { it.text }.replace(" ", ""))
		texts.forEach { assertTrue(it.x + it.w <= 60, "$it") }
		val link = texts.first { it.href != null }
		assertEquals("/w/Necron%27s_Handle", laid.hrefAt(link.x + 1, link.y + 1))
		assertEquals(null, laid.hrefAt(0, -5))
	}

	private val craftingHtml = """
		<div class="mw-parser-output"><div class="mcui mcui-Crafting&#95;Table"><div class="mcui-input">
		<div class="mcui-row">
		<span class="invslot"><span class="invslot-item invslot-item-image" data-minetip-title="&amp;aEnchanted Eye of Ender"><span typeof="mw:File"><a href="/w/Enchanted_Eye_of_Ender" title="Enchanted Eye of Ender"><img alt="Enchanted Eye of Ender" src="/images/Enchanted_Eye_of_Ender.png" width="32" height="32"/></a></span><span class="invslot-stacksize">16</span></span></span>
		<span class="invslot"><span class="invslot-item"></span></span>
		<span class="invslot animated"><span class="invslot-item invslot-item-image animated-active" data-minetip-title="Oak Log"><a href="/w/Oak_Log"><img src="/images/Oak_Log.png" width="32" height="32"/></a></span><span class="invslot-item invslot-item-image" data-minetip-title="Birch Log"><a href="/w/Birch_Log"><img src="/images/Birch_Log.png" width="32" height="32"/></a></span></span>
		</div></div>
		<div class="mcui-arrow"><br/></div>
		<div class="mcui-output"><span class="invslot"><span class="invslot-item invslot-item-image" data-minetip-title="&amp;9Aspect of the End"><a href="/w/Aspect_of_the_End"><img src="/images/Aspect_of_the_End.png" width="32" height="32"/></a></span></span></div>
		</div></div>
	""".trimIndent()

	@Test fun craftingGridSlotsAreLinks() {
		val p = WikiDoc.parse("Aspect of the End", craftingHtml)
		val g = p.blocks.filterIsInstance<WikiDoc.Block.Grid>().single()
		assertEquals("/w/Enchanted_Eye_of_Ender", g.rows[0][0]?.href, "the slot keeps its link")
		assertEquals(null, g.rows[0][1], "empty slot")
		assertEquals(listOf("/w/Oak_Log", "/w/Birch_Log"), g.rows[0][2]!!.frames.map { it.href }, "every frame keeps its own link")
		assertEquals("/w/Aspect_of_the_End", g.output?.href)
		val laid = WikiLayout.layout(p, 300, WikiFixtures.m, title = null)
		val slots = laid.ops.filterIsInstance<WikiLayout.Image>().filter { it.slot }
		val first = slots.first { it.img?.href == "/w/Enchanted_Eye_of_Ender" }
		assertEquals("/w/Enchanted_Eye_of_Ender", laid.hrefAt(first.x + first.w / 2, first.y + first.h / 2))
		val empty = slots.first { it.img == null }
		assertEquals(null, laid.hrefAt(empty.x + empty.w / 2, empty.y + empty.h / 2), "an empty slot is not clickable")
		val anim = slots.first { it.img?.frames?.isNotEmpty() == true }
		val ax = anim.x + anim.w / 2; val ay = anim.y + anim.h / 2
		assertEquals("/w/Oak_Log", laid.hrefAt(ax, ay))
		assertEquals("/w/Birch_Log", laid.hrefAt(ax, ay) { it.frames.last() }, "the shown frame's link")
		val out = slots.first { it.img?.href == "/w/Aspect_of_the_End" }
		assertEquals("/w/Aspect_of_the_End", laid.hrefAt(out.x + out.w / 2, out.y + out.h / 2), "the output slot links too")

		assertTrue(WikiLinks.classify(laid.hrefAt(first.x + 1, first.y + 1)!!) is WikiLinks.Kind.Internal)
	}

	@Test fun sectionJumpTargetsHaveAPosition() {
		val p = WikiDoc.parse(aote.title, aote.html)
		val laid = WikiLayout.layout(p, 300, WikiFixtures.m)
		val usage = p.indexOfAnchor("Usage")!!
		val obtaining = p.indexOfAnchor("Obtaining")!!
		assertTrue(laid.yOfBlock(usage) > laid.yOfBlock(obtaining))
		assertEquals(obtaining, p.sections.first { it.first.anchor == "Obtaining" }.second)
	}
}
