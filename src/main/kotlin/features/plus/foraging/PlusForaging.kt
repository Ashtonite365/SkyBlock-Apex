// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration.Companion.minutes
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.plus.matches
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object PlusForaging {
	@Config
	object TConfig : ManagedConfig("foraging-extras", Category.FORAGING) {
		val muteFusionMachine by toggle("mute-fusion-machine") { false }
		val muteTreeBreaking by toggle("mute-tree-breaking") { false }
		val hideTreeBlocks by toggle("hide-tree-blocks") { false }
		val beaconReadyAlert by toggle("beacon-ready-alert") { false }
		val honeyhiveReminder by toggle("honeyhive-reminder") { false }
		val queenBeeAlert by toggle("queen-bee-alert") { false }
		val tutorialQuestWaypoint by toggle("tutorial-quest-waypoint") { false }
	}

	private val island get() = SkyBlockSession.skyblockIsland
	private fun inGalatea() = island == SkyBlockIsland.GALATEA
	private fun inForagingArea() = inGalatea() || island == SkyBlockIsland.PARK

	private val FUSION_PATHS = setOf("entity.firework_rocket.blast", "entity.firework_rocket.blast_far")
	private const val FUSION_VOLUME = 20f
	private const val TREE_BREAK_PATH = "entity.creaking.death"

	fun isFusionSound(path: String, volume: Float) = volume == FUSION_VOLUME && path in FUSION_PATHS

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val path = event.sound.value().location().path
		val fusion = TConfig.muteFusionMachine && inGalatea() && isFusionSound(path, event.volume)
		if (fusion) event.cancel()
		val tree = TConfig.muteTreeBreaking && inForagingArea() && path == TREE_BREAK_PATH
		if (tree) event.cancel()
	}

	private val TREE_BLOCKS: Set<Block> by lazy {
		hashSetOf(
			Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.MANGROVE_WOOD,
			Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_MANGROVE_WOOD,
		)
	}

	fun shouldHideEntity(entity: Entity): Boolean {
		if (!(TConfig.hideTreeBlocks && inGalatea())) return false
		if (entity !is Display.BlockDisplay) return false
		val block = entity.blockRenderState()?.blockState()?.block ?: return false
		return block in TREE_BLOCKS
	}

	private val patterns = PlusPatterns.group("foraging")
	private val beaconAvailable by patterns.pattern("beacon.available", " ?Cooldown: AVAILABLE")
	private val hiveLooted by patterns.pattern("honeyhive.hive-looted", "You stick your hand into the honeyhive and feel around\\.\\.\\.")
	private val queenBee by patterns.pattern("honeyhive.queen-bee", "QUEEN BEE! The Honeyhive instantly refilled with loot!")
	private val questNeeded by patterns.pattern("tutorial.required", "You must complete the (?<quest>.*) Quest to use this!")

	fun isBeaconReady(lines: List<String>): Boolean {
		for (line in lines) if (beaconAvailable.matches(line)) return true
		return false
	}

	private var beaconAlertedAt: TimeMark = TimeMark.farPast()
	private var hiveLootedAt: TimeMark? = null

	private fun checkBeacon() {
		if (!TConfig.beaconReadyAlert || !inGalatea()) return
		if (beaconAlertedAt.passedTime() <= 9.minutes) return
		val tab = TabListReader.currentSnapshot.body.map { it.bareText }
		if (!isBeaconReady(tab)) return
		beaconAlertedAt = TimeMark.now()
		Alerts.show(Component.literal("§aBeacon Ready"))
	}

	private fun checkHoneyhive() {
		val looted = hiveLootedAt
		if (!TConfig.honeyhiveReminder || looted == null || looted.passedTime() <= 5.minutes) return
		hiveLootedAt = null
		Client.showChat(Component.literal("§b[SkyBlock Apex] §eHoneyhives are ready to be collected in the Torrhus Canyon."))
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 == 0) {
			checkBeacon()
			checkHoneyhive()
		}
	}

	enum class Quest(val questName: String, val npc: String, val pos: BlockPos) {
		FIRST("Foraging Tutorial", "Lumber Jack", BlockPos(-124, 74, -30)),
		SECOND("Into the Woods", "Charlie", BlockPos(-276, 80, -18)),
		THIRD("A Helping Hand", "Kelly", BlockPos(-351, 94, 31)),
		FOURTH("The Campfire Cult", "Ryan", BlockPos(-363, 102, -91)),
		FIFTH("The Rebuild", "Melody", BlockPos(-413, 109, 70)),
	}

	private var targetQuest: Quest? = null

	fun questFor(message: String): Quest? {
		val name = questNeeded.matchMatcher(message) { group("quest") } ?: return null
		return Quest.entries.find { it.questName == name }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val text = event.bareText.trim()
		if (TConfig.honeyhiveReminder && hiveLooted.matches(text)) hiveLootedAt = TimeMark.now()
		if (TConfig.queenBeeAlert && queenBee.matches(text)) Alerts.show(Component.literal("§6Honeyhive Instantly Refilled!"))
		if (TConfig.tutorialQuestWaypoint) {
			val found = questFor(text)
			if (found != null) targetQuest = found
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val quest = targetQuest ?: return
		if (!TConfig.tutorialQuestWaypoint) return
		if (!(inForagingArea() || island == SkyBlockIsland.HUB)) return
		val me = Client.player ?: return
		if (me.blockPosition().distSqr(quest.pos) < 25) {
			targetQuest = null
			return
		}
		WorldDraw.drawInWorld(event, WaypointType.FORAGING) { waypoint(quest.pos, Component.literal("§e${quest.npc} §7(${quest.questName})")) }
	}
}
