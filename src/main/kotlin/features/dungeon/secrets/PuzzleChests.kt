// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

object PuzzleChests {

	val CATALOGUED_CHEST_ROOMS = setOf("Tic Tac Toe", "Lower Blaze", "Higher Blaze")

	fun liveChestsAreSecrets(type: String?, name: String?): Boolean =
		!type.equals("PUZZLE", ignoreCase = true) || name in CATALOGUED_CHEST_ROOMS
}
