// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

object PlusFishingMerges {

	fun timeAlert(alive: Int, oldestSeconds: Long, limitSeconds: Int): Boolean {
		if (limitSeconds <= 0 || alive <= 0) return false
		return oldestSeconds >= limitSeconds
	}

	fun baitChanged(before: String?, after: String?): Boolean = before?.let { it != after } ?: false

	fun hideTrophy(tier: String, have: Int, bronzeAfter: Int, silverAfter: Int): Boolean {
		val threshold = when (tier) {
			"BRONZE" -> bronzeAfter
			"SILVER" -> silverAfter
			else -> return false
		}
		return threshold > 0 && have >= threshold
	}
}
