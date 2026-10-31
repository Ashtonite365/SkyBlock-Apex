/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: hooks the trail into log4j (the logging library Minecraft already ships) and into the
 * JVM's uncaught exception handling.
 */
package dev.ashtonite365.apex.util.trail

import java.nio.file.Path
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.config.Property

object TrailInstall {

	val APEX_LOGGERS = listOf(
		"Apex", "dev.ashtonite365.apex", "SkyBlock Apex textures",
		"Apex/ApexDoorPath", "Apex/Boulder", "Apex/ApexWeirdos", "Apex/IceFill", "Apex/ChestProfit", "Apex/CreeperBeams",
	)

	const val APPENDER_NAME = "ApexErrorTrail"

	fun isApexLogger(name: String) =
		name.startsWith("Apex") || name.startsWith("dev.ashtonite365.apex") || name == "SkyBlock Apex textures"

	class Appender : AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
		override fun append(event: LogEvent) {
			val level = event.level
			if (!level.isMoreSpecificThan(Level.WARN) && !isApexLogger(event.loggerName ?: "")) return
			val line = TrailLine(level.name(), event.loggerName ?: "", event.message?.formattedMessage ?: "", event.thrown)
			val thread = event.threadName
			Trail.logLine(thread, line)
			if (level.isMoreSpecificThan(Level.ERROR)) Trail.onErrorLine(line, thread)
		}
	}

	fun attach(context: LoggerContext): Boolean {
		val config = context.configuration
		if (config.appenders.containsKey(APPENDER_NAME)) return true
		val appender = Appender()
		appender.start()
		config.addAppender(appender)
		val root = config.rootLogger

		root.addAppender(appender, Level.ALL, null)
		for (name in APEX_LOGGERS) {
			val existing = config.getLoggerConfig(name)
			if (existing.name == name) {

				existing.addAppender(appender, Level.ALL, null)
				continue
			}
			val own = LoggerConfig(name, Level.ALL, false)
			root.filter?.let { own.addFilter(it) }
			for ((appenderName, target) in root.appenders) {
				if (appenderName == APPENDER_NAME) continue

				val ref = root.appenderRefs.firstOrNull { it.ref == appenderName }
				val refLevel = ref?.level
				val level = if (refLevel != null && refLevel.isMoreSpecificThan(root.level)) refLevel else root.level
				own.addAppender(target, level, ref?.filter)
			}
			own.addAppender(appender, Level.ALL, null)
			config.addLogger(name, own)
		}
		context.updateLoggers()
		return true
	}

	fun install(dir: Path) {
		if (System.getProperty(Trail.PROPERTY) == "off" || Trail.isRunning) return
		try {
			Trail.start(TrailRing(Trail.CAPACITY, Trail.MAX_AGE_MS), TrailWriter(dir))
			val context = LogManager.getContext(false) as? LoggerContext
			if (context == null || !attach(context)) System.err.println("[SkyBlock Apex] error trail: log4j core not available, log lines are not buffered")
			val previous = Thread.getDefaultUncaughtExceptionHandler()
			Thread.setDefaultUncaughtExceptionHandler { thread, error ->
				try {
					Trail.onUncaught(thread, error)
				} catch (_: Throwable) {
				}
				if (previous != null) previous.uncaughtException(thread, error)
				else {
					System.err.print("Exception in thread \"" + thread.name + "\" ")
					error.printStackTrace(System.err)
				}
			}
		} catch (e: Throwable) {
			System.err.println("[SkyBlock Apex] error trail could not start: $e")
		}
	}
}
