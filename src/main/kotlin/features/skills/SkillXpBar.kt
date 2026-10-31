// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.skills

import kotlin.time.Duration.Companion.seconds
import dev.ashtonite365.apex.apis.Skill
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.TimeMark

object SkillXpBar {
	data class State(val skill: String, val fraction: Float, val needed: Double?, val level: Int?,
	                 val gain: Double? = null, val cur: Double? = null, val next: Double? = null, val levelFrom: Int? = null)

	@Volatile var state: State? = null; private set
	private var shownAt = TimeMark.farPast()

	fun parse(skill: String, progress: String, ladder: List<Int>?, knownLevel: Int? = null): State? {
		val t = progress.trim()
		if (t.endsWith("%")) {

			val pct = t.dropLast(1).replace(",", "").toDoubleOrNull() ?: return null
			val f = (pct / 100).coerceIn(0.0, 1.0)
			val next = knownLevel?.let { ladder?.getOrNull(it) }?.toDouble()?.takeIf { it > 0 }
			return State(skill, f.toFloat(), next?.let { it * (1 - f) }, knownLevel, cur = next?.let { it * f }, next = next)
		}
		val parts = t.split('/')
		if (parts.size != 2) return null
		val cur = SkillOverlay.parseShort(parts[0]) ?: return null
		val next = SkillOverlay.parseShort(parts[1])?.takeIf { it > 0 } ?: return null
		return State(skill, (cur / next).coerceIn(0.0, 1.0).toFloat(), (next - cur).coerceAtLeast(0.0), ladder?.let { levelFor(next, it) }, cur = cur, next = next)
	}

	fun levelFor(next: Double, ladder: List<Int>): Int? {
		val best = ladder.withIndex().minByOrNull { kotlin.math.abs(it.value - next) } ?: return null
		return best.index.takeIf { kotlin.math.abs(best.value - next) <= next * 0.05 }
	}

	private fun ladderOf(skill: String): List<Int>? = runCatching {
		val s = Skill.entries.firstOrNull { it.name.equals(skill.trim(), true) } ?: return null
		s.xpLadder(RepoManager.coreRepo.constants.leveling)
	}.getOrNull()

	private val knownLevels = mutableMapOf<String, Int>()
	private var levelUp: Triple<String, Int, Int>? = null
	private var levelUpAt = TimeMark.farPast()

	private val LEVEL_UP = Regex("SKILL LEVEL UP (\\w+) ([IVXLCDM]+|\\d+)\\s*➜\\s*([IVXLCDM]+|\\d+)")

	fun parseLevelUp(chat: String): Triple<String, Int, Int>? {
		val m = LEVEL_UP.find(chat.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")) ?: return null
		val from = level(m.groupValues[2]) ?: return null
		val to = level(m.groupValues[3]) ?: return null
		return Triple(m.groupValues[1], from, to)
	}

	private fun level(t: String): Int? = t.toIntOrNull() ?: run {
		val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
		var total = 0
		for (i in t.indices) { val c = v[t[i]] ?: return null; val n = t.getOrNull(i + 1)?.let { v[it] } ?: 0; total += if (c < n) -c else c }
		total
	}

	fun onChat(chat: String) {
		val lu = parseLevelUp(chat) ?: return
		knownLevels[lu.first.lowercase()] = lu.third
		levelUp = lu; levelUpAt = TimeMark.now()
		state?.takeIf { it.skill.equals(lu.first, true) }?.let { state = it.copy(level = lu.third, levelFrom = lu.second) }
	}

	fun onGain(skill: String, progress: String, gain: Double? = null) {
		val key = skill.trim().lowercase()
		val fresh = levelUp?.takeIf { it.first.equals(skill.trim(), true) && levelUpAt.passedTime() <= SkillOverlay.TConfig.xpBarSeconds.seconds }
		val st = parse(skill, progress, ladderOf(skill), knownLevels[key])?.copy(gain = gain, levelFrom = fresh?.second) ?: return
		st.level?.let { knownLevels[key] = it }
		state = st
		if (SkillOverlay.TConfig.xpBarDebugLog) dev.ashtonite365.apex.Apex.logger.info(logLine(st))
		shownAt = TimeMark.now()
	}

	@JvmStatic fun active(): State? {
		val st = state ?: return null
		if (!SkillOverlay.TConfig.xpBar || shownAt.passedTime() > SkillOverlay.TConfig.xpBarSeconds.seconds) return null
		return st
	}

	@JvmStatic fun progress(vanilla: Float): Float = active()?.fraction ?: vanilla
	@JvmStatic fun level(vanilla: Int): Int = active()?.level ?: vanilla

	@JvmStatic fun xpNeeded(vanilla: Int): Int = if (active() != null) maxOf(vanilla, 1) else vanilla

	private fun num(v: Double) = java.text.DecimalFormat("#,##0.#", java.text.DecimalFormatSymbols(java.util.Locale.ROOT)).format(v)

	private fun compact(v: Double): String {
		val a = kotlin.math.abs(v)
		fun f(x: Double) = "%.1f".format(java.util.Locale.ROOT, x).removeSuffix(".0")
		return when { a >= 1e9 -> f(v / 1e9) + "B"; a >= 1e6 -> f(v / 1e6) + "M"; a >= 1e3 -> f(v / 1e3) + "k"; else -> f(v) }
	}

	fun barText(st: State): String = listOfNotNull(
		st.gain?.let { "+" + num(it) + " " + st.skill } ?: st.skill,
		st.needed?.let { compact(it) + " to next" },
		st.level?.let { l -> st.levelFrom?.takeIf { it < l }?.let { "$it → $l" } ?: l.toString() },
	).joinToString(" · ")

	fun logLine(st: State): String =
		"[ApexSkillXp] skill=${st.skill} gain=${st.gain?.let { num(it) } ?: "?"} " +
			"current/needed=${st.cur?.let { num(it) } ?: "?"}/${st.next?.let { num(it) } ?: "?"} " +
			"level=${st.level ?: "?"}"
}
