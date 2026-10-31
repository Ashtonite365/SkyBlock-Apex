// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.modules.EventHandlesBinding
import dev.ashtonite365.apex.modules.ModuleDescriptor
import dev.ashtonite365.apex.modules.ModuleGate
import dev.ashtonite365.apex.modules.ModuleGraph
import dev.ashtonite365.apex.modules.ModuleLoader
import dev.ashtonite365.apex.modules.ModuleStates
import dev.ashtonite365.apex.modules.ModuleTag
import dev.ashtonite365.apex.modules.SubscribedHandle

class ModuleManagerTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	class Ping(val n: Int) : ApexEvent() {
		class Bus : ApexEventBus<Ping>()
	}

	private val graph = ModuleGraph(listOf(
		ModuleDescriptor("a", "Test", ModuleTag.ALLOWED),
		ModuleDescriptor("risky", "Test", ModuleTag.RED),
		ModuleDescriptor("hard", "Test", ModuleTag.PURPLE),
	))

	private fun setup(): Triple<Ping.Bus, MutableList<String>, ModuleLoader> {
		val bus = Ping.Bus()
		val log = mutableListOf<String>()
		fun handle(id: String) = SubscribedHandle(bus, bus.subscribe(id) { log += "$id${it.n}" })
		val loader = ModuleLoader(graph, mapOf(
			"a" to EventHandlesBinding(listOf(handle("a"))),
			"risky" to EventHandlesBinding(listOf(handle("risky"))),
			"hard" to EventHandlesBinding(listOf(handle("hard"))),
		), gates = mapOf("hard" to ModuleGate("hard")))
		loader.adoptActive(graph.byId.keys)
		return Triple(bus, log, loader)
	}

	@Test
	fun bootDetachesWhatIsOffByDefault() {
		val (bus, log, loader) = setup()
		loader.apply(ModuleStates(graph).evaluate(emptyMap(), dangerousAllowed = false))
		bus.publish(Ping(1))
		assertEquals(listOf("a1"), log)
		assertEquals(setOf("a"), loader.active)
		assertFalse(loader.gates.getValue("hard").active)
	}

	@Test
	fun reEnableRestoresExactlyOnce() {
		val (bus, log, loader) = setup()
		val states = ModuleStates(graph)
		loader.apply(states.evaluate(mapOf("a" to false), false))
		bus.publish(Ping(1))
		loader.apply(states.evaluate(mapOf("a" to true, "risky" to true), false))
		loader.apply(states.evaluate(mapOf("a" to true, "risky" to true), false))
		bus.publish(Ping(2))
		assertEquals(listOf("a2", "risky2"), log.sorted())
		loader.apply(states.evaluate(mapOf("hard" to true), true))
		log.clear()
		bus.publish(Ping(3))
		assertEquals(listOf("a3", "hard3"), log.sorted())
		assertTrue(loader.gates.getValue("hard").active)
	}

	@Test
	fun hasHandlersFollowsState() {
		val (bus, _, loader) = setup()
		assertTrue(bus.hasHandlers)
		loader.apply(ModuleStates(graph).evaluate(mapOf("a" to false), false))
		assertFalse(bus.hasHandlers)
	}

	@Test
	fun detachDuringPublishIsSafe() {
		val bus = Ping.Bus()
		val log = mutableListOf<String>()
		lateinit var self: SubscribedHandle<Ping>
		self = SubscribedHandle(bus, bus.subscribe("x") { log += "x${it.n}"; self.detach() })
		bus.subscribe("y") { log += "y${it.n}" }
		bus.publish(Ping(1))
		bus.publish(Ping(2))
		assertEquals(listOf("x1", "y1", "y2"), log)
	}
}
