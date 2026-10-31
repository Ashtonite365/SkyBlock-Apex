// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.plus.slayer

import kotlin.math.ceil
import kotlin.math.max

object PlusSlayerLogic {
	private val SUFFIXES = mapOf('k' to 1e3, 'm' to 1e6, 'b' to 1e9)

	fun parseShort(s: String): Double? {
		val text = s.replace(",", "").trim()
		val factor = text.lastOrNull()?.lowercaseChar()?.let(SUFFIXES::get)
		val digits = if (factor == null) text else text.substring(0, text.length - 1)
		val value = digits.toDoubleOrNull() ?: return null
		return value * (factor ?: 1.0)
	}

	private val PROGRESS = Regex("^\\(?([0-9,.]+[kKmM]?)/([0-9,.]+[kKmM]?)\\)? .*")

	fun parseProgress(line: String): Pair<Double, Double>? {
		val g = PROGRESS.matchEntire(line.trim())?.groupValues ?: return null
		val current = parseShort(g[1]) ?: return null
		val total = parseShort(g[2]) ?: return null
		if (total <= 0) return null
		return current to total
	}

	fun remainingKills(current: Double, total: Double, xpPerKill: Double): Int? {
		if (xpPerKill <= 0) return null
		return max(ceil((total - current) / xpPerKill).toInt(), 0)
	}

	val rngMeterChat = Regex("^ {0,3}RNG Meter - .* Stored XP$")

	private val NOT_SPAWNABLE = setOf("Only inside The Rift!", "Doesn't exist here!")
	fun isNotSpawnable(lore: List<String>) = lore.any { it in NOT_SPAWNABLE }

	private val ORDINARY_MOBS = setOf("Zombie", "Zombie Villager", "Crypt Ghoul", "Graveyard Zombie", "Dasher Spider", "Weaver Spider",
		"Splitter Spider", "Voracious Spider", "Silverfish", "Wolf", "Howling Spirit", "Pack Spirit", "Old Wolf", "Soul of the Alpha",
		"Zealot", "Enderman", "Voidling Fanatic", "Voidling Extremist", "Blaze", "Mutated Blaze", "Bezal", "Wither Skeleton",
		"Flaming Spider", "Magma Cube", "Kada Knight", "Smoldering Blaze")
	private val MOB_TAG = Regex("^\\[Lv\\d+] (?:\\S+ )?(.+?) [\\d.,]+[kMB]?/[\\d.,]+[kMB]?❤$")

	fun isIrrelevantMobTag(name: String): Boolean {
		val mob = MOB_TAG.matchEntire(name.trim())?.groupValues?.get(1) ?: return false
		return mob in ORDINARY_MOBS
	}

	private val FIRE_PILLAR = Regex("^(\\d+)s 8 hits$")
	fun firePillarSeconds(name: String): Int? = FIRE_PILLAR.matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull()

	enum class HellionShield(val label: String, val colour: String, val dagger: String) {
		ASHEN("Ashen", "§8", FIRE_DAGGERS),
		AURIC("Auric", "§e", FIRE_DAGGERS),
		SPIRIT("Spirit", "§f", TWILIGHT_DAGGERS),
		CRYSTAL("Crystal", "§b", TWILIGHT_DAGGERS);

		companion object {

			fun inName(name: String): HellionShield? = entries.find { it.name in name }
		}
	}

	private val TWINCLAWS = Regex(".*TWINCLAWS [0-9.,]+s.*")
	fun isTwinclaws(name: String) = TWINCLAWS.matches(name)
}

private const val FIRE_DAGGERS = "Firedust/Kindlebane/Pyrochaos Dagger"
private const val TWILIGHT_DAGGERS = "Twilight/Mawdredge/Deathripper Dagger"
