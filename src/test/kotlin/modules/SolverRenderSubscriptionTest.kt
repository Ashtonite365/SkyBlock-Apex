// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.modules.ModuleGraph
import dev.ashtonite365.apex.modules.ModuleStates
import dev.ashtonite365.apex.modules.ModuleTable

class SolverRenderSubscriptionTest {
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))

	private val owners: Map<String, String> by lazy {
		javaClass.getResourceAsStream("/assets/apex/modules/owners.tsv")!!.readBytes().decodeToString()
			.lines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split('\t') }.associate { it[0] to it[1] }
	}

	private val subscriptions: List<Pair<String, String>> by lazy {
		val f = project.resolve("build/generated/ksp/main").walkTopDown().first { it.name == "AllSubscriptionsMain.kt" }
		Regex("""Subscription<([\w.]+)>\(\s*([\w.]+),""").findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
	}

	private val states by lazy { ModuleStates(ModuleGraph(ModuleTable.DEFAULT.descriptors())).evaluate(emptyMap(), false) }

	@Test fun creeperBeamsHandlersAreSubscribed() {
		val o = "dev.ashtonite365.apex.features.dungeon.CreeperBeamsSolver"
		val ev = subscriptions.filter { it.second == o }.map { it.first.substringAfterLast('.') }.toSet()
		assertEquals(setOf("WorldRenderLastEvent", "TickEvent", "WorldReadyEvent"), ev)
	}

	@Test fun everyEnabledSolverRenderHandlerStaysAttached() {
		val solvers = subscriptions.filter { (e, o) ->
			o.substringAfterLast('.').contains("Solver") && e.endsWith("WorldRenderLastEvent")
		}.map { it.second }.toSet()
		assertTrue(solvers.size >= 5, "only $solvers")
		for (o in solvers) {
			val module = owners[o] ?: error("$o has no owners.tsv row")
			if (module == "core") continue
			val s = states.getValue(module)

			if (s.switchedOn) assertEquals(null, s.inactive, "$o ($module) is switched on but inactive: ${s.inactive}")
		}
	}

	@Test fun noDefaultOnModuleIsInactive() {
		val stuck = states.values.filter { it.switchedOn && it.inactive != null }.map { "${it.id}: ${it.inactive}" }
		assertEquals(emptyList<String>(), stuck)
	}
}
