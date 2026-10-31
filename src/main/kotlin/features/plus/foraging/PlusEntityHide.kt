// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.features.plus.hunting.PlusHunting
import dev.ashtonite365.apex.features.plus.rift.PlusRiftDanceRoom
import dev.ashtonite365.apex.features.plus.rift.PlusRiftMisc
import dev.ashtonite365.apex.features.plus.rift.PlusRiftParkour
import net.minecraft.world.entity.Entity

object PlusEntityHide {

	private val hiders: List<(Entity) -> Boolean> = listOf(
		{ PlusForaging.shouldHideEntity(it) },
		{ PlusHunting.shouldHideEntity(it) },
		{ PlusRiftMisc.shouldHideEntity(it) },
		{ PlusRiftParkour.shouldHideEntity(it) },
		{ PlusRiftDanceRoom.shouldHideEntity(it) },
	)

	@JvmStatic
	fun shouldHide(entity: Entity): Boolean = hiders.any { hide -> hide(entity) }
}
