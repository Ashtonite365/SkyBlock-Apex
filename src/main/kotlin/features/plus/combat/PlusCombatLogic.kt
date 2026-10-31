// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.plus.combat

import dev.ashtonite365.apex.features.plus.slayer.PlusSlayerLogic

object PlusCombatLogic {

	val bosses = listOf(
		"Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Conjoined Brood", "Sven Packmaster", "Voidgloom Seraph",
		"Inferno Demonlord", "Riftstalker Bloodfiend", "Arachne", "Broodmother", "Magma Boss", "Bladesoul", "Barbarian Duke X",
		"Mage Outlaw", "Ashfang", "Kuudra", "Dragon", "Endstone Protector", "Headless Horseman", "Thunder", "Lord Jawbus",
		"Reindrake", "Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Maxor", "Storm", "Goldor", "Necron",
		"Wither King", "Leech Supreme", "Bacte", "Inquisitor", "Minos Champion", "Minotaur", "Gaia Construct", "Sea Emperor",
		"Ragnarok", "Grim Reaper", "Phantom Fisher", "Vanquisher", "Matriarch", "Mutant Tarantula", "Primal Fear",
	)

	data class BossTag(val name: String, val health: Double, val maxHealth: Double?) {

		val percent: Double?
			get() {
				val max = maxHealth ?: return null
				return if (max > 0) health / max * 100 else null
			}
	}

	private val HEALTH = Regex("([\\d.,]+[kKmMbB]?)(?:/([\\d.,]+[kKmMbB]?))?❤")

	fun parseBossTag(tag: String): BossTag? {
		val boss = bosses.find { it in tag } ?: return null
		val health = HEALTH.find(tag)?.groupValues ?: return null
		val current = PlusSlayerLogic.parseShort(health[1]) ?: return null
		val max = if (health[2].isEmpty()) null else PlusSlayerLogic.parseShort(health[2])
		return BossTag(boss, current, max)
	}

	private val ENDER_NODE = Regex("^ENDER NODE! You found (?:(\\d+)x )?(.+)!$")

	fun parseEnderNode(line: String): Pair<Long, String>? {
		val g = ENDER_NODE.matchEntire(line)?.groupValues ?: return null
		return (g[1].toLongOrNull() ?: 1L) to g[2]
	}

	private val ARMOR_STACK = Regex(" (\\d+[ᝐ⁑|҉Ѫ⚶])")
	fun armorStack(actionBar: String): String? = ARMOR_STACK.find(actionBar)?.groupValues?.get(1)

	const val ARACHNE_CALLING = "[BOSS] Arachne: A befitting welcome!"
	const val ARACHNE_CRYSTAL = "[BOSS] Arachne: With your sacrifice."
	fun isArachneDown(line: String) = line.trim().endsWith("ARACHNE DOWN!")

	private val BROOD_STAGE = Regex("Broodmother: (Slain|Dormant|Soon|Awakening|Imminent|Alive!)")
	fun broodmotherStage(tab: List<String>): String? {
		for (line in tab) BROOD_STAGE.find(line)?.let { return it.groupValues[1] }
		return null
	}

	private val DRAGON_DROP = Regex("^(?:RARE|PET|LEGENDARY|EPIC)? ?DROP! (?:\\(\\d+x\\) )?(.+?)(?: \\(.*\\))?$")
	fun dragonLoot(line: String): String? = DRAGON_DROP.matchEntire(line)?.groupValues?.get(1)

	private val SUMMON_SPAWN = Regex("^You have spawned your .+ soul! \\(\\d+ Mana\\)$")
	private val SERAPH_RECALL = Regex("^The Seraph recalled your \\d+ summoned allies!$")
	private val DESPAWN_LINES = setOf("You have despawned your monster!", "You have despawned your monsters!")

	fun isSummonSpawn(line: String) = SUMMON_SPAWN.matches(line)
	fun isSummonDespawn(line: String) = line in DESPAWN_LINES || SERAPH_RECALL.matches(line)
}
