package dev.ashtonite365.apex.repo

import dev.ashtonite365.apex.repo.SafeRepoConstants
import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.constants.Enchants
import dev.ashtonite365.apex.features.inventory.EnchantPoolLogic

class EnchantData : io.github.moulberry.repo.IReloadable {

	var allEnchants: Enchants? = null; private set

	var maxLevels: Map<String, Int> = emptyMap()
	override fun reload(repo: NEURepository): Unit {
		val e = SafeRepoConstants.enchants(repo)
		allEnchants = e
		val known = e?.availableEnchants?.values?.flatten()?.map { it.lowercase() }?.toSet().orEmpty()
		maxLevels = EnchantPoolLogic.maxLevels(repo.items.items.keys, known,
			e?.enchantExperienceCost?.mapValues { it.value.size }.orEmpty())
	}
}
