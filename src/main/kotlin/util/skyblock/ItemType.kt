// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.petBlob

data class ItemType private constructor(val name: String)
{
	val isDungeon: Boolean get() = name.startsWith(DUNGEON_PREFIX)

	val dungeonVariant: ItemType get() = ofName(DUNGEON_PREFIX + name)

	val nonDungeonVariant: ItemType get() = ofName(name.removePrefix(DUNGEON_PREFIX))

	override fun toString(): String = this.name

	companion object Known {
		private const val DUNGEON_PREFIX = "DUNGEON "

		private val OBFUSCATED_RUN = Regex("§[kK].*?(?:§[0-9a-fA-FrR]|$)")

		fun ofName(name: String): ItemType = ItemType(name)

		fun fromEscapeCodeLore(lore: String): ItemType? =
			OBFUSCATED_RUN.replace(lore, "").trim().substringAfter(' ', missingDelimiterValue = "").takeUnless(String::isEmpty)?.let(::ofName)

		fun fromItemStack(itemStack: ItemView): ItemType? {
			if (itemStack.petBlob != null) return PET
			return itemStack.loreAccordingToNbt.firstNotNullOfOrNull { line ->
				val words = line.string.split(' ').filter { word -> word.length >= 2 }
				if (words.any { word -> word.any(Char::isLowerCase) }) return@firstNotNullOfOrNull null
				val rarityAt = words.indexOfFirst { word -> Rarity.fromString(word) != null }
				val typeWords = if (rarityAt < 0) emptyList() else words.drop(rarityAt + 1)
				typeWords.takeIf { it.isNotEmpty() }?.let { ofName(it.joinToString(" ")) }
			}
		}

		val SWORD: ItemType = ofName("SWORD")
		val DRILL: ItemType = ofName("DRILL")
		val PICKAXE: ItemType = ofName("PICKAXE")
		val AXE: ItemType = ofName("AXE")
		val GAUNTLET: ItemType = ofName("GAUNTLET")
		val LONGSWORD: ItemType = ofName("LONG SWORD")
		val EQUIPMENT: ItemType = ofName("EQUIPMENT")
		val FISHING_WEAPON: ItemType = ofName("FISHING WEAPON")
		val CLOAK: ItemType = ofName("CLOAK")
		val BELT: ItemType = ofName("BELT")
		val NECKLACE: ItemType = ofName("NECKLACE")
		val BRACELET: ItemType = ofName("BRACELET")
		val GLOVES: ItemType = ofName("GLOVES")
		val ROD: ItemType = ofName("ROD")
		val FISHING_ROD: ItemType = ofName("FISHING ROD")
		val VACUUM: ItemType = ofName("VACUUM")
		val CHESTPLATE: ItemType = ofName("CHESTPLATE")
		val LEGGINGS: ItemType = ofName("LEGGINGS")
		val HELMET: ItemType = ofName("HELMET")
		val BOOTS: ItemType = ofName("BOOTS")
		val SHOVEL: ItemType = ofName("SHOVEL")
		val BOW: ItemType = ofName("BOW")
		val HOE: ItemType = ofName("HOE")
		val CARNIVAL_MASK: ItemType = ofName("CARNIVAL MASK")

		val NIL: ItemType = ofName("__NIL")

		val PET: ItemType = ofName("PET")
	}
}
