// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.constants.PetLevelingBehaviourOverride
import io.github.moulberry.repo.data.Rarity
import java.util.concurrent.ConcurrentHashMap

object ExpLadders
	: IReloadable {

	data class PetLevel(val currentLevel: Int, val maxLevel: Int,
		val expRequiredForNextLevel: Long, val expRequiredForMaxLevel: Long,
		val expInCurrentLevel: Float, var expTotal: Float,

		val known: Boolean = true
	) {

		val levelText: String get() = if (!known) "?" else "$currentLevel"
		val percentageToNextLevel: Float = expInCurrentLevel.div(expRequiredForNextLevel)
		val percentageToMaxLevel: Float = expTotal.div(expRequiredForMaxLevel)
	}

	data class ExpLadder(val individualLevelCost: List<Long>) {
		val cumulativeLevelCost: List<Long> = buildList {
			var sum = 0F
			add(sum.toLong())
			for (cost in individualLevelCost) {
				sum += cost
				add(sum.toLong())
			}
		}

		fun getPetLevel(
			currentExp: Double,
		): PetLevel {
			val asFloat = currentExp.toFloat()

			if (individualLevelCost.isEmpty()) return PetLevel(1, 1, 0L, 0L, 0F, asFloat, known = false)
			val totals = cumulativeLevelCost
			val level = totals.indexOfLast { total -> total <= currentExp } + 1
			val maxed = level > individualLevelCost.size
			val nextCost = individualLevelCost[if (maxed) individualLevelCost.lastIndex else level - 1]
			val progress = if (level < totals.size) (nextCost - (totals[level] - asFloat)).let { if (it < 0F) 0F else it } else asFloat - totals.last()
			return PetLevel(level, totals.size, nextCost, totals.last(), progress, asFloat)
		}

		fun getPetExpForLevel(level: Int): Long = when {
			level < 2 || individualLevelCost.isEmpty() -> 0L
			level >= cumulativeLevelCost.size -> cumulativeLevelCost.last()
			else -> cumulativeLevelCost[level - 1]
		}
	}

	private val ladders = ConcurrentHashMap<Pair<String, Rarity>, ExpLadder>()

	private const val USERS = "pet levels and pet XP tooltips"

	private fun buildLadder(petId: String, rarity: Rarity): ExpLadder {

		val leveling = runCatching { RepoManager.coreRepo.constants.petLevelingData }.getOrNull()
		val baseOffset = leveling?.petLevelStartOffset?.get(rarity)
		if (baseOffset == null) SafeRepoConstants.reportMissing("constants/pets.json#pet_rarity_offset", USERS)
		var costs: List<Int> = leveling?.petExpCostForLevel.orEmpty()
		if (costs.isEmpty()) SafeRepoConstants.reportMissing("constants/pets.json#pet_levels", USERS)
		var offset = baseOffset ?: 0
		var topLevel = 100
		leveling?.petLevelingBehaviourOverrides?.get(petId)?.let { special ->
			special.maxLevel?.let { topLevel = it }
			special.petLevelStartOffset?.get(rarity)?.let { offset = it }
			val extra = special.petExpCostModifier.orEmpty()
			costs = when (special.petExpCostModifierType) {
				PetLevelingBehaviourOverride.PetExpModifierType.APPEND -> costs + extra
				PetLevelingBehaviourOverride.PetExpModifierType.REPLACE -> extra
				null -> costs
			}
		}
		val steps = costs.drop(maxOf(offset, 0)).take(maxOf(topLevel - 1, 0))
		return ExpLadder(steps.map(Int::toLong))
	}

	fun getExpLadder(petId: String, rarity: Rarity): ExpLadder =
		try {
			ladders.computeIfAbsent(petId to rarity) { (id, tier) -> buildLadder(id, tier) }
		} catch (_: Throwable) {
			ExpLadder(emptyList())
		}

	fun maxLevelOverride(petId: String): Int? = try {
		RepoManager.coreRepo.constants.petLevelingData?.petLevelingBehaviourOverrides?.get(petId)?.maxLevel
	} catch (_: Throwable) {
		null
	}

	override fun reload(repository: NEURepository?) = ladders.clear()
}
