// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.EntityRenderTintEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.diana.DianaWaypoints
import dev.ashtonite365.apex.features.events.helpers.EventHelpers
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.TintOverlay
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.tr

object DianaPlusPorts {
	@Config
	object DConfig : ProfileSpecificDataHolder<ProfitTracker.Data>(serializer(), "diana-profit-tracker", { ProfitTracker.Data() })

	val tracker = ProfitTracker(
		"Diana Profit Tracker",
		totalProvider = { DConfig.data },
		onChange = { DConfig.markDirty() },
	)

	private val inHub get() = SkyBlockSession.skyblockIsland == SkyBlockIsland.HUB

	sealed interface Gain {
		data class Coins(val amount: Double) : Gain
		data class Item(val id: String, val name: String, val amount: Long) : Gain
		data object Burrow : Gain
	}

	private val nonIdChars = Regex("[^A-Z0-9]+")

	fun idOf(name: String): String = nonIdChars.replace(name.uppercase().replace("'", ""), "_").trim('_')

	private val coinsDug = Regex("^Wow! You dug out ([\\d,]+) coins!$")
	private val itemDug = Regex("^RARE DROP! You dug out an? (.+?)!$")
	private val chainStep = Regex("^You (dug out a Griffin Burrow|finished the Griffin burrow chain)!.*")
	private val mobLoot = Regex("^(?:RARE|VERY RARE|CRAZY RARE|INSANE) DROP! (?:\\((\\d+)x )?(.+?)\\)?(?: \\(\\+[\\d,]+% ✯ Magic Find\\))?!?$")

	private fun itemGain(name: String, count: Long) = Gain.Item(idOf(name), name, count)

	fun parse(line: String): Gain? {
		coinsDug.matchEntire(line)?.let { return Gain.Coins(it.groupValues[1].replace(",", "").toDouble()) }
		itemDug.matchEntire(line)?.let { return itemGain(it.groupValues[1], 1) }
		if (chainStep.matches(line)) return Gain.Burrow
		val loot = mobLoot.matchEntire(line) ?: return null
		return itemGain(loot.groupValues[2], loot.groupValues[1].toLongOrNull() ?: 1)
	}

	private val displayNames = mutableMapOf<String, String>()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!EventHelpers.TConfig.dianaProfit || !inHub) return
		val gain = parse(event.bareText.trim()) ?: return
		when (gain) {
			is Gain.Coins -> tracker.addCoins(gain.amount)
			is Gain.Item -> {
				displayNames[gain.id] = gain.name
				tracker.addItem(gain.id, gain.amount)
			}
			Gain.Burrow -> tracker.addEvent()
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val helpers = EventHelpers.TConfig
		if (!helpers.dianaProfit || !inHub) return
		tracker.mode = when {
			helpers.dianaProfitTotal -> ProfitTracker.Mode.TOTAL
			else -> ProfitTracker.Mode.SESSION
		}
		val view = tracker.current
		val nothing = view.events == 0L && view.coins == 0.0 && view.items.isEmpty()
		if (nothing) return
		val rows = tracker.lines({ id -> displayNames[id] ?: id }).toMutableList()
		rows.add(1, Component.literal("§7Burrows: §f${view.events}"))
		event.drawPlusLines(helpers.dianaProfitHud, rows)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("resetdianaprofit", ManagedConfig.Category.EVENTS) {
			onRun {
				tracker.reset()
				source.sendFeedback(tr("apex.diana-profit.reset", "Reset the Diana profit tracker (current view)."))
			}
		}
	}

	val rareMobs = listOf("Sphinx", "Minos Inquisitor", "Manticore", "King Minos")

	fun rareMobName(tag: String): String? {
		for (mob in rareMobs) if (mob in tag) return mob
		return null
	}

	private val tinted = mutableSetOf<Int>()
	private val alerted = mutableSetOf<Int>()
	private val pink by lazy { TintOverlay().tinted(0xFFFF55FF.toInt()) }

	private fun bodyOf(stand: ArmorStand): LivingEntity? {
		val level = Client.level ?: return null
		val around = stand.boundingBox.inflate(1.0, 3.0, 1.0)
		return level.getEntitiesOfClass(LivingEntity::class.java, around) { it !is ArmorStand }
			.minByOrNull { it.distanceToSqr(stand) }
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		val wanted = DianaWaypoints.TConfig.highlightRareMobs || DianaWaypoints.TConfig.rareMobAlert
		if (!wanted || !inHub) {
			tinted.clear()
			return
		}
		val level = Client.level ?: return
		val found = mutableSetOf<Int>()
		for (stand in level.entitiesForRendering().filterIsInstance<ArmorStand>()) {
			if (!stand.hasCustomName()) continue
			val tag = stand.customName?.string ?: continue
			val mobName = rareMobName(tag) ?: continue
			val body = bodyOf(stand) ?: continue
			found += body.id
			if (DianaWaypoints.TConfig.rareMobAlert && alerted.add(body.id)) Alerts.show(Component.literal("§d$mobName!"))
		}
		tinted.clear()
		tinted += found
	}

	@Subscribe
	fun onTint(event: EntityRenderTintEvent) {
		if (DianaWaypoints.TConfig.highlightRareMobs && event.entity.id in tinted) event.renderState.overlayTexture_apex = pink
	}
}
