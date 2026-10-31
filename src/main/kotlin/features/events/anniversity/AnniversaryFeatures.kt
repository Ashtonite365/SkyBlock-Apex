package dev.ashtonite365.apex.features.events.anniversity

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.xml.Bind
import org.joml.Vector2i
import java.util.regex.Pattern
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.animal.pig.Pig
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityInteractionEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.gui.hud.MoulConfigHud
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SHORT_NUMBER_FORMAT
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.collections.batchUpdate
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.labelName
import dev.ashtonite365.apex.util.useMatch

object AnniversaryFeatures {

	val identifier: String get() = "anniversary"

	@Config
	object TConfig : ManagedConfig(category = Category.EVENTS, name = identifier) {
		val showPigTimers: Boolean by toggle("shiny-pigs") { true }
		val pigHudPosition by position("pig-hud", 200, 300) { Vector2i(100, 200) }
	}

	val pigTimeLimit: Duration = 90.seconds

	data class LedPig(val clickedAt: TimeMark, val startLocation: BlockPos, val pigEntity: Pig) {

		@Bind("timeLeft")
		fun remainingFraction(): Double = 1.0 - clickedAt.passedTime() / pigTimeLimit
	}

	val ledPigs: ObservableList<LedPig> = ObservableList(mutableListOf())

	var lastTouchedPig: Pig? = null

	val rewards: MutableList<Reward> = mutableListOf()

	private const val PICKED_UP_LINE = "Oink! Bring the pig back to the Shiny Orb!"
	private const val ORB_CHARGED_LINE = "SHINY! The orb is charged! Click on it for loot!"
	val orbLootLine: Pattern = "SHINY! You extracted Shiny Shard and (?<reward>.*) from the piglet's orb!".toPattern()

	val xpPart: Pattern = "\\+(?<exp>$SHORT_NUMBER_FORMAT) (?<kind>[^ ]+) XP".toPattern()
	val coinPart: Pattern = "(?i)\\+(?<amount>$SHORT_NUMBER_FORMAT) Coins".toPattern()
	val itemPart: Pattern = "(?:(?<amount>[0-9]+)x )?(?<name>.*)".toPattern()

	sealed interface Reward {
		val sumKey: Any?

		fun plusSameKind(other: Reward): Reward

		fun combine(other: Reward): Reward? {
			val key = sumKey ?: return null
			if (other.javaClass != javaClass || other.sumKey != key) return null
			return plusSameKind(other)
		}

		data class EXP(val amount: Double, val skill: String) : Reward {
			override val sumKey: Any get() = skill
			override fun plusSameKind(other: Reward) = copy(amount = amount + (other as EXP).amount)
		}

		data class Coins(val amount: Double) : Reward {
			override val sumKey: Any get() = "coins"
			override fun plusSameKind(other: Reward) = Coins(amount + (other as Coins).amount)
		}

		data class Items(val amount: Int, val item: ItemId) : Reward {
			override val sumKey: Any get() = item
			override fun plusSameKind(other: Reward) = copy(amount = amount + (other as Items).amount)
		}

		data class Unknown(val text: String) : Reward {
			override val sumKey: Any? get() = null
			override fun plusSameKind(other: Reward): Reward = this
		}
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		ledPigs.removeIf { it.clickedAt.passedTime() > pigTimeLimit }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.showPigTimers) return
		val text = event.bareText
		if (text == PICKED_UP_LINE) pigPickedUp()
		else if (text == ORB_CHARGED_LINE) orbCharged()
		orbLootLine.useMatch(text) {
			addReward(parseReward(group("reward")))
			showRewardTotals()
		}
	}

	private fun pigPickedUp() {
		val pig = lastTouchedPig ?: return
		lastTouchedPig = null
		ledPigs.add(LedPig(TimeMark.now(), pig.blockPosition(), pig))
	}

	private fun orbCharged() {
		val player = Client.player?.position() ?: return
		ledPigs.minByOrNull { it.startLocation.distToCenterSqr(player) }?.let(ledPigs::remove)
	}

	private fun showRewardTotals() {
		val rows = PigHud.rewards
		rows.batchUpdate {
			rows.clear()
			for (total in rewards) rows.add(PigHud.RewardRow(total))
		}
	}

	fun addReward(reward: Reward) {
		val slot = rewards.indexOfFirst { reward.combine(it) != null }
		if (slot < 0) rewards.add(reward) else rewards[slot] = reward.combine(rewards[slot])!!
	}

	fun parseReward(text: String): Reward {
		fun Pattern.whole() = matcher(text).takeIf { it.matches() }
		xpPart.whole()?.let { return Reward.EXP(parseShortNumber(it.group("exp")), it.group("kind")) }
		coinPart.whole()?.let { return Reward.Coins(parseShortNumber(it.group("amount"))) }
		val item = itemPart.whole()?.let { m ->
			ItemNameLookup.guessItemByName(m.group("name"), false)?.let { id -> Reward.Items(m.group("amount")?.toIntOrNull() ?: 1, id) }
		}
		return item ?: Reward.Unknown(text)
	}

	@Subscribe
	fun onWorldClear(event: WorldReadyEvent) {
		lastTouchedPig = null
		ledPigs.clear()
	}

	@Subscribe
	fun onEntityClick(event: EntityInteractionEvent) {
		val pig = event.entity as? Pig ?: return
		lastTouchedPig = pig
	}

	@Subscribe
	fun init(event: WorldReadyEvent) {
		PigHud.forceInit()
	}

	object PigHud : MoulConfigHud("anniversary_pig", TConfig.pigHudPosition) {
		override fun shouldRender(): Boolean = TConfig.showPigTimers && ledPigs.isNotEmpty()

		@Bind("pigs")
		fun pigs(): ObservableList<LedPig> = ledPigs

		@get:Bind(value = "rewards")
		val rewards: ObservableList<RewardRow> = ObservableList(mutableListOf())

		class RewardRow(val total: Reward) {
			private val shownStack: SBItemStack =
				if (total is Reward.Items) SBItemStack(total.item, total.amount) else SBItemStack(ItemId.NULL)

			@Bind("count")
			fun amountText(): String = when (total) {
				is Reward.EXP -> total.amount.toString()
				is Reward.Coins -> total.amount.toString()
				is Reward.Items -> total.amount.toString()
				is Reward.Unknown -> "0"
			}

			@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
			@Bind("name")
			fun label(): Component = when (total) {
				is Reward.EXP -> Component.literal(total.skill)
				is Reward.Coins -> Component.literal("Coins")
				is Reward.Items -> shownStack.asImmutableItemStack().labelName
				is Reward.Unknown -> Component.literal(total.text)
			}

			@Bind("isKnown")
			fun isKnown(): Boolean = total !is Reward.Unknown
		}
	}
}
