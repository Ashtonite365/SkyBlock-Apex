// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

class SlotDumpGate {
	companion object {
		const val STABLE_MS = 300L; const val MAX_IDS = 64

		val EVERY_UPDATE_TITLES: List<Regex> get() = listOf(
			dev.ashtonite365.apex.features.menus.museum.MilestonesParser.TITLE,
			dev.ashtonite365.apex.features.menus.wardrobe.LoadoutParser.TITLE,
			Regex("^Dungeon Blacksmith$"), Regex("^Salvage Items?$"))
		fun everyUpdate(title: String) = MenuText.clean(title).let { t -> EVERY_UPDATE_TITLES.any { it.matches(t) } }
	}

	private val done = LinkedHashSet<Pair<Int, Int>>()

	fun opened(containerId: Int) {
		done.removeAll { it.first == containerId }
		seen.remove(containerId); updates.remove(containerId)
		if (id == containerId) { id = Int.MIN_VALUE }
	}
	private var id = Int.MIN_VALUE
	private var sig = 0
	private var since = 0L

	fun offer(containerId: Int, signature: Int, filledSlots: Int, nowMs: Long, everyUpdate: Boolean = false): Boolean {
		val key = containerId to (if (everyUpdate) signature else 0)
		if (key in done) return false
		if (containerId != id || signature != sig) { id = containerId; sig = signature; since = nowMs; return false }
		if (filledSlots <= 0 || nowMs - since < STABLE_MS) return false
		done += key
		if (done.size > MAX_IDS) done.remove(done.first())
		return true
	}

	private val seen = HashMap<Int, MutableSet<Int>>()
	private val updates = HashMap<Int, Int>()

	fun dumped(containerId: Int, contentKey: Int) {
		seen.getOrPut(containerId) { HashSet() } += contentKey
		if (seen.size > MAX_IDS) seen.remove(seen.keys.first())
	}

	fun contentUpdate(containerId: Int, contentKey: Int, filledSlots: Int, enabled: Boolean): String? {
		if (!enabled || filledSlots <= 0) return null
		val s = seen[containerId] ?: return null
		if (!s.add(contentKey)) return null
		val n = (updates[containerId] ?: 0) + 1
		updates[containerId] = n
		return "update#$n"
	}

	fun contentKey(title: String, slots: List<SlotText>): Int = MenuDebug.dump(title, 0, slots).hashCode()
}
