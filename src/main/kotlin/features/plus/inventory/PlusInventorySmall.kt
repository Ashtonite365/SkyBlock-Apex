// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.inventory

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matches
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

object PlusInventorySmall {
	@Config
	object TConfig : ManagedConfig("inventory-extras", Category.INVENTORY) {
		val ultimateStar by toggle("ultimate-star") { false }
		val evolvingTimeHeld by toggle("evolving-time-held") { false }
		val evolvingTimeLeft by toggle("evolving-time-left") { false }
		val museumHighlight by toggle("museum-highlight") { false }
		val auctionOutbid by toggle("auction-outbid") { false }
	}

	internal val outbidPattern by PlusPatterns.pattern("auction.outbid", "\\[Auction] .* outbid you by .*")
	internal val allDonatedPattern by PlusPatterns.pattern("museum.all-items-donated", "Items Donated: 100%")

	const val EVOLVE_SECONDS = 300 * 60 * 60

	private val SECONDS_KEYS = mapOf("NEW_BOTTLE_OF_JYRRE" to "bottle_of_jyrre_seconds", "DISCRITE" to "rift_discrite_seconds")

	internal fun secondsAttribute(id: String?): String = id?.let { SECONDS_KEYS[it] } ?: "seconds_held"

	internal fun timeLeft(secondsHeld: Int): Int = maxOf(EVOLVE_SECONDS - secondsHeld, 0)

	private const val DONATED_TINT = 0x6000FF00

	private fun isUltimateBook(stack: net.minecraft.world.item.ItemStack): Boolean {
		val enchants = stack.view().customDataTag.getCompound("enchantments").orElse(null)?.keySet()

		val ultimateLore = stack.view().loreAccordingToNbt.any { "§d§l" in it.getLegacyFormatString() }
		if (enchants == null || enchants.size != 1) return false
		return ultimateLore || enchants.first().startsWith("ultimate_")
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!SkyBlockSession.inSkyBlock) return
		val slot = event.slot
		val stack = slot.item
		if (TConfig.ultimateStar && stack.`is`(Items.ENCHANTED_BOOK) && isUltimateBook(stack)) {
			val g = event.context
			g.pose().pushMatrix()
			g.pose().translate(slot.x.toFloat(), slot.y.toFloat())
			g.text(Client.font, "§d✦", 0, 0, 0xFFFFFFFF.toInt(), true)
			g.pose().popMatrix()
		}
		if (!TConfig.museumHighlight) return
		if (Client.screen?.title?.bareText?.contains("Museum") != true) return
		if (stack.view().loreAccordingToNbt.none { allDonatedPattern.matches(it.bareText) }) return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, DONATED_TINT)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val wantHeld = TConfig.evolvingTimeHeld
		val wantLeft = TConfig.evolvingTimeLeft
		if (!(wantHeld || wantLeft) || !SkyBlockSession.inSkyBlock) return
		val item = event.stack.view()
		val held = item.customDataTag.getInt(secondsAttribute(item.repoItemId?.skyblockId)).orElse(null) ?: return
		val insertAt = minOf(event.lines.size, 10)

		if (wantLeft) event.lines.add(insertAt, Component.literal("§7Time Left: §b" + ApexFormatters.timespanText(timeLeft(held).seconds)))
		if (wantHeld) event.lines.add(insertAt, Component.literal("§7Time Held: §b" + ApexFormatters.timespanText(held.seconds)))
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.auctionOutbid || !SkyBlockSession.inSkyBlock) return
		if (!outbidPattern.matches(event.bareText)) return
		Alerts.show(Component.literal("§cYou have been outbid!"))
	}
}
