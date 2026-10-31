// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.slayer

import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.skills.SlayerOverlay
import dev.ashtonite365.apex.features.skills.SlayerOverlay.TConfig
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.bareText

object SlayerPlusMerges {
	@Config
	object ProfitData : DataHolder<ProfitTracker.Data>(serializer(), "slayer-profit", { ProfitTracker.Data() })

	val profit = ProfitTracker("Slayer Profit", { ProfitData.data }, onChange = { ProfitData.markDirty() })

	private val lootLine = Regex("^(?:RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|RNGESUS INCARNATE)?\\s*DROP!\\s*\\(?(?:(\\d+)x )?([^()]+?)\\)?(?: \\(\\+\\d+% ✯ Magic Find\\))?$")

	private object Run {
		var startedAt: TimeMark? = null
		var bossAt: TimeMark? = null
		var killedAt = TimeMark.farPast()
		var previousXp: Double? = null

		var pace = 0.0
		var soonShown = false

		fun endQuest() {
			startedAt = null
			bossAt = null
		}

		fun sample(xp: Double) {
			val before = previousXp
			previousXp = xp
			if (before == null || xp <= before) return
			val gained = xp - before
			pace = if (pace == 0.0) gained else 0.8 * pace + 0.2 * gained
		}
	}

	private val tracking get() = TConfig.itemProfitTracker && SlayerOverlay.questName != null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val text = event.bareText.trim()
		if (text == "SLAYER QUEST STARTED!") {
			Run.startedAt = TimeMark.now()
			Run.soonShown = false
			Run.previousXp = null
		} else if (text == "SLAYER QUEST COMPLETE!" || text == "NICE! SLAYER BOSS SLAIN!") {
			questFinished()
		} else if (text == "SLAYER QUEST FAILED!") {
			if (TConfig.questWarning) Alerts.show(Component.literal("§eNew Slayer Quest! §7(the old one has failed)"))
			Run.endQuest()
		}
		if (!tracking) return
		val hit = lootLine.matchEntire(text) ?: return
		val item = ItemNameLookup.guessItemByName(hit.groupValues[2].trim(), false) ?: return
		profit.addItem(item.skyblockId, hit.groupValues[1].toLongOrNull() ?: 1L)
	}

	private fun questFinished() {
		Run.killedAt = TimeMark.now()
		if (tracking) profit.addEvent()
		if (TConfig.timeMessages) {
			val who = SlayerOverlay.questName ?: "the boss"
			Run.bossAt?.let { say("§eIt took §b${ApexFormatters.timespanText(it.passedTime())}§e to kill $who") }
			Run.startedAt?.let { say("§eSlayer quest took §b${ApexFormatters.timespanText(it.passedTime())}§e to complete.") }
		}
		Run.endQuest()
	}

	private fun say(text: String) = Client.showChat(Component.literal(text))

	@Subscribe
	fun onChatHide(event: AllowChatEvent) {
		if (!TConfig.hideRngMeterChat) return
		if (PlusSlayerLogic.rngMeterChat.matches(event.bareText)) event.cancel()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		val line = SlayerOverlay.progress
		if (Run.bossAt == null && line == "Slay the boss!") Run.bossAt = TimeMark.now()
		val (xp, needed) = line?.let { PlusSlayerLogic.parseProgress(it) } ?: return
		Run.sample(xp)
		if (!TConfig.bossSpawnSoon || Run.soonShown) return
		if (xp / needed * 100 >= TConfig.bossSpawnSoonPercent) {
			Run.soonShown = true
			Alerts.show(Component.literal("§eSlayer boss soon!"))
		}
	}

	fun extraHudLines(lines: MutableList<Component>) {
		if (TConfig.remainingKills) killsLeftRow()?.let(lines::add)
		if (!TConfig.itemProfitTracker) return
		profit.mode = when {
			TConfig.itemProfitTotal -> ProfitTracker.Mode.TOTAL
			else -> ProfitTracker.Mode.SESSION
		}
		lines += profit.lines()
	}

	private fun killsLeftRow(): Component? {
		val (xp, needed) = SlayerOverlay.progress?.let { PlusSlayerLogic.parseProgress(it) } ?: return null
		val left = PlusSlayerLogic.remainingKills(xp, needed, Run.pace) ?: return null
		return Component.literal("§7Kills left: §e~$left")
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!TConfig.blockNotSpawnable) return
		val slot = event.slot
		if (slot.container is Inventory || Client.screenTitle != "Slayer") return
		val lore = slot.item.view().loreAccordingToNbt.map { it.bareText.trim() }
		if (!PlusSlayerLogic.isNotSpawnable(lore)) return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA0202020.toInt())
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val me = Client.player ?: return
		val world = Client.level ?: return
		val labelItems = TConfig.itemsOnGround && Run.killedAt.passedTime() < 15.seconds
		val boxMinis = TConfig.highlightMinibosses && SlayerOverlay.questName != null
		if (!(labelItems || boxMinis)) return
		WorldDraw.drawInWorld(event) {
			if (labelItems) {
				for (drop in world.getEntitiesOfClass(ItemEntity::class.java, me.boundingBox.inflate(12.0)) { true })
					text(drop.position().add(0.0, 0.8, 0.0), drop.item.hoverName)
			}
			if (boxMinis) {
				val stands = world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(25.0)) { it.hasCustomName() }
				for (stand in stands) {
					val label = stand.customName?.bareText ?: continue
					if (!SlayerOverlay.minibosses.any { label.contains(it) }) continue
					val body = world.getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.0, 3.0, 1.0)) {
						it !== me && it !is ArmorStand
					}.minByOrNull { it.distanceToSqr(stand) } ?: continue
					box(body.boundingBox, 0x50FFAA00)
				}
			}
		}
	}

	fun shouldHide(entity: Entity): Boolean {
		if (SlayerOverlay.questName == null || !TConfig.hideMobNames) return false
		val label = (entity as? ArmorStand)?.customName?.bareText ?: return false
		return PlusSlayerLogic.isIrrelevantMobTag(label)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		Run.previousXp = null
	}
}
