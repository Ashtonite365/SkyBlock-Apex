package dev.ashtonite365.apex.util.mc

import kotlin.jvm.optionals.getOrNull
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.RegistryOps
import net.minecraft.core.HolderLookup
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStackTemplate
import dev.ashtonite365.apex.util.Client

fun ItemEditor.appendLore(args: List<Component>) {
	if (args.isNotEmpty()) modifyLore { existing -> existing + args }
}

fun ItemEditor.modifyLore(update: (List<Component>) -> List<Component>) {
	loreAccordingToNbt = update(loreAccordingToNbt)
}

fun loadItemFromNbt(nbt: CompoundTag, registries: HolderLookup.Provider = Client.builtinRegistries): KeptStack? {
	val ops = RegistryOps.create(NbtOps.INSTANCE, registries)
	val template = ItemStackTemplate.CODEC.parse(ops, nbt).result().getOrNull() ?: return null
	return KeptStack.ofTemplate(template)
}
