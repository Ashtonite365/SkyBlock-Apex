/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.fishing

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.projectile.FishingHook
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.fishing.FishingHelper.TConfig
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object FishingExtras {
	private var baits: List<Pair<String, Int>> = emptyList()
	private var warnedNoBait = false
	private var totem: Pair<String, Duration>? = null
	private var totemAlerted = false
	private var hotspot: BlockPos? = null
	private var bobbers = 0
	private val spawned = mutableMapOf<Int, Pair<String, TimeMark>>()
	private var firstCreature: TimeMark? = null
	private var capAlerted = false
	private var timeAlerted = false
	private var lastTopBait: String? = null
	var rareCreatures: Set<Int> = emptySet()
		private set
	private var nearby: List<NearbySeaCreatures.Creature> = emptyList()
	private var lavaFishingSince: TimeMark? = null
	private var lastLavaCast: TimeMark? = null

	private val REMAINING = Regex("Remaining: (?:(\\d+)m )?(\\d+)s")
	private val GOLDEN_FISH = Regex("^You spot a Golden Fish surface from beneath the lava!$|^TROPHY FISH! You caught a Golden Fish .*|^The Golden Fish escapes your hook.*")

	private fun holdingRod() = Client.player?.mainHandItem?.item == net.minecraft.world.item.Items.FISHING_ROD

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		spawned.clear(); nearby = emptyList(); firstCreature = null; rareCreatures = emptySet(); hotspot = null; totem = null
		lavaFishingSince = null; lastLavaCast = null
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		if (!SkyBlockSession.inSkyBlock) return

		val counts = mutableMapOf<String, Int>()
		for (stack in player.inventory.nonEquipmentItems) {
			val id = stack.view().repoItemId?.skyblockId ?: continue
			if (id.endsWith("_BAIT")) counts.merge(stack.hoverName.bareText, stack.count, Int::plus)
		}
		baits = counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
		if (TConfig.noBaitWarning && holdingRod() && player.fishing != null) {
			if (baits.isEmpty() && !warnedNoBait) {
				warnedNoBait = true
				Alerts.show(Component.literal("§c§lNo bait in inventory!"), 2.seconds, sound = true)
			}
		}
		if (baits.isNotEmpty()) warnedNoBait = false
		val topBait = baits.firstOrNull()?.first
		if (TConfig.baitChangeWarning && holdingRod() && dev.ashtonite365.apex.features.plus.fishing.PlusFishingMerges.baitChanged(lastTopBait, topBait))
			Alerts.show(Component.literal("§eBait changed: §f${lastTopBait ?: "None"} §e-> §f${topBait ?: "None"}"), 2.seconds, sound = true)
		lastTopBait = topBait

		val area = player.boundingBox.inflate(40.0)
		val stands = level.getEntitiesOfClass(ArmorStand::class.java, area) { it.customName != null }
		val names = stands.map { it to it.customName!!.bareText.trim() }

		totem = names.firstOrNull { it.second == "Totem of Corruption" }?.let { (stand, _) ->
			names.filter { it.first.distanceToSqr(stand) < 4.0 }.firstNotNullOfOrNull { REMAINING.find(it.second) }
				?.let { m -> "Totem of Corruption" to ((m.groupValues[1].toIntOrNull() ?: 0).minutes + m.groupValues[2].toInt().seconds) }
		}
		val t = totem
		if (t == null) totemAlerted = false
		else if (TConfig.totemOverlay && TConfig.totemExpiryAlert > 0 && t.second <= TConfig.totemExpiryAlert.seconds && !totemAlerted) {
			totemAlerted = true
			Alerts.show(Component.literal("§5§lTotem expiring!"), 2.seconds, sound = true)
		}

		hotspot = if (TConfig.hotspotWaypoint) HotspotReached.hotspots.pick(
			names.filter { it.second == "HOTSPOT" }.map { it.first.position() }, player.position(), player.fishing?.position(),
		)?.let { BlockPos.containing(it) } else null
		bobbers = if (TConfig.bobberCount) level.getEntitiesOfClass(FishingHook::class.java, player.boundingBox.inflate(30.0)) { true }.size else 0

		val now = mutableMapOf<Int, String>()
		val rare = mutableSetOf<Int>()
		for ((stand, name) in names) {
			if ('❤' !in name) continue
			val sc = SeaCreatureBook.mentionedIn(name) ?: continue
			now[stand.id] = sc.name
			if (sc.rare && TConfig.highlightRareCreatures) {
				level.getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.5, 3.0, 1.5)) { it !is ArmorStand && it !== player }
					.minByOrNull { it.distanceToSqr(stand) }?.let { rare += it.id }
			}
		}
		rareCreatures = rare
		val known = SeaCreatureBook.kinds.map { it.name to it.rare }
		nearby = names.mapNotNull { NearbySeaCreatures.fromTag(it.second, known) }
		dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureExtras.update(level, player, names.filter { '❤' in it.second }.mapNotNull { (st, n) ->
			SeaCreatureBook.mentionedIn(n)?.let { Triple(st, n, it) }
		})
		for ((id, scName) in now) spawned.getOrPut(id) { scName to TimeMark.now() }
		val gone = spawned.keys - now.keys
		for (id in gone) {
			val (scName, at) = spawned.remove(id) ?: continue
			val e: Entity? = level.getEntity(id)
			if (TConfig.killTimeMessage && (e == null || !e.isAlive) && player.distanceToSqr(e ?: player) < 1600)
				Client.showChat(Component.literal("§b[SkyBlock Apex] §f$scName §7lived §f${ApexFormatters.timespanText(at.passedTime(), millis = true)}"))
		}
		if (spawned.isEmpty()) {
			firstCreature = null; capAlerted = false
		} else if (firstCreature == null) firstCreature = TimeMark.now()
		if (spawned.isEmpty()) timeAlerted = false
		val oldest = firstCreature?.passedTime()?.inWholeSeconds ?: 0
		if (dev.ashtonite365.apex.features.plus.fishing.PlusFishingMerges.timeAlert(spawned.size, oldest, TConfig.creatureTimeAlert) && !timeAlerted) {
			timeAlerted = true
			Alerts.show(Component.literal("§c§lSea creatures alive for ${oldest}s!"), 2.seconds, sound = true)
		}
		if (TConfig.creatureCapAlert > 0 && spawned.size >= TConfig.creatureCapAlert && !capAlerted) {
			capAlerted = true
			Alerts.show(Component.literal("§c§l${spawned.size} sea creatures alive!"), 2.seconds, sound = true)
		}

		val hook = player.fishing
		if (SkyBlockSession.skyblockIsland == SkyBlockIsland.CRIMSON_ISLE && hook != null && hook.isInLava) {
			lastLavaCast = TimeMark.now()
			if (lavaFishingSince == null) lavaFishingSince = TimeMark.now()
		}
		if (lastLavaCast?.let { it.passedTime() > 3.minutes } == true) {
			lavaFishingSince = null; lastLavaCast = null
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (GOLDEN_FISH.matches(event.bareText.trim())) lavaFishingSince = TimeMark.now()
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		val since = lavaFishingSince ?: return

		val left = 8.minutes - since.passedTime()
		event.add(Timers.TimerEntry("golden-fish", "Golden Fish",
		                            if (left.isPositive()) "§e${ApexFormatters.timespanText(left)}" else "§aCan spawn §7(${ApexFormatters.timespanText(since.passedTime())})",
		                            !left.isPositive(), left))
	}

	fun hudLines(): List<Component> {
		val out = mutableListOf<Component>()
		val rod = holdingRod()
		if (TConfig.baitDisplay && rod) {
			if (baits.isEmpty()) out += Component.literal("§cNo bait")
			else baits.take(4).forEach { (n, c) -> out += Component.literal("§7$n: §f$c") }
		}
		if (TConfig.totemOverlay) totem?.let { (n, left) ->
			dev.ashtonite365.apex.features.timers.TimerBoards.keep("totem", Component.literal("§5$n: §f${ApexFormatters.timespanText(left)}"))?.let(out::add)
		}
		if (TConfig.nearbyCreatures) {
			val since = firstCreature?.passedTime()?.let { ApexFormatters.timespanText(it) }
			NearbySeaCreatures.summaryLine(spawned.size, since)?.let { out += Component.literal(it) }
		}
		if (TConfig.bobberCount && rod) out += Component.literal("§7Bobbers nearby: §f$bobbers")
		return out
	}

	fun creatureHealthLines(): List<Component> {
		if (!TConfig.nearbyCreatures || !TConfig.nearbyCreaturesHealth) return emptyList()
		val opts = NearbySeaCreatures.Options(true, TConfig.nearbyCreaturesLimit, TConfig.nearbyCreaturesRareFirst)
		return NearbySeaCreatures.lines(nearby, null, opts).second.map { Component.literal(it) }
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val h = hotspot
		val rare = rareCreatures
		if (h == null && rare.isEmpty()) return
		val level = Client.level ?: return
		WorldDraw.drawInWorld(event) {
			if (h != null && TConfig.hotspotWaypoint) looks(WaypointType.FISHING) { waypoint(h, Component.literal("§dHotspot")) }
			if (TConfig.highlightRareCreatures) for (id in rare) {
				val e = level.getEntity(id) ?: continue
				if (e.isAlive) box(e.boundingBox, 0x60FFAA00)
			}
		}
	}

	@JvmStatic
	fun shouldHide(entity: Entity): Boolean {
		if (FishingPlusPorts.shouldHideChum(entity)) return true
		if (dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureExtras.shouldHide(entity)) return true
		if (!TConfig.hideOtherBobbers || !SkyBlockSession.inSkyBlock) return false
		val hook = entity as? FishingHook ?: return false
		val owner = hook.playerOwner ?: return false
		return owner !== Client.player
	}
}
