package dev.ashtonite365.apex.features.mining

import io.github.notenoughupdates.moulconfig.xml.Bind
import org.joml.Vector2i
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.gui.hud.MoulConfigHud
import dev.ashtonite365.apex.util.ApexFormatters.withThousands
import dev.ashtonite365.apex.util.BazaarPriceStrategy
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.StringUtil.parseIntWithComma
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.useMatch

object PristineProfitTracker {
	private const val SLUG = "pristine-profit"

	val identifier: String get() = SLUG

	const val SECONDS_PER_HOUR = 3600
	const val ROUGHS_PER_FLAWED = 80
	const val FLAWED_PER_FINE = 80
	const val ROUGHS_PER_FINE = ROUGHS_PER_FLAWED * FLAWED_PER_FINE

	enum class GemstoneKind {
		SAPPHIRE, RUBY, AMETHYST, AMBER, TOPAZ, JADE, JASPER, OPAL, PERIDOT, ONYX, AQUAMARINE, CITRINE;

		val label: String = name.lowercase().replaceFirstChar { it.uppercaseChar() }
		val flawedId: ItemId = ItemId("FLAWED_${name}_GEM")
		val fineId: ItemId = ItemId("FINE_${name}_GEM")

		companion object {

			fun byName(text: String): GemstoneKind? {
				val key = text.trim().uppercase().let { if (it == "ONXY") "ONYX" else it }
				return entries.firstOrNull { it.name == key }
			}
		}
	}

	@Serializable
	data class BestRates(
		@SerialName("maxMoneyPerSecond") var coinsPerSecond: Double = 1.0,
		@SerialName("maxCollectionPerSecond") var roughPerSecond: Double = 1.0,
	)

	@Config
	object BestStore : ProfileSpecificDataHolder<BestRates>(serializer(), SLUG, { BestRates() })

	@Config
	object TConfig : ManagedConfig(SLUG, Category.MINING) {

		val enabled: Boolean by toggle("enabled") { true }

		val timeout by duration("timeout", 0.seconds, 120.seconds) { 30.seconds }
		val gui by position("position", 100, 30) { Vector2i() }

		val useFineGemstones: Boolean by toggle("fine-gemstones") { false }
	}

	val sellingStrategy: BazaarPriceStrategy = BazaarPriceStrategy.SELL_ORDER

	val pristineRegex = GemstoneKind.entries.joinToString("|") { it.label }
		.let { "PRISTINE! You found . Flawed (?<kind>$it) Gemstone x(?<count>[0-9,]+)!".toPattern() }

	private val WINDOW = 3.minutes
	private const val MAX_SAMPLES = 4096

	val collectionHistogram: Histogram<Double> = Histogram(MAX_SAMPLES, WINDOW)

	val moneyHistogram: Histogram<Double> = Histogram(MAX_SAMPLES, WINDOW)

	object ProfitHud : MoulConfigHud("pristine_profit", TConfig.gui) {
		@get:Bind var moneyCurrent: Double = 0.0
		@get:Bind var moneyMax: Double = 1.0
		@get:Bind var moneyText: String = ""
		@get:Bind var collectionCurrent: Double = 0.0
		@get:Bind var collectionMax: Double = 1.0
		@get:Bind var collectionText: String = ""

		override fun shouldRender(): Boolean =
			TConfig.enabled && collectionHistogram.latestUpdate().passedTime() < TConfig.timeout
	}

	private fun perHourLine(key: String, perSecond: Double): String =
		Component.translatableEscape(key, withThousands(perSecond * SECONDS_PER_HOUR, 1)).formattedString()

	fun updateUi() {
		val roughs = collectionHistogram.averagePer({ it }, 1.seconds) ?: 0.0
		val coins = moneyHistogram.averagePer({ it }, 1.seconds) ?: 0.0
		val best = BestStore.data
		if (roughs > best.roughPerSecond || coins > best.coinsPerSecond) {
			best.roughPerSecond = maxOf(best.roughPerSecond, roughs)
			best.coinsPerSecond = maxOf(best.coinsPerSecond, coins)
			BestStore.markDirty()
		}
		ProfitHud.collectionCurrent = roughs
		ProfitHud.collectionMax = best.roughPerSecond
		ProfitHud.collectionText = perHourLine("apex.pristine-profit.collection", roughs)
		ProfitHud.moneyCurrent = coins
		ProfitHud.moneyMax = best.coinsPerSecond
		ProfitHud.moneyText = perHourLine("apex.pristine-profit.money", coins)
	}

	@Subscribe
	fun onMessage(event: ProcessChatEvent) {
		pristineRegex.useMatch(event.bareText) {
			val kind = GemstoneKind.byName(group("kind")) ?: return@useMatch
			val flawed = parseIntWithComma(group("count"))
			val coins = if (TConfig.useFineGemstones)
				sellingStrategy.getSellPrice(kind.fineId) * flawed / FLAWED_PER_FINE
			else sellingStrategy.getSellPrice(kind.flawedId) * flawed
			collectionHistogram.record(flawed.toDouble() * ROUGHS_PER_FLAWED)
			moneyHistogram.record(coins)
			updateUi()
		}
	}
}
