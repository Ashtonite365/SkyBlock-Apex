// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.modules.InactiveReason
import dev.ashtonite365.apex.modules.ModuleGates
import dev.ashtonite365.apex.modules.ModuleManager
import dev.ashtonite365.apex.modules.ModuleState
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.RowKind

class ModuleGatesTest {
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))
	private val rows: List<Pair<String, String>> by lazy {
		project.resolve("scripts/modules/mixin-gates.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
			.drop(1).map { it.split('\t').let { (a, b) -> a to b } }
	}

	private fun field(id: String) = id.split('-').let { p -> p[0] + p.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) } }

	@AfterEach fun reset() { for (id in ModuleGates.ids) ModuleGates.set(id, true) }

	@Test fun gatesStartOpen() {
		for (id in ModuleGates.ids) assertEquals(true, ModuleGates.get(id), id)
	}

	@Test fun everyGatedModuleHasAGateAndIsAModuleRow() {
		assertEquals(rows.map { it.second }.distinct().sorted(), ModuleGates.ids)
		for (id in ModuleGates.ids) assertEquals(RowKind.MODULE, ModuleTable.DEFAULT.byKey[id]?.kind, id)
	}

	@Test fun everyListedMixinReadsItsGate() {
		val mixins = project.resolve("src/main/java/dev/ashtonite365/apex/mixins")
		for ((path, id) in rows) {
			val source = mixins.resolve(path).readText()
			assertTrue("ModuleGates.${field(id)}" in source, "$path does not read ModuleGates.${field(id)}")
		}
	}

	@Test fun gatesFollowTheStatesOfMasterlessModules() {
		val table = ModuleTable.DEFAULT
		val masterless = ModuleGates.ids.filter { table.byKey.getValue(it).master.isEmpty() }
		val withMaster = ModuleGates.ids.filter { table.byKey.getValue(it).master.isNotEmpty() }
		assertTrue(masterless.size >= 2 && withMaster.isNotEmpty(), "$masterless / $withMaster")
		val off = masterless.first()
		val on = masterless.last()
		ModuleManager.applyGates(mapOf(
			off to ModuleState(off, switchedOn = false, inactive = InactiveReason.SwitchedOff),
			on to ModuleState(on, switchedOn = true, inactive = null),
		) + withMaster.associateWith { ModuleState(it, switchedOn = false, inactive = InactiveReason.SwitchedOff) })
		assertEquals(false, ModuleGates.get(off))
		assertEquals(true, ModuleGates.get(on))

		for (id in ModuleGates.ids - setOf(off)) assertEquals(true, ModuleGates.get(id), id)
		ModuleManager.applyGates(mapOf(off to ModuleState(off, switchedOn = true, inactive = InactiveReason.NeedsDangerous)))
		assertEquals(false, ModuleGates.get(off))
		ModuleManager.applyGates(mapOf(off to ModuleState(off, switchedOn = true, inactive = null)))
		assertEquals(true, ModuleGates.get(off))
		assertEquals(false, ModuleGates.set("no-such-module", false))
	}
}
