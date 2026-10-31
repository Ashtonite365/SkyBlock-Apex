package dev.ashtonite365.apex.test.util.net

import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import dev.ashtonite365.apex.util.net.NetRetry

class NetRetryTest {
	private val log = LogManager.getLogger("test")

	@Test
	fun failuresAreContainedAndRetriedWithBoundedBackoff() = runBlocking {
		val sleeps = mutableListOf<Long>()
		var calls = 0
		val r = NetRetry.attempt<Int>(log, "x", tries = 3, sleep = { sleeps += it }) {
			calls++; throw java.net.ConnectException("boom").also { it.initCause(java.nio.channels.ClosedChannelException()) }
		}
		Assertions.assertNull(r); Assertions.assertEquals(3, calls); Assertions.assertEquals(listOf(5_000L, 15_000L), sleeps)
		var n = 0
		Assertions.assertEquals(7, NetRetry.attempt(log, "y", sleep = {}) { if (n++ == 0) throw IllegalStateException("parse") else 7 })
		Assertions.assertEquals(120_000L, NetRetry.backoffMs(10))

		var rounds = 0
		repeat(3) { NetRetry.attempt<Unit>(log, "loop", tries = 1, sleep = {}) { rounds++; throw java.net.ConnectException() } }
		Assertions.assertEquals(3, rounds)

		Assertions.assertThrows(CancellationException::class.java) {
			runBlocking { NetRetry.attempt<Unit>(log, "c", sleep = {}) { throw CancellationException("stop") } }
		}
		Unit
	}

	@Test
	fun apexScopeContainsEscapedExceptions() = runBlocking {
		val handler = dev.ashtonite365.apex.Apex.coroutineScope.coroutineContext[kotlinx.coroutines.CoroutineExceptionHandler]
		Assertions.assertNotNull(handler)
		val job = dev.ashtonite365.apex.Apex.coroutineScope.launch0 { throw java.net.ConnectException("escaped") }
		job.join()
		Assertions.assertTrue(job.isCancelled)
		Assertions.assertTrue(dev.ashtonite365.apex.Apex.coroutineScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
	}
}

private fun kotlinx.coroutines.CoroutineScope.launch0(block: suspend () -> Unit) = this.launch { block() }
