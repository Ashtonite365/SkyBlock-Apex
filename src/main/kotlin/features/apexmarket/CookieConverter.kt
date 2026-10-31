// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object CookieConverter {
	@Config
	object TConfig : ManagedConfig("cookie-converter", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }

		val bitsMultiplierTenths by integer("bits-multiplier-tenths", 10, 30) { 10 }
		val bulkCookie by toggle("bulk-cookie") { false }
	}

	const val BASE_BITS_PER_COOKIE = 4800
	val COOKIE = ItemId("BOOSTER_COOKIE")
	private const val BUFF_DAYS_PER_COOKIE = 4

	fun bitsPerCookie(multiplierTenths: Int): Double = BASE_BITS_PER_COOKIE * multiplierTenths / 10.0

	fun coinsPerBit(coins: Double, bits: Double): Double? = if (bits <= 0.0) null else coins / bits

	private val COOKIE_AMOUNT = Regex("(?:Booster Cookie x|Amount: )(\\d+).*")
	private val BITS_COST = Regex("^([0-9,]+) Bits$")

	fun parseCookieAmount(lines: List<String>): Int {
		for (line in lines) {
			val amount = COOKIE_AMOUNT.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull() ?: continue
			return amount
		}
		return 1
	}

	fun parseBitsCost(lore: List<String>): Long? {
		for (line in lore) {
			val digits = BITS_COST.find(line.trim())?.groupValues?.get(1) ?: continue
			return digits.replace(",", "").toLongOrNull() ?: continue
		}
		return null
	}

	private fun short(v: Double) = ApexFormatters.compactNumber(v)

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val stack = event.stack.view()
		val id = stack.repoItemId ?: return
		val cookiePrice = HypixelStaticData.getPriceOfItem(COOKIE)
		val bitsEach = bitsPerCookie(TConfig.bitsMultiplierTenths)
		if (id == COOKIE && cookiePrice != null) {
			cookieLines(event, cookiePrice, bitsEach)
			return
		}
		val bits = parseBitsCost(stack.loreAccordingToNbt.map { it.bareText }) ?: return
		val unit = HypixelStaticData.getPriceOfItem(id) ?: return
		val total = unit * event.stack.count
		val perBit = coinsPerBit(total, bits.toDouble()) ?: return
		val cookies = if (cookiePrice != null) ", ${"%.2f".format(bits / bitsEach)} cookies" else ""
		event.lines.add(Component.literal("§eBits value: §6${short(total)} coins §7(§a${"%.1f".format(perBit)} coins/bit§7$cookies)"))
	}

	private fun cookieLines(event: ItemTooltipEvent, price: Double, bitsEach: Double) {
		event.lines.add(Component.literal("§eCookie: §6${short(price)} coins §7= §b${short(bitsEach)} bits §7(${"%.1f".format(price / bitsEach)} coins/bit)"))
		if (!TConfig.bulkCookie) return
		val count = maxOf(parseCookieAmount(event.lines.map { it.bareText }), event.stack.count)
		if (count <= 1) return
		val bits = ApexFormatters.withThousands((bitsEach * count).toLong())
		event.lines.add(Component.literal("§8‣ §7$count cookies: gain §b$bits bits§7, buff for §b${BUFF_DAYS_PER_COOKIE * count} §7days"))
	}
}
