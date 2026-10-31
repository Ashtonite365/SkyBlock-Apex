// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.modules.ModuleBox
import dev.ashtonite365.apex.modules.ModuleGraph
import dev.ashtonite365.apex.modules.ModuleListModel
import dev.ashtonite365.apex.modules.ModuleStates
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.ModuleTag
import dev.ashtonite365.apex.modules.InactiveReason.NeedsDangerous
import dev.ashtonite365.apex.modules.InactiveReason.NeedsModule
import dev.ashtonite365.apex.modules.RowKind
import dev.ashtonite365.apex.modules.InactiveReason.SwitchedOff
import dev.ashtonite365.apex.settings.MainSection

class ModuleListModelTest {
	private val table = ModuleTable.DEFAULT
	private val rules = ModuleStates(ModuleGraph(table.descriptors()))

	private fun build(dangerous: Boolean, query: String = "") =
		ModuleListModel.build(table, rules.evaluate(emptyMap(), dangerous), dangerous, { it }, query)

	@Test
	fun sectionsFollowTheSettingsOrder() {
		assertEquals(MainSection.entries.size, ModuleListModel.SECTION_ORDER.size)
		val tops = table.modules.map { it.category.substringBefore(ModuleListModel.SEPARATOR) }.toSet()
		assertTrue(ModuleListModel.SECTION_ORDER.containsAll(tops), "unknown categories: ${tops - ModuleListModel.SECTION_ORDER.toSet()}")
		val shown = build(true).map { it.category }
		assertEquals(ModuleListModel.SECTION_ORDER.filter { it in shown }, shown)
	}

	@Test
	fun miscHasSortedSubHeadersAndRowsAreSortedByName() {
		val misc = build(true).single { it.category == "Miscellaneous" }
		val headers = misc.groups.map { it.header }
		assertTrue(headers.all { it != null } && headers.contains("Chat"))
		assertEquals(headers.sortedBy { it }, headers)
		for (s in build(true)) for (g in s.groups) assertEquals(g.rows.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER), g.rows.map { it.name })
	}

	@Test
	fun onlyModulesAreListedAndPurpleOnlyWithDangerous() {
		val withD = build(true).flatMap { it.rows }.map { it.id }
		val withoutD = build(false).flatMap { it.rows }.map { it.id }
		assertEquals(table.modules.size, withD.size)
		assertTrue(table.rows.filter { it.kind != RowKind.MODULE }.none { it.key in withD })
		val purple = table.modules.filter { it.tag == ModuleTag.PURPLE }.map { it.key }
		assertTrue(purple.isNotEmpty())
		assertTrue(purple.none { it in withoutD })
		assertEquals(table.modules.size - purple.size, withoutD.size)
	}

	@Test
	fun boxesShowTheHighestVisibleTag() {
		for (m in table.modules) {
			val withD = ModuleListModel.box(table, m, true)
			assertEquals(m.effective.name, if (withD == ModuleBox.NONE) "ALLOWED" else withD.name, m.key)
		}

		val mixed = table.modules.filter { it.tag != ModuleTag.PURPLE && it.effective == ModuleTag.PURPLE }
		assertTrue(mixed.isNotEmpty())
		for (m in mixed) assertTrue(ModuleListModel.box(table, m, false) != ModuleBox.PURPLE, m.key)
	}

	@Test
	fun notesAndSearch() {
		assertNull(ModuleListModel.inactiveNote(null) { it })
		assertNull(ModuleListModel.inactiveNote(SwitchedOff) { it })
		assertEquals("Dangerous off", ModuleListModel.inactiveNote(NeedsDangerous) { it })
		assertEquals("needs Chat", ModuleListModel.inactiveNote(NeedsModule("chat")) { "Chat" })
		val hits = build(true, "chat peek").flatMap { it.rows }.map { it.id }
		assertTrue("chat-peek" in hits)
		assertTrue(hits.all { "chat" in it || "peek" in it || table.byKey.getValue(it).category.lowercase().contains("chat") })
		assertFalse(build(true, "zzzz-no-such").any())

		val rows = build(true).flatMap { it.rows }
		for (r in rows) if (r.box != ModuleBox.NONE && table.byKey.getValue(r.id).tag != ModuleTag.ALLOWED) assertFalse(r.switchedOn, r.id)
	}
}
