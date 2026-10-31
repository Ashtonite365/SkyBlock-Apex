// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.rei

import com.mojang.serialization.Codec
import java.util.stream.Stream
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntrySerializer
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext
import me.shedaniel.rei.api.common.entry.type.EntryDefinition
import me.shedaniel.rei.api.common.entry.type.EntryType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ItemId

object ReiEntries {
	val TYPE_ID: Identifier = Identifier.fromNamespaceAndPath(Apex.MOD_ID, "skyblock_item")
	val TYPE: EntryType<SBItemStack> by lazy { EntryType.deferred(TYPE_ID) }

	var stackFactory: (SBItemStack) -> EntryStack<*> = { EntryStack.of(TYPE, it) }
	var ingredientFactory: (List<EntryStack<*>>) -> EntryIngredient = { EntryIngredient.of(it) }

	fun stack(stack: SBItemStack): EntryStack<*> = stackFactory(stack)
	fun ingredient(stacks: List<SBItemStack>): EntryIngredient = ingredientFactory(stacks.map(::stack))

	fun skyblockOf(entry: EntryStack<*>?): SBItemStack? = entry?.takeUnless { it.isEmpty }?.value as? SBItemStack
}

object SkyblockEntryDefinition : EntryDefinition<SBItemStack> {
	override fun getValueType(): Class<SBItemStack> = SBItemStack::class.java
	override fun getType(): EntryType<SBItemStack> = ReiEntries.TYPE
	override fun getRenderer(): EntryRenderer<SBItemStack> = SkyblockEntryRenderer
	override fun getIdentifier(entry: EntryStack<SBItemStack>?, value: SBItemStack): Identifier = value.catalogItemId.identifier
	override fun getContainingNamespace(entry: EntryStack<SBItemStack>?, value: SBItemStack): String = SkyblockEntries.SOURCE_MOD
	override fun isEmpty(entry: EntryStack<SBItemStack>?, value: SBItemStack): Boolean = value.getStackSize() == 0
	override fun copy(entry: EntryStack<SBItemStack>?, value: SBItemStack): SBItemStack = value
	override fun normalize(entry: EntryStack<SBItemStack>?, value: SBItemStack): SBItemStack = SkyblockEntries.normalize(value)
	override fun wildcard(entry: EntryStack<SBItemStack>?, value: SBItemStack): SBItemStack = SkyblockEntries.normalize(value)
	override fun cheatsAs(entry: EntryStack<SBItemStack>?, value: SBItemStack): ItemStack = SkyblockEntries.cheatStack(value)

	override fun hash(entry: EntryStack<SBItemStack>?, value: SBItemStack, context: ComparisonContext): Long =
		SkyblockEntries.identity(value).hashCode().toLong()

	override fun equals(o1: SBItemStack, o2: SBItemStack, context: ComparisonContext): Boolean =
		SkyblockEntries.identity(o1) == SkyblockEntries.identity(o2)

	override fun getSerializer(): EntrySerializer<SBItemStack> = Serializer
	override fun asFormattedText(entry: EntryStack<SBItemStack>?, value: SBItemStack): Component =
		Component.literal(SkyblockEntries.searchName(value))

	override fun getTagsFor(entry: EntryStack<SBItemStack>?, value: SBItemStack): Stream<out TagKey<*>> = Stream.empty()

	object Serializer : EntrySerializer<SBItemStack> {
		private val codec: Codec<SBItemStack> = Codec.STRING.xmap({ SBItemStack(ItemId(it)) }, { it.catalogItemId.skyblockId })
		override fun codec(): Codec<SBItemStack> = codec
		override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, SBItemStack> =
			StreamCodec.of({ buf, v -> buf.writeUtf(v.catalogItemId.skyblockId) }, { buf -> SBItemStack(ItemId(buf.readUtf())) })
	}
}

object SkyblockEntryRenderer : EntryRenderer<SBItemStack> {
	override fun render(entry: EntryStack<SBItemStack>, graphics: GuiGraphics, bounds: Rectangle, mouseX: Int, mouseY: Int, delta: Float) {
		SkyblockEntries.draw(graphics, entry.value, bounds.centerX - 8, bounds.centerY - 8)
	}

	override fun getTooltip(entry: EntryStack<SBItemStack>, context: TooltipContext): Tooltip? =
		Tooltip.create(context.point, SkyblockEntries.tooltip(entry.value, context.vanillaContext(), context.flag))
}
