package dev.ashtonite365.apex.features.mining

import kotlin.time.Duration
import dev.ashtonite365.apex.util.TimeMark

class Histogram<T>(
	val maxSize: Int,
	val maxDuration: Duration,

	private val clock: () -> TimeMark = TimeMark::now,
) {
	private class Sample<T>(val at: TimeMark, val value: T)

	private val samples = ArrayList<Sample<T>>()

	val size: Int get() = samples.size

	fun record(entry: T, timestamp: TimeMark = clock()): Unit {
		samples.add(insertionIndex(timestamp), Sample(timestamp, entry))
		evict()
	}

	private fun insertionIndex(at: TimeMark): Int {
		var low = 0
		var high = samples.size
		while (low < high) {
			val mid = (low + high) ushr 1
			if (samples[mid].at <= at) low = mid + 1 else high = mid
		}
		return low
	}

	fun oldestUpdate(): TimeMark = samples.also { evict() }.firstOrNull()?.at ?: clock()

	fun latestUpdate(): TimeMark = samples.also { evict() }.lastOrNull()?.at ?: TimeMark.farPast()

	fun averagePer(valueExtractor: (T) -> Double, perDuration: Duration): Double? =
		aggregate(0.0, { total, sample, _ -> total + valueExtractor(sample) }) { total, first, last ->
			val span = last - first
			if (span <= perDuration) null else total / (span / perDuration)
		}

	fun <V, R> aggregate(seed: V, operator: (V, T, TimeMark) -> V, finish: (V, TimeMark, TimeMark) -> R): R? {
		evict()
		if (samples.isEmpty()) return null
		val folded = samples.fold(seed) { acc, sample -> operator(acc, sample.value, sample.at) }
		return finish(folded, samples.first().at, samples.last().at)
	}

	private fun evict() {
		val excess = samples.size - maxSize
		if (excess > 0) samples.subList(0, excess).clear()
		val cutoff = clock().before(maxDuration)
		val expired = samples.indexOfFirst { it.at > cutoff }.let { if (it < 0) samples.size else it }
		if (expired > 0) samples.subList(0, expired).clear()
	}
}
