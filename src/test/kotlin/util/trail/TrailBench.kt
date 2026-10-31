// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.util.trail

import dev.ashtonite365.apex.test.texengine.Bench
import dev.ashtonite365.apex.test.texengine.BenchPacks
import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import dev.ashtonite365.apex.util.trail.TrailInstall
import dev.ashtonite365.apex.util.trail.TrailRing
import dev.ashtonite365.apex.util.trail.TrailWriter
import java.nio.file.Files
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.config.Property
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("bench")
class TrailBench {

	private class Sink : AbstractAppender("sink", null, null, true, Property.EMPTY_ARRAY) {
		var n = 0L
		override fun append(event: LogEvent) {
			n++
		}
	}

	private fun context(withTrail: Boolean): Pair<LoggerContext, Sink> {
		val ctx = LoggerContext("trail-bench-$withTrail")
		ctx.start(DefaultConfiguration())
		val config = ctx.configuration
		config.rootLogger.appenders.keys.toList().forEach { config.rootLogger.removeAppender(it) }
		config.rootLogger.level = Level.INFO
		val sink = Sink().also { it.start() }
		config.addAppender(sink)
		config.rootLogger.addAppender(sink, null, null)
		ctx.updateLoggers()
		if (withTrail) TrailInstall.attach(ctx)
		return ctx to sink
	}

	@Test
	fun `B-TRAIL overhead`() {
		val dir = BenchPacks.dir.resolve("trail-files")
		if (Files.exists(dir)) dir.toFile().deleteRecursively()
		val results = mutableListOf<Bench.Result>()
		val ops = 10_000
		val screen = String::class.java

		Trail.stop()
		results += Bench.run("B-TRAIL", "crumb, trail off", ops) { repeat(ops) { Trail.crumb(Crumb.SCREEN, screen) }; 0 }
		Trail.start(TrailRing(Trail.CAPACITY, Trail.MAX_AGE_MS), TrailWriter(dir))
		try {
			results += Bench.run("B-TRAIL", "crumb, trail on", ops) { repeat(ops) { Trail.crumb(Crumb.SCREEN, screen) }; 0 }

			for (withTrail in listOf(false, true)) {
				val (ctx, sink) = context(withTrail)
				try {
					val apex = ctx.getLogger("Apex")
					val other = ctx.getLogger("net.minecraft.Other")
					val label = if (withTrail) "with trail" else "without trail"
					results += Bench.run("B-TRAIL", "Apex info line, $label", ops) { repeat(ops) { apex.info("line {}", 7) }; sink.n }
					results += Bench.run("B-TRAIL", "Apex debug line (hidden), $label", ops) { repeat(ops) { apex.debug("detail {}", 7) }; sink.n }
					results += Bench.run("B-TRAIL", "other logger info line, $label", ops) { repeat(ops) { other.info("line {}", 7) }; sink.n }
					results += Bench.run("B-TRAIL", "other logger debug line (off), $label", ops) { repeat(ops) { other.debug("detail {}", 7) }; sink.n }
				} finally {
					ctx.stop()
				}
			}
		} finally {
			Trail.stop()
		}
		assertFalse(Files.exists(dir), "no trail file without an error")
		val report = StringBuilder("B-TRAIL (case, ns/op, B/op)\n")
		for (r in results) report.append(String.format("%-48s %10.1f ns/op %10.1f B/op%n", r.case, r.nsPerOp, r.bytesPerOp))
		println(report)
		Files.writeString(BenchPacks.dir.resolve("b-trail.txt"), report.toString())
	}
}
