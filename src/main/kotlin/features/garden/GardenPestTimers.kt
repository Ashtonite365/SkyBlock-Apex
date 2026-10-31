// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.bareText
import kotlin.time.Duration.Companion.seconds

object GardenPestTimers {
	private val patterns = PlusPatterns.group("garden.pests")

	private val cooldownLine by patterns.pattern("cooldowntime-no-color",
		"\\s*Cooldown: (?<time>\\d{1,2}[hms](?: \\d{1,2}[ms]?)?(?: \\d{1,2}s)?)?(?<ready>READY)?(?<maxPests>MAX PESTS)?.*")
	private val fullGardenLine by patterns.pattern("max-pests-alive", "\\s*Alive: 8\\b.*")

	private val bonusLine by patterns.pattern("pestfortunebuff-no-color",
		"\\s*Bonus: (?<inactive>INACTIVE)?(?:\\+(?<fortune>\\d+)\\D*? (?<time>\\d.*))?.*")
	private val pestChanceLine by patterns.pattern("bonus-pest-chance", "\\s*Bonus Pest Chance: \\D*(?<amount>[\\d,.]+).*")
	private val sporesMessage by patterns.pattern("burrowing-spores", "VERY RARE CROP! .*Burrowing Spores.*")

	sealed interface Cooldown {
		data object Ready : Cooldown
		data object MaxPests : Cooldown
		data class Time(val text: String) : Cooldown
	}

	fun parseCooldown(lines: List<String>): Cooldown? {
		if (lines.any { fullGardenLine.matcher(it).matches() }) return Cooldown.MaxPests
		val m = lines.asSequence().map { cooldownLine.matcher(it) }.firstOrNull { it.matches() } ?: return null
		if (m.group("maxPests") != null) return Cooldown.MaxPests
		if (m.group("ready") != null) return Cooldown.Ready
		return m.group("time")?.let { Cooldown.Time(it) }
	}

	fun parseBonus(lines: List<String>): Pair<Int, String?>? {
		for (line in lines) {
			val m = bonusLine.matcher(line).takeIf { it.matches() } ?: continue
			if (m.group("inactive") != null) return 0 to null
			val fortune = m.group("fortune")?.toIntOrNull() ?: continue
			return fortune to m.group("time")?.trim()
		}
		return null
	}

	fun parseBonusPestChance(lines: List<String>): Double? {
		for (line in lines) {
			val m = pestChanceLine.matcher(line)
			if (!m.matches()) continue
			m.group("amount")?.replace(",", "")?.toDoubleOrNull()?.let { return it }
		}
		return null
	}

	fun isBurrowingSpores(message: String): Boolean = sporesMessage.matcher(message).matches()

	private val timeUnit = Regex("(\\d+)([hms])")
	private const val MINUTE = 60_000L

	fun tabEndTime(text: String, now: Long, currentEnd: Long?): Long? {
		val parts = timeUnit.findAll(text.lowercase()).toList()
		if (parts.isEmpty()) return null
		var length = 0L
		for (p in parts) {
			val amount = p.groupValues[1].toLong()
			length += amount * when (p.groupValues[2]) { "h" -> 60 * MINUTE; "m" -> MINUTE; else -> 1000L }
		}
		val precise = parts.any { it.groupValues[2] == "s" }
		val shown = now + length
		if (currentEnd != null) {
			val late = shown > currentEnd + 6_000
			val early = shown + (if (precise) 1_000 else MINUTE) < currentEnd
			if (!late && !early) return currentEnd
		}
		return if (precise) shown else shown + MINUTE
	}

	fun format(ms: Long): String {
		val total = (maxOf(ms, 0L) + 999) / 1000
		val h = total / 3600
		val m = total % 3600 / 60
		return if (h > 0) "${h}h ${m}m" else if (total >= 60) "${total / 60}m ${total % 60}s" else "${total}s"
	}

	var cooldownEnd: Long? = null
	var ready = false
	var maxPests = false
	var cooldownSeen = false
	var lastSpawn: Long? = null
	val spawnTimes = mutableListOf<Long>()
	var bonusFortune = 0
	var bonusEnd: Long? = null
	var bonusPestChance: Double? = null

	private var longestPause = 0L
	private var lastBreak = 0L
	private var bonusExpiryShown = true
	private var previousIsland: SkyBlockIsland? = null

	private object Warn {
		var expired = false
		var soon = false
		var repeat = false
		fun clear() { expired = false; soon = false }
	}

	fun averageSpawn(): Long? = spawnTimes.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

	private fun fixedCooldownEnd(spawn: Long) = spawn + TConfig.pestCustomCooldownSeconds * 1000L

	fun onCropProgress(now: Long) {
		if (lastBreak > 0 && now - lastBreak > longestPause) longestPause = now - lastBreak
		lastBreak = now
	}

	fun onPestSpawn(now: Long) {
		Warn.repeat = false
		lastSpawn?.let { previous ->
			val gap = now - previous
			if (longestPause <= TConfig.pestAfkTimeout * 1000L) spawnTimes.add(gap)
			if (TConfig.pestSpawnChatTime) Client.showChat(Component.literal("§e[Apex] Pests spawned in §b${format(gap)}"))
		}
		lastSpawn = now
		longestPause = 0
		Warn.clear()
		if (TConfig.pestCustomCooldown) cooldownEnd = fixedCooldownEnd(now)
	}

	private fun applyCooldown(state: Cooldown?, now: Long) {
		cooldownSeen = state != null
		when (state) {
			null -> Unit
			is Cooldown.Time -> {
				ready = false
				maxPests = false
				val spawn = lastSpawn
				cooldownEnd = if (spawn != null && TConfig.pestCustomCooldown) fixedCooldownEnd(spawn) else tabEndTime(state.text, now, cooldownEnd)
			}
			else -> {
				ready = state == Cooldown.Ready
				maxPests = state == Cooldown.MaxPests
				cooldownEnd = null
				Warn.repeat = false
			}
		}
	}

	private fun applyBonus(bonus: Pair<Int, String?>, now: Long) {
		val (fortune, remaining) = bonus
		if (remaining != null) {
			bonusExpiryShown = false
			bonusFortune = fortune
			bonusEnd = tabEndTime(remaining, now, bonusEnd)
			return
		}
		bonusFortune = 0
		bonusEnd = null
		if (bonusExpiryShown) return
		bonusExpiryShown = true
		if (TConfig.pestBonusWarning) {
			Client.showChat(Component.literal("§c[Apex] Pesthunter fortune bonus has expired! §7(trade pests at Phillip in the Barn)"))
			Alerts.show(Component.literal("§cPest Fortune Buff Expired!"), 3.seconds)
		}
	}

	fun update(lines: List<String>, now: Long) {
		applyCooldown(parseCooldown(lines), now)
		parseBonus(lines)?.let { applyBonus(it, now) }
		parseBonusPestChance(lines)?.let { bonusPestChance = it }
	}

	private fun warnings(now: Long) {
		if (!TConfig.pestCooldownWarning) return
		val end = cooldownEnd ?: return
		val left = end - now
		if (Warn.repeat) {
			if (left <= 0) Warn.repeat = false else Alerts.show(Component.literal("§cPest cooldown: ${format(left)}"), 1.seconds)
		}
		when {
			Warn.expired -> Unit
			left <= 0 -> {
				Warn.repeat = false
				Warn.expired = true
				Client.showChat(Component.literal("§c[Apex] Pest spawn cooldown has expired!"))
				Alerts.show(Component.literal("§cPest Cooldown Has Expired!"), 3.seconds)
			}
			Warn.soon || TConfig.pestCooldownWarnSeconds <= 0 -> Unit
			left <= (TConfig.pestCooldownWarnSeconds + 1) * 1000L -> {
				Warn.soon = true
				Client.showChat(Component.literal("§c[Apex] Pest spawn cooldown expires in ${format(left)}"))
				if (TConfig.pestCooldownRepeat) Warn.repeat = true else Alerts.show(Component.literal("§cPest Cooldown Expires Soon!"), 3.seconds)
			}
		}
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val island = SkyBlockSession.skyblockIsland
		if (island != previousIsland) {
			previousIsland = island
			if (island == SkyBlockIsland.GARDEN) {
				lastSpawn = null
				Warn.repeat = false
				longestPause = 0
			}
		}
		if (island != SkyBlockIsland.GARDEN) return
		val now = System.currentTimeMillis()
		update(TabListReader.currentSnapshot.body.map { it.bareText }, now)
		warnings(now)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.burrowingSporesAlert || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (isBurrowingSpores(event.bareText)) Alerts.show(Component.literal("§9Burrowing Spores!"), 3.seconds)
	}

	private fun cooldownText(now: Long): String {
		val end = cooldownEnd
		return when {
			!cooldownSeen -> "§cPests widget not found (enable it in /widget)"
			maxPests -> "§cMax Pests!"
			ready || (end != null && end <= now) -> "§aReady!"
			end == null -> "§cUnknown"
			else -> "§b${format(end - now)}"
		}
	}

	fun hudLines(now: Long = System.currentTimeMillis()): List<Component> {
		val text = mutableListOf<String>()
		if (TConfig.pestSpawnTimer) {
			text += lastSpawn?.let { "§eLast pest spawned: §b${format(now - it)} ago" } ?: "§cNo pest spawned since joining."
			text += "§ePest Cooldown: ${cooldownText(now)}"
			averageSpawn()?.let { text += "§eAverage time to spawn: §b${format(it)}" }
		}
		if (TConfig.pestBonusDisplay) {
			val end = bonusEnd
			text += if (end != null && end > now && bonusFortune != 0) "§ePesthunter Bonus: §6+$bonusFortune☘ §b${format(end - now)}"
			else "§ePesthunter Bonus: §cInactive!"
		}
		if (TConfig.bonusPestChanceDisplay) bonusPestChance?.let { chance ->
			val shown: Any = if (chance % 1.0 == 0.0) chance.toInt() else chance
			text += "§2Bonus Pest Chance: §f$shown"
		}
		return text.map { Component.literal(it) }
	}
}
