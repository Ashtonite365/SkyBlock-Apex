// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes.itemlist

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.PetPlaceholders
import dev.ashtonite365.apex.repo.PetVariants
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.darkGrey
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

object SkyblockEntries {

	const val SOURCE_MOD = "skyblock"

	fun all(): List<SBItemStack> =
		if (!RepoManager.shouldLoadREI()) listOf()
		else runCatching {
			RepoManager.coreRepo.items?.items?.keys?.let { PetVariants.current.merged(it) }?.map { SBItemStack(ItemId(it)) }
		}.getOrNull() ?: listOf()

	fun identity(stack: SBItemStack): String = PetVariants.current.uidOf(stack.catalogItemId.skyblockId)

	fun searchName(stack: SBItemStack): String {
		val name = stack.repoItem?.displayName ?: return stack.catalogItemId.skyblockId
		val idx = PetVariants.current
		val id = stack.catalogItemId.skyblockId
		if (!idx.isPet(id)) return name
		return PetPlaceholders.apply(name, mapOf("LVL" to "")).replace("[Lvl ]", "").trim() + " " +
			idx.rarityNames(id).joinToString(" ") { it.lowercase() }
	}

	fun normalize(stack: SBItemStack): SBItemStack = stack.copy(
		stackSize = 1, petBlob = RepoManager.getPotentialStubPetData(stack.catalogItemId),
		stars = 0, extraLore = listOf(), reforge = null,
	)

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	fun cheatStack(stack: SBItemStack): ItemStack = stack.asImmutableItemStack().currentCopy()

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	fun drawnItem(stack: SBItemStack): ItemStack {
		val repoItem = stack.repoItem
		return if (!RepoManager.TConfig.perfectRenders.rendersPerfectVisuals() && !stack.isWarm() && repoItem != null) {
			ItemCache.recacheSoon(repoItem)
			ItemStack(Items.PAINTING)
		} else stack.asImmutableItemStack().current()
	}

	fun countLabel(count: Int, maxStack: Int): String? =
		if (count > 1000) ApexFormatters.compactNumber(count.toDouble())
		else dev.ashtonite365.apex.util.mc.DisplayStackCount.label(count, maxStack)

	fun draw(g: GuiGraphicsExtractor, stack: SBItemStack, x: Int, y: Int, withCount: Boolean = true) {
		val item = drawnItem(stack)
		g.item(item, x, y)
		if (withCount) g.itemDecorations(Client.font, item, x, y, countLabel(stack.getStackSize(), item.maxStackSize))
	}

	fun fromVanilla(stack: ItemStack): ItemId? = runCatching {
		val view = stack.view()
		dev.ashtonite365.apex.repo.SkyblockStackLookup.resolveLive(view.repoItemId?.skyblockId, view.displayNameAccordingToNbt.string)
	}.getOrNull()

	var canUseVanillaTooltipEvents = true

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	fun tooltip(stack: SBItemStack, ctx: Item.TooltipContext = Item.TooltipContext.EMPTY, flag: TooltipFlag = TooltipFlag.NORMAL): List<Component> {

		val quick = !stack.isWarm() && !RepoManager.TConfig.perfectRenders.rendersPerfectText()
		val repoItem = if (quick) stack.repoItem else null
		if (repoItem != null && !stack.hasModifiers && repoItem.lore.none { it.contains('{') })
			return listOf(Component.literal(repoItem.displayName)) + repoItem.lore.map { Component.literal(it) }
		val item = stack.asImmutableItemStack().current()
		val nbt = item.view()
		val lore = ArrayList<Component>().apply { add(nbt.displayNameAccordingToNbt); addAll(nbt.loreAccordingToNbt) }
		if (canUseVanillaTooltipEvents) runCatching { ItemTooltipCallback.EVENT.invoker().getTooltip(item, ctx, flag, lore) }
			.onFailure { crash ->
				if (crash !is Exception) throw crash
				canUseVanillaTooltipEvents = false
				Faults.reportFault("Tooltip listeners failed on an item list entry; no longer calling them", crash)
			}
		petLines(stack).forEach { lore.add(Component.literal(it)) }
		val count = stack.getStackSize()
		if (lore.isNotEmpty() && count > 1000) lore.add(1, Component.literal("${count}x").darkGrey())
		return lore
	}

	fun petLines(stack: SBItemStack): List<String> {
		val id = stack.catalogItemId.skyblockId
		val idx = PetVariants.current
		val base = idx.baseOf(id) ?: return emptyList()
		val out = mutableListOf<String>()
		val rarities = idx.rarityNames(id)
		if (rarities.size > 1) out += "§8Rarities: " + rarities.joinToString(", ") { it.lowercase().replaceFirstChar(Char::uppercase) } + " (Pet Rarities page)"
		val info = runCatching { SafeRepoConstants.petNumbers()[base]?.get(PetVariants.rarityOf(id)) }.getOrNull()
		if (info == null && RepoManager.getPotentialStubPetData(stack.catalogItemId) != null) out += PetPlaceholders.NO_LEVEL_DATA_NOTE
		return out
	}
}
