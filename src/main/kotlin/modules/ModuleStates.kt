// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

sealed interface InactiveReason {

	data object SwitchedOff : InactiveReason

	data object NeedsDangerous : InactiveReason

	data class NeedsModule(val id: String) : InactiveReason
}

data class ModuleState(val id: String, val switchedOn: Boolean, val inactive: InactiveReason?) {
	val active get() = inactive == null
}

data class SwitchChange(
	val switches: Map<String, Boolean>,

	val autoEnabled: List<String>,

	val stoppedDependents: List<String>,
)

class ModuleStates(val graph: ModuleGraph) {
	fun defaultSwitch(id: String) = !graph.byId.getValue(id).tag.gated

	fun switchOf(switches: Map<String, Boolean>, id: String) = switches[id] ?: defaultSwitch(id)

	fun evaluate(switches: Map<String, Boolean>, dangerousAllowed: Boolean): Map<String, ModuleState> {
		val out = LinkedHashMap<String, ModuleState>()
		for (id in graph.order) {
			val m = graph.byId.getValue(id)
			val on = switchOf(switches, id)
			val reason = when {
				!on -> InactiveReason.SwitchedOff
				m.tag == ModuleTag.PURPLE && !dangerousAllowed -> InactiveReason.NeedsDangerous
				else -> m.dependsOn.sorted().firstOrNull { out.getValue(it).inactive != null }?.let { InactiveReason.NeedsModule(it) }
			}
			out[id] = ModuleState(id, on, reason)
		}
		return out
	}

	fun enable(switches: Map<String, Boolean>, id: String): SwitchChange {
		val out = switches.toMutableMap()
		val auto = graph.dependenciesOf(id).filter { !switchOf(switches, it) }
		for (d in auto) out[d] = true
		out[id] = true
		return SwitchChange(out, auto, emptyList())
	}

	fun disable(switches: Map<String, Boolean>, id: String, dangerousAllowed: Boolean): SwitchChange {
		val before = evaluate(switches, dangerousAllowed)
		val out = switches.toMutableMap()
		out[id] = false
		val after = evaluate(out, dangerousAllowed)
		val stopped = graph.dependentsOf(id).filter { before.getValue(it).active && !after.getValue(it).active }
		return SwitchChange(out, emptyList(), stopped)
	}
}
