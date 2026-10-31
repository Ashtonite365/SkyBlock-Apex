// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.enchanting

data class SeqItem(val path: String, val name: String, val count: Int)

object UltrasequencerOrder {

	fun orderIndex(item: SeqItem): Int? {
		if (!isDye(item.path)) return null

		item.name.trim().toIntOrNull()?.takeIf { it >= 1 }?.let { return it - 1 }
		return (item.count - 1).takeIf { it >= 0 }
	}

	private val legacyDyes = setOf("ink_sac", "cocoa_beans", "lapis_lazuli", "bone_meal")
	fun isDye(path: String) = path.endsWith("_dye") || path in legacyDyes

	fun read(slots: List<SeqItem?>): Map<Int, Int> = buildMap {
		for ((index, item) in slots.withIndex()) {
			val order = item?.let(::orderIndex) ?: continue
			put(order, index)
		}
	}

	fun advance(order: Map<Int, Int>, replayIndex: Int, clicked: Int): Int = if (order[replayIndex] == clicked) replayIndex + 1 else replayIndex
}
