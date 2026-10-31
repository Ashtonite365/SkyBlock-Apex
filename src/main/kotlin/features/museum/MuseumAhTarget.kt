// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.museum

object MuseumAhTarget {

	const val TTL_MS = 5 * 60_000L

	@Volatile var ids: Set<String> = emptySet(); private set
	@Volatile var setAtMs: Long = 0L; private set

	internal var now: () -> Long = System::currentTimeMillis

	fun set(targetIds: Collection<String>) { ids = targetIds.toSet(); setAtMs = now() }
	fun clear() { ids = emptySet(); setAtMs = 0L }
	fun active(): Boolean = ids.isNotEmpty() && now() - setAtMs <= TTL_MS

	fun matches(slotId: String?, target: Set<String>): Boolean = slotId != null && slotId in target

	fun blocked(slotId: String?): Boolean = active() && !matches(slotId, ids)
}
