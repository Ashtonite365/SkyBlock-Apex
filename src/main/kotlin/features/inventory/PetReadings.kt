package dev.ashtonite365.apex.features.inventory

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.titleCase

class PetRecord(
	val name: String,
	val rarity: Rarity,
	var level: Int,

	val maxLevel: Int,
	var xpInLevel: Double,
	var xpToNext: Double,
	var xpTotal: Double,
	var xpAtLevelStart: Double,
	val xpForMax: Double,
	var xpOverflow: Double,

	var heldItem: String,
	var icon: Lazy<KeptStack>,
	var fromMenu: Boolean,
) {
	fun isSamePetAs(other: PetRecord) = name == other.name && rarity == other.rarity

	fun absorb(newer: PetRecord) {
		val levelledUp = newer.level > level
		xpInLevel = newer.xpInLevel
		xpToNext = newer.xpToNext
		if (levelledUp) {
			level = newer.level
			xpTotal = newer.xpTotal
			xpAtLevelStart = newer.xpAtLevelStart
			xpOverflow = newer.xpOverflow
		} else {
			xpTotal = maxOf(xpTotal, newer.xpTotal)
			xpAtLevelStart = maxOf(xpAtLevelStart, newer.xpAtLevelStart)
			xpOverflow = maxOf(xpOverflow, newer.xpOverflow)
		}
		if (newer.heldItem != UNKNOWN_ITEM) heldItem = newer.heldItem
		fromMenu = false
	}

	fun advanceFromTab(tab: PetRecord) {
		if (!isSamePetAs(tab)) return
		when {
			tab.level > level -> {
				level = tab.level; xpInLevel = tab.xpInLevel; xpToNext = tab.xpToNext; xpTotal = tab.xpTotal
			}
			tab.xpInLevel > xpInLevel -> { xpInLevel = tab.xpInLevel; xpTotal = tab.xpTotal }
			tab.xpOverflow > xpOverflow -> xpOverflow = tab.xpOverflow
		}
		if (tab.heldItem != UNKNOWN_ITEM && tab.heldItem != heldItem) {
			heldItem = tab.heldItem
			icon = tab.icon
		}
	}

	companion object {
		const val UNKNOWN_ITEM = "Unknown"
		const val NO_ITEM = "None"
	}
}

object PetReadings {

	private const val RARITY_COLOUR_CODES = "fa956d"

	fun rarityOfColourCode(code: String): Rarity? =
		RARITY_COLOUR_CODES.indexOf(code.singleOrNull() ?: return null).takeIf { it >= 0 }?.let { Rarity.entries[it] }

	private enum class TabLine(pattern: String) {
		KAT(""" Kat:.*"""),
		PROGRESS(""" §e((?:\d{1,3}[,.]?)+\d*[kM]?)§6/§e((?:\d{1,3}[,.]?)+\d*[kM]?) XP §6\(\d+(?:.\d+)?%\)"""),
		MAXED(""" §bMAX LEVEL"""),
		OVERFLOW(""" §6\+§e((?:\d{1,3}[,.])+\d*[kM]?) XP"""),
		HELD(""" (§[fa956dbc4][\s\w]+)"""),
		TITLE(""" §7\[Lvl (\d{1,3})] §([fa956d])([\w\s]+)""");

		val regex = Regex(pattern)
	}

	private fun stillWanted(kind: TabLine, got: Map<TabLine, MatchResult>) = when (kind) {
		TabLine.PROGRESS, TabLine.MAXED -> TabLine.PROGRESS !in got && TabLine.MAXED !in got
		TabLine.OVERFLOW -> TabLine.PROGRESS !in got
		else -> kind !in got
	}

	fun fromTab(lines: List<Component>): PetRecord? {
		val got = HashMap<TabLine, MatchResult>()
		for (text in lines.asReversed().map { it.formattedString() }) {
			for (kind in TabLine.entries) {
				if (!stillWanted(kind, got)) continue
				val m = kind.regex.matchEntire(text) ?: continue
				got[kind] = m
				break
			}
		}
		val title = got[TabLine.TITLE] ?: return null
		val name = titleCase(title.groupValues[3])
		val level = title.groupValues[1].toInt()
		val progress = got[TabLine.PROGRESS]?.let { parseShortNumber(it.groupValues[1]) to parseShortNumber(it.groupValues[2]) }
		return fromLadder(name, rarityOfColourCode(title.groupValues[2])!!, level, capFromLadder = true,
			progress = { ladderXp ->
				when {
					progress != null -> progress
					TabLine.MAXED in got -> ladderXp(level) to 0.0
					else -> 0.0 to (ladderXp(level + 1) - ladderXp(level))
				}
			},
			overflow = got[TabLine.OVERFLOW]?.let { parseShortNumber(it.groupValues[1]) } ?: 0.0,
			heldItem = got[TabLine.HELD]?.groupValues?.get(1) ?: PetRecord.UNKNOWN_ITEM)
	}

	fun fromChat(name: String, colourCode: String, level: Int): PetRecord? =
		fromLadder(name, rarityOfColourCode(colourCode) ?: Rarity.COMMON, level, capFromLadder = false,
			progress = { ladderXp -> 0.0 to (ladderXp(level + 1) - ladderXp(level)) },
			overflow = 0.0, heldItem = PetRecord.UNKNOWN_ITEM)

	@OptIn(ExpensiveItemCacheApi::class)
	private fun fromLadder(
		name: String, rarity: Rarity, level: Int, capFromLadder: Boolean,
		progress: ((Int) -> Double) -> Pair<Double, Double>, overflow: Double, heldItem: String,
	): PetRecord? {
		val repoId = name.uppercase().replace(' ', '_')
		val ladder = ExpLadders.getExpLadder(repoId, rarity.coreRepoRarity ?: return null)
		val xpAt = { lvl: Int -> ladder.getPetExpForLevel(lvl).toDouble() }
		val cap = ExpLadders.maxLevelOverride(repoId) ?: 100
		val (inLevel, toNext) = progress(xpAt)
		val start = xpAt(level)
		val iconId = "$repoId;${rarity.ordinal}"
		return PetRecord(name, rarity, level, if (capFromLadder) cap else -1, inLevel, toNext, start + inLevel, start,
			xpAt(cap), overflow, heldItem, lazy { RepoManager.coreRepo.items.itemsSafe[iconId].asItemStack() }, fromMenu = false)
	}

	fun fromMenuItem(stack: KeptStack): PetRecord? {
		val info = stack.petBlob ?: return null
		val lv = info.level
		val held = info.heldItemId?.let { RepoManager.coreRepo.items.itemsSafe[it]?.displayName } ?: PetRecord.NO_ITEM
		val inLevel = lv.expInCurrentLevel.toDouble()
		val total = lv.expTotal.toDouble()
		val forMax = lv.expRequiredForMaxLevel.toDouble()
		return PetRecord(
			name = titleCase(info.species),
			rarity = Rarity.fromCoreRepo(info.rarity) ?: Rarity.COMMON,
			level = lv.currentLevel, maxLevel = lv.maxLevel,
			xpInLevel = inLevel, xpToNext = lv.expRequiredForNextLevel.toDouble(),
			xpTotal = total, xpAtLevelStart = total - inLevel, xpForMax = forMax,
			xpOverflow = (total - forMax).coerceAtLeast(0.0),
			heldItem = held, icon = lazy { stack }, fromMenu = true,
		)
	}
}
