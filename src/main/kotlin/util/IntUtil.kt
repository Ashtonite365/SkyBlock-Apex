package dev.ashtonite365.apex.util

object IntUtil {

	data class RGBA(val r: Int, val g: Int, val b: Int, val a: Int)

	private fun Int.channel(shift: Int) = ushr(shift) and 0xFF

	fun Int.toRGBA(): RGBA = RGBA(r = channel(16), g = channel(8), b = channel(0), a = channel(24))

	private val digitGlyphs = arrayOf(
		arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"),
		arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"),
		arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"),
		arrayOf("", "M", "MM", "MMM"),
	)

	fun Int.toRomanNumeral(): String {
		if (this < 1 || this > 3999) return toString()
		val out = StringBuilder()
		var place = 1000
		for (row in 3 downTo 0) {
			out.append(digitGlyphs[row][this / place % 10])
			place /= 10
		}
		return out.toString()
	}
}
