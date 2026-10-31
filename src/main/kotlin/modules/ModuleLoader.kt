// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

interface ModuleHooks {

	fun onEnable() {}

	fun onDisable() {}
}

interface ModuleBinding {
	fun attach()
	fun detach()
}

class ModuleGate(val id: String) {
	@JvmField
	@Volatile
	var active: Boolean = false
}

sealed interface LoaderEvent {
	val id: String
	data class Activated(override val id: String) : LoaderEvent
	data class Deactivated(override val id: String) : LoaderEvent
	data class Failed(override val id: String, val error: Throwable) : LoaderEvent
}

class ModuleLoader(
	val graph: ModuleGraph,
	private val bindings: Map<String, ModuleBinding> = emptyMap(),
	private val hooks: Map<String, ModuleHooks> = emptyMap(),
	val gates: Map<String, ModuleGate> = emptyMap(),
) {
	private val activeSet = LinkedHashSet<String>()
	val active: Set<String> get() = activeSet.toSet()

	init {
		val unknown = (bindings.keys + hooks.keys + gates.keys) - graph.byId.keys
		require(unknown.isEmpty()) { "bindings/hooks/gates for unknown modules: ${unknown.sorted()}" }
	}

	fun adoptActive(ids: Collection<String>) {
		for (id in graph.order) if (id in ids && activeSet.add(id)) gates[id]?.active = true
	}

	fun apply(states: Map<String, ModuleState>): List<LoaderEvent> {
		val log = mutableListOf<LoaderEvent>()
		val want = states.filterValues { it.active }.keys
		for (id in graph.order.asReversed()) {
			if (id !in activeSet || id in want) continue
			runCatching { hooks[id]?.onDisable() }.onFailure { log += LoaderEvent.Failed(id, it) }
			gates[id]?.active = false
			runCatching { bindings[id]?.detach() }.onFailure { log += LoaderEvent.Failed(id, it) }
			activeSet -= id
			log += LoaderEvent.Deactivated(id)
		}
		for (id in graph.order) {
			if (id in activeSet || id !in want) continue
			if (graph.byId.getValue(id).dependsOn.any { it !in activeSet }) continue
			val attached = runCatching { bindings[id]?.attach() }
			if (attached.isFailure) {
				log += LoaderEvent.Failed(id, attached.exceptionOrNull()!!)
				continue
			}
			activeSet += id
			gates[id]?.active = true
			runCatching { hooks[id]?.onEnable() }.onFailure { log += LoaderEvent.Failed(id, it) }
			log += LoaderEvent.Activated(id)
		}
		return log
	}
}
