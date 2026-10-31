// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.features.dungeon.SectionHome
import dev.ashtonite365.apex.settings.MainSection

object MiningHome : SectionHome(
	MainSection.MINING,
	listOf(
		"mining-overlay", "powder-grinding", "pickaxe-info", "commissions", "mining-extras", "mining-loot",
		"pristine-profit", "mining-solvers", "dwarven-waypoints", "crystal-hollows-overlay", "crystal-hollows-waypoints",
		"crystal-hollows-minimap", "crystal-hollows-chests", "metal-detector", "wishing-compass", "fossil-excavator",
		"mining-risky",
	),
	links = listOf(MainSection.MINING to "timers.mining"),
)
