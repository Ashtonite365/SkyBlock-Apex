/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * APEX-26.3 PORT (item value tooltips). Original implementation written for this port from feature
 * descriptions only (similar features exist in, and BazaarNotifier); no code copied.
 */
package dev.ashtonite365.apex.features.inventory

import io.github.moulberry.repo.data.NEUCraftingRecipe
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object ItemValue {
	@Config
	object TConfig : ManagedConfig("item-value", Category.AUCTION_BAZAAR) {
		val craftCost by toggle("craft-cost") { true }
		val estimatedValue by toggle("estimated-value") { true }
		val containerValue by toggle("container-value") { true }
	}

	fun price(id: ItemId): Double? = HypixelStaticData.getPriceOfItem(id)

	fun craftCost(id: ItemId): Double? {
		val recipe = RepoManager.getRecipesFor(id).filterIsInstance<NEUCraftingRecipe>().firstOrNull() ?: return null
		var total = 0.0
		for (input in recipe.allInputs) {
			if (input.itemId == "SKYBLOCK_COIN") { total += input.amount; continue }
			total += (price(ItemId(input.itemId)) ?: return null) * input.amount
		}
		val outCount = recipe.allOutputs.firstOrNull { it.itemId == id.skyblockId }?.amount?.takeIf { it > 0 } ?: 1.0
		return total / outCount
	}

	data class Breakdown(val parts: List<Pair<String, Double>>) { val total get() = parts.sumOf { it.second } }

	fun estimate(stack: ItemStack): Breakdown? {
		val acc = stack.view()
		val id = acc.repoItemId ?: return null
		val parts = mutableListOf<Pair<String, Double>>()
		price(id)?.let { parts.add("Base" to it) }
		val ea = acc.customDataTag
		if (id.skyblockId != "ENCHANTED_BOOK" && !id.skyblockId.contains(';')) {
			val ench = ea.getCompoundOrEmpty("enchantments")
			var enchTotal = 0.0
			for (key in ench.keySet()) {
				val lvl = ench.getIntOr(key, 0)
				price(ItemId("${key.uppercase()};$lvl"))?.let { enchTotal += it }
			}
			if (enchTotal > 0) parts.add("Enchantments" to enchTotal)
		}
		if (ea.getIntOr("rarity_upgrades", 0) > 0) price(ItemId("RECOMBOBULATOR_3000"))?.let { parts.add("Recombobulator" to it) }
		val potato = ea.getIntOr("hot_potato_count", 0)
		if (potato > 0) {
			val hpb = minOf(potato, 10) * (price(ItemId("HOT_POTATO_BOOK")) ?: 0.0)
			val fpb = maxOf(potato - 10, 0) * (price(ItemId("FUMING_POTATO_BOOK")) ?: 0.0)
			if (hpb + fpb > 0) parts.add("Potato books" to hpb + fpb)
		}
		if (ea.getIntOr("art_of_war_count", 0) > 0) price(ItemId("THE_ART_OF_WAR"))?.let { parts.add("Art of War" to it) }
		if (parts.isEmpty()) return null
		return Breakdown(parts)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val id = event.stack.view().repoItemId ?: return
		if (TConfig.craftCost) {
			craftCost(id)?.let {
				event.lines.add(Component.literal("§eCraft cost: §6${ApexFormatters.withThousands(it * event.stack.count, 1)} coins"))
			}
		}
		if (TConfig.estimatedValue) {
			val est = estimate(event.stack) ?: return
			if (est.parts.size > 1) {
				event.lines.add(Component.literal("§eEstimated value: §6${ApexFormatters.compactNumber(est.total)} coins"))
				for ((name, v) in est.parts) event.lines.add(Component.literal("§8 - $name: §7${ApexFormatters.compactNumber(v)}"))
			}
		}
	}

	private val COINS = Regex("([0-9][0-9,]*) Coins")
	private val QTY_SUFFIX = Regex(" x[0-9]+$")
	private val QTY = Regex(" x([0-9]+)$")

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		if (!TConfig.containerValue || !SkyBlockSession.inSkyBlock) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (screen is net.minecraft.client.gui.screens.inventory.InventoryScreen) return
		val title = screen.title.bareText.trim()
		val isStorageLike = title == "Chest" || title == "Large Chest" || title.contains("Backpack") || title.startsWith("Ender Chest")

		val isReward = title.endsWith(" Chest") && !isStorageLike &&
			!dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.TConfig.rewardChestOverlay
		val isSack = dev.ashtonite365.apex.features.misc.GapFeatures2.TConfig.sackValue && dev.ashtonite365.apex.features.misc.GapFeatures2.isSackScreen()
		if (!isStorageLike && !isReward && !isSack) return
		val lines = containerLines(screen, title, isReward, isSack) ?: return
		drawContainerBox(event, screen, lines)
	}

	private var memoKey: Array<Any?> = emptyArray()
	private var memoAt = 0L
	private var memoLines: List<String>? = null
	const val CONTAINER_MEMO_MS = 1000L

	internal fun containerKey(title: String, isReward: Boolean, isSack: Boolean, stacks: List<ItemStack>): Array<Any?> {
		val k = arrayOfNulls<Any?>(3 + stacks.size * 2)
		k[0] = title; k[1] = isReward; k[2] = isSack
		for ((i, st) in stacks.withIndex()) { k[3 + i * 2] = st; k[4 + i * 2] = st.count }
		return k
	}

	private fun keyEquals(a: Array<Any?>, b: Array<Any?>): Boolean {
		if (a.size != b.size) return false
		for (i in a.indices) {
			val x = a[i]; val y = b[i]
			if (x is ItemStack || y is ItemStack) { if (x !== y) return false } else if (x != y) return false
		}
		return true
	}

	private fun containerLines(screen: AbstractContainerScreen<*>, title: String, isReward: Boolean, isSack: Boolean): List<String>? {
		val stacks = screen.menu.slots.filter { it.container !is Inventory }.map { it.item }
		val key = containerKey(title, isReward, isSack, stacks)
		val now = System.currentTimeMillis()
		if (now - memoAt < CONTAINER_MEMO_MS && keyEquals(key, memoKey)) return memoLines
		memoKey = key; memoAt = now
		memoLines = computeContainerLines(stacks, isReward, isSack)
		return memoLines
	}

	private fun computeContainerLines(stacks: List<ItemStack>, isReward: Boolean, isSack: Boolean): List<String>? {
		var total = 0.0
		var cost: Double? = null
		val top = mutableListOf<Pair<String, Double>>()
		for (stack in stacks) {
			if (stack.isEmpty) continue
			val lore = stack.view().loreAccordingToNbt.map { it.bareText.trim() }
			val costIdx = lore.indexOf("Cost")
			if (isReward && costIdx >= 0) {

				var c = 0.0
				for (line in lore.drop(costIdx + 1)) {
					if (line.isEmpty()) break
					COINS.find(line)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.let { c += it }
						?: dev.ashtonite365.apex.repo.ItemNameLookup.guessItemByName(line.replace(QTY_SUFFIX, ""), false)
							?.let(::price)?.let { c += it * (QTY.find(line)?.groupValues?.get(1)?.toInt() ?: 1) }
				}
				cost = c
				continue
			}
			val v = if (isSack) (dev.ashtonite365.apex.features.misc.GapFeatures2.sackValue(stack) ?: continue) else (estimate(stack)?.total ?: continue) * stack.count
			total += v
			top.add(stack.hoverName.bareText to v)
		}
		if (total <= 0) return null
		val lines = mutableListOf(if (isSack) "§lSack value" else "§lContainer value", "§6${ApexFormatters.compactNumber(total)} coins")
		cost?.let {
			val profit = total - it
			lines.add("Cost: §c${ApexFormatters.compactNumber(it)}")
			lines.add("Profit: " + (if (profit >= 0) "§a" else "§c") + ApexFormatters.compactNumber(profit))
		}
		top.sortedByDescending { it.second }.take(5).forEach { lines.add("§7${it.first.take(22)}: §6${ApexFormatters.compactNumber(it.second)}") }
		return lines
	}

	private fun drawContainerBox(event: ScreenRenderPostEvent, screen: AbstractContainerScreen<*>, lines: List<String>) {

		val w = lines.maxOf { Client.font.width(it) } + 8

		val anchor = SidePanel.anchorOf(screen)
		val left = anchor.x
		val right = anchor.x + anchor.width
		val y = anchor.y
		val x = if (right + 3 + w <= screen.width || left - 3 - w < 0) right + 3 else left - 3 - w
		SidePanel.drawBox(event.drawContext, x, y, x + w, y + lines.size * 10 + 6)
		lines.forEachIndexed { i, l -> SidePanel.text(event.drawContext, l, x + 4, y + 4 + i * 10) }
	}
}
