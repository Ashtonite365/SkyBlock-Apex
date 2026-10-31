/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: writes a buffered trail to logs/apex-trail/ when something went wrong.
 */
package dev.ashtonite365.apex.util.trail

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TrailWriter(
	val dir: Path,
	val minGapMs: Long = 60_000,
	val maxPerHour: Int = 10,
	val maxFiles: Int = 20,
	private val zone: ZoneId = ZoneId.systemDefault(),
) {
	private var lastWrite = Long.MIN_VALUE / 2
	private val recentWrites = ArrayDeque<Long>()
	private var heldBack = 0

	enum class Trigger { ERROR_LOG, UNCAUGHT_EXCEPTION, CRASH_REPORT }

	class Problem(val trigger: Trigger, val summary: String, val error: Throwable?, val thread: String?)

	@Volatile
	var written = 0
		private set

	@Synchronized
	fun claim(trigger: Trigger, now: Long): Boolean {
		while (recentWrites.isNotEmpty() && recentWrites.first() <= now - 3_600_000) recentWrites.removeFirst()
		val crash = trigger == Trigger.CRASH_REPORT
		if (recentWrites.size >= maxPerHour || (!crash && now - lastWrite < minGapMs)) {
			heldBack++
			return false
		}
		lastWrite = now
		recentWrites.addLast(now)
		return true
	}

	@Synchronized
	private fun takeHeldBack(): Int = heldBack.also { heldBack = 0 }

	fun write(problem: Problem, events: List<TrailRing.Event>, now: Long, maxAgeMs: Long): Path? = try {
		Files.createDirectories(dir)
		val file = freshFile(now)
		Files.writeString(file, render(problem, events, now, maxAgeMs, takeHeldBack()), StandardOpenOption.CREATE_NEW)
		synchronized(this) { written++ }
		pruneOldFiles()
		file
	} catch (e: Exception) {

		System.err.println("[SkyBlock Apex] could not write error trail: $e")
		null
	}

	private val nameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
	private val lineTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
	private val headerTime = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm:ss.SSS z")

	private fun freshFile(now: Long): Path {
		val base = nameFormat.format(Instant.ofEpochMilli(now).atZone(zone))
		var file = dir.resolve("$base.log")
		var n = 1
		while (Files.exists(file)) file = dir.resolve("$base-${n++}.log")
		return file
	}

	fun trailFiles(): List<Path> =
		if (!Files.isDirectory(dir)) listOf()
		else Files.list(dir).use { s -> s.filter { isTrailFile(it) }.sorted().toList() }

	private fun pruneOldFiles() {
		val files = trailFiles()
		for (old in files.take((files.size - maxFiles).coerceAtLeast(0))) {
			try {
				Files.deleteIfExists(old)
			} catch (e: Exception) {
				System.err.println("[SkyBlock Apex] could not remove old error trail $old: $e")
			}
		}
	}

	fun render(problem: Problem, events: List<TrailRing.Event>, now: Long, maxAgeMs: Long, heldBack: Int): String {
		val out = StringBuilder()
		out.append("SkyBlock Apex error trail\n")
		out.append("Written: ").append(headerTime.format(Instant.ofEpochMilli(now).atZone(zone))).append('\n')
		out.append("Trigger: ").append(problem.trigger).append(" on thread ").append(problem.thread ?: "?").append('\n')
		out.append("Problem: ").append(problem.summary).append('\n')
		if (heldBack > 0) out.append("Held back by the rate limit since the previous trail file: ").append(heldBack).append('\n')
		if (problem.error != null) out.append("\n--- error ---\n").append(stackTrace(problem.error))
		out.append("\n--- trail: ").append(events.size).append(" events from the last ").append(maxAgeMs / 1000)
			.append(" s, oldest first ---\n")
		for (e in events) {
			out.append(lineTime.format(Instant.ofEpochMilli(e.time).atZone(zone))).append(" [").append(e.thread ?: "?").append("] ")
			when (val kind = e.kind) {
				is TrailLine -> {
					out.append(kind.level).append(' ').append(kind.logger).append(": ").append(kind.message).append('\n')
					kind.error?.let { out.append(indent(stackTrace(it))) }
				}
				is Crumb -> out.append("CRUMB ").append(kind.describe(e.detail, e.number)).append('\n')
				else -> out.append(kind).append('\n')
			}
		}
		return out.toString()
	}

	private fun stackTrace(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()
	private fun indent(s: String) = s.lineSequence().filter { it.isNotEmpty() }.joinToString("") { "    $it\n" }

	companion object {
		private val trailName = Regex("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}-\d{3}(-\d+)?\.log""")
		fun isTrailFile(p: Path) = trailName.matches(p.fileName.toString())
	}
}

class TrailLine(val level: String, val logger: String, val message: String, val error: Throwable?)
