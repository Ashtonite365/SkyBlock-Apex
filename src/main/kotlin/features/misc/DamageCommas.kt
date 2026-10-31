// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import java.util.WeakHashMap
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.getLegacyFormatString

object DamageCommas {

	enum class Style : net.minecraft.util.StringRepresentable {
		SHORT, COMMAS;
		override fun getSerializedName() = name.lowercase()
	}

	@Config
	object TConfig : ManagedConfig("damage-commas", Category.MISC) {
		val enabled by toggle("enabled") { false }
		val style by choice("style") { Style.SHORT }
	}

	fun format(n: Long, style: Style): String = if (style == Style.COMMAS) ApexFormatters.withThousands(n) else shortNumber(n)

	private val magnitudes = listOf('T' to 1_000_000_000_000L, 'B' to 1_000_000_000L, 'M' to 1_000_000L, 'k' to 1_000L)

	fun shortNumber(n: Long): String {
		val (letter, size) = magnitudes.firstOrNull { n >= it.second } ?: return n.toString()
		val scaled = n.toDouble() / size
		val decimals = when {
			scaled >= 100 -> 0
			scaled >= 10 -> 1
			else -> 2
		}
		var text = "%.${decimals}f".format(scaled)
		val separator = text.firstOrNull { !it.isDigit() && it != '-' }
		if (separator != null) text = text.trimEnd('0').removeSuffix(separator.toString())
		return text + letter
	}

	private const val CRIT_STAR = '✧'
	private const val OVERLOAD_STAR = '✯'

	private val critRainbow = listOf("§f", "§e", "§6", "§c", "§c", "§f")

	private fun isLineBreak(c: Char) = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'
	private fun isAsciiDigit(c: Char) = c in '0'..'9'

	private fun codeAt(s: String, i: Int) = i + 1 < s.length && s[i] == '§' && !isLineBreak(s[i + 1])

	private class Tag(val before: String, val digits: String, val after: String, val crit: Boolean)

	private fun colouredDigits(s: String, from: Int, commaAsDigit: Boolean): Pair<String, Int>? {
		val digits = StringBuilder()
		var i = from
		while (codeAt(s, i) && i + 2 < s.length) {
			val c = s[i + 2]
			if (isAsciiDigit(c)) digits.append(c) else if (!(commaAsDigit && c == ',')) break
			i += 3
			if (!commaAsDigit && codeAt(s, i) && i + 2 < s.length && s[i + 2] == ',') i += 3
		}
		return if (i == from) null else digits.toString() to i
	}

	private fun critTag(s: String): Tag? {
		if (!s.startsWith("§f$CRIT_STAR")) return null
		val (digits, end) = colouredDigits(s, 3, commaAsDigit = false) ?: return null
		if (!(codeAt(s, end) && end + 2 < s.length && s[end + 2] == CRIT_STAR)) return null
		val rest = s.substring(end + 3)
		if (rest.any(::isLineBreak)) return null
		return Tag("§f$CRIT_STAR", digits, "§f$CRIT_STAR$rest", crit = true)
	}

	private fun overloadTag(s: String): Tag? {
		if (!(codeAt(s, 0) && s.length > 2 && s[2] == OVERLOAD_STAR)) return null
		val (digits, end) = colouredDigits(s, 3, commaAsDigit = true) ?: return null
		if (!(codeAt(s, end) && s.length == end + 5 && s[end + 2] == OVERLOAD_STAR && s.endsWith("§r"))) return null
		return Tag(s.substring(0, 3), digits, s.substring(end, end + 3) + "§r", crit = true)
	}

	private fun plainTag(s: String): Tag? {
		if (!codeAt(s, 0)) return null
		var end = 2
		while (end < s.length && (isAsciiDigit(s[end]) || s[end] == ',' || s[end] == '+')) end++
		val rest = s.substring(end)
		if (rest.any(::isLineBreak)) return null
		val after = "§r" + (if ("♞" in rest) "§d" else "") + rest
		return Tag(s.substring(0, 2), s.substring(2, end).replace(",", ""), after, crit = false)
	}

	fun rewrite(formatted: String, style: Style = Style.SHORT): String? = rewrite(formatted, style, ::shortNumber)

	internal fun rewrite(formatted: String, style: Style, shorten: (Long) -> String): String? {
		val tag = critTag(formatted) ?: overloadTag(formatted) ?: plainTag(formatted) ?: return null
		val amount = tag.digits.toLongOrNull()?.takeIf { it > 999 } ?: return null
		val text = if (style == Style.COMMAS) ApexFormatters.withThousands(amount) else shorten(amount)
		val shown = if (!tag.crit) text else text.withIndex().joinToString("") { (i, c) -> critRainbow[i % critRainbow.size] + c }
		return tag.before + shown + tag.after
	}

	private val lastSeen = WeakHashMap<Entity, Pair<String, Component?>>()

	@JvmStatic
	fun replace(entity: Entity, name: Component?): Component? {
		if (name == null || !TConfig.enabled || entity !is ArmorStand || !SkyBlockSession.inSkyBlock) return null
		val formatted = name.getLegacyFormatString()
		lastSeen[entity]?.takeIf { it.first == formatted }?.let { return it.second }
		return rewrite(formatted, TConfig.style)?.let { Component.literal(it) }.also { lastSeen[entity] = formatted to it }
	}
}
