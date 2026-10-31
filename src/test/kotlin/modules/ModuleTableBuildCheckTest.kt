// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import java.io.File
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.modules.ModuleGraph
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.RowKind
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs

class ModuleTableBuildCheckTest {
	private val table = ModuleTable.DEFAULT
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val byName by lazy { configs.associateBy { it.name } }
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))

	@Test fun tableIsGeneratedFromTheCurrentDecisions() {
		val decisions = project.resolve("scripts/modules/module-decisions.tsv")
		val sha = MessageDigest.getInstance("SHA-256").digest(decisions.readBytes()).joinToString("") { "%02x".format(it) }
		assertEquals(sha, table.sourceSha256, "modules.tsv is out of date: run python3 scripts/gen_module_table.py")
	}

	@Test fun everyRegisteredHolderHasExactlyOneRow() {
		val registered = configs.map { it.name }.toSet()
		val rows = table.holders.map { it.key }.toSet()
		assertEquals(emptyList<String>(), (registered - rows).sorted(), "holders with no row in modules.tsv")
		assertEquals(emptyList<String>(), (rows - registered).sorted(), "rows in modules.tsv that name no registered holder")
	}

	@Test fun optionRowsNameRegisteredOptions() {
		val missing = table.rows.filter { it.kind == RowKind.OPTION }
			.filter { byName[it.holder]?.allOptions?.containsKey(it.option) != true }.map { it.key }
		assertEquals(emptyList<String>(), missing)
	}

	@Test fun masterColumnMatchesTheCode() {
		val masters = listOf("enabled", "enable", "enabled-toggle")
		for (row in table.holders) {
			val opts = byName.getValue(row.key).allOptions
			if (row.master.isEmpty()) assertTrue(masters.none { it in opts }, "${row.key} has a master option but no master in the table")
			else assertTrue(opts[row.master]?.default?.invoke() is Boolean, "${row.key}.${row.master} is not a toggle")
		}
	}

	@Test fun redAndPurpleDefaultOff() {
		val on = mutableListOf<String>()
		for (row in table.rows.filter { it.tag.gated }) {
			val opts = byName[row.holder]?.allOptions ?: continue
			val keys = when {
				row.kind == RowKind.OPTION -> listOf(row.option!!)
				row.master.isNotEmpty() -> listOf(row.master)

				table.optionRows[row.key].isNullOrEmpty() -> opts.filterValues { it.default() is Boolean }.keys.toList()
				else -> emptyList()
			}
			for (k in keys) if (opts.getValue(k).default() == true) on += "${row.holder}.$k"
		}
		assertEquals(emptyList<String>(), on, "red/purple options that default on")
	}

	@Test fun effectiveTagIsTheHighestOfRowAndOptions() {
		for (row in table.holders) {
			val want = (listOf(row.tag) + table.optionRows[row.key].orEmpty().map { it.tag }).max()
			assertEquals(want, row.effective, row.key)
		}
	}

	@Test fun graphIsValidAndCoversEveryModule() {
		val graph = ModuleGraph(table.descriptors())
		assertEquals(table.modules.map { it.key }.toSet(), graph.order.toSet())
		assertTrue(table.modules.all { it.category.isNotEmpty() }, "every module has a category")
	}

	@Test fun ashtonItemsAreFlagged() {

		for (key in listOf("puzzle-solvers.lost-adventurer", "hide-players", "jade-integration"))
			assertTrue(table.byKey[key]?.askAshton == true, "$key should be marked ask=ashton")

		for (key in listOf("plus-garden-warps", "garden-tools.no-break"))
			assertTrue(table.byKey[key]?.askAshton == false, "$key was decided by Ashton and should not be ask=ashton")
	}
}
