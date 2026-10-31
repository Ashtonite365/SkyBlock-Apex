// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.events.helpers

import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.joml.Vector2i
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object EventHelpers {
	@Config
	object TConfig : ManagedConfig("event-helpers", Category.EVENTS) {
		val dianaTracker by toggle("diana-tracker") { true }
		val dianaHud by position("diana-hud", 140, 90) { Vector2i(10, 120) }
		val chocolateBestUpgrade by toggle("chocolate-best-upgrade") { true }
		val chocolateUpgradeTooltip by toggle("chocolate-upgrade-tooltip") { false }
		val dianaProfit by toggle("diana-profit") { false }
		val dianaProfitTotal by toggle("diana-profit-total") { false }
		val dianaProfitHud by position("diana-profit-hud", 160, 120) { Vector2i(10, 260) }
	}

	@Serializable
	data class DianaData(
		var burrows: Int = 0,
		var coins: Long = 0,
		val mobs: MutableMap<String, Int> = mutableMapOf(),
		val drops: MutableMap<String, Int> = mutableMapOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<DianaData>(serializer(), "diana-tracker", ::DianaData)

	private val coinLine = Regex("Wow! You dug out ([0-9,]+) coins!")
	private val dropLine = Regex("RARE DROP! You dug out an? (.+?)!.*")
	private val mobLine = Regex("(?:Uh oh|Oi|Yikes|Woah|Oh|Danger|Good Grief)! You dug out an? (.+)!")
	private val burrowLine = Regex("You (?:dug out|finished) (?:a|the) Griffin [Bb]urrow!.*")

	fun applyDiana(data: DianaData, line: String): Boolean {
		val coins = coinLine.matchEntire(line)
		if (coins != null) {
			data.coins += coins.groupValues[1].filter { it != ',' }.toLong()
			return true
		}
		val drop = dropLine.matchEntire(line)
		if (drop != null) {
			bump(data.drops, drop.groupValues[1])
			return true
		}
		val mob = mobLine.matchEntire(line)
		if (mob != null) {
			bump(data.mobs, mob.groupValues[1])
			return true
		}
		if (!burrowLine.matches(line)) return false
		data.burrows += 1
		return true
	}

	private fun bump(counts: MutableMap<String, Int>, key: String) {
		counts[key] = (counts[key] ?: 0) + 1
	}

	val sessionTracker = SessionTracker(idleTimeout = 2.minutes, resetAfter = 15.minutes)
	private val session = DianaData()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.dianaTracker) return
		val totals = DConfig.data
		val line = event.bareText.trim()
		if (!applyDiana(totals, line)) return
		DConfig.markDirty()
		val burrowsBefore = session.burrows
		val coinsBefore = session.coins
		applyDiana(session, line)
		sessionTracker.add("Burrows", (session.burrows - burrowsBefore).toDouble())
		sessionTracker.add("Coins", (session.coins - coinsBefore).toDouble())
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.dianaTracker) return
		val d = DConfig.data
		val empty = d.burrows == 0 && d.coins == 0L && d.mobs.isEmpty() && d.drops.isEmpty()
		if (empty) return
		val rows = buildList {
			add("§6§lMythological Ritual")
			add("§7Burrows: §f${d.burrows}")
			add("§7Coins: §6${ApexFormatters.compactNumber(d.coins.toDouble())}")
			for ((mob, n) in d.mobs.entries.sortedByDescending { it.value }) add("§c$mob: §f$n")
			for ((drop, n) in d.drops.entries.sortedByDescending { it.value }) add("§d$drop: §f$n")
			sessionTracker.perHour("Burrows")?.let { add("§7Burrows/h: §f${ApexFormatters.withThousands(it.toLong())}") }
			sessionTracker.perHour("Coins")?.let { add("§7Coins/h: §6${ApexFormatters.compactNumber(it)}") }
		}
		event.drawHudLines(TConfig.dianaHud, rows.map(Component::literal))
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("resetdiana", ManagedConfig.Category.EVENTS) {
			onRun {
				DConfig.data.run {
					burrows = 0
					coins = 0
					mobs.clear()
					drops.clear()
				}
				DConfig.markDirty()
				sessionTracker.reset()
				source.sendFeedback(tr("apex.diana.reset", "Reset the mythological ritual tracker."))
			}
		}
	}

	private val EMPLOYEES = 28..34
	private val costLine = Regex("^([0-9,]+) Chocolate$")
	private val balanceName = Regex("^([0-9,]+) Chocolates?$")
	private val rateLore = Regex("([0-9,.]+) Chocolate per second")

	private fun String.number(): Double? = replace(",", "").toDoubleOrNull()

	private fun upgradeCost(stack: ItemStack): Double? {
		val lore = stack.view().loreAccordingToNbt.map { it.bareText.trim() }
		val at = lore.indexOf("Cost")
		if (at < 0) return null
		val priceText = lore.getOrNull(at + 1) ?: return null
		return costLine.find(priceText)?.groupValues?.get(1)?.number()
	}

	fun bestEmployee(screen: AbstractContainerScreen<*>): Int? {
		val slots = screen.menu.slots
		return EMPLOYEES.mapNotNull { slot ->
			val stack = slots.getOrNull(slot)?.item ?: return@mapNotNull null
			val cost = upgradeCost(stack) ?: return@mapNotNull null
			slot to cost / (slot - EMPLOYEES.first + 1)
		}.filter { it.second < Double.MAX_VALUE }.minByOrNull { it.second }?.first
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (!TConfig.chocolateBestUpgrade || Client.screenTitle != "Chocolate Factory") return
		val slot = event.slot
		if (slot.container is Inventory) return
		val screen = Client.containerScreen ?: return
		if (slot.index == bestEmployee(screen)) event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8000FF00.toInt())
	}

	fun employeeTooltip(index: Int, cost: Double, current: Double?, perSecond: Double?): List<String> {
		val gain = index + 1
		val wait = affordableIn(cost, current, perSecond)
		return buildList {
			add("§8§m-----------------")
			if (wait != null) add("§7Time until upgrade: §e$wait")
			add("§7Extra: §6$gain §7choc/s")
			add("§7Effective Cost: §6${ApexFormatters.withThousands((cost / gain).toLong())}")
		}
	}

	private fun affordableIn(cost: Double, balance: Double?, rate: Double?): String? {
		if (balance == null || rate == null || rate <= 0) return null
		val short = cost - balance
		if (short <= 0) return "now"
		return ApexFormatters.timespanText(Duration.parse("${(short / rate).toLong()}s"))
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.chocolateUpgradeTooltip || Client.screenTitle != "Chocolate Factory") return
		val screen = Client.containerScreen ?: return
		val slots = screen.menu.slots
		val slot = slots.firstOrNull { it.container !is Inventory && it.item === event.stack } ?: return
		if (slot.index !in EMPLOYEES) return
		val cost = upgradeCost(event.stack) ?: return
		val factoryInfo = slots.getOrNull(13)?.item
		val balance = factoryInfo?.hoverName?.bareText?.trim()?.let { balanceName.find(it) }?.groupValues?.get(1)?.number()
		val rate = factoryInfo?.view()?.loreAccordingToNbt
			?.firstNotNullOfOrNull { rateLore.find(it.bareText) }
			?.groupValues?.get(1)?.number()
		for (row in employeeTooltip(slot.index - EMPLOYEES.first, cost, balance, rate)) event.lines.add(Component.literal(row))
	}
}
