// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.events.subscription.Subscription
import dev.ashtonite365.apex.events.subscription.SubscriptionList
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.compatloader.CompatSwitch
import dev.ashtonite365.apex.util.data.ManagedConfig

class EventHandlesBinding(private val handles: List<SubscribedHandle<*>>) : ModuleBinding {
	override fun attach() = handles.forEach { it.attach() }
	override fun detach() = handles.forEach { it.detach() }
}

class SubscribedHandle<T : ApexEvent>(val bus: ApexEventBus<T>, val handler: ApexEventBus.Handler<T>) {
	fun attach() { bus.resubscribe(handler) }
	fun detach() { bus.unsubscribe(handler) }
}

object ModuleManager {
	private val handlesByOwner = LinkedHashMap<Any, MutableList<SubscribedHandle<*>>>()
	private var loader: ModuleLoader? = null

	fun boot() {
		for (generated in SubscriptionList.allLists.filter { CompatSwitch.mayLoad(it.javaClass.name) })
			Faults.attempt("Error while loading events from $generated") { generated.provideSubscriptions { attach(it) } }
	}

	private fun <E : ApexEvent> attach(s: Subscription<E>) {
		val h = s.eventBus.subscribe(false, s.owner.javaClass.simpleName + ":" + s.methodName, s.invoke)
		handlesByOwner.getOrPut(s.owner) { mutableListOf() } += SubscribedHandle(s.eventBus, h)
	}

	fun bindings(owners: Map<Any, List<SubscribedHandle<*>>>, holders: List<ManagedConfig>, table: ModuleTable): Map<String, ModuleBinding> {
		val switchable = table.modules.filter { it.master.isEmpty() }.map { it.key }.toSet()
		val byModule = LinkedHashMap<String, MutableList<SubscribedHandle<*>>>()
		for ((owner, handles) in owners) {
			val mine = holders.filter { it.javaClass.enclosingClass == owner.javaClass }
			val holder = mine.singleOrNull() ?: continue
			if (holder.name !in switchable) continue
			byModule.getOrPut(holder.name) { mutableListOf() } += handles
		}
		return byModule.mapValues { EventHandlesBinding(it.value) }
	}

	fun switches(holders: List<ManagedConfig>, table: ModuleTable): Map<String, Boolean> {
		val out = LinkedHashMap(ModuleSwitches.current())
		for (m in table.modules.filter { it.master.isNotEmpty() }) {
			val v = holders.firstOrNull { it.name == m.key }?.allOptions?.get(m.master)?.value as? Boolean ?: continue
			out[m.key] = v
		}
		return out
	}

	fun afterConfigLoad() {
		val table = ModuleTable.DEFAULT
		val graph = ModuleGraph(table.descriptors())
		val holders = ApexConfigLoader.allConfigs.filterIsInstance<ManagedConfig>()
		val l = ModuleLoader(graph, bindings(handlesByOwner, holders, table))
		l.adoptActive(graph.byId.keys)
		loader = l
		refresh()
		ModuleSwitches.listeners += ::refresh
		dev.ashtonite365.apex.gui.config.DangerousSettings.listeners += ::refresh
	}

	fun refresh() {
		val l = loader ?: return
		val table = ModuleTable.DEFAULT
		val holders = ApexConfigLoader.allConfigs.filterIsInstance<ManagedConfig>()
		val states = ModuleStates(l.graph).evaluate(switches(holders, table), dev.ashtonite365.apex.gui.config.DangerousSettings.allowed)
		for (e in l.apply(states)) if (e is LoaderEvent.Failed) Faults.reportFault("Module ${e.id} failed to switch", e.error)
		applyGates(states)
	}

	fun applyGates(states: Map<String, ModuleState>, table: ModuleTable = ModuleTable.DEFAULT) {
		for (id in ModuleGates.ids) {
			val row = table.byKey[id] ?: continue
			if (row.master.isNotEmpty()) continue
			states[id]?.let { ModuleGates.set(id, it.active) }
		}
	}

	private val defaultGraph by lazy { ModuleGraph(ModuleTable.DEFAULT.descriptors()) }

	private fun liveHolders() = ApexConfigLoader.allConfigs.filterIsInstance<ManagedConfig>()

	fun currentStates(): Map<String, ModuleState> =
		ModuleStates(loader?.graph ?: defaultGraph)
			.evaluate(switches(liveHolders(), ModuleTable.DEFAULT), dev.ashtonite365.apex.gui.config.DangerousSettings.allowed)

	@JvmStatic
	fun isModuleOff(holderName: String): Boolean = currentStates()[holderName]?.active == false

	fun setSwitch(id: String, on: Boolean): SwitchChange {
		val table = ModuleTable.DEFAULT
		val holders = liveHolders()
		val before = switches(holders, table)
		val rules = ModuleStates(loader?.graph ?: defaultGraph)
		val change = if (on) rules.enable(before, id)
		else rules.disable(before, id, dev.ashtonite365.apex.gui.config.DangerousSettings.allowed)
		for ((key, value) in change.switches) {
			if (rules.switchOf(before, key) == value) continue
			val row = table.byKey[key] ?: continue
			if (row.master.isNotEmpty()) {
				@Suppress("UNCHECKED_CAST")
				val master = holders.firstOrNull { it.name == key }?.allOptions?.get(row.master) as? dev.ashtonite365.apex.gui.config.ManagedOption<Boolean>
				master?.value = value
			} else ModuleSwitches.TConfig.switches[key]?.value = value
		}
		refresh()
		return change
	}
}
