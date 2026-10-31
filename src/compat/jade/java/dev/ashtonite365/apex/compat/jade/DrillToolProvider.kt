// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jade

import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.JadeIds
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.theme.IThemeHelper
import snownee.jade.api.ui.JadeUI
import snownee.jade.impl.ui.ItemStackElement
import snownee.jade.impl.ui.TextElementImpl
import net.minecraft.client.gui.layouts.LayoutElement
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents

class DrillToolProvider : IBlockComponentProvider {
	override fun getUid(): Identifier = Apex.identifier(UID_PATH)

	override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
		val power = CustomFakeBlockProvider.getCustomBlock(accessor)?.breakingPower ?: return
		if (power <= 0) return
		val tool = RepoManager.miningData.getToolsThatCanBreak(power).firstOrNull() ?: return
		val icon = iconOf(tool)
		val mark = markFor(heldBreakingPower() >= power)
		tooltip.replace(JadeIds.MC_HARVEST_TOOL) { lines -> lines.map { line -> line.map { swap(it, icon, mark) } } }
	}

	private fun swap(element: LayoutElement, icon: ItemStack, mark: LayoutElement): LayoutElement = when {
		element is ItemStackElement -> JadeUI.item(icon)
		element is TextElementImpl && element.string.trim() in MARKS -> mark
		else -> element
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	private fun iconOf(tool: SBItemStack): ItemStack = tool.asImmutableItemStack().currentCopy()

	private fun heldBreakingPower(): Int {
		val held = Client.player?.mainHandItem ?: return 0
		if (held.isEmpty) return 0
		return SBItemStack(held).breakingPower
	}

	private fun markFor(canHarvest: Boolean): LayoutElement {
		val theme = IThemeHelper.get()
		return JadeUI.text(if (canHarvest) theme.success(CAN) else theme.failure(CANNOT))
	}

	private companion object {
		const val UID_PATH = "toolprovider"
		const val CAN = "✔"
		const val CANNOT = "✕"
		val MARKS = setOf(CAN, CANNOT)
	}
}
