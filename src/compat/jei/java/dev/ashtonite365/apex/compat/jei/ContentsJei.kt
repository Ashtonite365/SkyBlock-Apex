// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jei

import me.shedaniel.math.Rectangle
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.BoxContents
import dev.ashtonite365.apex.repo.BoxContentsLayout
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.setCustomName

data class ContentsPage(val box: String, val page: Int)

class ContentsJeiCategory(guiHelper: IGuiHelper) : LayouterCategory<ContentsPage>(
	TYPE, Component.literal("Contents"), guiHelper.createDrawableItemStack(ItemStack(Items.CHEST)), 160, 130,
) {
	companion object {
		val TYPE: IRecipeType<ContentsPage> = IRecipeType.create(Apex.MOD_ID, "box_contents", ContentsPage::class.java)
		const val BODY_TOP = 24
	}

	override fun allRecipes(): List<ContentsPage> = BoxContents.boxes.values.flatMap { b ->
		BoxContentsLayout.pages(b, getHeight() - BODY_TOP, getWidth() - 8, ::measure).indices.map { ContentsPage(b.box, it) }
	}

	private fun stackOf(d: BoxContents.Drop, box: BoxContents.Box): SBItemStack {
		val lore = BoxContents.dropLines(d, box).map { Component.literal(it) }
		return when (d.type) {
			BoxContents.Type.ITEM -> SBItemStack(ItemId(d.id!!), d.max.toInt().coerceAtLeast(1))
			BoxContents.Type.COINS -> SBItemStack(ItemId.COINS, d.max.toInt().coerceAtLeast(1))
			BoxContents.Type.SKILL_XP -> SBItemStack(ItemId("SKYBLOCK_SKILL_" + d.skill!!.uppercase()), 1).copy(
				fallback = dev.ashtonite365.apex.util.mc.KeptStack.build(Items.EXPERIENCE_BOTTLE) {
					setCustomName(Component.literal("§3" + d.skill!!.lowercase().replaceFirstChar(Char::uppercase) + " XP"))
				})
		}.copy(extraLore = lore)
	}

	override fun layout(recipe: ContentsPage, bounds: Rectangle, layouter: RecipeLayouter) {
		val box = BoxContents.boxes[recipe.box] ?: return
		val pages = BoxContentsLayout.pages(box, getHeight() - BODY_TOP, getWidth() - 8, ::measure)
		layouter.itemSlotAt(bounds.minX + 4, bounds.minY + 4, SBItemStack(ItemId(box.box)), RecipeLayouter.SlotRole.BIG_OUTPUT)
		val suffix = if (pages.size > 1) " §8(${recipe.page + 1}/${pages.size})" else ""
		val head = dev.ashtonite365.apex.features.menus.MenuTheme.fit(BoxContentsLayout.headerName(box), getWidth() - 36 - measure(suffix), ::measure) + suffix
		layouter.placeLabel(BoxContentsLayout.leftLabelX(bounds.minX + 32, head, ::measure), bounds.minY + 8, Component.literal(head))
		var y = bounds.minY + BODY_TOP
		for (line in pages.getOrNull(recipe.page).orEmpty()) {
			when (line) {
				is BoxContentsLayout.Label -> layouter.placeLabel(BoxContentsLayout.leftLabelX(bounds.minX + 4, line.text, ::measure), y + 1, Component.literal(line.text))
				is BoxContentsLayout.Drops -> line.drops.forEachIndexed { i, d ->
					layouter.itemSlotAt(bounds.minX + 4 + i * 18, y, stackOf(d, box), RecipeLayouter.SlotRole.SMALL_INPUT)
				}
			}
			y += line.h
		}
	}
}

private fun measure(s: String): Int = runCatching { net.minecraft.client.Minecraft.getInstance().font.width(s) }.getOrElse { BoxContentsLayout.roughWidth(s) }
