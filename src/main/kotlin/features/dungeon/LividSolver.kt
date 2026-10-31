/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons F5/M5 Livid finder, rebuilt (replaces the old one-block finder in PuzzleSolvers.kt).
 * Every re-check reads the indicator wool/stained glass above the boss room, maps its colour to the Livid, and boxes
 * that Livid's player entity (or the space under its coloured nametag). Rules are in LividLogic.kt (pure, tested).
 * Ideas and reference positions, and (see LividLogic.kt and CREDITS.md); no code copied.
 * Visual only: never clicks, attacks or sends anything. Setting: puzzle-solvers.livid (⚠ grey area, default off).
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.LividLogic.Livid
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object LividSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	private const val SCAN_RADIUS = 64.0

	private var body: Player? = null
	private var tag: ArmorStand? = null
	private var current: LividLogic.Match? = null
	private var lastReadLog = ""
	private var lastMatchLog = ""

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	private fun reset() {
		body = null; tag = null; current = null; lastReadLog = ""; lastMatchLog = ""
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % LividLogic.RECHECK_TICKS != 0) return
		if (!cfg.livid || !DungeonUtil.isInDungeonIsland) { if (current != null || lastReadLog.isNotEmpty()) reset(); return }
		val level = Client.level ?: return
		val player = Client.player ?: return
		val area = player.boundingBox.inflate(SCAN_RADIUS)
		val players = level.getEntitiesOfClass(Player::class.java, area) { it !== player && it.isAlive && LividLogic.isLividEntityName(it.name.bareText) }
		val stands = level.getEntitiesOfClass(ArmorStand::class.java, area) { it.isAlive && it.hasCustomName() && it.customName!!.bareText.contains("Livid") }

		if (players.isEmpty() && stands.isEmpty()) { if (current != null || lastReadLog.isNotEmpty()) reset(); return }

		val reads = LividLogic.indicatorPositions(cfg.lividLegacyGlass).map { p ->
			p to BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos(p.x, p.y, p.z)).block).toString()
		}
		val picked = LividLogic.pickIndicator(reads)
		val readLog = reads.joinToString(" ") { (p, id) -> "$p=${id.removePrefix("minecraft:")}" } + " -> " + (picked?.let { "${it.second.displayName} at ${it.first}" } ?: "none")
		if (readLog != lastReadLog) {
			Apex.logger.info("[ApexLivid] block read {} (blind={}, livid players={}, livid tags={})", readLog, isBlind(), players.size, stands.size)
			lastReadLog = readLog
		}
		if (picked == null) { body = null; tag = null; current = null; logMatch("no indicator colour"); return }

		val bodies = players.map { LividLogic.Body(it.id, it.name.bareText, it.x, it.y, it.z) }
		val tags = stands.map { LividLogic.Tag(it.id, it.customName!!.getLegacyFormatString(), it.x, it.y, it.z) }
		val m = LividLogic.match(picked.second, bodies, tags)
		current = m
		body = m?.bodyId?.let { id -> players.firstOrNull { it.id == id } }
		tag = m?.tagId?.let { id -> stands.firstOrNull { it.id == id } }
		logMatch(
			if (m == null) "target=${picked.second.entityName}: no matching player or nametag (players=${bodies.joinToString { it.name }})"
			else "target=${m.livid.entityName} via=${m.via} body=${body?.let { "${it.name.bareText}#${it.id}" } ?: "none"} tag=${tag?.let { "'${it.customName!!.bareText}'#${it.id}" } ?: "none"}"
		)
	}

	private fun logMatch(line: String) {
		if (line == lastMatchLog) return
		lastMatchLog = line
		Apex.logger.info("[ApexLivid] match {}", line)
	}

	private fun isBlind() = Client.player?.hasEffect(MobEffects.BLINDNESS) == true

	private fun colourFor(l: Livid): Int =
		if (cfg.lividOwnColour) (0x60 shl 24) or l.rgb else cfg.lividColour.getEffectiveColourRGB()

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.livid) return
		val m = current ?: return
		if (!LividLogic.shouldDraw(isBlind(), cfg.lividWhileBlind)) return
		val colour = colourFor(m.livid)
		val label = Component.literal(m.livid.entityName).withColor(m.livid.rgb)
		val b = body?.takeIf { it.isAlive && !it.isRemoved }
		val t = tag?.takeIf { it.isAlive && !it.isRemoved }
		WorldDraw.drawInWorld(event) {
			if (b != null) {
				box(b.boundingBox.inflate(0.1), colour)
				text(b.position().add(0.0, b.bbHeight + 0.8, 0.0), label)
			} else if (t != null) {

				box(AABB(t.x - 0.5, t.y - 2.0, t.z - 0.5, t.x + 0.5, t.y, t.z + 0.5), colour)
				text(t.position().add(0.0, 0.6, 0.0), label)
			}
		}
	}
}
