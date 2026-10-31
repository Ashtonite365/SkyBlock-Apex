// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.emi

import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries
import dev.ashtonite365.apex.repo.SBItemStack

class SkyblockEmiStack(val stack: SBItemStack) : EmiStack() {
	init {
		setAmount(stack.getStackSize().toLong())
	}

	override fun copy(): EmiStack = SkyblockEmiStack(stack).also { it.setAmount(amount) }
	override fun isEmpty(): Boolean = stack.getStackSize() == 0
	override fun getComponentChanges(): DataComponentPatch = DataComponentPatch.EMPTY
	override fun getKey(): Any = SkyblockEntries.identity(stack)
	override fun getId(): Identifier = stack.catalogItemId.identifier

	override fun getItemStack(): ItemStack = runCatching { SkyblockEntries.cheatStack(stack) }.getOrDefault(ItemStack.EMPTY)
	override fun getTooltipText(): List<Component> = SkyblockEntries.tooltip(stack)
	override fun getName(): Component = Component.literal(SkyblockEntries.searchName(stack))

	override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, delta: Float, flags: Int) {
		SkyblockEntries.draw(graphics, stack, x, y, withCount = flags and EmiIngredient.RENDER_AMOUNT != 0)
	}
}

object EmiEntries {
	var ingredientFactory: (List<EmiStack>) -> EmiIngredient = { EmiIngredient.of(it) }

	fun stack(stack: SBItemStack): EmiStack = SkyblockEmiStack(stack)
	fun ingredient(stacks: List<SBItemStack>): EmiIngredient = when (stacks.size) {
		0 -> EmiStack.EMPTY
		1 -> stack(stacks[0])
		else -> ingredientFactory(stacks.map(::stack))
	}

	fun skyblockOf(ingredient: EmiIngredient?): SBItemStack? =
		(ingredient?.emiStacks?.firstOrNull() as? SkyblockEmiStack)?.stack
}
