// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.skills

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.features.plus.slayer.SlayerPlusMerges
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

object SlayerOverlay {
	@Config
	object TConfig : ManagedConfig("slayer-overlay", Category.SLAYERS) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 140, 50) { Vector2i(5, 150) }
		val trackPbs by toggle("track-pbs") { true }
		val pbAlert by toggle("pb-alert") { true }
		val dropProfit by toggle("drop-profit") { true }
		val showXpRng by toggle("show-xp-rng") { true }
		val bossSpawnAlert by toggle("boss-spawn-alert") { false }
		val minibossAlert by toggle("miniboss-alert") { false }
		val highlightBoss by riskyToggle("highlight-boss", RiskLevel.RISKY)
		val itemProfitTracker by toggle("item-profit-tracker") { false }
		val itemProfitTotal by toggle("item-profit-total") { false }
		val resetItemProfit by button("reset-item-profit") { SlayerPlusMerges.profit.reset() }
		val hideRngMeterChat by toggle("hide-rng-meter-chat") { false }
		val timeMessages by toggle("time-messages") { false }
		val questWarning by toggle("quest-warning") { false }
		val bossSpawnSoon by toggle("boss-spawn-soon") { false }
		val bossSpawnSoonPercent by integer("boss-spawn-soon-percent", 50, 99) { 90 }
		val remainingKills by toggle("remaining-kills") { false }
		val highlightMinibosses by riskyToggle("highlight-minibosses", RiskLevel.GREY_AREA)
		val blockNotSpawnable by toggle("block-not-spawnable") { false }
		val itemsOnGround by toggle("items-on-ground") { false }
		val hideMobNames by toggle("hide-mob-names") { false }
		val resetPbs by button("reset-pbs") { Records.wipe() }
	}

	@Serializable
	data class Data(val bestBossMillis: MutableMap<String, Long> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "slayer-pbs", ::Data)

	private object Records {
		fun of(boss: String): Long? = DConfig.data.bestBossMillis[boss]

		fun offer(boss: String, took: Duration): Long? {
			val millis = took.inWholeMilliseconds
			val previous = of(boss)
			if (previous != null && millis >= previous) return null
			DConfig.data.bestBossMillis[boss] = millis
			DConfig.markDirty()
			return previous ?: -1L
		}

		fun wipe() {
			DConfig.data.bestBossMillis.clear()
			DConfig.markDirty()
		}
	}

	val dropTracker = SessionTracker(idleTimeout = 5.minutes, resetAfter = 30.minutes)

	var questName: String? = null
	var progress: String? = null
	var questStart: TimeMark? = null
	var bossSpawn: TimeMark? = null
	var lastBossTime: String? = null
	var bossesKilled = 0

	var slayerXpLine: String? = null

	var rngMeterLine: String? = null

	private const val BOSS_PHASE = "Slay the boss!"
	private val levelLine = Regex("^(\\w+ Slayer LVL \\d+) - (?:Next LVL in ([\\d,]+) XP!|LVL MAXED OUT!)")
	private val meterLine = Regex("^RNG Meter - ([\\d,]+) Stored XP")
	private val dropLine = "^(?:RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|RNGESUS INCARNATE)?\\s*DROP!\\s*\\(?(?:\\d+x )?([^()]+?)\\)?(?: \\(\\+\\d+% ✯ Magic Find\\))?$".toRegex()
	private val killLines = setOf("SLAYER QUEST COMPLETE!", "NICE! SLAYER BOSS SLAIN!")

	private val xpToNext = HashMap<String, Long>()

	@Subscribe
	fun onTick(event: TickEvent) {
		val rows = ScoreboardUtil.simplifiedScoreboardLines.map(String::trim)
		val header = rows.indexOf("Slayer Quest")
		if (header == -1) {
			progress = null
			questName = null
			return
		}
		noteQuest(rows.getOrNull(header + 1))
		noteProgress(rows.getOrNull(header + 2))
	}

	private fun noteQuest(shown: String?) {
		if (shown == questName) return
		questName = shown
		questStart = questStart ?: TimeMark.now()
	}

	private fun noteProgress(shown: String?) {
		val bossJustAppeared = shown == BOSS_PHASE && shown != progress
		progress = shown
		if (!bossJustAppeared) return
		bossSpawn = TimeMark.now()
		if (TConfig.bossSpawnAlert) Alerts.show(Component.literal("§c§lBoss spawned!"))
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val text = event.bareText.trim()
		levelLine.find(text)?.let(::noteLevel)
		meterLine.find(text)?.let { rngMeterLine = "RNG Meter: ${it.groupValues[1]} XP" }
		when {
			text in killLines -> bossDown()
			text == "SLAYER QUEST FAILED!" || text.startsWith("Your Slayer Quest has been cancelled") -> clearTimers()
			text == "SLAYER QUEST STARTED!" -> questStart = TimeMark.now()
			TConfig.dropProfit && questName != null -> dropLine.matchEntire(text)?.let { valueDrop(it.groupValues[1].trim()) }
		}
	}

	private fun noteLevel(m: MatchResult) {
		val label = m.groupValues[1]
		val remainingText = m.groupValues[2]
		val remaining = remainingText.replace(",", "").toLongOrNull()
		if (remaining != null) {
			val kind = label.substringBefore(" LVL")
			val before = xpToNext.put(kind, remaining)
			if (before != null && before > remaining) dropTracker.add("Slayer XP", (before - remaining).toDouble())
		}
		val suffix = if (remainingText.isEmpty()) " §7(maxed)" else " §7(next in $remainingText XP)"
		slayerXpLine = label + suffix
	}

	private fun bossDown() {
		bossesKilled += 1
		val spawnedAt = bossSpawn
		if (spawnedAt != null) {
			val took = spawnedAt.passedTime()
			lastBossTime = ApexFormatters.timespanText(took)
			val boss = questName
			if (boss != null && TConfig.trackPbs) {
				val beaten = Records.offer(boss, took)
				if (beaten != null && beaten >= 0 && TConfig.pbAlert)
					Alerts.show(Component.literal("§6§lNew PB! §e" + ApexFormatters.timespanText(took)))
			}
		}
		if (questName != null) dropTracker.add("Bosses")
		clearTimers()
	}

	private fun clearTimers() {
		bossSpawn = null
		questStart = null
	}

	private fun valueDrop(itemName: String) {
		val item = ItemNameLookup.guessItemByName(itemName, false) ?: return
		val coins = HypixelStaticData.bazaarData[item.bazaarProduct]?.quickStatus?.sellPrice
			?: HypixelStaticData.lowestBin[item]
			?: return
		dropTracker.add("Drop value", coins)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		val quest = questName ?: return
		val rows = ArrayList<Component>()
		fun row(s: String) { rows += Component.literal(s) }
		row("§5$quest")
		progress?.let { row("§7$it") }
		lastBossTime?.let { row("§7Last boss: §a$it") }
		row("§7Bosses this session: §e$bossesKilled")
		if (TConfig.trackPbs) Records.of(quest)?.let { row("§7PB: §a" + ApexFormatters.timespanText(it.milliseconds)) }
		if (TConfig.showXpRng) {
			slayerXpLine?.let { row("§d$it") }
			rngMeterLine?.let { row("§b$it") }
			dropTracker.perHour("Slayer XP")?.let { row("§7Slayer XP/h: §a" + ApexFormatters.compactNumber(it)) }
		}
		if (TConfig.dropProfit && !dropTracker.isEmpty) rows += dropTracker.lines()
		SlayerPlusMerges.extraHudLines(rows)
		event.drawHudLines(TConfig.hud, rows)
	}

	private var ownBoss: LivingEntity? = null

	@Subscribe
	fun findOwnBoss(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		ownBoss = if (TConfig.highlightBoss) locateOwnBoss() else null
	}

	private fun locateOwnBoss(): LivingEntity? {
		val me = Client.player ?: return null
		val world = Client.level ?: return null
		val wanted = "Spawned by: ${me.gameProfile.name}"
		val marker = world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(40.0)) {
			it.customName?.bareText?.trim() == wanted
		}.firstOrNull() ?: return null
		return world.getEntitiesOfClass(LivingEntity::class.java, marker.boundingBox.inflate(1.5, 4.0, 1.5)) {
			it !== me && it !is ArmorStand
		}.minByOrNull { it.distanceToSqr(marker) }
	}

	@Subscribe
	fun renderOwnBoss(event: WorldRenderLastEvent) {
		val target = ownBoss?.takeIf { it.isAlive } ?: return
		if (!TConfig.highlightBoss) return
		WorldDraw.drawInWorld(event) { box(target.boundingBox, 0x60FF3030) }
	}

	internal val minibosses = listOf(
		"Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant",
		"Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula",
		"Pack Enforcer", "Sven Follower", "Sven Alpha",
		"Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac",
		"Flare Demon", "Kindleheart Demon", "Burningsoul Demon",
	)
	private val announced = HashSet<Int>()

	@Subscribe
	fun onMinibossTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		if (questName == null || !TConfig.minibossAlert) {
			announced.clear()
			return
		}
		val me = Client.player ?: return
		val world = Client.level ?: return
		for (stand in world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(20.0)) { it.hasCustomName() }) {
			val tagText = stand.customName?.bareText ?: continue
			val which = minibosses.firstOrNull(tagText::contains) ?: continue
			if (announced.add(stand.id)) Alerts.show(Component.literal("§6Miniboss: §e$which"))
		}
	}
}
