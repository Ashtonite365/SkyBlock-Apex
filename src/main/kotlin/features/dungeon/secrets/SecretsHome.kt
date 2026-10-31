// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

import dev.ashtonite365.apex.features.dungeon.SectionHome
import dev.ashtonite365.apex.settings.MainSection

object SecretsHome : SectionHome(
	MainSection.SECRETS,
	listOf("dungeon-secrets", "breakable-walls"),
	borrowed = listOf(
		"dungeon-extras.room-clear", "dungeon-extras.room-clear-mode",
		"dungeon-map.room-ticks",
		"dungeon-tweaks.secret-chime", "dungeon-tweaks.mute-secret-sounds",
		"dungeon-splits.run-summary",
	),
)
