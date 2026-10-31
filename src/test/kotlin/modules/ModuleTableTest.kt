// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.ModuleTag
import dev.ashtonite365.apex.modules.RowKind

class ModuleTableTest {
	private fun table(vararg rows: String) = ModuleTable.parse(
		"# SPDX\n# source-sha256: abc\n" + ModuleTable.COLUMNS.joinToString("\t") + "\n" + rows.joinToString("\n")
	)

	private val sample = table(
		"mixed\tmodule\tDungeons\tallowed\tpurple\tA0\t\tenabled\t\t",
		"mixed.blocker\toption\t\tpurple\tpurple\tP2\t\t\t\t",
		"whole\tmodule\tMining\tred\tred\tR1\tmixed\t\tashton\tnote here",
		"repo\tcore\tMiscellaneous > Misc\tallowed\tallowed\t-\t\t\t\t",
	)

	@Test fun parsesRows() {
		assertEquals("abc", sample.sourceSha256)
		assertEquals(listOf("mixed", "whole"), sample.modules.map { it.key })
		assertEquals(listOf("mixed", "whole", "repo"), sample.holders.map { it.key })
		val w = sample.byKey.getValue("whole")
		assertEquals(listOf("mixed"), w.deps)
		assertTrue(w.askAshton)
		assertEquals("note here", w.note)
		assertEquals(RowKind.OPTION, sample.byKey.getValue("mixed.blocker").kind)
		assertEquals("blocker", sample.byKey.getValue("mixed.blocker").option)
	}

	@Test fun optionsInheritTheHolderTag() {
		assertEquals(ModuleTag.PURPLE, sample.tagOf("mixed", "blocker"))
		assertEquals(ModuleTag.ALLOWED, sample.tagOf("mixed", "other"))
		assertEquals(ModuleTag.RED, sample.tagOf("whole", "anything"))
		assertNull(sample.tagOf("unknown"))
	}

	@Test fun loaderUsesTheOwnTag() {

		assertEquals(ModuleTag.ALLOWED, sample.descriptors().single { it.id == "mixed" }.tag)
		assertEquals(ModuleTag.RED, sample.descriptors().single { it.id == "whole" }.tag)
	}

	@Test fun rejectsBadInput() {
		assertThrows<IllegalArgumentException> { table("a\tmodule\tX\tgrey\tgrey\tA0\t\t\t\t") }
		assertThrows<IllegalArgumentException> { table("a\tthing\tX\tallowed\tallowed\tA0\t\t\t\t") }
		assertThrows<IllegalArgumentException> { table("a\tmodule\tX\tallowed") }
		assertThrows<IllegalArgumentException> { table("a\tmodule\tX\tallowed\tallowed\tA0\t\t\t\t", "a\tcore\tX\tallowed\tallowed\tA0\t\t\t\t") }
		assertThrows<IllegalArgumentException> { ModuleTable.parse("key\tkind\n") }
	}

	@Test fun tagRanks() {
		assertEquals(ModuleTag.PURPLE, ModuleTag.highest(listOf(ModuleTag.RED, ModuleTag.PURPLE, ModuleTag.ALLOWED)))
		assertEquals(ModuleTag.ALLOWED, ModuleTag.highest(emptyList()))
	}

	@Test fun shippedTableCounts() {

		val t = ModuleTable.DEFAULT
		val eff = t.modules.groupingBy { it.effective }.eachCount()
		assertEquals(22, eff[ModuleTag.RED])
		assertEquals(5, eff[ModuleTag.PURPLE])
		assertEquals(listOf("apex-far-terrain"), t.rows.filter { it.kind == RowKind.EXCLUDED }.map { it.key })

		val menus = t.holders.filter { it.key.startsWith("custom-menu-") }
		assertEquals(87, menus.size)
		assertTrue(menus.all { it.kind == RowKind.MODULE && it.rule == "A6" })
		assertEquals(88, t.holders.count { it.category == "Custom Menus" })
	}
}
