// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.ManagedConfig

object P2Opts {
	fun load(vararg cfgs: ManagedConfig) {
		for (cfg in cfgs) if (cfg.sortedOptions.any { it._actualValue == null }) cfg.explicitDefaultLoad()
	}

	@Suppress("UNCHECKED_CAST")
	fun <T> with(cfg: ManagedConfig, values: Map<String, Any>, block: () -> T): T {
		load(cfg)
		val opts = values.keys.map { id -> cfg.sortedOptions.first { it.propertyName == id } as ManagedOption<Any> }
		val old = opts.map { it._actualValue }
		opts.forEach { it._actualValue = values.getValue(it.propertyName) }
		try { return block() } finally { opts.zip(old).forEach { (o, v) -> o._actualValue = v } }
	}
}
