// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config.storage

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.OnErrorResult
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import dev.ashtonite365.apex.Apex

class ConfigLoadContext(val loadId: String) : AutoCloseable {
	val backupPath: Path = Path("backups", Apex.MOD_ID, "config-$loadId").toAbsolutePath()
	val logFile: Path = Path("logs", Apex.MOD_ID, "config-$loadId.log").toAbsolutePath()
	val logBuffer: StringBuilder = StringBuilder()

	var shouldSaveLogBuffer: Boolean = false

	fun markShouldSaveLogBuffer() = run { shouldSaveLogBuffer = true }

	private enum class Level(val failure: Boolean) { DEBUG(false), INFO(false), ERROR(true) }

	private fun entry(level: Level, text: String, cause: Throwable? = null) {
		if (level.failure) shouldSaveLogBuffer = true
		val echo = Apex.DEBUG || (level == Level.ERROR && cause == null)
		if (echo) when (level) {
			Level.INFO -> Apex.logger.info("[ConfigUpgrade] {}", text)
			Level.ERROR -> if (cause == null) Apex.logger.error("[ConfigUpgrade] {}", text) else Apex.logger.error("[ConfigUpgrade] $text", cause)
			Level.DEBUG -> Unit
		}
		logBuffer.append("[${level.name}] ").append(text).append('\n')
		if (cause != null) logBuffer.append(cause.stackTraceToString()).append('\n')
	}

	fun logDebug(message: String) = entry(Level.DEBUG, message)
	fun logInfo(message: String) = entry(Level.INFO, message)
	fun logFault(message: String) = entry(Level.ERROR, message)
	fun logFault(message: String, exception: Throwable) = entry(Level.ERROR, message, exception)

	fun ensureWritable(path: Path): Unit = path.createParentDirectories().let { }

	fun use(block: (ConfigLoadContext) -> Unit): Unit {
		val failure = runCatching { block(this) }.exceptionOrNull()
		if (failure is Exception) logFault("Config pass $loadId failed", failure)
		this.close()
		if (failure != null && failure !is Exception) throw failure
	}

	override fun close(): Unit {
		logInfo("Config pass $loadId done")
		if (shouldSaveLogBuffer) persistJournal()
	}

	private fun persistJournal() = try {
		ensureWritable(this.logFile)
		logFile.writeText(logBuffer)
	} catch (problem: Exception) {
		logFault("Writing $logFile failed", problem)
	}

	@OptIn(ExperimentalPathApi::class)
	fun createBackup(folder: Path, reason: String) {
		val target = backupPath.resolve("$reason-${System.currentTimeMillis()}")
		logInfo("Backing up $folder to $target")
		val skip = { from: Path, to: Path, problem: Exception ->
			logFault("Skipped $from -> $to while backing up", problem); OnErrorResult.SKIP_SUBTREE
		}
		folder.copyToRecursively(target.createParentDirectories(), onError = skip, followLinks = false, overwrite = false)
	}
}
