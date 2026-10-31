// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.combat

import dev.ashtonite365.apex.features.plus.dungeon.DungeonPlusPorts
import dev.ashtonite365.apex.features.plus.slayer.SlayerPlusMerges
import dev.ashtonite365.apex.util.SkyBlockSession
import net.minecraft.world.entity.Entity

object PlusH2EntityHider {

	private val hiders: List<(Entity) -> Boolean> = listOf(
		{ DungeonPlusPorts.shouldHide(it) },
		{ SlayerPlusMerges.shouldHide(it) },
		{ CombatPlusMerges.shouldHide(it) },
	)

	@JvmStatic
	fun shouldHide(entity: Entity): Boolean = SkyBlockSession.inSkyBlock && hiders.any { hide -> hide(entity) }
}
