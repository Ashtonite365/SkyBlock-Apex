
package dev.ashtonite365.apex.features.dungeon

import org.joml.Vector2i
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component

object DungeonScore {
	val identifier: String
		get() = "dungeon-score"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val enabled by toggle("enabled") { true }
		val alert270 by toggle("alert-270") { true }
		val alert300 by toggle("alert-300") { true }
		val showCrypts by toggle("show-crypts") { true }

		val showSecrets by toggle("show-secrets") { true }
		val mimicAlert by toggle("mimic-alert") { false }
		val spiritPet by toggle("spirit-pet") { true }
		val paulEzpz by toggle("paul-ezpz") { false }
		val hud by position("hud", 120, 60) { Vector2i(4, 140) }
	}

	data class Breakdown(val skill: Int, val explore: Int, val speed: Int, val bonus: Int, val crypts: Int = 0, val mimic: Boolean = false) {
		val total get() = skill + explore + speed + bonus
		val rank get() = when {
			total >= 300 -> "§6S+"; total >= 270 -> "§eS"; total >= 230 -> "§aA"; total >= 160 -> "§9B"; total >= 100 -> "§2C"; else -> "§cD"
		}
	}

	private var mimicKilled = false
	private var alerted270 = false
	private var alerted300 = false
	private val floorRegex = "The Catacombs \\((\\w+)\\)".toRegex()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val t = event.bareText
		if ((t.contains("Mimic dead!", true) || t.contains("Mimic Killed!", true)) && !mimicKilled) {
			mimicKilled = true
			if (TConfig.mimicAlert) Alerts.show(Component.literal("§aMimic killed"))
		}
		if (t.contains("Here, I found this map when I first entered the dungeon.")) { mimicKilled = false; alerted270 = false; alerted300 = false }
	}

	fun requiredSecretFraction(floor: String): Double = when (floor) {
		"E" -> 0.3; "F1" -> 0.3; "F2" -> 0.4; "F3" -> 0.5; "F4" -> 0.6; "F5" -> 0.7; "F6" -> 0.85
		else -> 1.0
	}

	fun speedOffset(floor: String): Int = when (floor) {
		"E", "F1", "F2", "F3", "F5" -> 120
		"F4", "F6" -> 240
		"F7", "M7" -> 360
		"M6" -> 120
		else -> 0
	}

	fun speedScore(floor: String, seconds: Int): Int {
		val t = (seconds - speedOffset(floor)).toDouble()
		return when {
			t < 480 -> 100
			t < 600 -> kotlin.math.ceil(140 - t / 12).toInt()
			t < 840 -> kotlin.math.ceil(115 - t / 24).toInt()
			t < 1140 -> kotlin.math.ceil(108 - t / 30).toInt()
			t < 3940 -> kotlin.math.ceil(98.5 - t / 40).toInt()
			else -> 0
		}
	}

	fun mimicFloor(floor: String) = floor == "F6" || floor == "F7" || floor == "M6" || floor == "M7"

	fun compute(
		floor: String, cleared: Double, secretsFound: Double, crypts: Int, deaths: Int, incompletePuzzles: Int,
		seconds: Int, mimic: Boolean, paul: Boolean, firstDeathSpirit: Boolean,
	): Breakdown {
		val c = cleared.coerceIn(0.0, 1.0)
		val deathPenalty = 2 * deaths - if (deaths > 0 && firstDeathSpirit) 1 else 0
		val skill = (20 + kotlin.math.floor(80 * c).toInt() - 10 * incompletePuzzles - deathPenalty).coerceIn(20, 100)
		val secretPart = kotlin.math.floor(40 * (secretsFound / requiredSecretFraction(floor)).coerceIn(0.0, 1.0)).toInt()
		val explore = (kotlin.math.floor(60 * c).toInt() + secretPart).coerceIn(0, 100)
		val speed = speedScore(floor, seconds)
		val mimicPts = if (mimic && mimicFloor(floor)) 2 else 0
		val bonus = crypts.coerceIn(0, 5) + mimicPts + (if (paul) 10 else 0)
		return Breakdown(skill, explore, speed, bonus, crypts, mimic && mimicFloor(floor))
	}

	private fun tabNumber(lines: List<String>, label: String): Double? =
		lines.firstNotNullOfOrNull { l ->
			Regex("$label:? ([\\d.,]+)").find(l)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
		}

	private val elapsedRegex = "Time Elapsed: (?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?".toRegex()
	fun parseElapsed(lines: List<String>): Int = lines.firstNotNullOfOrNull { elapsedRegex.find(it) }?.let { m ->
		val (h, mi, se) = m.destructured
		(h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
	} ?: 0

	fun countIncompletePuzzles(tab: List<String>): Int = tab.count { it.contains("[✦]") || it.contains("[✖]") }

	fun estimate(): Breakdown? {
		if (!DungeonUtil.isInActiveDungeon) return null
		val board = ScoreboardUtil.simplifiedScoreboardLines
		val tab = TabListReader.currentSnapshot.body.map { it.bareText.trim() }
		val floor = board.firstNotNullOfOrNull { floorRegex.find(it)?.groupValues?.get(1) } ?: "F7"
		return compute(
			floor,
			cleared = (tabNumber(board, "Cleared") ?: 0.0) / 100.0,
			secretsFound = (DungeonSecretCounts.fromTab(tab).share ?: 0.0) / 100.0,
			crypts = tabNumber(tab, "Crypts")?.toInt() ?: 0,
			deaths = tabNumber(tab, "Team Deaths")?.toInt() ?: 0,
			incompletePuzzles = countIncompletePuzzles(tab),
			seconds = parseElapsed(board),
			mimic = mimicKilled, paul = TConfig.paulEzpz, firstDeathSpirit = TConfig.spiritPet,
		)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		val b = estimate() ?: return
		if (b.total >= 270 && !alerted270) { alerted270 = true; if (TConfig.alert270) Alerts.show(Component.literal("§e270 score reached")) }
		if (b.total >= 300 && !alerted300) { alerted300 = true; if (TConfig.alert300) Alerts.show(Component.literal("§6300 score reached")) }
		event.drawHudLines(TConfig.hud, listOf(
			Component.literal("§eScore: §f${b.total} ${b.rank}"),
			Component.literal("§7Skill ${b.skill}  Explore ${b.explore}"),
			Component.literal("§7Speed ${b.speed}  Bonus ${b.bonus}"),
		) + (if (TConfig.showCrypts) listOf(Component.literal((if (b.crypts >= 5) "§a" else "§c") + "Crypts ${b.crypts}/5" + (if (b.mimic) "  §aMimic ✔" else "  §7Mimic ✖"))) else emptyList()) +
			(if (TConfig.showSecrets) secretLines() else emptyList()))
	}

	fun secretLines(): List<Component> {
		val tab = TabListReader.currentSnapshot.body.map { it.bareText.trim() }
		val floor = ScoreboardUtil.simplifiedScoreboardLines.firstNotNullOfOrNull { floorRegex.find(it)?.groupValues?.get(1) } ?: "F7"
		return secretLines(DungeonSecretCounts.fromTab(tab), floor).map { Component.literal(it) }
	}

	fun secretLines(run: DungeonSecretCounts.TeamCount, floor: String): List<String> {
		val found = run.found ?: return emptyList()
		val total = run.estimatedTotal
		val need = DungeonSecretCounts.stillNeeded(run, requiredSecretFraction(floor))
		val main = if (total != null) DungeonSecretCounts.colourFor(found, total) + "Secrets $found/$total" else "§7Secrets $found"
		return listOf(main + (need?.let { if (it == 0) "  §aDone ✔" else "  §7Needed §e$it" } ?: ""))
	}
}
