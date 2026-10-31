// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.features.dungeon.SectionHome
import dev.ashtonite365.apex.settings.MainSection

object FarmingHome : SectionHome(
	MainSection.FARMING,
	listOf("garden-tools", "plus-spray", "plus-plots", "plus-garden-warps", "composter", "trevor"),

	links = listOf(
		MainSection.TIMERS to "farming.trevor",
		MainSection.FARMING to "timers.garden",
	),
)
