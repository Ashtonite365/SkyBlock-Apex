// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.modules.InactiveReason
import dev.ashtonite365.apex.modules.LoaderEvent
import dev.ashtonite365.apex.modules.ModuleBinding
import dev.ashtonite365.apex.modules.ModuleDescriptor
import dev.ashtonite365.apex.modules.ModuleGate
import dev.ashtonite365.apex.modules.ModuleGraph
import dev.ashtonite365.apex.modules.ModuleGraphException
import dev.ashtonite365.apex.modules.ModuleHooks
import dev.ashtonite365.apex.modules.ModuleLoader
import dev.ashtonite365.apex.modules.ModuleStates
import dev.ashtonite365.apex.modules.ModuleTag

class ModuleLoaderTest {
	private fun m(id: String, tag: ModuleTag = ModuleTag.ALLOWED, vararg deps: String) = ModuleDescriptor(id, "Test", tag, deps.toList())

	private val graph = ModuleGraph(listOf(
		m("top", ModuleTag.ALLOWED, "mid"), m("mid", ModuleTag.ALLOWED, "base"), m("base"),
		m("purple", ModuleTag.PURPLE), m("needs-purple", ModuleTag.ALLOWED, "purple"), m("red", ModuleTag.RED),
	))
	private val states = ModuleStates(graph)

	@Test fun graphOrderAndClosures() {
		val o = graph.order
		assertTrue(o.indexOf("base") < o.indexOf("mid") && o.indexOf("mid") < o.indexOf("top"))
		assertEquals(listOf("base", "mid"), graph.dependenciesOf("top"))
		assertEquals(listOf("mid", "top"), graph.dependentsOf("base"))
		assertEquals(emptyList<String>(), graph.dependentsOf("red"))
	}

	@Test fun buildCheckFailures() {
		assertThrows<ModuleGraphException> { ModuleGraph(listOf(m("a", ModuleTag.ALLOWED, "missing"))) }
		assertThrows<ModuleGraphException> { ModuleGraph(listOf(m("a"), m("a"))) }
		assertThrows<ModuleGraphException> { ModuleGraph(listOf(m("a", ModuleTag.ALLOWED, "a"))) }
		val e = assertThrows<ModuleGraphException> {
			ModuleGraph(listOf(m("a", ModuleTag.ALLOWED, "b"), m("b", ModuleTag.ALLOWED, "c"), m("c", ModuleTag.ALLOWED, "a")))
		}
		assertTrue(e.message!!.contains("cycle"), e.message)
	}

	@Test fun defaultsFollowTheTag() {
		val s = states.evaluate(emptyMap(), dangerousAllowed = false)
		assertTrue(s.getValue("base").active)
		assertEquals(InactiveReason.SwitchedOff, s.getValue("red").inactive)
		assertEquals(InactiveReason.SwitchedOff, s.getValue("purple").inactive)
		assertEquals(InactiveReason.NeedsModule("purple"), s.getValue("needs-purple").inactive)
	}

	@Test fun purpleNeedsDangerousAndKeepsItsSwitch() {
		val sw = mapOf("purple" to true)
		val off = states.evaluate(sw, dangerousAllowed = false)
		assertEquals(InactiveReason.NeedsDangerous, off.getValue("purple").inactive)
		assertTrue(off.getValue("purple").switchedOn)
		assertTrue(states.evaluate(sw, dangerousAllowed = true).getValue("purple").active)
	}

	@Test fun enablingAutoEnablesDependencies() {
		val change = states.enable(mapOf("base" to false, "mid" to false), "top")
		assertEquals(listOf("base", "mid"), change.autoEnabled)
		assertTrue(states.evaluate(change.switches, false).getValue("top").active)
		assertEquals(emptyList<String>(), states.enable(emptyMap(), "top").autoEnabled, "already-on dependencies are not reported")
	}

	@Test fun disablingADependencyStopsDependents() {
		val change = states.disable(emptyMap(), "base", dangerousAllowed = false)
		assertEquals(listOf("mid", "top"), change.stoppedDependents)
		val s = states.evaluate(change.switches, false)
		assertEquals(InactiveReason.NeedsModule("base"), s.getValue("mid").inactive)
		assertEquals(InactiveReason.NeedsModule("mid"), s.getValue("top").inactive)
		assertFalse("top" in change.switches, "dependents keep their own switch")
		assertTrue(states.evaluate(states.enable(change.switches, "base").switches, false).getValue("top").active)
	}

	private class Rec(val log: MutableList<String>, val id: String, val failAttach: Boolean = false) : ModuleBinding, ModuleHooks {
		var attached = 0
		override fun attach() { if (failAttach) error("boom"); attached++; log += "attach $id" }
		override fun detach() { attached--; log += "detach $id" }
		override fun onEnable() { log += "enable $id" }
		override fun onDisable() { log += "disable $id" }
	}

	@Test fun loaderOrdersActivationAndDeactivation() {
		val log = mutableListOf<String>()
		val recs = listOf("base", "mid", "top").associateWith { Rec(log, it) }
		val gates = recs.keys.associateWith { ModuleGate(it) }
		val loader = ModuleLoader(graph, recs, recs, gates)
		loader.apply(states.evaluate(emptyMap(), false))
		assertEquals(listOf("attach base", "enable base", "attach mid", "enable mid", "attach top", "enable top"), log)
		assertTrue(gates.values.all { it.active })
		log.clear()
		loader.apply(states.evaluate(states.disable(emptyMap(), "base", false).switches, false))
		assertEquals(listOf("disable top", "detach top", "disable mid", "detach mid", "disable base", "detach base"), log)
		assertTrue(gates.values.none { it.active })
		assertTrue(recs.values.all { it.attached == 0 })
	}

	@Test fun reEnableAttachesExactlyOnce() {
		val log = mutableListOf<String>()
		val rec = Rec(log, "red")
		val loader = ModuleLoader(graph, mapOf("red" to rec), mapOf("red" to rec))
		val on = states.evaluate(mapOf("red" to true), false)
		loader.apply(on)
		loader.apply(on)
		assertEquals(1, rec.attached)
		loader.apply(states.evaluate(mapOf("red" to false), false))
		loader.apply(on)
		assertEquals(1, rec.attached)
		assertEquals(listOf("attach red", "enable red", "disable red", "detach red", "attach red", "enable red"), log)
	}

	@Test fun failedAttachKeepsModuleAndDependentsInactive() {
		val log = mutableListOf<String>()
		val loader = ModuleLoader(graph, mapOf("base" to Rec(log, "base", failAttach = true), "mid" to Rec(log, "mid")))
		val events = loader.apply(states.evaluate(emptyMap(), false))
		assertTrue(events.any { it is LoaderEvent.Failed && it.id == "base" })
		assertFalse("base" in loader.active)
		assertFalse("mid" in loader.active)
		assertFalse("top" in loader.active)
		assertTrue("red" !in loader.active && "purple" !in loader.active)
	}

	@Test fun rejectsBindingsForUnknownModules() {
		assertThrows<IllegalArgumentException> { ModuleLoader(graph, gates = mapOf("nope" to ModuleGate("nope"))) }
	}
}
