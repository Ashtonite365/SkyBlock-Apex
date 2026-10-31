// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.repo

object PriceSanity {

	const val MIN_RATIO = 0.25

	fun guard(lbin: Double?, avg: Double?): Double? =
		if (lbin != null && avg != null && avg > 0 && lbin < avg * MIN_RATIO) avg else lbin ?: null

	fun isOutlier(lbin: Double?, avg: Double?) = lbin != null && avg != null && avg > 0 && lbin < avg * MIN_RATIO

	fun <K> guardAll(lbin: Map<K, Double>, avg: (K) -> Double?): Map<K, Double> =
		lbin.mapValues { (k, v) -> guard(v, avg(k)) ?: v }
}
