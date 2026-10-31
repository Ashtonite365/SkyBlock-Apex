// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus

class ApexEventBusHandleTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	class Ping(val n: Int) : ApexEvent() {
		class Bus : ApexEventBus<Ping>()
	}

	@Test
	fun unsubscribeRemovesOnlyThatHandle() {
		val bus = Ping.Bus()
		val log = mutableListOf<String>()
		assertFalse(bus.hasHandlers)
		val a = bus.subscribe("a") { log += "a${it.n}" }
		val b = bus.subscribe("b") { log += "b${it.n}" }
		assertTrue(bus.hasHandlers)
		bus.publish(Ping(1))
		assertTrue(bus.unsubscribe(a))
		assertFalse(bus.unsubscribe(a), "second unsubscribe is a no-op")
		bus.publish(Ping(2))
		assertEquals(listOf("a1", "b1", "b2"), log)
		assertTrue(bus.hasHandlers)
		assertTrue(bus.unsubscribe(b))
		assertFalse(bus.hasHandlers)
	}

	@Test
	fun handlesCompareByIdentity() {
		val bus = Ping.Bus()
		val fn: (Ping) -> Unit = {}
		val first = bus.subscribe("same") { fn(it) }
		val twin = ApexEventBus.Handler(first.invocation, first.receivesCancelled, label = first.label)
		assertEquals(first, twin)
		assertFalse(bus.unsubscribe(twin), "an equal but different handle must not detach the original")
		assertEquals(1, bus.handlers.size)
	}

	@Test
	fun resubscribeAttachesOnceAtTheEnd() {
		val bus = Ping.Bus()
		val log = mutableListOf<String>()
		val a = bus.subscribe("a") { log += "a" }
		bus.subscribe("b") { log += "b" }
		assertSame(a, bus.resubscribe(a))
		assertEquals(2, bus.handlers.size, "already attached: no duplicate")
		bus.unsubscribe(a)
		bus.resubscribe(a)
		bus.publish(Ping(0))
		assertEquals(listOf("b", "a"), log)
	}

	@Test
	fun unsubscribeDuringPublishIsSafe() {
		val bus = Ping.Bus()
		val log = mutableListOf<String>()
		lateinit var b: ApexEventBus.Handler<Ping>
		bus.subscribe("a") { log += "a${it.n}"; bus.unsubscribe(b) }
		b = bus.subscribe("b") { log += "b${it.n}" }
		bus.publish(Ping(1))
		bus.publish(Ping(2))
		assertEquals(listOf("a1", "b1", "a2"), log)
	}
}
