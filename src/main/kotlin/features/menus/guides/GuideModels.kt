// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.guides

import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.collections.LoreParse
import dev.ashtonite365.apex.features.menus.collections.NavButton
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

internal fun dashList(lore: List<String>, vararg header: String): List<String> {
	val clean = lore.map(MenuText::clean)
	val i = clean.indexOfFirst { l -> header.any { h -> l.equals("$h:", true) || l.equals(h, true) } }
	if (i < 0) return emptyList()
	return clean.drop(i + 1).takeWhile { it.isNotBlank() }.map { it.removePrefix("-").trimStart { c -> !c.isLetterOrDigit() && c != '(' }.trim() }.filter { it.isNotEmpty() }
}

private fun nav(chest: List<SlotText>) = chest.filter { LoreParse.isNav(it.name) }.map { NavButton(it.slot, it.cleanName, it.cleanLore) }

data class BlockCategory(val slot: Int, val name: String, val affectedBy: List<String>)
data class BlockEntry(val slot: Int, val name: String, val kind: String?, val breakingPower: Int?, val strength: Int?)

sealed interface BlockGuidePage {
	val nav: List<NavButton>
	data class Main(val categories: List<BlockCategory>, override val nav: List<NavButton>) : BlockGuidePage
	data class Category(val name: String, val affectedBy: List<String>, val blocks: List<BlockEntry>, override val nav: List<NavButton>) : BlockGuidePage {

		fun groups(): List<Pair<String, List<BlockEntry>>> = blocks.groupBy { it.breakingPower }
			.toSortedMap(compareBy<Int?> { it ?: -1 })
			.map { (bp, bs) -> (if (bp == null) "No Breaking Power listed" else "Breaking Power $bp") to bs }
	}
}

object BlockGuideParser {
	const val MAIN_NAME = "Handy Block Guide"

	val PAGES = listOf("Ores", "Blocks", "Dwarven Metals", "Gemstones", "Miscellaneous")
	val TITLE = Regex("^(?:⚑\\s*)?Handy Block Guide$|^(?:${PAGES.joinToString("|") { Regex.escape(it) }})$", RegexOption.IGNORE_CASE)
	private const val CLASS = "Block Classification"

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): BlockGuidePage? {
		val t = MenuText.clean(title).removePrefix("⚑").trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val nav = nav(chest)
		val body = chest.filter { s -> nav.none { it.slot == s.slot } }
		if (t.equals(MAIN_NAME, true)) {
			val head = body.firstOrNull { it.cleanName.removePrefix("⚑").trim().equals(MAIN_NAME, true) } ?: return null
			val cats = body.filter { it !== head && it.cleanLore.firstOrNull() == CLASS }
				.map { BlockCategory(it.slot, it.cleanName, dashList(it.lore, "These blocks are affected by")) }
			return if (cats.isEmpty()) null else BlockGuidePage.Main(cats, nav)
		}

		val head = body.firstOrNull { it.cleanName.equals(t, true) && it.cleanLore.firstOrNull() == CLASS } ?: return null
		val blocks = body.filter { it !== head }.map { s ->
			BlockEntry(s.slot, s.cleanName, s.cleanLore.firstOrNull()?.takeIf { it.isNotBlank() },
				LoreParse.value(s.lore, "Breaking Power")?.toInt(), LoreParse.value(s.lore, "Block Strength")?.toInt())
		}
		return BlockGuidePage.Category(head.cleanName, dashList(head.lore, "These blocks are affected by"), blocks, nav)
	}
}

data class GuideEnchant(
	val slot: Int, val name: String, val base: String, val maxLevel: Int?, val description: List<String>,
	val sources: List<String>, val appliedTo: List<String>, val requiredEnchanting: Int?, val conflicts: List<String>,
	val ultimate: Boolean,
)

data class EnchantGuidePage(
	val page: Pair<Int, Int>?, val enchants: List<GuideEnchant>, val nav: List<NavButton>,
	val searchSlot: Int?, val filterSlot: Int?, val filter: String?,
) {
	fun groups(): List<Pair<String, List<GuideEnchant>>> = listOf(
		"Enchantments" to enchants.filter { !it.ultimate },
		"Ultimate Enchantments" to enchants.filter { it.ultimate },
	).filter { it.second.isNotEmpty() }
}

object EnchantGuideParser {

	val TITLE = MenuTitles.anyOf(
		Regex("^(?:\\(\\d+/\\d+\\)\\s*)?(?:⚑\\s*)?Enchantments? Guide$", RegexOption.IGNORE_CASE),
		MenuTitles.endsWith("Enchantment Guide", prefix = "^\\(\\d+/\\d+\\)\\s*"),
	)
	private val SECTIONS = setOf("sources:", "source:", "applied to:", "requirements:", "requirement:", "conflicts:", "conflict:")

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): EnchantGuidePage? {
		val t = MenuText.clean(title)
		if (!TITLE.containsMatchIn(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val head = chest.firstOrNull { it.cleanName.contains("Enchantment Guide", true) } ?: return null
		val search = chest.firstOrNull { it.cleanName.equals("Search", true) }
		val filter = chest.firstOrNull { it.cleanName.equals("Filter", true) }
		val nav = chest.filter { LoreParse.isNav(it.name) && it !== search && it !== filter }.map { NavButton(it.slot, it.cleanName, it.cleanLore) }
		val known = setOfNotNull(head.slot, search?.slot, filter?.slot) + nav.map { it.slot }
		val enchants = chest.filter { it.slot !in known }.map(::enchant)
		return EnchantGuidePage(LoreParse.pageOf(t), enchants, nav, search?.slot, filter?.slot,
			filter?.cleanLore?.firstOrNull { it.startsWith("▶") }?.removePrefix("▶")?.trim())
	}

	fun enchant(s: SlotText): GuideEnchant {
		val lore = s.cleanLore
		val (base, lvl) = LoreParse.splitTier(s.cleanName)
		val desc = lore.takeWhile { it.isNotBlank() && it.lowercase() !in SECTIONS }
		val req = dashList(s.lore, "Requirements", "Requirement").firstNotNullOfOrNull {
			Regex("Enchanting Level (\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toInt()
		}
		return GuideEnchant(s.slot, s.cleanName, base, lvl, desc, dashList(s.lore, "Sources", "Source"),
			dashList(s.lore, "Applied To"), req, dashList(s.lore, "Conflicts", "Conflict"),
			lore.any { it.contains("Ultimate", true) && it.contains("Enchantment", true) || it.startsWith("You can only have 1 Ultimate", true) })
	}
}
