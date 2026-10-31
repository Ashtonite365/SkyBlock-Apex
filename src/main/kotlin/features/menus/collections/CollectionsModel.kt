// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.collections

import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

data class NavButton(val slot: Int, val name: String, val lore: List<String>)

enum class TileState { MAXED, UNLOCKED, IN_PROGRESS, LOCKED, UNKNOWN }

data class CollTile(
	val slot: Int,
	val name: String,

	val base: String,
	val tier: Int?,
	val state: TileState,
	val progress: LoreProgress?,
	val total: Double?,

	val details: List<String>,
	val lore: List<String>,
)

data class CollSummary(val name: String, val unlocked: LoreProgress?, val extra: List<String>)

sealed interface CollPage {
	val title: String
	val header: CollSummary?
	val nav: List<NavButton>
	val page: Pair<Int, Int>?

	data class Main(override val title: String, override val header: CollSummary?, val categories: List<CollTile>,
	                override val nav: List<NavButton>, override val page: Pair<Int, Int>? = null) : CollPage
	data class Category(override val title: String, val category: String, override val header: CollSummary?,
	                    val items: List<CollTile>, override val nav: List<NavButton>, override val page: Pair<Int, Int>?) : CollPage
	data class Item(override val title: String, val item: String, override val header: CollSummary?, val tiers: List<CollTile>,
	                override val nav: List<NavButton>, override val page: Pair<Int, Int>?,

	                val headSlot: Int? = null) : CollPage {
		val unlockedTiers get() = tiers.count { it.state == TileState.UNLOCKED || it.state == TileState.MAXED }

		val maxTier get() = tiers.maxOfOrNull { it.tier ?: 0 } ?: 0
		val maxed get() = tiers.isNotEmpty() && tiers.all { it.state == TileState.UNLOCKED || it.state == TileState.MAXED }

		val next get() = tiers.sortedBy { it.tier ?: 0 }.firstOrNull { it.state != TileState.UNLOCKED && it.state != TileState.MAXED }
	}
	data class Minions(override val title: String, override val header: CollSummary?, val minions: List<MinionTile>,
	                   override val nav: List<NavButton>, override val page: Pair<Int, Int>?,
	                   val crafted: Pair<Int, Int>?, val limit: Pair<Int, Int>?) : CollPage
}

data class MinionTile(val slot: Int, val name: String, val crafted: Set<Int>, val maxTier: Int) {
	val none get() = crafted.isEmpty()
	val complete get() = maxTier > 0 && crafted.size == maxTier
}

object CollectionsParser {

	val MAIN = Regex("^Collections?$", RegexOption.IGNORE_CASE)

	val PAGES = MenuTitles.anyOf(
		Regex("^(?:\\(\\d+/\\d+\\)\\s*)?[^➜]+\\sCollections(?:\\s*\\(\\d+/\\d+\\))?$", RegexOption.IGNORE_CASE),
		Regex("^(?:\\(\\d+/\\d+\\)\\s*)?Crafted Minions$", RegexOption.IGNORE_CASE),
	)

	val ITEM_TITLES = MenuTitles.anyOf(
		Regex("^(?:\\(\\d+/\\d+\\)\\s*)?[^➜]+\\sCollection(?:\\s*\\(\\d+/\\d+\\))?$", RegexOption.IGNORE_CASE),

		MenuTitles.cutTail("[^➜]+", "Collection"),
	)

	fun isItemPage(slots: List<SlotText>): Boolean {
		val back = slots.any { it.cleanName.equals("Go Back", true) && it.cleanLore.any { l -> Regex("^To\\s+(?:.+\\s)?Collections$", RegexOption.IGNORE_CASE).matches(l.trim()) } }
		val head = slots.any { it.slot < 9 && it.cleanLore.joinToString(" ").let { l -> l.contains("View all your", true) && l.contains("Collection", true) } }
		return back || head
	}

	fun matchesItemPage(title: String, slots: List<SlotText>) = ITEM_TITLES.containsMatchIn(MenuText.clean(title)) && isItemPage(slots)

	fun stripPage(title: String) = MenuText.clean(title).replace(Regex("^\\(\\d+/\\d+\\)\\s*|\\s*\\(\\d+/\\d+\\)$"), "")

	private fun nav(slots: List<SlotText>) = slots.filter { LoreParse.isNav(it.name) || it.cleanName.startsWith("Show ", true) }
		.map { NavButton(it.slot, it.cleanName, it.cleanLore) }

	private fun real(slots: List<SlotText>, chestSize: Int) = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }

	private fun summary(s: SlotText?) = s?.let { h ->
		CollSummary(h.cleanName, LoreParse.progress(h.lore).firstOrNull { it.label.contains("Unlocked", true) || it.label.contains("Maxed", true) }
			?: LoreParse.progress(h.lore).firstOrNull(),
			h.cleanLore.filter { l -> Regex("^[A-Za-z][\\w '-]*:\\s*\\S").containsMatchIn(l) && !l.contains("accessible", true) })
	}

	private fun header(slots: List<SlotText>, name: String) =
		slots.firstOrNull { it.cleanName.equals(name, true) } ?: slots.minByOrNull { it.slot }?.takeIf { it.slot < 9 }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): CollPage? {
		val t = MenuText.clean(title)
		val chest = real(slots, chestSize)
		if (chest.isEmpty()) return null
		val nav = nav(chest)
		if (MAIN.matches(t)) return parseMain(t, chest, nav)
		if (!PAGES.containsMatchIn(t) && !matchesItemPage(t, chest)) return null
		val bare = stripPage(t)
		val page = LoreParse.pageOf(t)
		if (bare.equals("Crafted Minions", true)) return parseMinions(t, chest, nav, page)
		val head = header(chest, bare)
		val body = chest.filter { it !== head && nav.none { n -> n.slot == it.slot } }
		val tiles = body.map(::tile)

		val tiered = tiles.filter { it.tier != null }.groupBy { it.base.lowercase() }.maxByOrNull { it.value.size }
		val itemName = bare.replace(Regex("\\s+Collections?$", RegexOption.IGNORE_CASE), "").trim()

		val singular = Regex("\\sCollection$", RegexOption.IGNORE_CASE).containsMatchIn(bare)
		if (tiered != null && (tiered.key == itemName.lowercase() && (singular || tiered.value.size >= 2) || singular && tiered.value.size >= 2))
			return CollPage.Item(t, itemName, summary(head), tierStates(tiered.value.sortedBy { it.tier }), nav, page, head?.slot)

		if (body.none { s -> s.cleanName.contains("collection", true) || s.cleanLore.any { it.contains("collection", true) } }) return null
		return CollPage.Category(t, itemName, summary(head), tiles, nav, page ?: paging(nav))
	}

	fun paging(nav: List<NavButton>): Pair<Int, Int>? {
		fun target(n: String) = nav.firstOrNull { it.name.equals(n, true) }?.let { b ->
			b.lore.firstNotNullOfOrNull { Regex("^Page\\s+(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toInt() } ?: -1 }
		val next = target("Next Page"); val prev = target("Previous Page")
		return when {
			prev != null && prev > 0 -> (prev + 1) to (if (next == null) prev + 1 else 0)
			next != null && next > 0 -> (next - 1) to 0
			next != null -> 1 to 0
			prev != null -> 2 to 0
			else -> null
		}
	}

	fun prevButton(p: CollPage) = p.nav.firstOrNull { it.name.equals("Previous Page", true) }
	fun nextButton(p: CollPage) = p.nav.firstOrNull { it.name.equals("Next Page", true) }

	fun withPaneStates(p: CollPage.Item, itemPath: (Int) -> String?): CollPage.Item = p.copy(tiers = p.tiers.map { t ->
		val c = itemPath(t.slot)?.let(::stateFromItem)
		if (c != null && t.state == TileState.UNKNOWN) t.copy(state = c) else t
	})

	private fun parseMain(t: String, chest: List<SlotText>, nav: List<NavButton>): CollPage.Main? {
		val head = header(chest, "Collections")
		val cats = chest.filter { it !== head && nav.none { n -> n.slot == it.slot } }
			.filter { it.cleanName.endsWith("Collections", true) || LoreParse.progress(it.lore, "").let { p -> p != null && p.label.contains("Collections", true) } }
			.map(::tile)
		if (cats.isEmpty()) return null
		val navPlus = nav + chest.filter { s -> s !== head && cats.none { it.slot == s.slot } && nav.none { it.slot == s.slot } }
			.map { NavButton(it.slot, it.cleanName, it.cleanLore) }
		return CollPage.Main(t, summary(head), cats, navPlus)
	}

	private val TIER = Regex("^([✔✖✓✗xX])\\s*Tier\\s+([IVXLC]+|\\d+)$")
	private fun fraction(lore: List<String>, label: String) = lore.map(MenuText::clean).firstOrNull { it.startsWith("$label:", true) }
		?.let(LoreParse::fraction)?.let { it.first.toInt() to it.second.toInt() }

	private fun parseMinions(t: String, chest: List<SlotText>, nav: List<NavButton>, page: Pair<Int, Int>?): CollPage.Minions {
		val head = header(chest, "Crafted Minions")
		val tiles = chest.filter { it !== head && nav.none { n -> n.slot == it.slot } && it.cleanName.endsWith("Minion", true) }.map { s ->
			val tiers = s.cleanLore.mapNotNull { TIER.matchEntire(it) }.map { m ->
				(m.groupValues[1] in setOf("✔", "✓")) to (m.groupValues[2].toIntOrNull() ?: LoreParse.roman(m.groupValues[2]) ?: 0)
			}
			MinionTile(s.slot, s.cleanName, tiers.filter { it.first }.map { it.second }.toSet(), tiers.maxOfOrNull { it.second } ?: 0)
		}
		val hl = head?.lore.orEmpty()
		return CollPage.Minions(t, summary(head), tiles, nav, page, fraction(hl, "Crafted minions"), fraction(hl, "Minion limit"))
	}

	private val LOCKED_HINTS = listOf("find this item", "haven't found", "not unlocked", "locked", "you haven't", "kill this boss once", "once to view")

	fun tile(s: SlotText, colour: TileState? = null): CollTile {
		val lore = s.cleanLore
		val (base, tier) = LoreParse.splitTier(s.cleanName)
		val prog = LoreParse.progress(s.lore).firstOrNull()
		val total = LoreParse.value(s.lore, "Total Collected") ?: LoreParse.value(s.lore, "Total collected")
		val low = lore.joinToString("\n").lowercase()
		val state = colour ?: when {
			"maxed" in low || prog == null && tier != null && total != null && "progress" !in low -> TileState.MAXED
			LOCKED_HINTS.any { it in low } && !low.contains("unlocked") -> TileState.LOCKED
			prog?.done == true || "unlocked!" in low || "claimed" in low -> TileState.UNLOCKED
			prog != null -> TileState.IN_PROGRESS
			else -> TileState.UNKNOWN
		}
		val details = LoreParse.block(s.lore, "Rewards").ifEmpty { LoreParse.block(s.lore, "Co-op Contributions") }
		return CollTile(s.slot, s.cleanName, base, tier, state, prog, total, details, lore)
	}

	fun tierStates(sorted: List<CollTile>): List<CollTile> {
		var seenOpen = false
		return sorted.map { t ->
			val done = t.state == TileState.UNLOCKED || t.state == TileState.MAXED || t.progress?.done == true
			when {
				done -> if (t.state == TileState.MAXED) t else t.copy(state = TileState.UNLOCKED)
				!seenOpen -> { seenOpen = true; t.copy(state = TileState.IN_PROGRESS) }
				else -> t.copy(state = TileState.LOCKED)
			}
		}
	}

	fun stateFromItem(itemPath: String): TileState? = if ("stained_glass" !in itemPath) null else when {
		"lime" in itemPath || "green" in itemPath -> TileState.UNLOCKED
		"yellow" in itemPath -> TileState.IN_PROGRESS
		"red" in itemPath -> TileState.LOCKED
		else -> null
	}

	fun groups(tiles: List<CollTile>): List<Pair<String, List<CollTile>>> = listOf(
		"In progress" to tiles.filter { it.state == TileState.IN_PROGRESS || it.state == TileState.UNLOCKED || it.state == TileState.UNKNOWN },
		"Maxed" to tiles.filter { it.state == TileState.MAXED },
		"Locked" to tiles.filter { it.state == TileState.LOCKED },
	).filter { it.second.isNotEmpty() }
}
