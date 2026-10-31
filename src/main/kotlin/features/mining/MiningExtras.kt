/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): mining extras. Inspired by,, and
 * PartlySaneSkies (worm warning, mineshaft helpers, ghost tracker, disabled HOTM perk and commission mob
 * highlights; feature descriptions only). Original implementation, no code copied.
 */
package dev.ashtonite365.apex.features.mining

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object MiningExtras {
	@Config
	object TConfig : ManagedConfig("mining-extras", Category.MINING) {
		val wormWarning by toggle("worm-warning") { true }
		val mineshaftTracker by toggle("mineshaft-tracker") { true }
		val mineshaftHud by position("mineshaft-hud", 130, 40) { Vector2i(5, 280) }
		val ghostTracker by toggle("ghost-tracker") { true }
		val ghostHud by position("ghost-hud", 130, 60) { Vector2i(5, 320) }
		val ghostTrackerExtended by toggle("ghost-tracker-extended") { false }
		val nucleusTracker by toggle("nucleus-tracker") { true }
		val nucleusHud by position("nucleus-hud", 130, 60) { Vector2i(5, 360) }
		val excavatorTracker by toggle("excavator-tracker") { true }
		val corpseLoot by toggle("corpse-loot") { false }
		val corpseLootHud by position("corpse-loot-hud", 130, 80) { Vector2i(5, 200) }
		val nucleusBarriers by toggle("nucleus-barriers") { false }
		val miningNotifications by toggle("mining-notifications") { false }
		val excavatorHud by position("excavator-hud", 130, 60) { Vector2i(5, 400) }
		val disabledPerks by toggle("disabled-hotm-perks") { true }
		val commissionMobs by riskyToggle("commission-mob-highlight", RiskLevel.RISKY)
		val commissionMobNames by string("commission-mob-names") { "Glacite Walker|Goblin|Treasure Hoarder|Star Sentry|Ice Walker|Automaton|Team Treasurite" }
	}

	private val mining = setOf(SkyBlockIsland.DWARVEN_MINES, SkyBlockIsland.CRYSTAL_HOLLOWS, SkyBlockIsland.MINESHAFT)
	private fun inMines() = SkyBlockSession.skyblockIsland in mining

	private val WORM = Regex("^You hear the sound of something (?:approaching|loud)\\.\\.\\.$")
	private val MINESHAFT = Regex("^WOW! You found a Glacite Mineshaft portal!.*")
	private val CORPSE = Regex("^\\s*(LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!\\s*$")
	private val GHOST_DROP = Regex("^RARE DROP! (Sorrow|Volta|Plasma|Ghostly Boots)(?: \\(.*\\))?!?.*")

	fun isWorm(line: String) = WORM.matches(line.trim())
	fun parseCorpse(line: String): String? = CORPSE.matchEntire(line)?.groupValues?.get(1)
	fun parseGhostDrop(line: String): String? = GHOST_DROP.matchEntire(line.trim())?.groupValues?.get(1)

	fun corpseKey(type: String): String? = when (type.uppercase()) {
		"UMBER" -> "Umber Key"; "TUNGSTEN" -> "Tungsten Key"; "VANGUARD" -> "Skeleton Key"; else -> null
	}

	val mineshafts = SessionTracker(idleTimeout = 15.minutes, resetAfter = 60.minutes)
	val ghosts = SessionTracker(idleTimeout = 2.minutes, resetAfter = 20.minutes)
	private var lastMineshaft: TimeMark? = null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText
		if (TConfig.wormWarning && inMines() && isWorm(line)) Alerts.show(Component.literal("§c§lWorm spawning!"), 2.seconds)
		if (MINESHAFT.matches(line.trim())) {
			lastMineshaft = TimeMark.now()
			if (TConfig.mineshaftTracker) mineshafts.add("Mineshafts")

		}
		if (TConfig.mineshaftTracker) parseCorpse(line)?.let { type ->
			mineshafts.add("${type.lowercase().replaceFirstChar(Char::uppercase)} corpses")
		}
		if (TConfig.ghostTracker && SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES) parseGhostDrop(line)?.let { ghosts.add(it) }
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		if (!TConfig.mineshaftTracker) return
		val mark = lastMineshaft ?: return
		event.add(Timers.TimerEntry("mineshaft", "Last Mineshaft", "§7${dev.ashtonite365.apex.util.ApexFormatters.timespanText(mark.passedTime())} ago", false, null))
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!inMines() && SkyBlockSession.skyblockIsland != SkyBlockIsland.MINESHAFT) return
		if (TConfig.mineshaftTracker && !mineshafts.isEmpty) {
			val lines = mutableListOf<Component>(Component.literal("§bMineshafts"))
			lines += mineshafts.lines()
			if (SkyBlockSession.skyblockIsland == SkyBlockIsland.MINESHAFT)
				lines += Component.literal("§7Keys: Umber → Umber, Tungsten → Tungsten, Vanguard → Skeleton")
			event.drawHudLines(TConfig.mineshaftHud, lines)
		}
		if (TConfig.ghostTracker && !TConfig.ghostTrackerExtended && !ghosts.isEmpty && SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES)
			event.drawHudLines(TConfig.ghostHud, listOf(Component.literal("§5Ghost drops")) + ghosts.lines())
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MINING, "resetmining", "Reset the mineshaft and ghost trackers") {
			onRun { mineshafts.reset(); ghosts.reset(); lastMineshaft = null }
		}
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (!TConfig.disabledPerks || Client.screenTitle?.startsWith("Heart of the Mountain") != true) return
		if (event.slot.container is Inventory) return
		val lore = event.slot.item.view().loreAccordingToNbt.map { it.bareText.trim() }
		if (lore.any { it == "DISABLED" || it.endsWith("to enable!") && it.startsWith("Click") })
			event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, 0x60FF0000)
	}

	fun matchesName(name: String, names: String) =
		names.split('|').map { it.trim() }.any { it.isNotEmpty() && name.contains(it, ignoreCase = true) }

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!TConfig.commissionMobs || !inMines()) return
		val level = Client.game.level ?: return
		val names = TConfig.commissionMobNames
		WorldDraw.drawInWorld(event) {
			for (entity in level.entitiesForRendering()) {
				val tag = entity.customName?.bareText ?: continue
				if (!matchesName(tag, names)) continue
				val box = if (entity is net.minecraft.world.entity.decoration.ArmorStand)
					net.minecraft.world.phys.AABB(entity.x - 0.5, entity.y - 2.0, entity.z - 0.5, entity.x + 0.5, entity.y, entity.z + 0.5)
				else entity.boundingBox
				box(box, 0x6000AAFF)
			}
		}
	}
}
