// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.quests

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder

object QuestNotes {
	const val MAX_LENGTH = 120

	@Serializable
	data class Store(val notes: MutableMap<String, String> = mutableMapOf())

	@Config
	object Data : DataHolder<Store>(serializer(), "apex-quest-notes", ::Store)

	fun key(questName: String) = MenuText.clean(questName)

	fun get(store: Store, questName: String): String? = store.notes[key(questName)]?.takeIf { it.isNotBlank() }

	fun set(store: Store, questName: String, text: String): Boolean {
		val k = key(questName)
		if (k.isEmpty()) return false
		val v = clean(text)
		return if (v.isEmpty()) store.notes.remove(k) != null
		else (store.notes.put(k, v) != v)
	}

	fun clean(text: String) = text.replace("§", "").replace(Regex("[\\r\\n\\t]+"), " ").trim().take(MAX_LENGTH).trim()

	fun note(questName: String): String? = runCatching { get(Data.data, questName) }.getOrNull()

	fun save(questName: String, text: String) {
		val d = runCatching { Data.data }.getOrNull() ?: return
		if (set(d, questName, text)) Data.markDirty()
	}
}

class QuestNoteEditor(val questName: String, initial: String) {
	var text: String = initial; private set

	enum class Result { NONE, SAVE, CANCEL }

	fun type(codepoint: Int) {
		if (codepoint < 32 || codepoint == 0x7F || codepoint == '§'.code) return
		val s = String(Character.toChars(codepoint))
		if (text.length + s.length <= QuestNotes.MAX_LENGTH) text += s
	}

	fun key(code: Int): Result = when (code) {
		InputConstants.KEY_ESCAPE -> Result.CANCEL
		InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> Result.SAVE
		InputConstants.KEY_BACKSPACE -> { text = text.dropLast(1); Result.NONE }
		else -> Result.NONE
	}

	fun clear() { text = "" }
}

object QuestNoteUi {
	const val ICON = 10

	const val LABEL_RESERVE = ICON + 2

	fun icon(tx: Int, ty: Int, tileW: Int) = intArrayOf(tx + tileW - ICON - 2, ty + 2, ICON, ICON)

	fun inside(mx: Int, my: Int, r: IntArray) = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]

	enum class Action { OPEN_POPUP, FORWARD, NOTHING }

	fun click(mx: Int, my: Int, tx: Int, ty: Int, tileW: Int, button: Int): Action =
		if (inside(mx, my, icon(tx, ty, tileW))) iconAction(button) else Action.FORWARD

	fun iconAction(button: Int): Action = if (button == 0) Action.OPEN_POPUP else Action.NOTHING

	fun hasIcon(group: String) = group != QuestParser.MORE

	fun popup(x: Int, y: Int, w: Int, h: Int): IntArray {
		val pw = minOf(240, w - 16); val ph = 64
		return intArrayOf(x + (w - pw) / 2, y + (h - ph) / 2, pw, ph)
	}

	fun tooltip(normal: List<String>, note: String?, wrap: (String) -> List<String>): List<String> =
		if (note == null) normal else normal + "" + "§e✎ Note:" + wrap(note).map { "§f$it" }
}
