// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import java.util.concurrent.CopyOnWriteArrayList
import org.apache.commons.lang3.reflect.TypeUtils
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client

open class ApexEventBus<T
	: ApexEvent> {

	val eventType = TypeUtils.getTypeArguments(this.javaClass, ApexEventBus::class.java)!!.values.single()

	data class Handler<T>(val invocation: (T) -> Unit, val receivesCancelled: Boolean,
		var knownErrors: MutableSet<Class<*>> = mutableSetOf(), val label: String)

	private val listeners = CopyOnWriteArrayList<Handler<T>>()

	val handlers: List<Handler<T>> get() = listeners

	@Volatile
	var hasHandlers: Boolean = false
		private set

	init { allEventBuses += this }

	fun subscribe(label: String, handle: (T) -> Unit): Handler<T> = subscribe(false, label, handle)

	fun subscribe(receivesCancelled: Boolean, label: String, handle: (T) -> Unit): Handler<T> =
		resubscribe(Handler(handle, receivesCancelled, label = label))

	@Synchronized
	fun resubscribe(handler: Handler<T>): Handler<T> {
		if (listeners.none { it === handler }) listeners.add(handler)
		hasHandlers = listeners.isNotEmpty()
		return handler
	}

	@Synchronized
	fun unsubscribe(handler: Handler<T>): Boolean {
		val removed = listeners.removeIf { it === handler }
		hasHandlers = listeners.isNotEmpty()
		return removed
	}

	fun publish(event: T): T = event.also { published -> listeners.forEach { deliver(it, published) } }

	private fun deliver(listener: Handler<T>, event: T) {
		val skip = event is ApexEvent.Cancellable && event.cancelled && !listener.receivesCancelled
		if (skip) return
		runCatching { listener.invocation(event) }.onFailure { problem ->
			if (problem !is Exception) throw problem
			val firstOfKind = listener.knownErrors.add(problem.javaClass)
			if (firstOfKind || Apex.DEBUG)
				Faults.reportFault("Event listener $listener failed while handling $event", problem)
		}
	}

	fun publishSync(event: T): Unit = Client.onClientThread { publish(event) }

	companion object Registry {

		val allEventBuses: MutableList<ApexEventBus<*>> = ArrayList()
	}
}
