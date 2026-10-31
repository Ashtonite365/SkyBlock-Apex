// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.network.chat.Component

object PlusTrophyFillet {
	@Config
	object TConfig : ManagedConfig("trophy-fillet", Category.FISHING) {
		val filletTooltip by toggle("fillet-tooltip") { false }
	}

	private val TIERS = listOf("BRONZE", "SILVER", "GOLD", "DIAMOND")

	fun filletValue(id: String, table: Map<String, List<Int>>): Int? {
		val tierIndex = TIERS.indexOf(id.substringAfterLast('_', ""))
		if (tierIndex == -1) return null
		val perTier = table[id.substringBeforeLast('_')] ?: return null
		return perTier.getOrNull(tierIndex)
	}

	private fun repoTable(): Map<String, List<Int>> {
		val root = RepoConstants.get("trophyfish") ?: return emptyMap()
		val table = LinkedHashMap<String, List<Int>>()
		for ((fish, amounts) in root.entrySet()) {
			val parsed = try { amounts.asJsonArray.map { it.asInt } } catch (e: Throwable) { continue }
			table[fish] = parsed
		}
		return table
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.filletTooltip || !SkyBlockSession.inSkyBlock || Client.screenTitle?.contains("Sack") == true) return
		val id = event.stack.view().repoItemId?.skyblockId ?: return
		val perFish = filletValue(id, repoTable()) ?: return
		val multiplier = if (SavedKeyBinding.isShiftDown()) event.stack.count else 1
		val total = perFish * multiplier
		val worth = RepoConstants.price(ItemId("MAGMA_FISH"))?.times(total)
		val suffix = worth?.let { " §7(§6${ApexFormatters.compactNumber(it)}§7)" }.orEmpty()
		event.lines += Component.literal("§7Fillet: §8${ApexFormatters.withThousands(total)} Magmafish$suffix")
	}
}
