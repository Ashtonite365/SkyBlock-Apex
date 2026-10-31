// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.museum

import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.support.SlotText

enum class MuseumStatus { DONATED, MISSING, LOCKED, UNKNOWN }

data class MuseumCategory(val name: String, val slot: Int, val donated: Int?, val total: Int?) {
	val fraction: Double? get() = if (donated != null && total != null && total > 0) (donated.toDouble() / total).coerceIn(0.0, 1.0) else null
}

data class MuseumEntry(val slot: Int, val name: String, val status: MuseumStatus,

	val details: MuseumItemDetails? = null,

	val removable: Boolean = false)

data class MuseumItemDetails(val rarity: String?, val donated: String?, val created: String?, val cleanValue: String?,
	val value: String?, val atriumSlot: Int?)

data class MuseumSortControl(val slot: Int, val options: List<String>, val selected: String?)

data class MuseumStats(val slot: Int, val lines: List<String>, val donated: Int?, val total: Int?)

enum class MuseumSort : StringRepresentable {
	SLOT, STATUS, NAME;
	override fun getSerializedName() = name.lowercase()
}

enum class MuseumFilter { ALL, DONATED, MISSING }

data class MuseumModel(

	val category: String?,
	val categories: List<MuseumCategory>,
	val entries: List<MuseumEntry>,
	val prevSlot: Int?, val nextSlot: Int?, val backSlot: Int?,
	val page: Int?, val pages: Int?,

	val donated: Int?, val total: Int?,

	val stats: MuseumStats? = null,

	val extraDetailsSlot: Int? = null, val extraDetailsOn: Boolean? = null,
	val sortControl: MuseumSortControl? = null,

	val countOnly: Int? = null,
) {
	val isOverview get() = category == null
	val donatedShown get() = donated ?: entries.count { it.status == MuseumStatus.DONATED }
	val totalShown get() = total ?: entries.count { it.status != MuseumStatus.UNKNOWN }
}

object MuseumParser {
	private val I = setOf(RegexOption.IGNORE_CASE)

	val KNOWN_CATEGORIES = listOf("Weapons", "Armor Sets", "Armour Sets", "Rarities", "Special Items")
	private val CAT_ALT = "Weapons|Armou?r(?:\\s+Sets)?|Rarities|Special\\s+Items"
	private val PAGE = "(?:\\s*[(\\[]\\s*(\\d+)\\s*/\\s*(\\d+)\\s*[)\\]])?"
	val OVERVIEW_TITLE = Regex("^(?:(?:Your|[\\w ]{1,20}?'s?)\\s+)?Museum$", I)

	private val PREFIX_PAGE = "(?:[(\\[]\\s*(\\d+)\\s*/\\s*(\\d+)\\s*[)\\]]\\s*)?"
	val SUB_TITLE = Regex("^$PREFIX_PAGE(?:(?:Your|[\\w ]{1,20}?'s?)\\s+)?Museum\\s*[^\\w\\s(\\[]+\\s*(\\w.*?)$PAGE$", I)
	val BARE_CATEGORY_TITLE = Regex("^($CAT_ALT)$PAGE$", I)
	val ANY_TITLE = Regex("${OVERVIEW_TITLE.pattern}|${SUB_TITLE.pattern}|${BARE_CATEGORY_TITLE.pattern}", I)

	fun parseTitle(title: String): Triple<String?, Int?, Int?>? {
		val t = MenuText.clean(title)
		if (OVERVIEW_TITLE.matches(t)) return Triple(null, null, null)
		SUB_TITLE.matchEntire(t)?.let { m ->
			val g = m.groupValues
			return Triple(g[3].trim(), g[1].toIntOrNull() ?: g[4].toIntOrNull(), g[2].toIntOrNull() ?: g[5].toIntOrNull())
		}
		val m = BARE_CATEGORY_TITLE.matchEntire(t) ?: return null
		return Triple(m.groupValues[1].trim(), m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
	}

	private val RATIO = Regex("(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)")
	private val DONATED_RATIO = Regex("(?:items?\\s+)?(?:donated|progress|collected|unlocked)\\s*[:=]?\\s*(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)", I)
	private val NAV_NEXT = Regex("^next(?:\\s+page)?\\b|^→|^»", I)
	private val NAV_PREV = Regex("^prev(?:ious)?(?:\\s+page)?\\b|^←|^«", I)
	private val NAV_BACK = Regex("^(?:go\\s+)?back\\b", I)
	private val IGNORED = Regex("^(?:close|museum\\s+(?:rewards?|appraisal(?:\\s+services?)?|browser|npc)|edit\\s+npc|sort\\b|filter\\b|search\\b|view\\b.*museum|museum\\s+(?:value|worth)|armor\\s+stand|display|.*\\bguide)$|^[\\s-]*$", I)

	private val OVERVIEW_ANCHOR = Regex("^(?:$CAT_ALT|Combat|Farming|Mining|Fishing|Foraging|Dungeoneering|Hunting|Special|Museum\\b.*)$", I)

	val STATS_NAME = Regex("^(?:your\\s+)?museum(?:\\s+(?:stats|statistics|overview|progress|info(?:rmation)?))?$", I)
	private val CLICK_PROMPT = Regex("^(?:click|right-click|left-click)\\b", I)

	fun statsOf(s: SlotText): MuseumStats {
		val l = s.cleanLore.map { it.trim() }
		val c = loreCount(s.cleanLore)
		val pct = l.firstNotNullOfOrNull { DONATED_PCT.find(it)?.let { _ -> Regex("([\\d.]+)\\s*%").find(it)?.groupValues?.get(1) } }
		val special = l.firstNotNullOfOrNull { Regex("^special items donated\\s*:\\s*(\\d[\\d,]*)$", I).matchEntire(it)?.groupValues?.get(1) }
		val top = topItems(l).firstOrNull()
		val lines = buildList {
			if (c?.second != null) add("Items Donated: ${c.first}/${c.second}" + (pct?.let { " ($it%)" } ?: ""))
			special?.let { add("Special Items: $it") }
			top?.let { add("Top: ${it.first} (${it.second})") }
			if (isEmpty()) addAll(l.filter { it.isNotBlank() && !CLICK_PROMPT.containsMatchIn(it) })
		}
		return MuseumStats(s.slot, lines, c?.first, c?.second)
	}

	val APPRAISAL = Regex("^museum\\s+appraisal", I)
	private val COINS_LINE = Regex("^[\\d,]+\\s+coins$", I)
	private val RANK = Regex("#\\s*([\\d,]+)")

	fun topItems(lines: List<String>): List<Pair<String, String>> {
		val out = mutableListOf<Pair<String, String>>()
		for (i in lines.indices) {
			val m = Regex("^\\d+\\.\\s+(.+)$").matchEntire(lines[i].trim()) ?: continue
			val v = (i + 1..minOf(i + 2, lines.size - 1)).map { lines[it].trim() }.firstOrNull { COINS_LINE.matches(it) } ?: continue
			out += m.groupValues[1] to v
		}
		return out
	}

	fun withAppraisal(st: MuseumStats, a: SlotText): MuseumStats {
		val l = a.cleanLore.map { it.trim() }
		val i = l.indexOfFirst { it.equals("Your Museum Value", true) }
		val value = l.getOrNull(i + 1)?.takeIf { i >= 0 && COINS_LINE.matches(it) }
		val rank = l.firstNotNullOfOrNull { RANK.find(it)?.groupValues?.get(1) }
		val extra = listOfNotNull(value?.let { "Value: $it" }, rank?.let { "Rank #$it" })
		return st.copy(lines = st.lines.filterNot { it.startsWith("Value:") } + extra)
	}

	fun packStatsLines(lines: List<String>, maxW: Int, width: (String) -> Int): List<String> {
		val rows = mutableListOf<String>()
		var cur = ""
		for (l in lines) {
			val cand = if (cur.isEmpty()) l else "$cur · $l"
			if (cur.isNotEmpty() && width(cand) > maxW) { rows += cur; cur = l } else cur = cand
		}
		if (cur.isNotEmpty()) rows += cur
		return rows
	}

	private val OVERVIEW_IGNORED = Regex("^close$|^[\\s-]*$", I)
	private val LOCKED = Regex("\\blocked\\b|not\\s+(?:yet\\s+)?unlocked", I)

	private val MISSING_REAL = Regex("click\\s+on\\s+(?:this\\s+item|an\\s+armor\\s+piece)\\s+in\\s+your|add\\s+it\\s+to\\s+your\\s+museum|to\\s+donate\\s+the\\s+full\\s+set", I)

	private val CREDITED = Regex("been\\s+credited\\s+with\\s+the\\s+progress", I)
	private val MISSING = Regex("not\\s+(?:yet\\s+)?donated|missing|click\\s+to\\s+donate|haven'?t\\s+donated|not\\s+in\\s+(?:your\\s+)?museum|✖|✗|❌", I)
	private val DONATED = Regex("(?<!not\\s)(?<!not yet\\s)\\bdonated\\b|in\\s+your\\s+museum|✔|✓|already\\s+donated", I)

	fun status(name: String, lore: List<String>): MuseumStatus {
		val n = MenuText.clean(name)
		val all = (listOf(n) + lore.map(MenuText::clean)).joinToString("\n")
		return when {
			n.isBlank() || Regex("^\\?+$").matches(n) || LOCKED.containsMatchIn(all) -> MuseumStatus.LOCKED
			CREDITED.containsMatchIn(all) -> MuseumStatus.DONATED
			MISSING_REAL.containsMatchIn(all) || MISSING.containsMatchIn(all) -> MuseumStatus.MISSING
			DONATED.containsMatchIn(all) -> MuseumStatus.DONATED
			else -> MuseumStatus.UNKNOWN
		}
	}

	private val DONATED_PCT = Regex("donated\\s*:\\s*[\\d.]+\\s*%\\s*$", I)
	private val ONLY_RATIO = Regex("^(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)$")
	private val DONATED_COUNT = Regex("^(?:items\\s+)?donated\\s*:\\s*(\\d[\\d,]*)\\s*$", I)
	private val MILESTONE = Regex("^milestone\\s*:\\s*(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)$", I)
	private fun num(s: String) = s.replace(",", "").toIntOrNull()

	fun loreCount(lore: List<String>): Pair<Int, Int?>? {
		val l = lore.map { MenuText.clean(it) }
		for (i in l.indices) if (DONATED_PCT.containsMatchIn(l[i])) {
			ONLY_RATIO.matchEntire(l.getOrNull(i + 1)?.trim() ?: "")?.let { m -> val a = num(m.groupValues[1]); val b = num(m.groupValues[2]); if (a != null && b != null) return a to b }
		}
		for (x in l) DONATED_COUNT.matchEntire(x.trim())?.let { m -> num(m.groupValues[1])?.let { return it to null } }
		for (x in l) MILESTONE.matchEntire(x.trim())?.let { m -> val a = num(m.groupValues[1]); val b = num(m.groupValues[2]); if (a != null && b != null) return a to b }
		ratio(l, strict = true)?.let { return it.first to it.second }
		return null
	}

	private fun ratio(lines: List<String>, strict: Boolean): Pair<Int, Int>? {
		for (l in lines) (DONATED_RATIO.find(l) ?: if (!strict) RATIO.find(l) else null)?.let { m ->
			val a = m.groupValues[1].replace(",", "").toIntOrNull(); val b = m.groupValues[2].replace(",", "").toIntOrNull()
			if (a != null && b != null && b > 0 && a <= b) return a to b
		}
		return null
	}

	fun dump(title: String, containerId: Int, slots: List<SlotText>) = buildString {
		append("[ApexMuseumDump] id=").append(containerId).append(" title=").append(MenuText.clean(title).map { if (it.code < 128) it.toString() else "\\u%04X".format(it.code) }.joinToString(""))
		for (t in slots) append("\n[ApexMuseumDump]   slot=").append(t.slot).append(" name=").append(t.name.replace("§", "&")).append(" lore=").append(t.lore.map { it.replace("§", "&") })
	}

	fun isGridSlot(slot: Int) = slot in 10..43 && slot % 9 in 1..7
	private val EXTRA_DETAILS = Regex("^extra\\s+details$", I)
	private val SORT = Regex("^sort$", I)
	private val TOGGLED = Regex("^toggled\\s*:\\s*(on|off)\\b", I)
	private val REMOVE = Regex("click\\s+to\\s+remove", I)
	private val RARITY = Regex("^(?:VERY\\s+SPECIAL|SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|ULTIMATE|ADMIN)(?:\\s+[A-Z ]+)?$")

	fun sortControl(s: SlotText): MuseumSortControl {
		val opts = mutableListOf<String>(); var sel: String? = null
		for (raw in s.cleanLore) {
			val t = raw.trim()
			if (t.isEmpty() || CLICK_PROMPT.containsMatchIn(t)) continue
			val marked = Regex("^[▶►➤>→»]\\s*").find(t)
			val name = if (marked != null) t.substring(marked.range.last + 1).trim() else t
			opts += name; if (marked != null) sel = name
		}
		return MuseumSortControl(s.slot, opts, sel)
	}

	fun details(lore: List<String>): MuseumItemDetails? {
		val l = lore.map { MenuText.clean(it).trim() }
		fun after(key: String) = l.firstNotNullOfOrNull { Regex("^${Regex.escape(key)}\\s*:\\s*(.+)$", I).matchEntire(it)?.groupValues?.get(1)?.trim() }
		val d = MuseumItemDetails(
			rarity = l.firstOrNull { RARITY.matches(it) },
			donated = after("Donated"), created = after("Created"),
			cleanValue = after("Clean Value"), value = after("Value"),
			atriumSlot = l.firstNotNullOfOrNull { Regex("^atrium\\s+slot\\s*#\\s*(\\d+)$", I).matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() })
		return d.takeIf { it.rarity != null || it.donated != null || it.value != null || it.cleanValue != null || it.atriumSlot != null }
	}

	private fun isCategoryName(n: String) = Regex("^(?:$CAT_ALT)$", I).matches(n)

	fun parse(title: String, slots: List<SlotText>): MuseumModel? {
		val (cat, page, pages) = parseTitle(title) ?: return null
		var prev: Int? = null; var next: Int? = null; var back: Int? = null
		val categories = mutableListOf<MuseumCategory>()
		val entries = mutableListOf<MuseumEntry>()
		var summary: Pair<Int, Int>? = null
		var stats: MuseumStats? = null
		var appraisal: SlotText? = null
		var extraSlot: Int? = null; var extraOn: Boolean? = null; var sortCtl: MuseumSortControl? = null; var countOnly: Int? = null
		for (s in slots) {
			val n = s.cleanName; val lore = s.cleanLore
			if (STATS_NAME.matches(n)) { if (stats == null) stats = statsOf(s); continue }
			if (cat != null && EXTRA_DETAILS.matches(n)) { extraSlot = s.slot; extraOn = lore.firstNotNullOfOrNull { TOGGLED.find(it.trim())?.groupValues?.get(1)?.equals("on", true) }; continue }
			if (cat != null && SORT.matches(n)) { sortCtl = sortControl(s); continue }

			val gridItem = cat != null && isGridSlot(s.slot) && (lore.any { it.isNotBlank() } || n.isNotBlank())
			when {
				NAV_NEXT.containsMatchIn(n) -> { if (next == null) next = s.slot; continue }
				NAV_PREV.containsMatchIn(n) -> { if (prev == null) prev = s.slot; continue }
				NAV_BACK.containsMatchIn(n) -> { if (back == null) back = s.slot; continue }
				cat == null && OVERVIEW_IGNORED.containsMatchIn(n) -> continue
				cat != null && !gridItem && IGNORED.containsMatchIn(n) -> continue
			}
			if (cat == null) {
				val r = loreCount(lore) ?: ratio(lore, strict = !isCategoryName(n))?.let { it.first to it.second }

				if (APPRAISAL.containsMatchIn(n)) { appraisal = s; continue }
				categories += MuseumCategory(n, s.slot, r?.first, r?.second)
			} else {

				if (!isGridSlot(s.slot)) {
					if (s.slot == 4 || summary == null && countOnly == null) loreCount(s.lore)?.let { c ->
						val tot = c.second; if (tot != null) summary = c.first to tot else countOnly = c.first
					}
					continue
				}
				val st = status(n, s.lore)

				entries += MuseumEntry(s.slot, n.ifBlank { "Locked" }, st, details(lore), st != MuseumStatus.LOCKED && lore.any { REMOVE.containsMatchIn(it) })
			}
		}

		if (stats != null && appraisal != null) stats = withAppraisal(stats, appraisal)
		if (cat == null) return if (stats == null && categories.none { OVERVIEW_ANCHOR.containsMatchIn(it.name) || it.donated != null }) null
			else if (categories.isEmpty()) null
			else MuseumModel(null, categories.sortedBy { it.slot }, emptyList(), prev, next, back, page, pages, null, null, stats)
		if (entries.isEmpty() && summary == null && countOnly == null) return null
		return MuseumModel(cat, emptyList(), entries, prev, next, back, page, pages, summary?.first, summary?.second, stats,
			extraSlot, extraOn, sortCtl, countOnly)
	}

	private val STATUS_ORDER = mapOf(MuseumStatus.MISSING to 0, MuseumStatus.LOCKED to 1, MuseumStatus.UNKNOWN to 2, MuseumStatus.DONATED to 3)

	fun sort(entries: List<MuseumEntry>, by: MuseumSort): List<MuseumEntry> = when (by) {
		MuseumSort.SLOT -> entries.sortedBy { it.slot }
		MuseumSort.STATUS -> entries.sortedWith(compareBy<MuseumEntry> { STATUS_ORDER[it.status] }.thenBy { it.slot })
		MuseumSort.NAME -> entries.sortedWith(compareBy<MuseumEntry> { it.name.lowercase() }.thenBy { it.slot })
	}

	fun filter(entries: List<MuseumEntry>, f: MuseumFilter) = when (f) {
		MuseumFilter.ALL -> entries
		MuseumFilter.DONATED -> entries.filter { it.status == MuseumStatus.DONATED }
		MuseumFilter.MISSING -> entries.filter { it.status == MuseumStatus.MISSING || it.status == MuseumStatus.LOCKED }
	}

	fun sameCategory(a: String, b: String) = norm(a) == norm(b)
	private fun norm(s: String) = MenuText.clean(s).lowercase().replace("armour", "armor").replace(" ", "")
}

data class MuseumTabs(val main: List<MuseumCategory>, val special: MuseumCategory?, val more: List<MuseumCategory>) {
	companion object {
		const val PER_PAGE = 6
		private val HUNTING = Regex("^hunting$", RegexOption.IGNORE_CASE)
		private val SPECIAL = Regex("^special(?:\\s+items?)?$", RegexOption.IGNORE_CASE)
		fun of(categories: List<MuseumCategory>): MuseumTabs {

			val sorted = categories.filterNot { MuseumParser.APPRAISAL.containsMatchIn(MenuText.clean(it.name)) }.sortedBy { it.slot }
			val i = sorted.indexOfFirst { SPECIAL.matches(MenuText.clean(it.name)) }
			if (i < 0) return MuseumTabs(sorted, null, emptyList())

			val before = sorted.take(i).toMutableList()
			val h = before.indexOfFirst { HUNTING.matches(MenuText.clean(it.name)) }
			before.add(if (h >= 0) h + 1 else before.size, sorted[i])
			return MuseumTabs(before, sorted[i], sorted.drop(i + 1))
		}
	}
	val pages get() = maxOf(1, (main.size + PER_PAGE - 1) / PER_PAGE)

	fun carousel(page: Int): List<MuseumCategory> = main.drop(Math.floorMod(page, pages) * PER_PAGE).take(PER_PAGE)

	fun pageOf(name: String): Int? = main.indexOfFirst { MuseumParser.sameCategory(it.name, name) }.takeIf { it >= 0 }?.let { it / PER_PAGE }
}

object MuseumMemory {
	var categories: List<MuseumCategory> = emptyList()
	var seenAtMs = 0L
	fun remember(m: MuseumModel, now: Long) { if (m.isOverview) { categories = m.categories; seenAtMs = now } }
}

class MuseumRemoveGuard {
	var pending: MuseumEntry? = null; private set

	fun click(e: MuseumEntry): Int? { if (!e.removable) return e.slot; pending = e; return null }
	fun prompt() = pending?.let { "Remove ${it.name} from museum?" }

	fun confirm(): Int? = pending?.slot.also { pending = null }
	fun cancel() { pending = null }

	companion object {

		fun isProtected(m: MuseumModel, slot: Int) = m.entries.any { it.slot == slot && (it.removable || it.status == MuseumStatus.LOCKED) } ||
			(m.category?.let { MenuText.clean(it).equals("Special Items", true) } == true && MuseumParser.isGridSlot(slot))
	}
}

object MuseumBottomRow {
	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)
	fun rects(x: Int, y: Int, width: Int, n: Int, h: Int = 16, gap: Int = 3): List<Rect> {
		if (n <= 0) return emptyList()
		val bw = (width - (n - 1) * gap) / n
		return List(n) { Rect(x + it * (bw + gap), y, bw, h) }
	}

	fun fits(labels: List<String>, rects: List<Rect>, width: (String) -> Int, scale: Float = 1f, pad: Int = 4) =
		labels.indices.all { width(labels[it]) * scale <= rects[it].w - pad }

	data class Layout(val rects: List<Rect>, val height: Int)
	private val MILESTONES = Regex("milestone", RegexOption.IGNORE_CASE)

	fun layout(x: Int, bottom: Int, width: Int, labels: List<String>, h: Int = 16, gap: Int = 3): Layout {
		val mi = labels.indexOfFirst { MILESTONES.containsMatchIn(it) }
		if (mi < 0) return Layout(rects(x, bottom - h, width, labels.size, h, gap), if (labels.isEmpty()) 0 else h)
		val others = labels.indices.filter { it != mi }
		val row = rects(x, bottom - 2 * h - gap, width, others.size, h, gap)
		val out = arrayOfNulls<Rect>(labels.size)
		out[mi] = Rect(x, bottom - h, width, h)
		others.forEachIndexed { k, i -> out[i] = row[k] }
		return Layout(out.map { it!! }, if (others.isEmpty()) h else 2 * h + gap)
	}

	fun scaleToFit(labels: List<String>, rects: List<Rect>, width: (String) -> Int, pad: Int = 4): Float =
		labels.indices.minOfOrNull { i -> ((rects[i].w - pad).toFloat() / maxOf(1, width(labels[i]))).coerceAtMost(1f) } ?: 1f
}

object MuseumSearchIcon {
	const val TOOLTIP = "Museum Search"
	const val SIZE = 14
	private val NAME = Regex("^Museum\\s+Search$", RegexOption.IGNORE_CASE)
	fun isSearch(c: MuseumCategory) = NAME.matches(c.name.trim())

	fun split(more: List<MuseumCategory>): Pair<MuseumCategory?, List<MuseumCategory>> =
		more.firstOrNull(::isSearch).let { s -> s to more.filter { it !== s } }

	data class Header(val icon: MuseumBottomRow.Rect, val backX: Int)

	fun titleMaxW(x: Int, pad: Int, vanillaX: Int, y: Int, hasSearch: Boolean, hasBack: Boolean, oldBackX: Int, gap: Int = 4): Int {
		val h = header(vanillaX, y)
		val left = when { hasBack -> if (hasSearch) h.backX else oldBackX; hasSearch -> h.icon.x; else -> vanillaX }
		return maxOf(0, left - gap - (x + pad))
	}
	fun header(vanillaX: Int, y: Int, gap: Int = 3, backW: Int = 44): Header {
		val icon = MuseumBottomRow.Rect(vanillaX - gap - SIZE, y + 3, SIZE, SIZE)
		return Header(icon, icon.x - gap - backW)
	}
}
