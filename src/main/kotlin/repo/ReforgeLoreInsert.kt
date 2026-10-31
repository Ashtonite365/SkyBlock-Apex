package dev.ashtonite365.apex.repo

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.bareText

object ReforgeLoreInsert {
	private val BONUS_HEADER = Regex("^[A-Z][\\w' -]* Bonus$")

	fun isBonusHeader(line: String): Boolean = BONUS_HEADER.matches(line.trim())

	fun rarityLineIndex(lore: List<Component>): Int =
		lore.indexOfLast { line -> line.bareText.split(' ').any { Rarity.fromString(it) != null } }

	fun insertionIndex(lore: List<Component>): Int {
		val lastBlank = lore.indexOfLast { it.bareText.isBlank() }
		if (lastBlank >= 0) return lastBlank
		val rarity = rarityLineIndex(lore)
		return if (rarity >= 0) rarity else lore.size
	}

	fun insert(lore: List<Component>, header: Component, ability: List<Component>): List<Component> {
		val headerText = header.bareText.trim()
		if (lore.any { it.bareText.trim() == headerText }) return lore
		val abilityHasHeader = ability.firstOrNull()?.bareText?.let(::isBonusHeader) == true
		val at = insertionIndex(lore)
		val out = ArrayList<Component>(lore.size + ability.size + 2)
		out.addAll(lore.subList(0, at))
		if (at > 0 && lore[at - 1].bareText.isNotBlank()) out.add(Component.literal(""))
		if (!abilityHasHeader) out.add(header)
		out.addAll(ability)
		if (at == lore.size || lore[at].bareText.isNotBlank()) {
			if (at < lore.size) out.add(Component.literal(""))
		}
		out.addAll(lore.subList(at, lore.size))
		return out
	}
}
