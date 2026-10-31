// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.dungeon

import io.github.notenoughupdates.moulconfig.ChromaColour
import org.joml.Vector2i
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Guardian
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.misc.MiscRender
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.skyblock.SackUtil
import dev.ashtonite365.apex.util.bareText

object PlusDungeonState {
	var floor: String? = null; private set

	var inBoss = false; private set
	var bossName: String? = null; private set
	var runStarted: TimeMark? = null; private set

	fun isFloor(vararg floors: String) = floors.contains(floor)

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		floor = if (!DungeonUtil.isInDungeonIsland) null
		else PlusDungeonLogic.floorOf(ScoreboardUtil.simplifiedScoreboardLines) ?: floor
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		val text = event.bareText.trim()
		if (text.startsWith(MORT_MAP)) runStarted = TimeMark.now()
		val boss = PlusDungeonLogic.floorBossOf(text) ?: return
		inBoss = true
		bossName = boss
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		floor = null
		inBoss = false
		bossName = null
		runStarted = null
	}

	internal const val MORT_MAP = "[NPC] Mort: Here, I found this map"
}

object DungeonPlusPorts {
	@Config
	object TConfig : ManagedConfig("dungeon-tweaks", Category.DUNGEONS) {
		val bloodTimer by toggle("blood-timer") { false }
		val architectNotifier by toggle("architect-notifier") { false }
		val cleanEnd by toggle("clean-end") { false }
		val cleanEndF3Guardians by toggle("clean-end-f3-guardians") { true }
		val hideBossMessages by toggle("hide-boss-messages") { false }
		val chatFilter by toggle("chat-filter") { false }
		val chatFilterDamage by toggle("chat-filter-damage") { true }
		val chatFilterAbility by toggle("chat-filter-ability") { true }
		val copilot by toggle("copilot") { false }
		val copilotHud by position("copilot-hud", 140, 12) { Vector2i(4, 380) }
		val deathCounter by toggle("death-counter") { false }
		val deathCounterHud by position("death-counter-hud", 60, 12) { Vector2i(4, 395) }
		val milestones by toggle("milestones") { false }
		val milestonesHud by position("milestones-hud", 100, 12) { Vector2i(4, 410) }
		val creationCooldown by toggle("creation-cooldown") { false }
		val creationCooldownBlock by toggle("creation-cooldown-block") { false }
		val creationCooldownHud by position("creation-cooldown-hud", 150, 12) { Vector2i(4, 425) }
		val hideItemsInBoss by toggle("hide-items-in-boss") { false }
		val hideDamageSplashInBoss by toggle("hide-damage-splash-in-boss") { false }
		val highlightClickedBlocks by toggle("highlight-clicked-blocks") { false }
		val clickedBlocksColour by colour("clicked-blocks-colour") { ChromaColour.fromStaticRGB(85, 255, 85, 80) }
		val secretChime by toggle("secret-chime") { false }
		val muteSecretSounds by toggle("mute-secret-sounds") { false }
		val shadowAssassin by toggle("shadow-assassin") { false }
		val lowHealthAlert by toggle("low-health-alert") { false }
		val springBoots by toggle("spring-boots") { false }
		val trinityHelper by toggle("trinity-helper") { false }
		val terracottaHideParticles by toggle("terracotta-hide-particles") { false }
		val terracottaHideDamageSplash by toggle("terracotta-hide-damage-splash") { false }
	}

	private fun inDungeon() = DungeonUtil.isInDungeonIsland
	private fun say(text: String) = Client.showChat(Component.literal(text))

	private class RunState {
		var bloodOpenedAt: TimeMark? = null
		var killBloodAt: TimeMark? = null
		var copilot: String? = null
		var deaths = 0
		var milestone = 0
		var terracotta = false
		var bossDefeated = false
		var chestsUp = false
		var springPlings = 0
		val lowHealthSeen = HashMap<String, TimeMark>()
		val clickedBlocks = LinkedHashMap<BlockPos, Pair<String, TimeMark>>()
	}

	private var run = RunState()

	private var enteredAt: TimeMark? = null
	private var cooldownNoticeDone = true
	private var trinityAt: TimeMark? = null
	private var lastBorderWarning = 0

	private val CREATION_COOLDOWN = 30.seconds

	private fun creationCooldownLeft(): Duration? {
		val since = enteredAt ?: return null
		val left = CREATION_COOLDOWN - since.passedTime()
		return if (left.isPositive()) left else null
	}

	private fun bloodMove(open: TimeMark) {
		val elapsed = open.passedTime() + 100.milliseconds
		val prediction = PlusDungeonLogic.bloodMovePrediction(elapsed)
		if (prediction == null) {
			say("§cInvalid blood move prediction")
			return
		}
		val secs = String.format("%.2f", prediction.inWholeMilliseconds / 1000.0)
		say("§7Move Prediction: §f$secs Seconds§7.")
		Alerts.show(Component.literal("§7Move Prediction: §f${secs}s"), 2500.milliseconds, sound = false)
		run.killBloodAt = TimeMark.ago(elapsed - (prediction - 150.milliseconds))
	}

	@Subscribe
	fun onChatData(event: ProcessChatEvent) {
		if (!inDungeon()) return
		val text = event.bareText.trim()
		val r = run
		if (TConfig.bloodTimer) {
			if (text in PlusDungeonLogic.bloodOpen) r.bloodOpenedAt = TimeMark.now()
			if (text == PlusDungeonLogic.BLOOD_MOVE) r.bloodOpenedAt?.let(::bloodMove)
		}
		val step = PlusDungeonLogic.copilotStep(text, Client.playerName)
		if (step != null) r.copilot = step
		if (TConfig.deathCounter && PlusDungeonLogic.isDeath(text)) {
			r.deaths += 1
			say("§c§l${r.deaths}. DEATH!")
		}
		if (PlusDungeonLogic.isMilestone(text)) r.milestone += 1
		when (text) {
			PlusDungeonLogic.TERRACOTTA_START -> r.terracotta = true
			PlusDungeonLogic.TERRACOTTA_END -> r.terracotta = false
		}
		if (text.startsWith(PlusDungeonState.MORT_MAP)) {
			r.deaths = 0
			r.milestone = 0
			trinityAt = TimeMark.now()
		}
		if (text.startsWith("☠ Defeated ") || "> EXTRA STATS <" in text) r.bossDefeated = true
		if (PlusDungeonLogic.isChestsSpawned(text)) r.chestsUp = true
		val entered = PlusDungeonLogic.enteredPlayer(text) ?: return
		if (entered == Client.playerName) enteredAt = TimeMark.now()
		cooldownNoticeDone = false
	}

	private fun architectNotice(failer: String): Boolean {
		val drafts = SackUtil.items[ItemId("ARCHITECT_FIRST_DRAFT")] ?: 0L
		if (drafts <= 0) return false
		val msg = Component.literal("§c§lPUZZLE FAILED! §b$failer §efailed a puzzle.\n§eClick here to get §5Architect's First Draft §7(§e${drafts}x left§7)")
			.withStyle { it.withClickEvent(ClickEvent.RunCommand("/gfs ARCHITECT_FIRST_DRAFT 1")) }
		Client.showChat(msg)
		Alerts.show(Component.literal("§c§lPUZZLE FAILED!"))
		return true
	}

	private fun filtered(text: String): Boolean {
		val kind = PlusDungeonLogic.filterType(text) ?: return false
		return when (kind) {
			PlusDungeonLogic.FilterType.DAMAGE -> TConfig.chatFilterDamage
			PlusDungeonLogic.FilterType.ABILITY -> TConfig.chatFilterAbility
			else -> true
		}
	}

	@Subscribe
	fun onChatHide(event: AllowChatEvent) {
		if (!inDungeon()) return
		val text = event.bareText.trim()
		val failer = if (TConfig.architectNotifier) PlusDungeonLogic.puzzleFailer(text) else null
		val hide = (failer != null && architectNotice(failer)) ||
			(TConfig.hideBossMessages && PlusDungeonLogic.isBossMessage(text)) ||
			(TConfig.milestones && PlusDungeonLogic.isMilestone(text)) ||
			(TConfig.chatFilter && filtered(text))
		if (hide) event.cancel()
	}

	@Subscribe
	fun onCommand(event: OutgoingPacketEvent) {
		if (!(TConfig.creationCooldown && TConfig.creationCooldownBlock)) return
		val cmd = (event.packet as? ServerboundChatCommandPacket)?.command ?: return
		if (!cmd.startsWith("joininstance")) return
		val left = creationCooldownLeft() ?: return
		event.cancel()
		say("§eBlocked instance creation due to cooldown! Cooldown: §b${ApexFormatters.timespanText(left)}")
	}

	private fun lowHealthScan() {
		for (line in ScoreboardUtil.scoreboardLines) {
			val mate = PlusDungeonLogic.parseTeammate(line.formattedString()) ?: continue
			if (!mate.isLow) continue
			val seen = run.lowHealthSeen[mate.name]
			if (seen != null && seen.passedTime() < 3.seconds) continue
			run.lowHealthSeen[mate.name] = TimeMark.now()
			Alerts.show(Component.literal("§c${mate.name} §ais low §c${mate.health}❤"), 1.seconds)
		}
	}

	private fun trinityCheck() {
		val at = trinityAt ?: return
		if (at.passedTime() < 3.seconds) return
		trinityAt = null
		if (!TConfig.trinityHelper) return
		val puzzles = PlusDungeonLogic.puzzleCount(TabListReader.currentSnapshot.body.map { it.bareText }) ?: return
		if (puzzles < 5) return
		Alerts.show(Component.literal("§dPossible Trinity"))
		say("§e5 puzzle dungeon, watch out for possible Trinity room!")
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (TConfig.creationCooldown && !cooldownNoticeDone && enteredAt != null && creationCooldownLeft() == null) {
			cooldownNoticeDone = true
			say("§eDungeon creation cooldown is over!")
		}
		if (!inDungeon()) return
		val kill = run.killBloodAt
		if (kill != null && kill.passedTime().isPositive()) {
			run.killBloodAt = null
			Alerts.show(Component.literal("§cKill Blood"), 1500.milliseconds)
		}
		if (TConfig.shadowAssassin) {
			val warning = Client.level?.worldBorder?.warningTime ?: 0
			val f3Boss = PlusDungeonState.floor?.contains("3") == true && PlusDungeonState.inBoss
			if (warning == 10000 && lastBorderWarning != 10000 && !f3Boss) Alerts.show(Component.literal("§cShadow Assassin Jumping!"))
			lastBorderWarning = warning
		}
		if (TConfig.lowHealthAlert && event.tickCount % 5 == 0) lowHealthScan()
		trinityCheck()
	}

	private fun secretBlockName(block: Block): String? = when (block) {
		Blocks.CHEST -> "Chest"
		Blocks.TRAPPED_CHEST -> "Trapped Chest"
		Blocks.LEVER -> "Lever"
		Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD -> "Wither Essence"
		else -> null
	}

	@Subscribe
	fun onUseBlock(event: UseBlockEvent) {
		if (!inDungeon() || PlusDungeonState.inBoss) return
		val pos = event.hitResult.blockPos
		val label = secretBlockName(event.world.getBlockState(pos).block) ?: return
		if (TConfig.highlightClickedBlocks) run.clickedBlocks[pos.immutable()] = label to TimeMark.now()
		if (TConfig.secretChime) Client.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val clicked = run.clickedBlocks
		if (clicked.isEmpty()) return
		clicked.values.removeIf { it.second.passedTime() > 3.seconds }
		if (!TConfig.highlightClickedBlocks) return
		val rgb = TConfig.clickedBlocksColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			for ((pos, entry) in clicked) {
				block(pos, rgb)
				text(Vec3.atCenterOf(pos), Component.literal("§aClicked ${entry.first}"))
			}
		}
	}

	private val harpPitches = listOf(0.7936508f, 0.8888889f, 1f, 1.0952381f, 1.1904762f)
	private fun close(x: Float, y: Float) = abs(x - y) < 1e-4f

	internal fun isSecretSound(id: String, volume: Float, pitch: Float): Boolean {
		if (id == "block.chest.open") return volume == 0.5f
		if (id == "block.note_block.harp") return volume == 1f && harpPitches.any { close(it, pitch) }
		if (id == "block.anvil.break") return volume == 1f && close(pitch, 1.6984127f)
		if (id == "block.lever.click") return (volume == 1f || volume == 2f) && close(pitch, 0.4920635f)
		return false
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!inDungeon()) return
		val path = event.sound.value().location.path
		if (TConfig.muteSecretSounds && isSecretSound(path, event.volume, event.pitch)) {
			event.cancel()
			return
		}
		if (TConfig.springBoots && PlusDungeonState.inBossOn("F7", "M7")) springBoots(path)
		if (TConfig.cleanEnd && run.bossDefeated && !run.chestsUp && path.startsWith("block.note_block.")) event.cancel()
	}

	private fun PlusDungeonState.inBossOn(vararg floors: String) = isFloor(*floors) && inBoss

	private fun springBoots(path: String) {
		val me = Client.player ?: return
		if (me.getItemBySlot(EquipmentSlot.FEET).view().repoItemId?.skyblockId != "SPRING_BOOTS") return
		if (path == "entity.firework_rocket.launch" || path == "entity.generic.eat") {
			run.springPlings = 0
			return
		}
		if (path != "block.note_block.pling" || !me.isShiftKeyDown) return
		run.springPlings += 1
		if (run.springPlings == 5) Alerts.show(Component.literal("§cSpring Boots ready!"))
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!inDungeon()) return
		val terracottaHide = TConfig.terracottaHideParticles && run.terracotta && PlusDungeonState.isFloor("F6", "M6")
		if (terracottaHide || (TConfig.cleanEnd && run.bossDefeated)) event.cancel()
	}

	private fun isHiddenSplash(entity: Entity): Boolean {
		if (entity !is ArmorStand || !entity.isInvisible) return false
		val name = entity.customName?.bareText ?: return false
		if (!MiscRender.isDamageSplash(name)) return false
		return (TConfig.hideDamageSplashInBoss && PlusDungeonState.inBoss) || (TConfig.terracottaHideDamageSplash && run.terracotta)
	}

	fun shouldHide(entity: Entity): Boolean {
		if (!inDungeon()) return false
		if (isHiddenSplash(entity)) return true
		if (entity is ItemEntity && TConfig.hideItemsInBoss && PlusDungeonState.inBoss) return true
		if (!TConfig.cleanEnd || !run.bossDefeated || entity === Client.player) return false
		val sneakingOnF3 = TConfig.cleanEndF3Guardians && PlusDungeonState.isFloor("F3", "M3") && entity is Guardian && Client.player?.isShiftKeyDown == true
		if (sneakingOnF3) return false
		val keptAfterChests = run.chestsUp && (entity is Player || (entity is ArmorStand && !entity.hasCustomName()))
		return !keptAfterChests
	}

	private fun milestoneColour(n: Int) = if (n <= 1) "§c" else if (n == 2) "§e" else "§a"

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (TConfig.creationCooldown) {
			val left = creationCooldownLeft()
			if (left != null) event.drawHudLines(TConfig.creationCooldownHud, listOf(Component.literal("§eDungeon Creation Cooldown: §b${ApexFormatters.timespanText(left)}")))
		}
		if (!inDungeon()) return
		val r = run
		val step = r.copilot
		if (TConfig.copilot && step != null) event.drawHudLines(TConfig.copilotHud, listOf(Component.literal("§e$step")))
		if (TConfig.deathCounter && r.deaths > 0)
			event.drawHudLines(TConfig.deathCounterHud, listOf(Component.literal(PlusDungeonLogic.deathColour(r.deaths) + "Deaths: ${r.deaths}")))
		if (TConfig.milestones && PlusDungeonState.runStarted != null)
			event.drawHudLines(TConfig.milestonesHud, listOf(Component.literal("${milestoneColour(r.milestone)}Current Milestone: ${minOf(r.milestone, 3)}")))
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		run = RunState()
	}
}
