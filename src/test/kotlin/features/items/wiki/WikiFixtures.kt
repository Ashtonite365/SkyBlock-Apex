// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import java.util.zip.GZIPInputStream
import dev.ashtonite365.apex.features.items.wiki.WikiApi
import dev.ashtonite365.apex.features.items.wiki.WikiDoc
import dev.ashtonite365.apex.features.items.wiki.WikiLayout

object WikiFixtures {

	val m = WikiLayout.Measure { s, st -> s.length * when { st.face == WikiLayout.Face.GAME -> 6f; st.bold -> 6.5f; else -> 5.8f } }

	private val cache = HashMap<String, WikiDoc.Page>()

	fun page(file: String, title: String): WikiDoc.Page = cache.getOrPut(file) {
		val raw = GZIPInputStream(javaClass.getResourceAsStream("/apex-fixtures/item-wiki/$file")!!).readBytes().toString(Charsets.UTF_8)
		val p = WikiApi.fromResponse(200, raw, title) as WikiApi.Outcome.Page
		WikiDoc.parse(p.title, p.html)
	}

	val decoy get() = page("parse-decoy.json.gz", "Decoy")
	val hyperion get() = page("parse-hyperion.json.gz", "Hyperion")
	val gdp get() = page("parse-golden-dragon-pet.json.gz", "Golden Dragon Pet")
	val hegemony get() = page("parse-hegemony-artifact.json.gz", "Hegemony Artifact")
	val all get() = listOf(decoy, hyperion, gdp, hegemony)

	fun spans(s: List<WikiDoc.Span>) = s.joinToString("") { it.text }

	fun rows(box: WikiDoc.Block.Infobox): Map<String, String> {
		val out = LinkedHashMap<String, String>()
		fun items(list: List<WikiDoc.Item>) {
			for (i in list) when (i) {
				is WikiDoc.Item.Row -> out.putIfAbsent(spans(i.label).replace('\n', ' ').trim(), i.value.joinToString(" ") { WikiDoc.plainOfBlock(it) }.trim())
				is WikiDoc.Item.Grid -> items(i.cells)
				is WikiDoc.Item.Tabs -> i.sections.forEach(::items)
				is WikiDoc.Item.Header -> {}
			}
		}
		items(box.items)
		return out
	}

	fun allSpans(p: WikiDoc.Page): List<WikiDoc.Span> = buildList {
		fun blocks(bs: List<WikiDoc.Block>) {
			for (b in bs) when (b) {
				is WikiDoc.Block.Para -> addAll(b.spans)
				is WikiDoc.Block.Table -> b.rows.forEach { r -> r.forEach { blocks(it.blocks) } }
				is WikiDoc.Block.Infobox -> {
					fun items(list: List<WikiDoc.Item>) {
						for (i in list) when (i) {
							is WikiDoc.Item.Row -> { addAll(i.label); blocks(i.value) }
							is WikiDoc.Item.Grid -> items(i.cells)
							is WikiDoc.Item.Tabs -> i.sections.forEach(::items)
							is WikiDoc.Item.Header -> addAll(i.spans)
						}
					}
					items(b.items)
				}
				is WikiDoc.Block.Figure -> addAll(b.caption)
				is WikiDoc.Block.Navbox -> { addAll(b.title); b.sections.forEach { s -> s.header?.let(::addAll); s.rows.forEach { r -> r.group?.let(::addAll); r.items.forEach(::addAll) } } }
				else -> {}
			}
		}
		blocks(p.blocks)
	}

	fun text(p: WikiDoc.Page): String = allSpans(p).joinToString(" ") { it.text } + " " +
		p.blocks.filterIsInstance<WikiDoc.Block.Heading>().joinToString(" ") { it.text }
}
