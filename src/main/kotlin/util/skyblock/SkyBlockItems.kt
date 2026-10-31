// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import dev.ashtonite365.apex.util.ItemId

object SkyBlockItems {
	private fun id(name: String): ItemId = ItemId(name)

	val COINS: ItemId = id("SKYBLOCK_COIN")
	val ROTTEN_FLESH: ItemId = id("ROTTEN_FLESH")
	val ENCHANTED_DIAMOND: ItemId = id("ENCHANTED_DIAMOND")
	val DIAMOND: ItemId = id("DIAMOND")
	val ANCESTRAL_SPADE: ItemId = id("ANCESTRAL_SPADE")
	val ARCHAIC_SPADE: ItemId = id("ARCHAIC_SPADE")
	val DEIFIC_SPADE: ItemId = id("DEIFIC_SPADE")
	val REFORGE_ANVIL: ItemId = id("REFORGE_ANVIL")
	val SLICE_OF_BLUEBERRY_CAKE: ItemId = id("SLICE_OF_BLUEBERRY_CAKE")
	val SLICE_OF_CHEESECAKE: ItemId = id("SLICE_OF_CHEESECAKE")
	val SLICE_OF_GREEN_VELVET_CAKE: ItemId = id("SLICE_OF_GREEN_VELVET_CAKE")
	val SLICE_OF_RED_VELVET_CAKE: ItemId = id("SLICE_OF_RED_VELVET_CAKE")
	val SLICE_OF_STRAWBERRY_SHORTCAKE: ItemId = id("SLICE_OF_STRAWBERRY_SHORTCAKE")
	val ASPECT_OF_THE_VOID: ItemId = id("ASPECT_OF_THE_VOID")
	val ASPECT_OF_THE_END: ItemId = id("ASPECT_OF_THE_END")
	val BONE_BOOMERANG: ItemId = id("BONE_BOOMERANG")
	val STARRED_BONE_BOOMERANG: ItemId = id("STARRED_BONE_BOOMERANG")
	val TRIBAL_SPEAR: ItemId = id("TRIBAL_SPEAR")
	val BLOCK_ZAPPER: ItemId = id("BLOCK_ZAPPER")
	val HUNTING_TOOLKIT: ItemId = id("HUNTING_TOOLKIT")
	val ETHERWARP_CONDUIT: ItemId = id("ETHERWARP_CONDUIT")
}
