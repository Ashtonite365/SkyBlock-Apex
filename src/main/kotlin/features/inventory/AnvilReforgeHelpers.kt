/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): anvil combine helper and reforge filter. Inspired by similar helpers
 * in and (feature descriptions only); original implementation, no code copied.
 * The reforge filter never rerolls for you: it only blocks the reforge button once the wanted reforge is on.
 */
package dev.ashtonite365.apex.features.inventory

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.bareText

object AnvilReforgeHelpers {
	@Config
	object TConfig : ManagedConfig("anvil-reforge", Category.INVENTORY) {
		val anvilHighlight by toggle("anvil-highlight") { true }
		val reforgeFilter by toggle("reforge-filter") { false }
		val wantedReforges by string("wanted-reforges") { "Withered|Fabled|Ancient|Renowned|Spiritual" }
	}

	const val ANVIL_LEFT = 29
	const val REFORGE_ITEM = 13

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (!TConfig.anvilHighlight || Client.screenTitle != "Anvil") return
		if (event.slot.container !is Inventory) return
		val screen = Client.containerScreen ?: return
		val left = screen.menu.slots.getOrNull(ANVIL_LEFT)?.item ?: return
		if (left.isEmpty) return
		val stack = event.slot.item
		if (!combinable(left, stack)) return
		event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, 0x6000FF00)
	}

	fun combinable(left: net.minecraft.world.item.ItemStack, stack: net.minecraft.world.item.ItemStack): Boolean {
		if (left.isEmpty || stack.isEmpty) return false
		val leftId = left.view().repoItemId ?: return false
		val rightId = stack.view().repoItemId ?: return false
		val enchants = dev.ashtonite365.apex.repo.RepoManager.enchantData.allEnchants
		val pools = enchants?.enchantPools.orEmpty()
		val leftEnch = enchantLevels(left)
		val rightEnch = enchantLevels(stack)
		val isBook = stack.item == net.minecraft.world.item.Items.ENCHANTED_BOOK
		return when {

			left.item == net.minecraft.world.item.Items.ENCHANTED_BOOK -> isBook && rightId == leftId && rightEnch.size == 1 &&
				rightEnch.all { (e, l) -> EnchantPoolLogic.combinedLevel(l, l, dev.ashtonite365.apex.repo.RepoManager.enchantData.maxLevels[e]) > l }
			isBook -> {
				val type = dev.ashtonite365.apex.util.skyblock.ItemType.fromItemStack(left.view())
				val applicable = type?.let { EnchantPoolLogic.poolFor(it.toString(), enchants?.availableEnchants.orEmpty()) }
				applicable != null && EnchantPoolLogic.canCombine(leftEnch, rightEnch, applicable, pools)
			}
			else -> rightId == leftId
		}
	}

	private fun enchantLevels(stack: net.minecraft.world.item.ItemStack): Map<String, Int> {
		val ench = stack.view().customDataTag.getCompound("enchantments").orElse(null) ?: return emptyMap()
		return ench.keySet().associateWith { ench.getInt(it).orElse(0) }
	}

	fun matchedReforge(itemName: String, wanted: String): String? {
		val words = itemName.trim().split(' ')
		return wanted.split('|').map { it.trim() }.firstOrNull { w -> w.isNotEmpty() && words.any { it.equals(w, ignoreCase = true) } }
	}

	private var lastAnnounced: String? = null

	@Subscribe
	fun onSlotProtect(event: IsSlotProtectedEvent) {
		if (!TConfig.reforgeFilter) return
		val title = Client.screenTitle ?: return
		if (!title.startsWith("Reforge")) return
		val slot = event.slot ?: return
		if (slot.container is Inventory || !slot.item.hoverName.bareText.contains("Reforge Item")) return
		val item = Client.containerScreen?.menu?.slots?.getOrNull(REFORGE_ITEM)?.item ?: return
		val name = item.hoverName.bareText
		val match = matchedReforge(name, TConfig.wantedReforges) ?: return
		event.protectSilent()
		if (lastAnnounced != name) {
			lastAnnounced = name
			Alerts.show(Component.literal("§a§l$match reforge applied"))
			Client.showChat(Component.literal("§e[SkyBlock Apex] $name has a wanted reforge; reforge blocked. Take the item out or turn off the filter to continue."))
		}
	}
}
