// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

object MinibossNames {

	val NAMES = listOf(
		"Lost Adventurer", "Frozen Adventurer", "Angry Archaeologist", "King Midas", "Redstone Warrior",
	)
	const val SHADOW_ASSASSIN = "Shadow Assassin"

	private val colourCode = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun of(tag: String): String? {
		val clean = tag.replace(colourCode, "")
		if (clean.contains(SHADOW_ASSASSIN, ignoreCase = true)) return null
		return NAMES.firstOrNull { clean.contains(it, ignoreCase = true) }
	}

	fun isDead(tag: String): Boolean = Regex("(?:^|\\s)0❤\\s*$").containsMatchIn(tag.replace(colourCode, ""))
}
