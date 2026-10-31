/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: keeps a short in-memory trail of recent log output and breadcrumbs, and writes it to
 * logs/apex-trail/ only when something goes wrong (an ERROR log line, an uncaught exception, a crash report).
 * Client-only diagnostics: nothing is sent anywhere and nothing in the game changes.
 */
package dev.ashtonite365.apex.util.trail

import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Trail {

	const val PROPERTY = "apex.trail"
	const val CAPACITY = 2000
	const val MAX_AGE_MS = 5 * 60_000L

	@Volatile
	private var ring: TrailRing? = null

	@Volatile
	private var writer: TrailWriter? = null

	@Volatile
	var clock: () -> Long = System::currentTimeMillis

	@Volatile
	var writeErrorsInBackground = true

	private var background: ExecutorService? = null

	private val writing = ThreadLocal.withInitial { false }

	val isRunning get() = ring != null

	@Synchronized
	fun start(ring: TrailRing, writer: TrailWriter) {
		this.writer = writer
		this.ring = ring
	}

	@Synchronized
	fun stop() {
		ring = null
		writer = null
		background?.let { it.shutdown(); it.awaitTermination(5, TimeUnit.SECONDS) }
		background = null
	}

	fun flush() {
		val b = synchronized(this) { background } ?: return
		b.submit {}.get(5, TimeUnit.SECONDS)
	}

	@JvmStatic
	@JvmOverloads
	fun crumb(kind: Crumb, detail: Any? = null, number: Long = 0) {
		val r = ring ?: return
		r.add(clock(), kind, detail, Thread.currentThread().name, number)
	}

	fun logLine(thread: String?, line: TrailLine) {
		ring?.add(clock(), line, null, thread, 0)
	}

	fun onErrorLine(line: TrailLine, thread: String?) {
		val summary = "${line.level} ${line.logger}: ${line.message.lineSequence().first().take(300)}"
		trigger(TrailWriter.Problem(TrailWriter.Trigger.ERROR_LOG, summary, line.error, thread), sync = !writeErrorsInBackground)
	}

	fun onUncaught(thread: Thread, error: Throwable) {
		trigger(TrailWriter.Problem(TrailWriter.Trigger.UNCAUGHT_EXCEPTION, "uncaught ${error.javaClass.name} on thread ${thread.name}: ${error.message}", error, thread.name), sync = true)
	}

	@JvmStatic
	fun onCrashReport(title: String?, error: Throwable?, file: Path?) {
		trigger(TrailWriter.Problem(TrailWriter.Trigger.CRASH_REPORT, "Minecraft crash report \"$title\" saved to $file", error, Thread.currentThread().name), sync = true)
	}

	fun trigger(problem: TrailWriter.Problem, sync: Boolean): Path? {
		if (writing.get()) return null
		val r = ring ?: return null
		val w = writer ?: return null
		val now = clock()
		if (!w.claim(problem.trigger, now)) return null
		val events = r.snapshot(now)
		if (sync) return writeGuarded(w, problem, events, now, r.maxAgeMs)
		val exec = synchronized(this) {
			background ?: Executors.newSingleThreadExecutor { task -> Thread(task, "Apex error trail").also { it.isDaemon = true } }.also { background = it }
		}
		exec.execute { writeGuarded(w, problem, events, now, r.maxAgeMs) }
		return null
	}

	private fun writeGuarded(w: TrailWriter, problem: TrailWriter.Problem, events: List<TrailRing.Event>, now: Long, maxAge: Long): Path? {
		writing.set(true)
		try {
			return w.write(problem, events, now, maxAge)
		} finally {
			writing.set(false)
		}
	}
}
