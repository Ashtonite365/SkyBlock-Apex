// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.minions

import io.github.moulberry.repo.data.NEUCraftingRecipe
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object MinionHelper {
	val identifier: String
		get() = "minion-helper"

	@Serializable
	data class Data(
		val crafted: MutableSet<String> = mutableSetOf(),
		var neededForNextSlot: Int = -1,
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), identifier, ::Data)

	@Config
	object TConfig : ManagedConfig(category = Category.INVENTORY, name = identifier) {
		val enabled by toggle("enabled") { true }
		val lines by integer("lines", 5, 30) { 15 }
		val infernoFuelBlocker by toggle("inferno-fuel-blocker") { false }
		val upgradeHelper by toggle("upgrade-helper") { true }
	}

	data class Upgrade(val id: ItemId, val name: String, val cost: Double?)

	class Catalogue(
		val tiers: Map<String, Int>,
		private val displayName: (String) -> String?,
		private val ingredients: (String) -> List<Pair<String, Double>>?,
		private val price: (String) -> Double?,
	) {

		fun baseFor(name: String): String? = tiers.keys.firstOrNull { tierOneName(it) == name }

		private fun tierOneName(base: String): String? {
			val shown = displayName("${base}_1")?.stripSectionCodes() ?: return null

			val end = LINE_ENDS.firstOrNull { shown.endsWith(it) } ?: ""
			val body = shown.dropLast(end.length)
			val cut = body.lastIndexOf(' ')
			val numeral = if (cut >= 0) body.substring(cut + 1) else ""
			val bare = if (numeral.isNotEmpty() && numeral.all { it in "IVXL" }) body.substring(0, cut) + end else shown
			return bare.trim()
		}

		fun costOf(tierId: String, previousTier: String?): Double? {
			val parts = ingredients(tierId) ?: return null
			return parts.filter { (item, _) -> item != "CORE_SENTINEL_EMPTY" && item != previousTier }
				.fold(0.0 as Double?) { sum, (item, amount) -> sum?.let { s -> price(item)?.let { s + it * amount } } }
		}

		fun nextUpgrades(crafted: Set<String>): List<Upgrade> {
			val found = mutableListOf<Upgrade>()
			for ((base, top) in tiers) {
				val tier = (1..top).firstOrNull { "${base}_$it" !in crafted } ?: continue
				val previous = if (tier == 1) null else "${base}_${tier - 1}"
				if (previous != null && previous !in crafted) continue
				val id = "${base}_$tier"
				found += Upgrade(ItemId(id), displayName(id)?.stripSectionCodes() ?: id, costOf(id, previous))
			}
			return found.sortedBy { it.cost ?: Double.MAX_VALUE }
		}
	}

	private val LINE_ENDS = listOf("\r\n", "\n", "\r", "\u0085", "\u2028", "\u2029")

	class MenuItem(val name: String, val lore: List<Pair<String, String>>)

	fun readMenu(items: List<MenuItem>, data: Data, baseFor: (String) -> String?): Boolean {
		var changed = false
		for (item in items) {
			if (item.name.endsWith(" Minion")) {
				val base = baseFor(item.name) ?: continue
				val tierLines = item.lore.filter { "Tier" in it.first }
				tierLines.forEachIndexed { i, (_, legacy) -> if ("§a" in legacy && data.crafted.add("${base}_${i + 1}")) changed = true }
				continue
			}
			for ((plain, _) in item.lore) {
				val needed = moreNeeded(plain.trim()) ?: continue
				if (needed != data.neededForNextSlot) { data.neededForNextSlot = needed; changed = true }
			}
		}
		return changed
	}

	private const val NEED_HEAD = "Craft "
	private const val NEED_MID = " more unique minions to unlock"

	fun moreNeeded(line: String): Int? {
		if (!line.startsWith(NEED_HEAD)) return null
		val digitsEnd = line.indexOf(' ', NEED_HEAD.length).takeIf { it > NEED_HEAD.length } ?: return null
		val digits = line.substring(NEED_HEAD.length, digitsEnd)
		if (!digits.all { it in '0'..'9' }) return null
		if (!line.startsWith(NEED_MID, digitsEnd)) return null
		if (line.substring(digitsEnd + NEED_MID.length).any { it in "\n\r\u0085\u2028\u2029" }) return null
		return digits.toInt()
	}

	fun minionTiers(): Map<String, Int> =
		RepoConstants.get("misc")?.getAsJsonObject("minions")?.entrySet()?.associate { it.key to it.value.asInt } ?: emptyMap()

	private fun catalogue() = Catalogue(
		minionTiers(),
		{ RepoManager.getCoreItem(ItemId(it))?.displayName },
		{ id -> RepoManager.getCoreItem(ItemId(id))?.recipes?.filterIsInstance<NEUCraftingRecipe>()?.firstOrNull()?.allInputs?.map { it.itemId to it.amount.toDouble() } },
		{ RepoConstants.price(ItemId(it)) },
	)

	fun baseIdForName(name: String): String? = catalogue().baseFor(name)

	fun nextUpgrades(crafted: Set<String>): List<Upgrade> = catalogue().nextUpgrades(crafted)

	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		if (!TConfig.enabled) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!screen.title.bareText.startsWith("Crafted Minions")) return
		val data = DConfig.data
		val catalogue = catalogue()
		val items = screen.menu.slots.filter { it.container !== Client.player?.inventory }.map { slot ->
			val lore = slot.item.view().loreAccordingToNbt
			MenuItem(slot.item.hoverName.bareText.trim(), lore.map { it.bareText to it.getLegacyFormatString() })
		}
		if (readMenu(items, data, catalogue::baseFor)) DConfig.markDirty()
		drawPanel(event, screen, data, catalogue.nextUpgrades(data.crafted).take(TConfig.lines))
	}

	private fun drawPanel(event: ScreenRenderPostEvent, screen: AbstractContainerScreen<*>, data: Data, rows: List<Upgrade>) {
		val area = SidePanel.anchorOf(screen)
		val width = 210
		val left = area.x + area.width + 4
		val top = area.y
		val ctx = event.drawContext
		SidePanel.drawBox(ctx, left, top, left + width, top + 28 + rows.size * 10)
		if (!SidePanel.palette.themed) ctx.outlineBox(left, top, width, 28 + rows.size * 10, 0xFF555555.toInt())
		val progress = data.neededForNextSlot.takeIf { it >= 0 }?.let { "§b$it§e more for next slot" } ?: "§7visit all pages"
		SidePanel.text(ctx, "§eUnique crafted: §6${data.crafted.size}§e, $progress", left + 5, top + 4)
		SidePanel.text(ctx, "§eCheapest new minions:", left + 5, top + 15)
		for ((i, row) in rows.withIndex()) {
			val rowY = top + 26 + i * 10
			val cost = row.cost?.let { "§6" + ApexFormatters.compactNumber(it) } ?: "§c?"
			SidePanel.text(ctx, "§f" + Client.font.plainSubstrByWidth(row.name, width - 50), left + 5, rowY)
			SidePanel.text(ctx, cost, left + width - 5 - Client.font.width(cost), rowY)
		}
	}
}
