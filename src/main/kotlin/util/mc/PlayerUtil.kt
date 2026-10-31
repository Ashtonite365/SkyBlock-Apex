package dev.ashtonite365.apex.util.mc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

val Player.mainHandStack: ItemStack
	get() = mainHandItem
