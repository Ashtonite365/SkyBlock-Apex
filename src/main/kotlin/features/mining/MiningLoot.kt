/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): mining loot/profit trackers (corpse, fossil excavation, Crystal Nucleus,
 * ghost coins), drill lore compaction, precision-mining spot and carpet highlighter. Inspired by and
 * feature descriptions only. Original implementation, no code copied.
 */
package dev.ashtonite365.apex.features.mining

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object MiningLoot {
	@Config
	object TConfig : ManagedConfig("mining-loot", Category.MINING) {
		val lootTracker by toggle("loot-tracker") { true }
		val lootHud by position("loot-hud", 150, 60) { Vector2i(5, 360) }
		val ghostCoins by toggle("ghost-coins") { true }
		val compactDrillLore by toggle("compact-drill-lore") { true }
		val precisionMining by toggle("precision-mining") { true }
		val carpetHighlight by toggle("carpet-highlight") { false }
	}

	enum class Source(val label: String) { CORPSE("Corpses"), EXCAVATION("Excavations"), NUCLEUS("Nucleus runs") }

	private val HEADERS = listOf(
		Regex("^(?:LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!$") to Source.CORPSE,
		Regex("^EXCAVATION COMPLETE$") to Source.EXCAVATION,
		Regex("^CRYSTAL NUCLEUS LOOT BUNDLE$") to Source.NUCLEUS,
	)
	private val REWARD = Regex("^(.+?)(?: x([0-9,]+))?$")
	private val GHOST_COINS = Regex("^The ghost's death materialized ([0-9,]+) coins from the mists!$")

	fun header(line: String): Source? = HEADERS.firstOrNull { it.first.matches(line.trim()) }?.second

	fun reward(line: String): Pair<String, Int>? {
		val t = line.trim()
		if (t.isEmpty() || t.startsWith("▬") || t == "REWARDS" || !line.startsWith(" ")) return null
		val m = REWARD.matchEntire(t) ?: return null
		return m.groupValues[1] to (m.groupValues[2].replace(",", "").toIntOrNull() ?: 1)
	}
	fun ghostCoins(line: String): Long? = GHOST_COINS.matchEntire(line.trim())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

	val loot = SessionTracker(idleTimeout = 10.minutes, resetAfter = 60.minutes)
	private var section: Source? = null
	private var sectionStarted = TimeMark.farPast()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val raw = event.bareText
		if (TConfig.ghostCoins && SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES)
			ghostCoins(raw)?.let { MiningExtras.ghosts.add("Coins", it.toDouble()) }
		if (!TConfig.lootTracker) return
		header(raw)?.let { section = it; sectionStarted = TimeMark.now(); loot.add(it.label); return }
		val src = section ?: return
		if (sectionStarted.passedTime() > 3.seconds || raw.trim().startsWith("▬")) { section = null; return }
		val (name, amount) = reward(raw) ?: return
		val id = ItemNameLookup.guessItemByName(name, false) ?: return
		val price = HypixelStaticData.getPriceOfItem(id) ?: return
		loot.add("${src.label} coins", price * amount)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.lootTracker || loot.isEmpty) return
		if (!isMiningIsland(SkyBlockSession.skyblockIsland)) return
		event.drawHudLines(TConfig.lootHud, listOf(Component.literal("§6Mining loot")) + loot.lines())
	}

	private val EMPTY_PART = Regex("^(?:Drill Engine|Fuel Tank|Upgrade Module): Not Installed$")

	fun compactDrill(lines: List<String>): Set<Int> {
		val drop = mutableSetOf<Int>()
		for ((i, l) in lines.withIndex()) if (EMPTY_PART.matches(l.trim())) {
			drop += i
			if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Increases") || i + 1 < lines.size && lines[i + 1].trim().startsWith("Apply")) drop += i + 1
		}
		return drop
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.compactDrillLore) return
		val text = event.lines.map { it.bareText }
		if (text.none { it.trim().startsWith("Fuel: ") }) return
		val drop = compactDrill(text)
		if (drop.isEmpty()) return
		val kept = event.lines.filterIndexed { i, _ -> i !in drop }
		event.lines.clear(); event.lines.addAll(kept)
	}

	fun isMiningIsland(island: SkyBlockIsland?): Boolean = island in MiningOverlay.miningIslands

	fun acceptsPrecisionHit(island: SkyBlockIsland?, hitType: HitResult.Type?, blockIsAir: Boolean): Boolean =
		isMiningIsland(island) && hitType == HitResult.Type.BLOCK && !blockIsAir

	private var precisionPos: BlockPos? = null
	private var precisionAt = TimeMark.farPast()

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.precisionMining) return
		val island = SkyBlockSession.skyblockIsland
		if (!isMiningIsland(island)) { precisionPos = null; return }
		if (event.particleEffect.type != ParticleTypes.CRIT && event.particleEffect.type != ParticleTypes.HAPPY_VILLAGER) return
		val hit = Client.game.hitResult as? BlockHitResult ?: return
		val target = hit.blockPos
		val blockIsAir = Client.game.level?.getBlockState(target)?.isAir ?: true
		if (!acceptsPrecisionHit(island, hit.type, blockIsAir)) return
		if (Vec3.atCenterOf(target).distanceToSqr(event.position) > 1.0) return
		precisionPos = target.immutable(); precisionAt = TimeMark.now()
	}

	private var carpets = listOf<BlockPos>()
	private var tick = 0

	@Subscribe
	fun onTick(event: TickEvent) {
		if (precisionPos != null && !isMiningIsland(SkyBlockSession.skyblockIsland)) precisionPos = null
		if (!TConfig.carpetHighlight || SkyBlockSession.skyblockIsland != SkyBlockIsland.DWARVEN_MINES) { carpets = emptyList(); return }
		if (tick++ % 20 != 0) return
		val level = Client.game.level ?: return
		val center = Client.player?.blockPosition() ?: return
		carpets = BlockPos.betweenClosed(center.offset(-12, -6, -12), center.offset(12, 6, 12))
			.filter { level.getBlockState(it).`is`(BlockTags.WOOL_CARPETS) }.map { it.immutable() }
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val p = precisionPos?.takeIf { precisionAt.passedTime() < 1.seconds && isMiningIsland(SkyBlockSession.skyblockIsland) }
		if (p == null && carpets.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			p?.let { block(it, 0x5000FF00) }
			for (c in carpets) block(c, 0x40FF0000)
		}
	}
}
