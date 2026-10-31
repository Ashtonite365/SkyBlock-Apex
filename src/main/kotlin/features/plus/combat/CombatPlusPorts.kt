// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.combat

import org.joml.Vector2i
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText

object CombatPlusPorts {
	@Config
	object TConfig : ManagedConfig("combat-extras", Category.COMBAT) {
		val damageIndicator by toggle("damage-indicator") { false }
		val damageIndicatorHud by position("damage-indicator-hud", 160, 40) { Vector2i(200, 20) }
		val enderNodeTracker by toggle("ender-node-tracker") { false }
		val enderNodeTotal by toggle("ender-node-total") { false }
		val enderNodeHud by position("ender-node-hud", 160, 80) { Vector2i(200, 160) }
		val resetEnderNode by button("reset-ender-node") { enderNodes.reset() }
		val dragonProfit by toggle("dragon-profit") { false }
		val dragonProfitHud by position("dragon-profit-hud", 160, 80) { Vector2i(200, 250) }
		val resetDragonProfit by button("reset-dragon-profit") { dragonLoot.reset() }
		val armorStack by toggle("armor-stack") { false }
		val armorStackHud by position("armor-stack-hud", 40, 12) { Vector2i(200, 340) }
		val ferocity by toggle("ferocity") { false }
		val ferocityHud by position("ferocity-hud", 60, 12) { Vector2i(200, 355) }
		val summons by toggle("summons") { false }
		val summonsHideChat by toggle("summons-hide-chat") { false }
		val summonsHud by position("summons-hud", 120, 40) { Vector2i(200, 370) }
	}

	@Config
	object EnderNodeData : DataHolder<ProfitTracker.Data>(serializer(), "ender-node-profit", { ProfitTracker.Data() })
	@Config
	object DragonData : DataHolder<ProfitTracker.Data>(serializer(), "dragon-profit", { ProfitTracker.Data() })

	val enderNodes = ProfitTracker("Ender Node Tracker", { EnderNodeData.data }, onChange = { EnderNodeData.markDirty() })
	val dragonLoot = ProfitTracker("Dragon Profit", { DragonData.data }, onChange = { DragonData.markDirty() })

	private val dragonSpawn = Regex("^☬ The \\w+ Dragon has spawned!$")
	private val ferocityRow = Regex("^Ferocity: ⫽?([\\d,.]+)")

	private val atEnd get() = SkyBlockSession.skyblockIsland == SkyBlockIsland.END

	private object Seen {
		var bosses: List<PlusCombatLogic.BossTag> = emptyList()
		var armor: String? = null
		var ferocity: String? = null
		var summons: List<String> = emptyList()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!atEnd) return
		val text = event.bareText.trim()
		if (TConfig.enderNodeTracker) countEnderNode(text)
		if (TConfig.dragonProfit) countDragonLoot(text)
	}

	private fun countEnderNode(text: String) {
		val node = PlusCombatLogic.parseEnderNode(text)
		if (node != null) {
			val (amount, itemName) = node
			enderNodes.addEvent()
			enderNodes.addItem(ItemNameLookup.guessItemByName(itemName, false)?.skyblockId ?: itemName, amount)
		}
		if ("Endermite Nest" in text) enderNodes.addItem("Endermite Nest")
	}

	private fun countDragonLoot(text: String) {
		if (dragonSpawn.matches(text)) dragonLoot.addEvent()
		val dropped = PlusCombatLogic.dragonLoot(text) ?: return
		val item = ItemNameLookup.guessItemByName(dropped, false) ?: return
		dragonLoot.addItem(item.skyblockId)
	}

	@Subscribe
	fun onChatHide(event: AllowChatEvent) {
		if (!TConfig.summonsHideChat) return
		val text = event.bareText.trim()
		if (PlusCombatLogic.isSummonSpawn(text) || PlusCombatLogic.isSummonDespawn(text)) event.cancel()
	}

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!TConfig.armorStack) return
		Seen.armor = PlusCombatLogic.armorStack(event.bareText)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!SkyBlockSession.inSkyBlock || event.tickCount % 10 != 0) return
		val me = Client.player ?: return
		val world = Client.level ?: return
		if (TConfig.damageIndicator || TConfig.summons) {
			val labels = world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(30.0)) { it.hasCustomName() }
				.mapNotNull { it.customName?.bareText }
			Seen.bosses = if (!TConfig.damageIndicator) emptyList() else
				labels.mapNotNull { PlusCombatLogic.parseBossTag(it) }.distinctBy { it.name to it.health }.take(5)
			val owner = "${me.gameProfile.name}'s "
			Seen.summons = if (!TConfig.summons) emptyList() else labels.filter { owner in it && "❤" in it }
		}
		if (TConfig.ferocity) Seen.ferocity = TabListReader.currentSnapshot.body.asSequence()
			.map { it.bareText.trim() }
			.mapNotNull { ferocityRow.find(it)?.groupValues?.get(1) }
			.firstOrNull()
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val bosses = Seen.bosses
		if (TConfig.damageIndicator && bosses.isNotEmpty()) event.drawPlusLines(TConfig.damageIndicatorHud) {
			for (b in bosses) {
				val share = b.percent?.let { " §7(${"%.1f".format(it)}%)" }.orEmpty()
				add("§c${b.name} §a${ApexFormatters.compactNumber(b.health)}$share")
			}
		}
		if (atEnd && TConfig.enderNodeTracker) {
			enderNodes.mode = if (TConfig.enderNodeTotal) ProfitTracker.Mode.TOTAL else ProfitTracker.Mode.SESSION
			event.drawPlusLines(TConfig.enderNodeHud, enderNodes.lines())
		}
		val dragonSeen = dragonLoot.session.events + dragonLoot.session.items.size > 0
		if (atEnd && TConfig.dragonProfit && dragonSeen) event.drawPlusLines(TConfig.dragonProfitHud, dragonLoot.lines())
		val armor = Seen.armor
		if (TConfig.armorStack && armor != null) event.drawPlusLines(TConfig.armorStackHud) { add("§6§l$armor") }
		val fero = Seen.ferocity
		if (TConfig.ferocity && fero != null) event.drawPlusLines(TConfig.ferocityHud) { add("§c⫽ $fero") }
		val mine = Seen.summons
		if (TConfig.summons && mine.isNotEmpty()) event.drawPlusLines(TConfig.summonsHud) {
			add("§eSummoning mobs: ${mine.size}")
			for (s in mine) add("§7$s")
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		Seen.bosses = emptyList()
		Seen.armor = null
		Seen.summons = emptyList()
	}
}
