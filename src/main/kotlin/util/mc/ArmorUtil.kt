package dev.ashtonite365.apex.util.mc

import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

val LivingEntity.iterableArmorItems: Sequence<Pair<EquipmentSlot, ItemStack>>
	get() = sequence {
		for (slot in EquipmentSlot.entries) yield(Pair(slot, getItemBySlot(slot)))
	}
