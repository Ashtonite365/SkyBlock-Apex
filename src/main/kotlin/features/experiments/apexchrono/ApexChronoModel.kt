// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.experiments.apexchrono

data class ChronoSlot(val index: Int, val name: String, val path: String, val glint: Boolean = false)

enum class ChronoPhase { UNKNOWN, SHOW, REPLAY }
enum class ChronoMarkKind { CURRENT, NEXT }

data class ChronoMark(val kind: ChronoMarkKind, val number: Int, val repeat: Int)

object ApexChronoModel {

	val TITLE = Regex("^Chronomatron\\s*\\(([^)]+)\\)$")
	val COLOURS = setOf("white", "orange", "magenta", "light blue", "yellow", "lime", "pink", "gray", "light gray", "cyan",
		"purple", "blue", "brown", "green", "red", "black", "aqua", "dark aqua", "dark blue", "dark green", "dark purple", "gold", "dark red")
	private val ROUND = Regex("^Round:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
	private val BLOCKS = listOf("_stained_glass_pane", "_stained_glass", "_terracotta", "_wool", "_concrete", "_dye")

	fun tier(title: String) = TITLE.matchEntire(clean(title))?.groupValues?.get(1)?.trim()
	fun clean(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()

	fun round(slots: List<ChronoSlot>) = slots.firstNotNullOfOrNull { ROUND.find(clean(it.name))?.groupValues?.get(1)?.toIntOrNull() }

	fun status(slots: List<ChronoSlot>): ChronoSlot? {
		fun isStatus(s: ChronoSlot) = s.path == "glowstone" || s.path == "clock" ||
			clean(s.name).contains("remember the pattern", true) || clean(s.name).startsWith("Timer", true)
		return slots.getOrNull(slots.size - 5)?.takeIf(::isStatus) ?: slots.firstOrNull(::isStatus)
	}

	fun phase(slots: List<ChronoSlot>): ChronoPhase? {
		val s = status(slots) ?: return null
		val n = clean(s.name)
		return when {
			n.contains("remember the pattern", true) || s.path == "glowstone" -> ChronoPhase.SHOW
			s.path == "clock" || n.startsWith("Timer", true) -> ChronoPhase.REPLAY
			else -> null
		}
	}

	fun terracottaBoard(notes: List<ChronoSlot>) = notes.isNotEmpty() && notes.count { it.path.endsWith("_terracotta") } * 2 > notes.size
	fun isLit(s: ChronoSlot, terracottaBoard: Boolean): Boolean = s.glint ||
		if (terracottaBoard) s.path.endsWith("_stained_glass") else s.path.endsWith("_terracotta")

	fun group(s: ChronoSlot): String? {
		val n = clean(s.name).lowercase()
		if (n in COLOURS) return n
		if (n.isEmpty() || n.startsWith("round") || n.contains("remember") || n.startsWith("timer")) return null
		val suffix = BLOCKS.firstOrNull { s.path.endsWith(it) } ?: return null
		val c = s.path.removeSuffix(suffix).replace('_', ' ')
		return c.takeIf { it != "black" && it != "gray" }
	}

	fun runAt(seq: List<String>, i: Int): Int { var n = 0; while (i + n < seq.size && seq[i + n] == seq[i]) n++; return n }
}

class ApexChronoSolver(private val log: (String) -> Unit = {}) {
	var phase = ChronoPhase.UNKNOWN; private set
	var round: Int? = null; private set

	val sequence = mutableListOf<String>()
	var index = 0; private set

	var completedRounds = 0; private set
	private val slotsOf = mutableMapOf<String, MutableSet<Int>>()
	private val groupAt = mutableMapOf<Int, String>()
	private var terracotta: Boolean? = null
	private var litBefore = emptySet<String>()
	private var lastClicked: String? = null
	private var clicked = false
	private var recording = false

	fun slotsOf(group: String): Set<Int> = slotsOf[group] ?: emptySet()
	fun groupOf(index: Int) = groupAt[index]
	val expected: String? get() = if (phase == ChronoPhase.REPLAY) sequence.getOrNull(index) else null
	val isTerracottaBoard get() = terracotta == true

	private fun newRound(why: String) {
		if (sequence.isNotEmpty() || index > 0) log("new round ($why): cleared ${sequence.size} notes")
		sequence.clear(); index = 0; clicked = false
	}

	fun update(slots: List<ChronoSlot>) {
		val status = ApexChronoModel.status(slots)
		val notes = slots.filter { it !== status && ApexChronoModel.group(it) != null }
		if (terracotta == null && notes.isNotEmpty()) { terracotta = ApexChronoModel.terracottaBoard(notes); log("board: ${notes.size} note slots, ${if (terracotta == true) "terracotta (lights as glass)" else "glass (lights as terracotta)"}") }
		val style = terracotta == true
		for (s in notes) {
			if (groupAt[s.index] != null) continue

			val g = ApexChronoModel.group(s) ?: continue
			groupAt[s.index] = g; slotsOf.getOrPut(g) { sortedSetOf() } += s.index
		}
		ApexChronoModel.round(slots)?.let { r -> if (r != round) { log("round $round -> $r"); round = r; if (phase != ChronoPhase.SHOW) newRound("round $r") } }
		val litNow = notes.filter { groupAt[it.index] != null && ApexChronoModel.isLit(it, style) }.sortedBy { it.index }.mapNotNull { groupAt[it.index] }.distinct()
		val p = ApexChronoModel.phase(slots) ?: phase
		if (p != phase) {
			log("phase $phase -> $p (round $round)")
			if (p == ChronoPhase.SHOW) {
				if (phase == ChronoPhase.REPLAY) newRound("show started")

				litBefore = litNow.filter { it == lastClicked }.toSet()
				recording = true; clicked = false
			}
			if (p == ChronoPhase.REPLAY) { index = 0; log("replay: ${sequence.joinToString(" → ")}") }
			phase = p
		}
		if (recording && !clicked) {
			for (g in litNow) if (g !in litBefore) {
				sequence += g
				log("note #${sequence.size}: $g (slots ${slotsOf(g)})" + if (phase == ChronoPhase.REPLAY) " [after timer flip]" else "")
			}
			litBefore = litNow.toSet()
		}
	}

	fun onClick(slot: Int) {
		if (phase != ChronoPhase.REPLAY) return
		val g = groupAt[slot] ?: return
		clicked = true; lastClicked = g
		val want = sequence.getOrNull(index)
		if (g == want) {
			index++; log("click $slot ($g) correct, ${sequence.size - index} left")
			if (index == sequence.size) { completedRounds++; log("round $round complete") }
		} else log("click $slot ($g) WRONG, expected $want")
	}

	fun markFor(slot: Int): ChronoMark? {
		if (phase != ChronoPhase.REPLAY || index >= sequence.size) return null
		val g = groupAt[slot] ?: return null
		val run = ApexChronoModel.runAt(sequence, index)
		if (g == sequence[index]) return ChronoMark(ChronoMarkKind.CURRENT, index + 1, run)
		val next = index + run
		if (next < sequence.size && g == sequence[next]) return ChronoMark(ChronoMarkKind.NEXT, next + 1, ApexChronoModel.runAt(sequence, next))
		return null
	}

	fun reset() {
		phase = ChronoPhase.UNKNOWN; round = null; sequence.clear(); index = 0; completedRounds = 0
		slotsOf.clear(); groupAt.clear(); terracotta = null; litBefore = emptySet(); lastClicked = null; clicked = false; recording = false
	}
}
