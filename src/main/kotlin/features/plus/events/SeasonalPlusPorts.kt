// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.render.WorldDraw

object SeasonalPlusPorts {
	@Config
	object TConfig : ManagedConfig("plus-seasonal", Category.EVENTS) {
		val spookyChestAlert by toggle("spooky-chest-alert") { false }
		val spookyChestCompact by toggle("spooky-chest-compact") { false }
		val jyrreTimer by toggle("jyrre-timer") { false }
		val jyrreShowInactive by toggle("jyrre-show-inactive") { false }
		val jyrreHud by position("jyrre-hud", 120, 12) { Vector2i(10, 200) }
		val reindrakeWarp by toggle("reindrake-warp") { false }
		val newYearCake by toggle("new-year-cake") { false }
		val treasureHighlight by riskyToggle("treasure-highlight", RiskLevel.GREY_AREA)
	}

	private const val PREFIX = "§e[SkyBlock Apex] "

	private fun clickHint(text: String, command: String): Component =
		Component.literal(PREFIX + text).withStyle { style ->
			style.withClickEvent(ClickEvent.RunCommand(command)).withHoverEvent(HoverEvent.ShowText(Component.literal(command)))
		}

	private val chestAppeared = Regex("^(SPOOKY|PARTY)! A (Trick or Treat Chest|Party Chest) has appeared!$")

	fun spookyChestTitle(line: String, compact: Boolean): String? {
		val groups = chestAppeared.matchEntire(line)?.groupValues ?: return null
		val kind = groups[1]
		val colour = when (kind) {
			"SPOOKY" -> '6'
			else -> 'c'
		}
		val label = when {
			compact -> "$kind CHEST"
			else -> groups[2].uppercase() + "!"
		}
		return "§l§$colour$label"
	}

	private val jyrreDrink = Regex("^You consumed a Refined Bottle of Jyrre and gained \\+300. Intelligence for 60m!$")
	fun isJyrreMessage(line: String) = jyrreDrink.matches(line)

	private val boostLength = 60.minutes
	private var drankAt: TimeMark? = null

	fun jyrreRemaining(elapsed: Duration): Duration {
		val left = boostLength - elapsed
		return if (left < Duration.ZERO) Duration.ZERO else left
	}

	private val reindrakeSummon = Regex("^WOAH! .+ summoned (a Reindrake|TWO Reindrakes) from the depths!$")
	fun isReindrakeSpawn(line: String) = reindrakeSummon.matches(line)

	private val newYearSidebar = Regex("^New Year Event! .*")
	fun isCakeTime(sidebar: List<String>) = sidebar.any { newYearSidebar.matches(it.trim()) }

	private object Cake {
		var claimed = false
		var remindedAt = TimeMark.farPast()
		val every = 30.seconds
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		if (!TConfig.newYearCake || !SkyBlockSession.inSkyBlock) return
		if (Client.screenTitle == "Baker") Cake.claimed = true
		if (!isCakeTime(ScoreboardUtil.simplifiedScoreboardLines)) {
			Cake.claimed = false
			return
		}
		if (Cake.claimed) return
		if (Cake.remindedAt.passedTime() < Cake.every) return
		Cake.remindedAt = TimeMark.now()
		Client.showChat(clickHint("Grab the free New Year Cake! §aClick to open the baker menu.", "/openbaker"))
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val line = event.bareText.trim()
		val title = if (TConfig.spookyChestAlert) spookyChestTitle(line, TConfig.spookyChestCompact) else null
		if (title != null) {
			Alerts.show(Component.literal(title))
			return
		}
		if (line == "You claimed a New Year Cake!") Cake.claimed = true
		if (TConfig.jyrreTimer && isJyrreMessage(line)) {
			drankAt = TimeMark.now()
			return
		}
		val onWorkshop = SkyBlockSession.skyblockIsland == SkyBlockIsland.JERRY_WORKSHOP
		if (TConfig.reindrakeWarp && onWorkshop && isReindrakeSpawn(line))
			Client.showChat(clickHint("A Reindrake was detected. §aClick to warp to the Winter Island spawn!", "/warp winter"))
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.jyrreTimer || !SkyBlockSession.inSkyBlock) return
		val left = drankAt?.passedTime()?.let(::jyrreRemaining) ?: Duration.ZERO
		val active = left != Duration.ZERO
		if (!active && !TConfig.jyrreShowInactive) return
		val status = if (active) "§b" + ApexFormatters.timespanText(left) else "§cInactive!"
		event.drawPlusLines(TConfig.jyrreHud) { add("§aJyrre Boost: $status") }
	}

	private val frozen = mutableListOf<BlockPos>()

	fun isTreasureStand(itemCount: Int, helmetIsHead: Boolean) = !helmetIsHead && itemCount == 1

	private fun holdsTreasure(stand: ArmorStand): Boolean {
		val helmet = stand.getItemBySlot(EquipmentSlot.HEAD)
		if (helmet.isEmpty) return false
		val worn = EquipmentSlot.entries.count { !stand.getItemBySlot(it).isEmpty }
		val mobHead = helmet.`is`(Items.PLAYER_HEAD) && helmet.hoverName.string.endsWith("Head")
		return isTreasureStand(worn, mobHead)
	}

	@Subscribe
	fun onTreasureTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		frozen.clear()
		if (!TConfig.treasureHighlight || SkyBlockSession.skyblockIsland != SkyBlockIsland.JERRY_WORKSHOP) return
		val me = Client.player ?: return
		val level = Client.level ?: return
		level.entitiesForRendering()
			.filterIsInstance<ArmorStand>()
			.filter { it.distanceTo(me) <= 50 && holdsTreasure(it) }
			.mapTo(frozen) { it.blockPosition().above(2) }
	}

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (frozen.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (pos in frozen) block(pos, 0x6000FFFF)
		}
	}
}
