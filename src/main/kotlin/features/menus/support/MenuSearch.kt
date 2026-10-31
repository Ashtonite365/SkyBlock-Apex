// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

class MenuSearch(val maxLength: Int = 32) {
	var query = ""
	var focused = false

	fun keyPressed(input: KeyEvent): Boolean {
		if (!focused) return false
		when (input.input()) {
			InputConstants.KEY_BACKSPACE -> query = query.dropLast(1)
			InputConstants.KEY_ESCAPE, InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> focused = false
		}
		return true
	}

	fun charTyped(input: CharacterEvent): Boolean {
		if (!focused) return false
		type(input.codepoint())
		return true
	}

	fun type(codepoint: Int) {
		if (codepoint >= 32 && query.length < maxLength) query += String(Character.toChars(codepoint))
	}

	fun clear() { query = "" }

	fun <T> filter(items: List<T>, name: (T) -> String): List<T> = filter(items, query, name)

	companion object {
		private val CODES = dev.ashtonite365.apex.util.SharedRegex.SECTION

		fun normal(s: String) = s.replace(CODES, "").trim().lowercase()

		fun matches(name: String, query: String): Boolean = normal(query).let { q -> q.isEmpty() || normal(name).contains(q) }
		fun <T> filter(items: List<T>, query: String, name: (T) -> String): List<T> =
			if (normal(query).isEmpty()) items else items.filter { matches(name(it), query) }
	}
}
