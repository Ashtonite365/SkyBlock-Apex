package dev.ashtonite365.apex.features.inventory

object EnchantPoolLogic {

	fun candidateKeys(itemTypeName: String): List<String> {
		val base = itemTypeName.trim().uppercase().removePrefix("DUNGEON ").trim()
		return listOf(base, base.replace(" ", ""), base.replace(" ", "_")).distinct()
	}

	private fun squash(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

	fun poolKey(itemTypeName: String, keys: Collection<String>): String? {
		candidateKeys(itemTypeName).firstOrNull { it in keys }?.let { return it }
		val base = squash(itemTypeName.trim().uppercase().removePrefix("DUNGEON "))
		if (base.isEmpty()) return null
		keys.filter { squash(it).endsWith(base) }.singleOrNull()?.let { return it }
		return keys.filter { squash(it).isNotEmpty() && base.endsWith(squash(it)) }.maxByOrNull { squash(it).length }
	}

	fun maxLevels(itemIds: Collection<String>, knownEnchants: Set<String>, xpCostSizes: Map<String, Int> = emptyMap()): Map<String, Int> {
		val out = HashMap<String, Int>()
		for ((k, v) in xpCostSizes) if (v > 0) out[k.lowercase()] = v
		val fromItems = HashMap<String, Int>()
		for (id in itemIds) {
			val i = id.lastIndexOf(';'); if (i <= 0) continue
			val ench = id.substring(0, i).lowercase(); if (ench !in knownEnchants) continue
			val lvl = id.substring(i + 1).toIntOrNull() ?: continue
			fromItems[ench] = maxOf(fromItems[ench] ?: 0, lvl)
		}
		out.putAll(fromItems)
		return out
	}

	fun namesToIds(bookNames: Map<String, String>): Map<String, String> =
		bookNames.entries.mapNotNull { (name, id) ->
			val base = numeralLine.matchEntire(name.trim())?.groupValues?.get(1)?.trim() ?: name.trim()
			val i = id.lastIndexOf(';'); if (i <= 0) null else base to id.substring(0, i).lowercase()
		}.toMap()

	fun parseLevel(text: String): Int {
		text.toIntOrNull()?.let { return it }
		val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
		if (text.isEmpty() || text.any { it !in v }) return 0
		var total = 0
		for (i in text.indices) { val c = v[text[i]]!!; val n = text.getOrNull(i + 1)?.let { v[it] } ?: 0; total += if (c < n) -c else c }
		return total
	}

	fun poolFor(itemTypeName: String, available: Map<String, List<String>>): List<String>? =
		poolKey(itemTypeName, available.keys)?.let { available[it] }

	fun conflictsWith(a: String, b: String, pools: List<List<String>>): Boolean =
		dev.ashtonite365.apex.features.menus.enchant.EnchantConflicts.conflicts(a, b, pools)

	fun combinedLevel(a: Int, b: Int, maxLevel: Int?): Int {
		val r = if (a == b) a + 1 else maxOf(a, b)
		return if (maxLevel != null) minOf(r, maxOf(maxLevel, a, b)) else r
	}

	fun canCombine(
		left: Map<String, Int>, right: Map<String, Int>,
		applicable: List<String>?, pools: List<List<String>>,
	): Boolean {
		if (right.isEmpty()) return false
		if (applicable != null && right.keys.any { it !in applicable }) return false
		return right.keys.none { r -> left.keys.any { conflictsWith(r, it, pools) } }
	}

	fun conflictText(name: String, with: String) = "$name (conflicts with $with)"

	fun wrapJoined(items: List<String>, maxWidth: Int, width: (String) -> Int): List<String> {
		val out = mutableListOf<String>()
		var line = ""
		for (item in items) {
			val testLine = if (line.isEmpty()) item else "$line, $item"
			if (width(testLine) > maxWidth && line.isNotEmpty()) { out += line; line = item } else line = testLine
		}
		if (line.isNotEmpty()) out += line
		return out
	}

	private val numeralLine = Regex("^(.+?) ([IVXLCDM]+)$")
	private val colour = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun titleCase(id: String): String =
		id.split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

	fun roman(n: Int): String {
		var x = n; val sb = StringBuilder()
		for ((v, r) in listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"))
			while (x >= v) { sb.append(r); x -= v }
		return sb.toString()
	}

	fun enchantName(enchId: String, level: Int?, lore: List<String>?): String {
		val base = lore.orEmpty().asSequence()
			.map { it.replace(colour, "").trim() }
			.mapNotNull { numeralLine.matchEntire(it)?.groupValues?.get(1)?.trim() }
			.firstOrNull { it.isNotBlank() && !it.contains("Anvil", ignoreCase = true) }
			?: titleCase(enchId).ifBlank { enchId }
		return if (level == null || level <= 1) base else "$base ${roman(level)}"
	}
}
