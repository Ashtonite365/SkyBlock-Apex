// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.data.Rarity
import dev.ashtonite365.apex.util.PetInfoJson

data class PetData(val rarity: Rarity, val petId: String, val exp: Double, val isStub: Boolean = false) {

	val levelData: ExpLadders.PetLevel by lazy { ladder().getPetLevel(exp) }

	private fun ladder() = ExpLadders.getExpLadder(petId, rarity)

	companion object Companion {
		fun fromHypixel(petInfo: PetInfoJson): PetData = PetData(rarity = petInfo.rarity, petId = petInfo.species, exp = petInfo.xp)

		fun forLevel(petId: String, rarity: Rarity, level: Int): PetData {
			val needed = ExpLadders.getExpLadder(petId, rarity).getPetExpForLevel(level)
			return PetData(rarity, petId, needed.toDouble())
		}
	}
}
