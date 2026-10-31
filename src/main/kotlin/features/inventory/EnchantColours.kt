// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.stripSectionCodes

object EnchantColours {

	const val DEFAULT_RULES = "[a-zA-Z\\- ]+:>:max:c:0|[a-zA-Z\\- ]+:=:max:6:0|[a-zA-Z\\- ]+:=:max-1:5:0"

	@Config
	object TConfig : ManagedConfig("enchant-colours", Category.INVENTORY) {
		val enabled by toggle("enabled") { false }
		val rules by string("rules") { DEFAULT_RULES }
	}

	private val styleCodes = listOf(1 to 'l', 2 to 'o', 4 to 'n', 8 to 'k', 16 to 'm')
	private const val COLOUR_CHARS = "0123456789abcdef"
	private const val MAX_HITS_PER_RULE = 5

	data class Rule(val pattern: Regex, val compareWith: Int, val formatting: String, val comparator: Char, val relativeToMax: Boolean = false) {
		fun levelMatches(level: Int, maxLevel: Int? = null): Boolean {
			val against = when {
				!relativeToMax -> compareWith
				maxLevel == null -> return false
				else -> maxLevel + compareWith
			}
			val order = level.compareTo(against)
			return when (comparator) {
				'>' -> order > 0
				'<' -> order < 0
				'=' -> order == 0
				else -> false
			}
		}
	}

	private fun tooltipPattern(namePattern: String): Regex =
		Regex("(§[b9]|§[b9l]§d§l)(?<name>$namePattern) (?<level>[0-9]+|[IVXLCDM]+)(?:(?:§9)?,|(?: §8(?:,?[0-9]+)*)?$)")

	private fun styleFor(colour: Char, flags: Int): String = buildString {
		append('§').append(colour)
		for ((bit, code) in styleCodes) if (flags and bit != 0) append('§').append(code)
	}

	private fun readLevel(text: String): Pair<Int, Boolean>? {
		val t = text.trim()
		if (!t.lowercase().startsWith("max")) return text.toIntOrNull()?.let { it to false }
		val offset = t.substring(3)
		return (if (offset.isEmpty()) 0 else offset.toIntOrNull() ?: return null) to true
	}

	fun parseRule(save: String): Rule? {
		val fields = save.split(":")
		if (fields.size < 4) return null
		val comparator = fields[1].singleOrNull()?.takeIf { it in "><=" } ?: return null
		val colour = fields[3].singleOrNull()?.takeIf { it in COLOUR_CHARS } ?: return null
		val (level, relative) = readLevel(fields[2]) ?: return null
		val pattern = try {
			tooltipPattern(fields[0])
		} catch (_: Exception) {
			return null
		}
		return Rule(pattern, level, styleFor(colour, fields.getOrNull(4)?.toIntOrNull() ?: 0), comparator, relative)
	}

	fun replaceLine(original: String, rules: List<Rule>, maxLevelOf: (String) -> Int? = { null }): String {
		var line = original
		val coloured = HashSet<String>()
		for (rule in rules) {
			var from = 0
			repeat(MAX_HITS_PER_RULE) {
				val hit = rule.pattern.find(line, from) ?: return@repeat
				val name = hit.groups["name"]!!.value
				val levelGroup = hit.groups["level"]!!
				val plainName = name.stripSectionCodes()
				val applies = !plainName.startsWith(" ") &&
					rule.levelMatches(EnchantPoolLogic.parseLevel(levelGroup.value), maxLevelOf(plainName)) && plainName !in coloured
				if (!applies) {
					from = hit.range.last + 1
				} else {
					coloured += plainName
					val upToLevel = line.substring(0, hit.range.first) + rule.formatting + name + " " + levelGroup.value
					line = upToLevel + line.substring(levelGroup.range.last + 1)
					from = upToLevel.length
				}
			}
		}
		return line
	}

	private var rulesFor: String? = null
	private var parsedRules: List<Rule> = emptyList()

	private var maxLevelsFor: Any? = null
	private var maxLevelByName: Map<String, Int> = emptyMap()

	private val recent = object : LinkedHashMap<String, String>(256, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1000
	}

	private fun currentRules(): List<Rule> {
		val setting = TConfig.rules
		if (setting != rulesFor) {
			rulesFor = setting
			parsedRules = setting.split("|").mapNotNull { parseRule(it.trim()) }
			recent.clear()
		}
		return parsedRules
	}

	private fun currentMaxLevels(): Map<String, Int> {
		val repo = dev.ashtonite365.apex.repo.RepoManager
		val levels = repo.enchantData.maxLevels
		if (maxLevelsFor !== levels) {
			maxLevelsFor = levels
			val ids = EnchantPoolLogic.namesToIds(repo.enchantedBookCache.byName.mapValues { it.value.skyblockId })
			maxLevelByName = buildMap { for ((name, id) in ids) levels[id]?.let { put(name, it) } }
			recent.clear()
		}
		return maxLevelByName
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.enabled) return
		val rules = currentRules().ifEmpty { return }
		val maxLevels = currentMaxLevels()
		event.lines.indices.forEach { i ->
			val legacy = event.lines[i].getLegacyFormatString()
			if (listOf("§9", "§b", "§d").none { it in legacy }) return@forEach
			val recoloured = recent.getOrPut(legacy) { replaceLine(legacy, rules) { maxLevels[it] } }
			if (recoloured != legacy) event.lines[i] = Component.literal(recoloured)
		}
	}
}
