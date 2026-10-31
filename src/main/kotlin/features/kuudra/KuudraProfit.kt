
package dev.ashtonite365.apex.features.kuudra

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object KuudraProfit {
	val identifier: String
		get() = "kuudra-profit"

	@Config
	object TConfig : ManagedConfig(category = Category.KUUDRA, name = identifier) {
		val enabled by toggle("enabled") { true }
		val includeKey by toggle("include-key") { true }
		val runCounter by toggle("run-counter") { false }
	}

	private val coins = "([\\d,]+) Coins".toRegex()

	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		if (!TConfig.enabled) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val title = screen.title.bareText.trim()
		if (title != "Paid Chest" && title != "Free Chest") return
		val slots = screen.menu.slots
		val items = (9 until 18).mapNotNull { i ->
			val stack = slots.getOrNull(i)?.item ?: return@mapNotNull null
			if (stack.isEmpty || stack.hoverName.bareText.isBlank()) return@mapNotNull null
			runCatching { DungeonChestProfit.priceRewardStack(stack) }
				.onFailure { DungeonChestProfit.failSoft("Kuudra chest slot $i '${stack.hoverName.bareText}'", it) }
				.getOrElse { DungeonChestProfit.PricedItem(stack.hoverName, null) }
		}
		var cost = 0.0
		slots.getOrNull(31)?.item?.view()?.loreAccordingToNbt?.forEach { line ->
			val t = line.bareText.trim()
			coins.matchEntire(t)?.let { cost += it.groupValues[1].replace(",", "").toDouble() }
			if (TConfig.includeKey && t.contains("Kuudra Key"))
				ItemNameLookup.guessItemByName(t.replace(Regex(" x\\d+$"), ""), false)?.let { RepoConstants.price(it) }?.let { cost += it }
		}
		val value = items.sumOf { it.value ?: 0.0 }
		val profit = value - cost
		val rows = items.map { it.label to (it.value?.let { v -> "§6" + ApexFormatters.compactNumber(v) } ?: "§e?") } +
			listOf(Component.literal("§bCost") to "§c" + ApexFormatters.compactNumber(cost),
			       Component.literal("§bProfit") to (if (profit >= 0) "§a" else "§c") + ApexFormatters.compactNumber(profit)) +
			(if (TConfig.runCounter) with(dev.ashtonite365.apex.features.kit.kuudra.KuudraKitPorts) {
				listOf(Component.literal("§bRuns (session)") to "§f$runs", Component.literal("§bPaid / Free chests") to "§f$paidChests / $freeChests")
			} else emptyList())
		val rect = SidePanel.anchorOf(screen)
		val x = rect.x + rect.width + 4
		val y = rect.y
		val w = 200
		val h = 18 + rows.size * 10
		val ctx = event.drawContext
		SidePanel.drawBox(ctx, x, y, x + w, y + h)
		if (!SidePanel.palette.themed) ctx.outlineBox(x, y, w, h, 0xFF555555.toInt())
		SidePanel.text(ctx, "§eKuudra chest profit", x + 5, y + 4)
		rows.forEachIndexed { i, (label, right) ->
			val ry = y + 15 + i * 10
			SidePanel.text(ctx, label, x + 5, ry)
			SidePanel.text(ctx, right, x + w - 5 - Client.font.width(right), ry)
		}
	}
}
