package dev.ashtonite365.apex.test.repo

import com.google.gson.JsonParser
import dev.ashtonite365.apex.repo.BoxContents
import dev.ashtonite365.apex.repo.BoxContentsLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxContentsFitTest {
	private val box = BoxContents.parse(JsonParser.parseString(
		javaClass.getResourceAsStream("/apex-fixtures/contents-generated/HIGH_CLASS_ARCHFIEND_DICE.json")!!.reader().readText()))
	private val m = BoxContentsLayout.roughWidth
	private val inner = 160 - 8

	@Test
	fun labelsWrapToThePageWidth() {
		val lines = BoxContentsLayout.lines(box, inner, m).filterIsInstance<BoxContentsLayout.Label>()
		lines.forEach { assertTrue(m(it.text) <= inner, "'${it.text}' ${m(it.text)} > $inner") }
		val joined = lines.joinToString(" ") { it.text.replace(Regex("§."), "") }
		assertTrue(joined.contains("+300 HP for 24h; dice is consumed"), joined)
	}

	@Test
	fun pagesStillFitTheHeightAndLoseNothing() {
		val pages = BoxContentsLayout.pages(box, 130 - 24, inner, m)
		pages.forEach { p -> assertTrue(p.sumOf { it.h } <= 106) }
		assertEquals(BoxContentsLayout.lines(box, inner, m), pages.flatten())
	}

	@Test
	fun headerIsTheNameNotTheUrl() {
		assertEquals("High Class Archfiend Dice", BoxContentsLayout.headerName(box))
	}

	@Test
	fun generatorNoteIsHidden() {
		val t = BoxContentsLayout.lines(box, inner, m).filterIsInstance<BoxContentsLayout.Label>().joinToString(" ") { it.text }
		assertFalse(t.contains("not in Apex repo"), t)
		assertTrue(t.contains("Archfiend Dye"), t)
	}

	@Test
	fun leftAlignedCentreMath() {
		assertEquals(4 + 30, BoxContentsLayout.leftLabelX(4, "abcdefghij", m))
	}
}
