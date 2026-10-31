// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

enum class UpgradeState { READY_TO_CLAIM, AVAILABLE, IN_PROGRESS, BLOCKED, CANNOT_AFFORD, MAXED }

data class UpgradeCell(val slot: Int, val row: Int, val col: Int, val name: String, val lore: List<String>,
                       val header: Boolean, val state: UpgradeState?) {
	val cleanLore get() = lore.map(MenuText::clean)
}

data class UpgradesModel(val cells: List<UpgradeCell>, val rows: List<Int>, val columns: List<Int>,
                         val status: List<String>, val divider: String?, val account: List<String> = emptyList())

object UpgradesParser {

	const val TITLE = "Community Shop"
	val HEADER = Regex("^(Profile|Account) Upgrades$", RegexOption.IGNORE_CASE)
	private val STATUS = Regex("^(Profile|Account):", RegexOption.IGNORE_CASE)
	private val IN_PROGRESS = Regex("(?i)^(time left|time remaining|remaining|ends in|finishes in|ready in|in progress|currently upgrading|upgrading\\b)")

	fun state(cleanLore: List<String>): UpgradeState? {
		fun has(s: String) = cleanLore.any { it.contains(s, true) }
		return when {
			has("Click to claim") -> UpgradeState.READY_TO_CLAIM
			has("Maxed out") -> UpgradeState.MAXED
			has("Another upgrade is underway") -> UpgradeState.BLOCKED
			has("Cannot afford") -> UpgradeState.CANNOT_AFFORD
			cleanLore.any { IN_PROGRESS.containsMatchIn(it) } -> UpgradeState.IN_PROGRESS
			has("Click to start") || has("Click to upgrade") -> UpgradeState.AVAILABLE
			else -> null
		}
	}

	private fun filler(s: SlotText) = s.cleanName.isBlank() && s.cleanLore.all { it.isBlank() }

	fun parse(title: String, slots: List<SlotText>, exclude: Set<Int> = emptySet(), accountSlot: Int? = null): UpgradesModel? {
		if (!MenuTitles.matchesEnd(MenuText.clean(title), TITLE)) return null
		val headers = slots.filter { it.slot % 9 == 0 && HEADER.matches(it.cleanName) }
		if (headers.isEmpty()) return null
		val firstRow = headers.minOf { it.slot / 9 }
		val cells = slots.filter { it.slot / 9 >= firstRow && it.slot !in exclude && !filler(it) }
			.sortedBy { it.slot }
			.map { s -> UpgradeCell(s.slot, s.slot / 9, s.slot % 9, s.name, s.lore, s.slot % 9 == 0 && HEADER.matches(s.cleanName),
				state(s.cleanLore)) }
		val selectedTab = slots.firstOrNull { it.slot < 9 && it.cleanLore.any { l -> l.equals("Currently selected!", true) } }
		val status = selectedTab?.cleanLore?.filter { STATUS.containsMatchIn(it) } ?: emptyList()
		val divider = slots.firstOrNull { it.slot / 9 in 1 until firstRow && it.cleanName.contains("Categories", true) }
			?.let { (listOf(it.cleanName) + it.cleanLore.filter(String::isNotBlank)).joinToString("  ") }
		val account = slots.firstOrNull { it.slot == accountSlot }?.cleanLore?.filter(String::isNotBlank) ?: emptyList()
		return UpgradesModel(cells, cells.map { it.row }.distinct(), cells.map { it.col }.distinct().sorted(), status, divider, account)
	}
}

object UpgradesLayout {
	const val GAP = 3
	const val PAD = 3
	const val LINE = 10
	const val BLANK = 4
	const val ICON = 18

	data class Placed(val cell: UpgradeCell, val x: Int, val y: Int, val w: Int, val h: Int,
	                  val nameLines: List<String>, val body: List<String?>, val nameBelowIcon: Boolean = false) {

		val nameY get() = if (nameBelowIcon) PAD + ICON + 1 else PAD + 1
		val bodyY get() = if (nameBelowIcon) PAD + ICON + nameLines.size * LINE + 2 else PAD + maxOf(ICON, nameLines.size * LINE) + 2
	}
	data class Result(val placed: List<Placed>, val height: Int, val columnX: Map<Int, Int>, val columnW: Map<Int, Int>)

	fun labelWidth(width: Int) = (width / 7).coerceIn(56, 100)

	private val KEY_VALUE = Regex("^([^:]{1,40}):\\s+(.+)$")

	fun colour(line: String): String {
		val c = MenuText.clean(line)
		return when {
			line.startsWith("§") -> line
			c.startsWith("Click to", true) -> "§e$line"
			c.contains("Cannot afford", true) || c.contains("Another upgrade is underway", true) -> "§c$line"
			c.contains("Maxed out", true) || c.contains("DONE", false) -> "§a$line"
			c.equals("Profile Upgrade", true) || c.equals("Account Upgrade", true) -> "§8$line"
			else -> KEY_VALUE.matchEntire(line)?.let { "§7${it.groupValues[1]}: §f${it.groupValues[2]}" } ?: "§7$line"
		}
	}

	fun wrapLore(lore: List<String>, w: Int, measure: (String) -> Int): List<String?> = lore.flatMap { l ->
		if (MenuText.clean(l).isEmpty()) listOf(null) else CityText.wrap(colour(l), w, measure)
	}

	fun wrapName(name: String, w: Int, measure: (String) -> Int, header: Boolean): List<String> =
		CityText.wrap((if (header) "§6§l" else if (name.startsWith("§")) "" else "§f") + name, w, measure)

	fun place(m: UpgradesModel, width: Int, measure: (String) -> Int): Result {
		val labelCol = m.columns.firstOrNull()?.takeIf { c -> m.cells.filter { it.col == c }.all { it.header } }
		val others = m.columns.filter { it != labelCol }
		val lw = if (labelCol != null) labelWidth(width) else 0
		val rest = width - lw - (if (labelCol != null && others.isNotEmpty()) GAP else 0)
		val cw = if (others.isEmpty()) rest else maxOf(20, (rest - GAP * (others.size - 1)) / others.size)
		val colX = mutableMapOf<Int, Int>(); val colW = mutableMapOf<Int, Int>()
		if (labelCol != null) { colX[labelCol] = 0; colW[labelCol] = if (others.isEmpty()) width else lw }
		others.forEachIndexed { i, c -> colX[c] = (if (labelCol != null) lw + GAP else 0) + i * (cw + GAP); colW[c] = cw }
		val out = mutableListOf<Placed>()
		var y = 0
		for (r in m.rows) {
			val row = m.cells.filter { it.row == r }.sortedBy { it.col }
			val laid = row.map { c ->
				val w = colW.getValue(c.col)

				val beside = wrapName(c.name, w - PAD * 2 - ICON - 2, measure, c.header)
				val below = beside.size > 2
				val name = if (below) wrapName(c.name, w - PAD * 2, measure, c.header) else beside
				Placed(c, colX.getValue(c.col), y, w, 0, name, wrapLore(c.lore, w - PAD * 2, measure), below)
			}
			val rh = laid.maxOfOrNull { cellHeight(it) } ?: 0
			laid.forEach { out += it.copy(h = rh) }
			y += rh + GAP
		}
		return Result(out, maxOf(0, y - GAP), colX, colW)
	}

	fun cellHeight(p: Placed) = p.bodyY + p.body.sumOf { if (it == null) BLANK else LINE } + PAD
}
