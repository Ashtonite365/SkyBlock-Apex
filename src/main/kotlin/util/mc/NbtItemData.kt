package dev.ashtonite365.apex.util.mc

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ItemLore

val ItemView.loreAccordingToNbt: List<Component>
	get() = get(DataComponents.LORE)?.lines ?: emptyList()

var ItemEditor.loreAccordingToNbt: List<Component>
	get() = asView().loreAccordingToNbt
	set(lines) = set(DataComponents.LORE, ItemLore(lines))

val ItemView.displayNameAccordingToNbt: Component
	get() {
		for (source in nameSources) get(source)?.let { return it }
		return CommonComponents.EMPTY
	}

var ItemEditor.displayNameAccordingToNbt: Component
	get() = asView().displayNameAccordingToNbt
	set(name) = setCustomName(name)

fun ItemEditor.setCustomName(text: Component) = set(DataComponents.CUSTOM_NAME, text)

private val nameSources = arrayOf(DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME)

private fun ItemEditor.asView(): ItemView = this
