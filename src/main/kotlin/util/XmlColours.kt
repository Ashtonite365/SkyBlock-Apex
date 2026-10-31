package dev.ashtonite365.apex.util

import me.shedaniel.math.Color

object XmlColours {
	fun awt(text: String): java.awt.Color {
		if (!text.startsWith("#")) return java.awt.Color(text.toInt(), true)
		val digits = text.drop(1)

		val packed = if (digits.length == 8) digits.toLong(16).toInt() else digits.toInt(16)
		return when (digits.length) {
			6 -> java.awt.Color(packed)
			8 -> java.awt.Color(packed, true)
			else -> error("Hexcolor $text needs to be exactly 6 or 8 hex digits long")
		}
	}

	fun shedaniel(text: String): Color = awt(text).let { Color.ofRGBA(it.red, it.green, it.blue, it.alpha) }
}
