@file:OptIn(ExperimentalContracts::class)

package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.Apex
import io.github.notenoughupdates.moulconfig.internal.InitUtil
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("NOTHING_TO_INLINE")
object Faults {
	@JvmField
	val strictFaults: Boolean = TestUtil.isInTest || Apex.DEBUG || Faults::class.java.desiredAssertionStatus()

	fun logFault(message: String) = Apex.logger.error(message)

	fun logFault(message: String, exception: Throwable) = Apex.logger.error(message, exception)

	inline fun reportFault(message: String) {
		if (strictFaults) throw IllegalStateException(message)
		logFault(message)
	}

	inline fun reportFault(message: String, exception: Throwable) {
		if (strictFaults) throw IllegalStateException(message, exception)
		logFault(message, exception)
	}

	inline fun expectOrReport(message: String, holds: Boolean) {
		if (!holds) reportFault(message)
	}

	inline fun debugCheck(message: String, condition: () -> Boolean) {
		contract { callsInPlace(condition, InvocationKind.AT_MOST_ONCE) }
		if (!strictFaults) return
		if (!condition()) throw IllegalStateException(message)
	}

	inline fun <T : Any> presentOr(candidate: T?, message: String, fallback: () -> T): T {
		contract { callsInPlace(fallback, InvocationKind.AT_MOST_ONCE) }
		return candidate ?: run {
			reportFault(message)
			fallback()
		}
	}

	class Attempt<T> private constructor(val value: T?, val failure: Throwable?) {

		fun valueOrNull(): T? = value

		inline fun orElse(recover: (failure: Throwable) -> T): T {
			contract { callsInPlace(recover, InvocationKind.AT_MOST_ONCE) }
			@Suppress("UNCHECKED_CAST")
			return when (val thrown = failure) {
				null -> value as T
				else -> recover(thrown)
			}
		}

		companion object {
			fun <T> succeed(value: T): Attempt<T> = Attempt(value, null)
			fun <T> fail(failure: Throwable): Attempt<T> = Attempt(null, failure)
		}
	}

	inline fun <T> attempt(message: String, action: () -> T): Attempt<T> {
		val result = try {
			Attempt.succeed(action())
		} catch (thrown: Throwable) {
			reportFault(message, thrown)
			Attempt.fail(thrown)
		}
		return result
	}

	@JvmStatic
	@JvmName("attemptJava")
	fun <T> attempt(message: String, action: InitUtil.ThrowingSupplier<T, Throwable>): Attempt<T> =
		attempt(message) { action.get() }

	fun reportUserError(text: String) {
		check(!TestUtil.isInTest) { text }
		Client.showChat(tr("apex.usererror", "SkyBlock Apex encountered a user caused error: $text"))
	}
}
