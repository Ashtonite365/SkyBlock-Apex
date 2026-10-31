package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters.withThousands
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.bold
import dev.ashtonite365.apex.util.darkGrey
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.statedAmount
import dev.ashtonite365.apex.util.gold
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.yellow

object PriceData {
	@Config
	object TConfig : ManagedConfig("price-data", Category.AUCTION_BAZAAR) {
		val alwaysOn by toggle("enable-always") { true }
		val enableKeybinding by keyBindingWithDefaultUnbound("enable-keybind")
		val stackSizeKey by keyBinding("stack-size-keybind") { InputConstants.KEY_LSHIFT }
		val avgLowestBin by choice("avg-lowest-bin-days") { LowestBinAverage.THREEDAYAVGLOWESTBIN }
		val bzPriceType by choice("bz-price-type") { BazaarRows.ORDERPRICES }
	}

	enum class LowestBinAverage : StringRepresentable {
		OFF, ONEDAYAVGLOWESTBIN, THREEDAYAVGLOWESTBIN, SEVENDAYAVGLOWESTBIN;

		override fun getSerializedName(): String = name

		fun averages(): Map<ItemId, Double>? = when (this) {
			OFF -> null
			ONEDAYAVGLOWESTBIN -> HypixelStaticData.avg1dlowestBin
			THREEDAYAVGLOWESTBIN -> HypixelStaticData.avg3dlowestBin
			SEVENDAYAVGLOWESTBIN -> HypixelStaticData.avg7dlowestBin
		}
	}

	enum class BazaarRows : StringRepresentable {
		ORDERPRICES,
		INSTANTPRICES,
		BOTH;

		override fun getSerializedName(): String = name
	}

	data class BazaarQuote(val instantBuy: Double, val instantSell: Double)

	fun priceRow(label: Component, price: Double): Component {
		val unit = Component.literal(if (price == 1.0) " coin" else " coins")
		val value = Component.literal(withThousands(price, 1)).gold().bold().append(unit)
		return Component.literal("").yellow().bold()
			.append(label)
			.append(Component.literal(": "))
			.append(value)
	}

	private fun instantRows(q: BazaarQuote, factor: Double): List<Component> = listOf(
		priceRow(tr("apex.tooltip.bazaar.instant-buy", "Bazaar Instant Buy"), q.instantBuy * factor),
		priceRow(tr("apex.tooltip.bazaar.instant-sell", "Bazaar Instant Sell"), q.instantSell * factor),
	)

	private fun orderRows(q: BazaarQuote, factor: Double): List<Component> = listOf(
		priceRow(tr("apex.tooltip.bazaar.buy-order", "Bazaar Buy Order"), q.instantSell * factor),
		priceRow(tr("apex.tooltip.bazaar.sell-order", "Bazaar Sell Order"), q.instantBuy * factor),
	)

	fun tooltipLines(
		quote: BazaarQuote?, lowestBin: Double?, averageBin: Double?,
		stackSize: Long, wholeStack: Boolean, type: BazaarRows, stackKeyName: Component,
	): List<Component> {
		if (quote == null && lowestBin == null) return emptyList()
		val lines = mutableListOf<Component>(Component.literal(""))
		if (stackSize > 1) lines += stackNote(stackSize, wholeStack, stackKeyName)
		val factor = if (wholeStack) stackSize.toDouble() else 1.0
		if (quote != null) {
			if (type != BazaarRows.ORDERPRICES) lines += instantRows(quote, factor)
			if (type != BazaarRows.INSTANTPRICES) lines += orderRows(quote, factor)
		} else if (lowestBin != null) {
			lines += priceRow(tr("apex.tooltip.ah.lowestbin", "Lowest BIN"), lowestBin * factor)
			if (averageBin != null) lines += priceRow(tr("apex.tooltip.ah.avg-lowestbin", "AVG Lowest BIN"), averageBin * factor)
		}
		return lines
	}

	private fun stackNote(stackSize: Long, wholeStack: Boolean, stackKeyName: Component): Component =
		if (wholeStack) tr("apex.tooltip.multiply", "Showing prices for x${stackSize}").darkGrey()
		else tr("apex.tooltip.multiply.hint", "[${stackKeyName}] to show x${stackSize}").darkGrey()

	@Subscribe
	fun addPrices(event: ItemTooltipEvent) {
		if (!TConfig.alwaysOn && !TConfig.enableKeybinding.isPressed()) return
		val view = event.stack.view()
		val id = view.repoItemId ?: return
		val status = HypixelStaticData.bazaarData[id.bazaarProduct]?.quickStatus
		event.lines += tooltipLines(
			quote = status?.let { BazaarQuote(instantBuy = it.buyPrice, instantSell = it.sellPrice) },
			lowestBin = HypixelStaticData.lowestBin[id],
			averageBin = TConfig.avgLowestBin.averages()?.get(id),
			stackSize = view.statedAmount(),
			wholeStack = TConfig.stackSizeKey.isPressed(),
			type = TConfig.bzPriceType,
			stackKeyName = TConfig.stackSizeKey.format(),
		)
	}
}
