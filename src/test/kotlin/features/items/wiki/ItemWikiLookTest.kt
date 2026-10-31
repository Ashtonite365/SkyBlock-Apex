// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.WikiDoc
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Block
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Item
import dev.ashtonite365.apex.features.items.wiki.WikiImageCore
import dev.ashtonite365.apex.features.items.wiki.WikiLayout
import dev.ashtonite365.apex.features.items.wiki.WikiPrices
import dev.ashtonite365.apex.test.features.items.wiki.WikiFixtures.m

class ItemWikiLookTest {
	private fun infobox(p: WikiDoc.Page) = p.blocks.filterIsInstance<Block.Infobox>().first()

	@Test fun infoboxIsDetectedWithItsPictureAndRows() {
		val box = infobox(WikiFixtures.decoy)
		assertEquals("Decoy", box.title)
		val rows = WikiFixtures.rows(box)
		assertEquals("Dungeon Item", rows["Type"]); assertEquals("UNCOMMON", rows["Rarity"]); assertEquals("DUNGEON_DECOY", rows["Item ID"])
		assertEquals("500 Coins", rows["Sell"])
		val headers = box.items.filterIsInstance<Item.Header>().map { WikiFixtures.spans(it.spans) }
		assertEquals(listOf("Item Properties", "Shop", "Bazaar Prices", "Item Metadata"), headers)
		val imgs = WikiFixtures.allSpans(WikiDoc.Page("x", listOf(box))).mapNotNull { it.img }
		val main = imgs.filter { it.main }
		assertEquals(1, main.size, "one main picture")
		assertTrue(main[0].src.startsWith("https://hypixelskyblock.minecraft.wiki/images/Decoy.png"), main[0].src)
		assertTrue(box.items.any { it is Item.Grid && it.cols == 2 }, "property grid kept as two columns")
		for (p in WikiFixtures.all) assertEquals(p.title, infobox(p).title.removeSuffix(" Pet").let { if (p.title.endsWith("Pet")) "$it Pet" else it })
	}

	@Test fun infoboxTabsAndGemSlots() {
		val tabs = infobox(WikiFixtures.gdp).items.filterIsInstance<Item.Tabs>().single()
		assertEquals(listOf("Legendary (Level 100)", "Legendary (Level 200)"), tabs.labels)
		assertEquals(2, tabs.sections.size)
		val gems = WikiFixtures.rows(infobox(WikiFixtures.hyperion))["Gemstones"]
		assertNotNull(gems)
		assertTrue("[✎]" in gems!! && "[⚔]" in gems, gems)
		val gemSpan = WikiFixtures.allSpans(WikiFixtures.hyperion).first { it.text == "[⚔]" }
		assertTrue(gemSpan.title!!.contains("Combat Gemstone Slot"), "gem slot keeps its in-game hover text")
	}

	@Test fun noDuplicatePlaceholdersOrDoubleCommas() {
		for (p in WikiFixtures.all) {
			val t = WikiFixtures.text(p)
			assertFalse("live on the website" in t, "${p.title}: placeholder note shown once at most (here: never)")
			assertFalse(Regex(",\\s*,").containsMatchIn(t), "${p.title}: double comma in '${Regex(".{20},\\s*,.{20}").find(t)?.value}'")
			assertFalse(Regex(" ,").containsMatchIn(WikiFixtures.allSpans(p).filter { it.img == null }.joinToString("") { it.text }.replace(Regex("\\s+"), " ")), "${p.title}: space before comma")
			assertFalse("calcname" in t, "calculator config dropped")
			assertFalse("VED" in t.replace("SAVED", ""), "table V/E/D buttons dropped")
		}

		val brewing = WikiFixtures.decoy.blocks.filterIsInstance<Block.Table>().first { t -> t.rows[0].any { it.text == "Brewing recipe" } }
		val req = brewing.rows[1][1].text.replace(Regex("\\s+"), " ")
		assertTrue(req.startsWith("Monster Hunter Armor, Monster Raider Armor"), req)
	}

	@Test fun tidyFixesCommaRunsFromRemovedIcons() {
		val html = "<p>Armor, <img src=\"x.svg\"/>, Monster and , trailing ,, end</p>"
		val p = WikiDoc.parse("T", html)
		assertEquals("Armor, Monster and, trailing, end", WikiFixtures.spans((p.blocks.first() as Block.Para).spans).replace(Regex("\\s+"), " "))
	}

	@Test fun livePlaceholdersBecomePricesOnce() {
		val lives = WikiFixtures.allSpans(WikiFixtures.decoy).filter { it.live != null }
		assertEquals(3, lives.size, lives.toString())
		assertEquals(listOf("bazaar", "bazaar", "spread"), lives.map { it.live!!.kind })
		assertEquals(listOf(true, false), lives.take(2).map { it.live!!.buy })
		assertTrue(lives.all { it.live!!.id == "DUNGEON_DECOY" })
		assertTrue(lives.all { it.text == "??? coins" }, lives.map { it.text }.toString())
		val bz = WikiPrices.Bazaar { id, buy -> if (id == "DUNGEON_DECOY") (if (buy) 1241.6 else 351.5) else null }
		val ah = WikiPrices.Auction { _, _ -> null }
		assertEquals(890.1, WikiPrices.price(lives[2].live!!, bz, ah)!!, 1e-9)
		val laid = WikiLayout.layout(WikiFixtures.decoy, 540, m, prices = { WikiPrices.price(it, bz, ah) })
		val text = laid.text()
		assertTrue("1,241.6 coins" in text && "351.5 coins" in text && "890.1 coins" in text, text.take(800))
		val none = WikiLayout.layout(WikiFixtures.decoy, 540, m).text()
		assertEquals(3, Regex("\\?\\?\\? coins").findAll(none).count(), "no data: the wiki's own placeholder, once each")

		val pet = WikiDoc.Live("auction", "GOLDEN_DRAGON;4+100")
		assertEquals(7.0e8, WikiPrices.price(pet, bz, { id, avg -> if (id == "GOLDEN_DRAGON;4" && !avg) 7.0e8 else null }))
		val recipe = WikiDoc.Live("recipe", "A", entries = listOf("A" to 2.0, "B" to 3.0))
		assertEquals(2 * 10.0 + 3 * 5.0, WikiPrices.price(recipe, { id, _ -> if (id == "A") 10.0 else null }, { id, _ -> if (id == "B") 5.0 else null }))
		assertNull(WikiPrices.price(recipe, { _, _ -> null }, { _, _ -> null }), "any missing ingredient -> no total")
		assertEquals("1,241.6", WikiLayout.formatPrice(1241.6)); assertEquals("500", WikiLayout.formatPrice(500.0))
	}

	@Test fun tablesKeepSpansAndHeaders() {
		val history = WikiFixtures.decoy.blocks.filterIsInstance<Block.Table>().last()
		assertEquals(3, history.cols)
		assertTrue(history.rows[0].single().let { it.header && it.colspan == 3 })
		val essence = WikiFixtures.hyperion.blocks.filterIsInstance<Block.Table>().first { t -> t.rows.any { r -> r.any { "Essence" in it.text } } }
		assertEquals(3, essence.cols)
		assertTrue(essence.rows.size >= 7)
		val html = """<table class="wikitable"><tr><th rowspan="2">A</th><th colspan="2">B</th></tr><tr><td>c</td><td style="text-align:center">d</td></tr></table>"""
		val t = WikiDoc.parse("T", html).blocks.single() as Block.Table
		assertTrue(t.bordered)
		assertEquals(3, t.cols)
		assertEquals(2, t.rows[0][0].rowspan); assertEquals(2, t.rows[0][1].colspan)
		assertTrue(t.rows[0].all { it.header && it.align == WikiDoc.Align.CENTER }, "th centred like the wiki")
		assertEquals(WikiDoc.Align.CENTER, t.rows[1][1].align)
		val laid = WikiLayout.layout(WikiDoc.Page("T", listOf(t)), 300, m, title = null)
		val txt = laid.ops.filterIsInstance<WikiLayout.Text>().associateBy { it.text }
		assertTrue(txt.getValue("c").x > txt.getValue("A").x + 4, "c sits right of the row-spanning A")
		assertTrue(txt.getValue("c").y > txt.getValue("B").y)
		assertTrue(laid.ops.filterIsInstance<WikiLayout.Rect>().any { it.argb == WikiLayout.Theme.LIGHT.th }, "header cells shaded")
		assertTrue(laid.ops.filterIsInstance<WikiLayout.Rect>().any { it.argb == WikiLayout.Theme.LIGHT.tableBorder }, "bordered")
	}

	@Test fun recipeGridsBecomeSlots() {
		val grids = WikiFixtures.hyperion.blocks.flatMap { b -> if (b is Block.Table) b.rows.flatMap { r -> r.flatMap { it.blocks } } else listOf(b) }.filterIsInstance<Block.Grid>()
		assertTrue(grids.isNotEmpty(), "crafting grids parsed")
		val g = grids.first()
		assertEquals(3, g.rows.size); assertTrue(g.rows.all { it.size == 3 })
		assertNotNull(g.output)
		assertTrue(g.rows.flatten().filterNotNull().all { it.slot && it.src.startsWith("https://") })
		val brew = WikiFixtures.decoy.blocks.filterIsInstance<Block.Table>().flatMap { t -> t.rows.flatMap { r -> r.flatMap { it.blocks } } }.filterIsInstance<Block.Grid>()
		assertTrue(brew.any { it.kind.startsWith("Brewing") && it.rows.size == 2 && it.rows[1].size == 3 }, brew.map { it.kind }.toString())
	}

	@Test fun imagesAreExtractedAbsoluteAndNoSvg() {
		for (p in WikiFixtures.all) {
			val imgs = p.images()
			assertTrue(imgs.size > 10, "${p.title}: ${imgs.size}")
			for (i in imgs) {
				assertTrue(i.src.startsWith("https://hypixelskyblock.minecraft.wiki/"), i.src)
				assertFalse(i.src.substringBefore('?').endsWith(".svg"), i.src)
				assertTrue(WikiImageCore.allowed(i.src))
			}
		}
		val tips = WikiFixtures.hyperion.images().mapNotNull { it.tip }
		assertTrue(tips.any { it.title.contains("§") }, "slot hover text keeps formatting codes")
		assertFalse(WikiImageCore.allowed("http://hypixelskyblock.minecraft.wiki/images/a.png"), "https only")
		assertFalse(WikiImageCore.allowed("https://example.com/a.png"))
	}

	@Test fun imageCacheEvictsLeastRecentlyDrawn() {
		var now = 0L
		val c = WikiImageCore<String>(max = 2) { now }
		assertNull(c.get("a"))
		c.loading("a"); assertEquals(WikiImageCore.State.Loading, c.get("a"))
		now = 1; assertTrue(c.ready("a", "A", 16, 16).isEmpty())
		now = 2; assertTrue(c.ready("b", "B", 16, 16).isEmpty())
		now = 3; c.get("a")
		now = 4; assertEquals(listOf("B"), c.ready("c", "C", 16, 16), "b was drawn longest ago")
		assertNull(c.get("b")); assertNotNull(c.get("a"))
		c.failed("d"); assertEquals(WikiImageCore.State.Failed, c.get("d"))
		assertEquals(WikiImageCore.fileName("x"), WikiImageCore.fileName("x"))
		assertNotEquals(WikiImageCore.fileName("x"), WikiImageCore.fileName("y"))
	}

	@Test fun tocNavboxesAndHatnote() {
		val d = WikiFixtures.decoy
		val toc = d.blocks.filterIsInstance<Block.Toc>().single()
		assertEquals(listOf("Obtaining", "Usage", "Brewing", "Tips", "Trivia", "History", "Navigation"), toc.entries.map { it.text })
		val navs = d.blocks.filterIsInstance<Block.Navbox>()
		assertEquals(listOf("Catacombs", "Tools", "Brewing Ingredients"), navs.map { WikiFixtures.spans(it.title) })
		assertEquals(7, navs[0].sections.size)
		assertEquals(navs.map { it.id }.distinct().size, navs.size, "navbox ids unique")
		val hat = WikiFixtures.gdp.blocks.first() as Block.Para
		assertTrue(hat.spans.all { it.italic } && hat.indent > 0, "hatnote italic and indented")
		assertTrue(WikiFixtures.spans(hat.spans).startsWith("This article is about the Golden Dragon Pet."))
	}

	@Test fun layoutLooksLikeThePage() {
		val d = WikiFixtures.decoy
		val w = 540
		val laid = WikiLayout.layout(d, w, m)
		val th = WikiLayout.Theme.LIGHT
		laid.ops.forEach { o -> assertTrue(o.x >= 0 && o.x + o.w <= w + 1, "$o") }
		val rects = laid.ops.filterIsInstance<WikiLayout.Rect>()
		val texts = laid.ops.filterIsInstance<WikiLayout.Text>()

		val title = texts.first()
		assertEquals("Decoy", title.text); assertEquals(WikiLayout.Face.SERIF, title.st.face); assertTrue(title.st.scale > 1.5f, "$title")

		val blue = rects.first { it.argb == th.ibBlue }
		assertTrue(blue.x > w / 2, "infobox on the right: $blue")
		val lead = texts.first { it.text.startsWith("A ") && it.y > title.y + title.h }
		assertTrue(lead.y < blue.y + 40 && lead.x + lead.w <= blue.x, "lead beside the infobox: $lead $blue")

		assertTrue(rects.count { it.argb == th.band } >= 6, "h2 bands")
		assertTrue(texts.any { it.href != null && it.argb == th.link }, "link colour")
		val main = laid.ops.filterIsInstance<WikiLayout.Image>().first { it.img?.main == true }
		assertTrue(main.x >= blue.x && main.w >= 40, "picture inside the infobox: $main")
		assertTrue(laid.ops.any { it is WikiLayout.Image && it.slot }, "inventory slots")

		assertEquals(3, rects.count { it.argb == th.navTitle && it.w > 100 }, "navbox title bars")

		laid.ops.forEach { assertTrue(it.y >= 0 && it.y + it.h <= laid.height, "$it below ${laid.height}") }
	}

	@Test fun togglesAndTabsChangeTheLayout() {
		val d = WikiFixtures.decoy
		val base = WikiLayout.layout(d, 540, m)
		val tocToggle = base.ops.filterIsInstance<WikiLayout.Toggle>().first { it.id == 1 }
		assertEquals(tocToggle, base.toggleAt(tocToggle.x + 1, tocToggle.y + 1))
		val hidden = WikiLayout.layout(d, 540, m, state = WikiLayout.State().flip(1))
		assertTrue(hidden.height < base.height, "hiding the contents shortens the page")
		val nav = d.blocks.filterIsInstance<Block.Navbox>().first()
		val collapsed = WikiLayout.layout(d, 540, m, state = WikiLayout.State().flip(nav.id))
		assertTrue(collapsed.height < base.height, "a navbox collapses")
		val g = WikiFixtures.gdp
		val tabs = g.blocks.filterIsInstance<Block.Infobox>().first().items.filterIsInstance<Item.Tabs>().single()
		val t0 = WikiLayout.layout(g, 540, m)
		val tabOps = t0.ops.filterIsInstance<WikiLayout.Toggle>().filter { it.id == tabs.id }
		assertEquals(2, tabOps.size)
		val t1 = WikiLayout.layout(g, 540, m, state = WikiLayout.State().tab(tabs.id, 1))

		val sel = { l: WikiLayout.Laid ->
			val shade = l.ops.filterIsInstance<WikiLayout.Rect>().filter { it.argb == WikiLayout.Theme.LIGHT.ibTab }
			l.ops.filterIsInstance<WikiLayout.Text>().filter { it.text.startsWith("Legendary (Level") }
				.single { t -> shade.any { r -> t.y >= r.y && t.y + t.h <= r.y + r.h && t.x >= r.x && t.x + t.w <= r.x + r.w } }.text
		}
		assertEquals("Legendary (Level 100)", sel(t0)); assertEquals("Legendary (Level 200)", sel(t1))
	}

	@Test fun minecraftFontModeAndDarkTheme() {
		val laid = WikiLayout.layout(WikiFixtures.hegemony, 400, m, theme = WikiLayout.Theme.DARK, smooth = false)
		val texts = laid.ops.filterIsInstance<WikiLayout.Text>()
		assertTrue(texts.all { it.st.face == WikiLayout.Face.GAME }, "Minecraft font everywhere")
		assertTrue(texts.all { it.st.scale in setOf(1f, 1.25f, 1.5f, 2f) || it.st.scale < 1f }, "bitmap font at whole/half steps")
		assertTrue(laid.ops.filterIsInstance<WikiLayout.Rect>().none { it.argb == WikiLayout.Theme.LIGHT.ibBlue })
		laid.ops.forEach { o -> assertTrue(o.x >= 0 && o.x + o.w <= 401, "$o") }
	}

	@Test fun narrowPanelsStillFit() {
		for (p in WikiFixtures.all) for (w in listOf(170, 260)) {
			val laid = WikiLayout.layout(p, w, m)
			laid.ops.forEach { o -> assertTrue(o.x >= 0 && o.x + o.w <= w + 1, "${p.title} @$w: $o") }
		}
	}

	@Test fun runBoundariesDoNotAddAnExtraSpace() {

		val fm = WikiLayout.Measure { t, _ -> t.length * 2.4f }
		val p = WikiDoc.parse("T", "<p>ab <a href=\"/w/X\">cd</a> ef <a href=\"/w/Y\">gh</a>.</p>")
		val texts = WikiLayout.layout(p, 300, fm, title = null).ops.filterIsInstance<WikiLayout.Text>()
		assertEquals(listOf("ab ", "cd", " ef ", "gh", "."), texts.map { it.text })

		assertEquals(listOf(0, 7, 12, 22, 26), texts.map { it.x })

		listOf(0f, 7.2f, 12f, 21.6f, 26.4f).zip(texts).forEach { (e, t) -> assertEquals(e, t.x + t.sub, 0.01f, t.text) }
	}

	@Test fun statIconsBecomeGameSymbols() {
		val stats = WikiFixtures.rows(infobox(WikiFixtures.hyperion))["Stats"]!!.replace('\u00A0', ' ')
		assertTrue("❁ Damage" in stats && "✎ Intelligence" in stats && "⫽ Ferocity" in stats, stats)
		assertFalse(WikiFixtures.text(WikiFixtures.hyperion).any { it in '\uE000'..'\uF8FF' }, "no private-use glyphs left")
	}

	@Test fun plainListsHaveNoMarkersOrIndent() {
		fun paras(bs: List<Block>): List<Block.Para> = bs.flatMap { b ->
			when (b) { is Block.Para -> listOf(b); is Block.Table -> b.rows.flatten().flatMap { paras(it.blocks) }; else -> emptyList() }
		}

		val ingredients = WikiFixtures.hyperion.blocks.filterIsInstance<Block.Table>().flatMap { it.rows.flatten() }
			.map { paras(it.blocks) }.first { ps -> ps.any { p -> WikiFixtures.spans(p.spans).startsWith("One of the following") } }
		assertTrue(ingredients.size >= 3)
		ingredients.forEach { assertNull(it.bullet, WikiFixtures.spans(it.spans)) }
		assertEquals(1, ingredients.map { it.indent }.distinct().size, "plainlist: no extra indent for the nested list")

		val raw = infobox(WikiFixtures.hyperion).items.filterIsInstance<Item.Row>().first { WikiFixtures.spans(it.label).trim() == "Raw Materials" }
		paras(raw.value).forEach { assertNull(it.bullet); assertEquals(0, it.indent) }

		assertEquals("•", WikiDoc.parse("T", "<ul><li>a</li></ul>").blocks.filterIsInstance<Block.Para>().single().bullet)
	}

	@Test fun navboxKeepsItsPictureColumn() {
		val nav = WikiFixtures.decoy.blocks.filterIsInstance<Block.Navbox>()
		val cat = nav.first { WikiFixtures.spans(it.title).trim() == "Catacombs" }
		assertNotNull(cat.image, "Catacombs navbox picture")
		assertNull(nav.first { WikiFixtures.spans(it.title).trim() == "Tools" }.image)
		val laid = WikiLayout.layout(WikiDoc.Page("Decoy", listOf(cat)), 500, m, title = null)
		val img = laid.ops.filterIsInstance<WikiLayout.Image>().single { it.img == cat.image }

		val rows = laid.ops.filterIsInstance<WikiLayout.Text>().filter { it.text == "[Show]" }
		assertTrue(rows.isNotEmpty())
		rows.forEach { assertTrue(it.x + it.w < img.x, "$it left of the picture") }
		assertTrue(img.x + img.w <= 500)

		val npcs = laid.ops.filterIsInstance<WikiLayout.Text>().first { it.text == "NPCs" }
		val floors = laid.ops.filterIsInstance<WikiLayout.Text>().first { it.text == "Floors" }
		assertEquals(floors.argb, npcs.argb)
		assertNotNull(npcs.href, "still clickable")
	}

	@Test fun treeviewListsGetConnectorsNotBullets() {
		val paras = WikiFixtures.gdp.blocks.filterIsInstance<Block.Para>()
		val top = paras.indexOfFirst { WikiFixtures.spans(it.spans).trim() == "1 Golden Dragon Egg" }
		assertTrue(top >= 0)
		assertNull(paras[top].bullet)
		val kids = paras.subList(top + 1, top + 8)
		assertEquals(List(6) { WikiDoc.TREE_MID } + WikiDoc.TREE_LAST, kids.map { it.bullet })
		assertTrue(kids.all { it.indent == paras[top].indent + 1 })
		assertTrue(WikiFixtures.spans(kids.last().spans).contains("Perfect Topaz Gemstone"))

		val laid = WikiLayout.layout(WikiDoc.Page("x", listOf(paras[top]) + kids), 400, m, title = null)
		assertFalse(laid.ops.filterIsInstance<WikiLayout.Text>().any { it.text == WikiDoc.TREE_MID || it.text == WikiDoc.TREE_LAST })
		assertTrue(laid.ops.filterIsInstance<WikiLayout.Rect>().size >= 14)
	}
}
