package dev.ashtonite365.apex.util.mc

import net.minecraft.world.item.component.CustomData
import net.minecraft.nbt.Tag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.CompoundTag
import dev.ashtonite365.apex.mixins.accessor.AccessorNbtComponent

fun Iterable<Tag>.toNbtList(): ListTag {
	val list = ListTag()
	forEach(list::add)
	return list
}

@Suppress("CAST_NEVER_SUCCEEDS")
val CustomData.unsafeNbt: CompoundTag
	get() = (this as AccessorNbtComponent).unsafeNbt_apex
