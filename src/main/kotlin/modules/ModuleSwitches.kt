// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ModuleSwitches {
	const val HOLDER = "modules"

	val switchedHere: List<String> get() = TConfig.switches.keys.toList()

	fun current(): Map<String, Boolean> = TConfig.switches.mapValues { it.value.get() }

	@Config
	object TConfig : ManagedConfig("modules", Category.MISC) {
		val switches: Map<String, ManagedOption<Boolean>> =
			ModuleTable.DEFAULT.modules.filter { it.master.isEmpty() }
				.associate { row -> row.key to toggle(row.key) { !row.tag.gated } }

		override fun onChange(option: ManagedOption<*>) = listeners.forEach { it() }
	}

	val listeners = mutableListOf<() -> Unit>()
}
