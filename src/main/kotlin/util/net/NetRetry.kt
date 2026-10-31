// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.net

import org.apache.logging.log4j.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object NetRetry {

	fun backoffMs(attempt: Int): Long = (5_000L * Math.pow(3.0, (attempt - 1).coerceAtLeast(0).toDouble()).toLong()).coerceAtMost(120_000L)

	suspend fun <T> attempt(logger: Logger, what: String, tries: Int = 3, sleep: suspend (Long) -> Unit = { delay(it) }, block: suspend () -> T): T? {
		for (i in 1..tries) {
			try {
				return block()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				val root = generateSequence(e) { it.cause }.last()
				logger.warn("Could not fetch $what (try $i/$tries): ${e.javaClass.simpleName}${root.takeIf { it !== e }?.let { " <- ${it.javaClass.simpleName}" } ?: ""}: ${e.message}")
				if (i < tries) sleep(backoffMs(i))
			}
		}
		return null
	}
}
