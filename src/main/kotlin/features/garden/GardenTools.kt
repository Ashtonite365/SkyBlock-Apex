/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex garden tools: coin values next to visitor requirements and copper rewards, a farming-tool
 * counter HUD (counter, crops per minute and the lines other garden modules contribute) and a pest-spawn alert.
 * Display only. The config object below also holds the ids of the other garden modules.
 */
package dev.ashtonite365.apex.features.garden

import com.google.gson.JsonElement
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component
import org.joml.Vector2i
import kotlin.time.Duration.Companion.seconds

@OptIn(NeedsComponents::class)
object GardenTools {
	val identifier: String
		get() = "garden-tools"

	@Config
	object TConfig : ManagedConfig(category = Category.GARDEN, name = identifier) {
		val visitorPrices by toggle("visitor-prices") { true }
		val copperValue by toggle("copper-value") { true }
		val farmingHud by toggle("farming-hud") { true }
		val farmingHudPosition by position("farming-hud-position", 140, 50) { Vector2i(4, 120) }
		val farmingAngles by toggle("farming-angles") { false }
		val pestAlert by toggle("pest-alert") { true }
		val visitorShoppingList by toggle("visitor-shopping-list") { true }
		val visitorListPosition by position("visitor-list-position", 160, 60) { Vector2i(4, 180) }
		val visitorAlert by toggle("visitor-alert") { true }
		val coinsPerHour by toggle("coins-per-hour") { true }
		val jacobWarningMinutes by integer("jacob-warning-minutes", 0, 30) { 2 }
		val composterLowAlert by toggle("composter-low-alert") { true }
		val composterLowThousands by integer("composter-low-thousands", 1, 500) { 20 }
		val cropMilestones by toggle("crop-milestones") { true }
		val bestCrop by toggle("best-crop") { true }
		val milestoneHudPosition by position("milestone-hud-position", 160, 40) { Vector2i(4, 240) }
		val contestGoldThresholds by string("contest-gold-thresholds") { "" }
		val composterOverlay by toggle("composter-overlay") { true }
		val composterUpgradeCosts by toggle("composter-upgrade-costs") { true }
		val visitorFullAt by integer("visitor-full-at", 0, 6) { 5 }

		val pestSpawnTimer by toggle("pest-spawn-timer") { false }
		val pestFinder by toggle("pest-finder") { false }
		val pestProfitTracker by toggle("pest-profit-tracker") { false }
		val pestProfitReset by button("pest-profit-reset") { GardenPests.resetAll() }

		val milestoneTimeLeft by toggle("milestone-time-left") { false }
		val contestTimeNeeded by toggle("contest-time-needed") { false }

		val sensitivityReducer by riskyToggle("sensitivity-reducer", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val sensitivityDivisor by integer("sensitivity-divisor", 2, 20) { 4 }
		val sensitivityKey by keyBindingWithDefaultUnbound("sensitivity-key")
		val visitorTimer by toggle("visitor-timer") { false }
		val visitorRewardWarning by toggle("visitor-reward-warning") { false }

		val pestCooldownWarning by toggle("pest-cooldown-warning") { false }
		val pestCooldownWarnSeconds by integer("pest-cooldown-warn-seconds", 0, 60) { 5 }
		val pestCooldownRepeat by toggle("pest-cooldown-repeat") { false }
		val pestCustomCooldown by toggle("pest-custom-cooldown") { false }
		val pestCustomCooldownSeconds by integer("pest-custom-cooldown-seconds", 30, 600) { 135 }
		val pestAfkTimeout by integer("pest-afk-timeout", 5, 300) { 30 }
		val pestSpawnChatTime by toggle("pest-spawn-chat-time") { false }
		val pestBonusDisplay by toggle("pest-bonus-display") { false }
		val pestBonusWarning by toggle("pest-bonus-warning") { false }
		val bonusPestChanceDisplay by toggle("bonus-pest-chance-display") { false }
		val burrowingSporesAlert by toggle("burrowing-spores-alert") { false }

		val plotMenuHighlight by toggle("plot-menu-highlight") { false }
		val plotIcons by toggle("plot-icons") { false }
		val nextPlotPrice by toggle("next-plot-price") { false }

		val pbGain by toggle("pb-gain") { false }
		val pbGainOverflow by toggle("pb-gain-overflow") { false }
		val contestHighlightRewards by toggle("contest-highlight-rewards") { false }
		val contestMedalIcon by toggle("contest-medal-icon") { false }
		val contestRealTime by toggle("contest-real-time") { false }

		val composterInventoryNumbers by toggle("composter-inventory-numbers") { false }

		val anitaExtraFortune by toggle("anita-extra-fortune") { false }

		val cropSpeedMeterPosition by position("crop-speed-meter-position", 180, 90) { Vector2i(4, 560) }

		val milestoneOverflow by toggle("milestone-overflow") { false }
		val milestoneOverflowChat by toggle("milestone-overflow-chat") { false }
		val milestoneCustomGoals by string("milestone-custom-goals") { "" }

		val visitorHighlight by toggle("visitor-highlight") { false }
		val visitorCompactChat by toggle("visitor-compact-chat") { false }
		val visitorColourNames by toggle("visitor-colour-names") { false }
		val visitorDropStats by toggle("visitor-drop-stats") { false }
		val visitorStatsPosition by position("visitor-stats-position", 160, 120) { Vector2i(4, 640) }

		val pestTrackerLine by riskyToggle("pest-tracker-line", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val pestTrackerLineColour by colour("pest-tracker-line-colour") { io.github.notenoughupdates.moulconfig.ChromaColour.fromStaticRGB(255, 85, 85, 255) }
		val pestTrackerGuessLine by riskyToggle("pest-tracker-guess-line", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val pestTrackerGuessColour by colour("pest-tracker-guess-colour") { io.github.notenoughupdates.moulconfig.ChromaColour.fromStaticRGB(255, 255, 85, 255) }
		val pestTrackerWaypoint by riskyToggle("pest-tracker-waypoint", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val pestTrackerWaypointColour by colour("pest-tracker-waypoint-colour") { io.github.notenoughupdates.moulconfig.ChromaColour.fromStaticRGB(255, 85, 85, 110) }
		val pestTrackerThickness by integer("pest-tracker-thickness", 1, 10) { 3 }
		val pestTrackerMaxDistance by integer("pest-tracker-max-distance", 8, 256) { 128 }
		val pestTrackerAllPests by toggle("pest-tracker-all-pests") { false }
		val pestWaypointHideParticles by toggle("pest-waypoint-hide-particles") { false }
		val pestWaypointSeconds by integer("pest-waypoint-seconds", 5, 60) { 15 }
		val pestProfitPosition by position("pest-profit-position", 160, 80) { Vector2i(4, 320) }

		val bpsTracker by toggle("bps-tracker") { false }
		val optimalSpeed by toggle("optimal-speed") { false }

		val fortuneDisplay by toggle("fortune-display") { false }
		val contestEstimate by toggle("contest-estimate") { false }

		val anitaMedalProfit by toggle("anita-medal-profit") { false }
		val skymartCopperPrice by toggle("skymart-copper-price") { false }

		val farmingLanes by toggle("farming-lanes") { false }
		val laneMarkKey by keyBindingWithDefaultUnbound("lane-mark-key")
		val laneWarnSeconds by integer("lane-warn-seconds", 0, 10) { 2 }

		val gardenLevelDisplay by toggle("garden-level-display") { false }
		val hoeLevelDisplay by toggle("hoe-level-display") { false }
		val cropFeverTracker by toggle("crop-fever-tracker") { false }
		val rareCropTracker by toggle("rare-crop-tracker") { false }
		val gardenMiscPosition by position("garden-misc-position", 180, 80) { Vector2i(4, 400) }

		val nextContestDisplay by toggle("next-contest-display") { false }
		val contestBrackets by toggle("contest-brackets") { false }
		val contestSummary by toggle("contest-summary") { false }

		val cropMoneyRanking by toggle("crop-money-ranking") { false }
		val bestCropTime by toggle("best-crop-time") { false }
		val cropRankingPosition by position("crop-ranking-position", 160, 130) { Vector2i(4, 480) }

		val phantomleafSolver by riskyToggle("phantomleaf-solver", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val harvestableHighlight by toggle("harvestable-highlight") { false }
		val carrolynHint by toggle("carrolyn-hint") { false }

		val anglePrecision by integer("angle-precision", 0, 4) { 1 }
		val optimalAngles by riskyToggle("optimal-angles", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)

		val inventoryNumbers by toggle("inventory-numbers") { false }
		val gardenUptime by toggle("garden-uptime") { false }

		val atmosphericFilter by toggle("atmospheric-filter") { false }
		val stereoHarmony by toggle("stereo-harmony") { false }
		val zorroReminder by toggle("zorro-reminder") { false }
		val logbookStats by toggle("logbook-stats") { false }
		val seeThroughWindow by toggle("see-through-window") { false }
		val seeThroughKey by keyBindingWithDefaultUnbound("see-through-key")
		val seeThroughOpacity by integer("see-through-opacity", 5, 100) { 50 }
		val noBreak by riskyToggle("no-break", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
	}

	private val REQUIREMENT = Regex("^ ?([A-Za-z' \\-]+?)(?: x([\\d,]+))?$")

	private val COPPER_REWARD = Regex(".*\\+([\\d,]+) Copper.*")
	private val PEST_SPAWN = Regex(".*\\b(?:\\d+ )?Pests? (?:has|have) spawned in Plot - (\\S+)!.*")

	private fun digits(s: String) = s.filter { it != ',' }

	fun requirementLines(lines: List<String>): IntRange? {
		val header = lines.indexOfFirst { it.trim() == "Items Required:" }
		if (header < 0) return null
		val end = (header + 1 until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
		return header + 1 until end
	}

	fun requirementSuffix(line: String, priceByName: (String) -> Double?): String? {
		val m = REQUIREMENT.matchEntire(line) ?: return null
		val count = digits(m.groupValues[2]).ifEmpty { "1" }.toInt()
		val unit = priceByName(m.groupValues[1].trim())
		val shown = if (unit == null) "?" else ApexFormatters.compactNumber(unit * count)
		return " §7(§6$shown§7 coins)"
	}

	fun copperOf(line: String): Int? = COPPER_REWARD.matchEntire(line)?.let { digits(it.groupValues[1]).toInt() }

	fun bestTrade(table: Iterable<Map.Entry<String, JsonElement>>, priceOf: (ItemId) -> Double?, copper: Int): Pair<String, Double>? {
		var best: Pair<String, Double>? = null
		for ((id, element) in table) {
			val entry = element.asJsonObject
			if (entry["currency"]?.asString != "copper") continue
			val cost = entry["price"]?.asDouble ?: continue
			val coins = priceOf(ItemId(id)) ?: continue
			val value = coins / cost * copper
			if (best == null || value.compareTo(best.second) > 0) best = (entry["display"]?.asString ?: id) to value
		}
		return best
	}

	fun bestCopperTrade(copper: Int): Pair<String, Double>? =
		RepoConstants.get("skymart")?.let { bestTrade(it.entrySet(), RepoConstants::price, copper) }

	fun copperSuffix(line: String, trade: (Int) -> Pair<String, Double>?): String? {
		val copper = copperOf(line) ?: return null
		val (name, coins) = trade(copper) ?: return null
		return " §7(§6${ApexFormatters.compactNumber(coins)}§7 selling $name§7)"
	}

	fun pestPlot(message: String): String? = PEST_SPAWN.matchEntire(message)?.groupValues?.get(1)

	fun annotate(
		plain: List<String>, visitorPrices: Boolean, copperValue: Boolean,
		priceByName: (String) -> Double?, trade: (Int) -> Pair<String, Double>?,
	): List<Pair<Int, String>> {
		val required = requirementLines(plain) ?: return emptyList()
		val out = mutableListOf<Pair<Int, String>>()
		if (visitorPrices) for (i in required) requirementSuffix(plain[i], priceByName)?.let { out += i to it }
		if (copperValue) for (i in plain.indices) copperSuffix(plain[i], trade)?.let { out += i to it }
		return out
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val lines = event.lines
		val suffixes = annotate(lines.map { it.bareText }, TConfig.visitorPrices, TConfig.copperValue,
			{ name -> ItemNameLookup.guessItemByName(name, false)?.let(RepoConstants::price) }, ::bestCopperTrade)
		for ((i, suffix) in suffixes) lines[i] = lines[i].copy().append(Component.literal(suffix))
	}

	class CounterWindow(private val windowMs: Long = 60_000L) {
		private val times = ArrayDeque<Long>()
		private val values = ArrayDeque<Long>()
		var current: Long? = null
			private set

		fun observe(now: Long, value: Long?): Boolean {
			val changed = value != current
			if (changed) {
				val previous = current
				if (value == null || (previous != null && value < previous)) { times.clear(); values.clear() }
				current = value
			}
			if (value == null) return false
			times.addLast(now); values.addLast(value)
			while (times.isNotEmpty() && now - times.first() > windowMs) { times.removeFirst(); values.removeFirst() }
			return changed
		}

		fun perMinute(): Double? {
			if (times.isEmpty()) return null
			val span = times.last() - times.first()
			if (span <= 0) return null
			return (values.last() - values.first()) * 60_000.0 / span
		}
	}

	private val window = CounterWindow()

	fun cropsPerMinute(): Double? = window.perMinute()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val attributes = Client.player?.mainHandItem?.view()?.customDataTag
		val reading = attributes?.let { it.getLong("mined_crops").orElse(null) ?: it.getLong("farmed_cultivating").orElse(null) }
		val now = System.currentTimeMillis()
		if (window.observe(now, reading)) GardenPestTimers.onCropProgress(now)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.farmingHud || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val value = window.current ?: return
		val out = mutableListOf<Component>(Component.literal("§eCounter: §f" + ApexFormatters.withThousands(value, 3)))
		window.perMinute()?.let { rate ->
			out += Component.literal("§eCrops/min: §f" + ApexFormatters.compactNumber(rate))
			GardenHelpers.coinsPerHourLine(rate)?.let(out::add)
		}
		if (TConfig.farmingAngles) GardenAngles.anglesLine()?.let(out::add)
		GardenAngles.optimalLine()?.let(out::add)
		out += GardenCropSpeed.hudLines()
		GardenLanes.hudLine?.let(out::add)
		GardenContests.nextContestLine()?.let(out::add)
		GardenMiscA.uptimeLine()?.let(out::add)
		GardenMiscB.atmosphericLine()?.let(out::add)
		GardenMiscB.stereoLine()?.let(out::add)

		out += dev.ashtonite365.apex.features.timers.TimerBoards.keep("pest-spawn", GardenPests.spawnTimerLines())
		dev.ashtonite365.apex.features.timers.TimerBoards.keep("visitor", GardenVisitors.timerLine())?.let(out::add)
		event.drawHudLines(TConfig.farmingHudPosition, out)
	}

	@Subscribe
	fun onPestChat(event: ProcessChatEvent) {
		if (!TConfig.pestAlert) return
		val plot = pestPlot(event.bareText) ?: return
		Alerts.show(Component.literal("§c§lPest! §ePlot $plot"), 2.seconds)
	}
}
