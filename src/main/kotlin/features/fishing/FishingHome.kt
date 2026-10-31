// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fishing

import dev.ashtonite365.apex.features.dungeon.SectionHome
import dev.ashtonite365.apex.settings.MainSection

object FishingHome : SectionHome(
	MainSection.FISHING,
	listOf(
		"fishing-helper", "fishing-tracker", "fishing-profit", "sea-creature-extras", "sea-creature-settings",
		"fishing-extras", "trophy-fishing", "trophy-fillet", "thunder-sparks", "lava-replacement", "fishing-risky",
	),
	links = listOf(MainSection.FISHING to "timers.fishing"),
)
