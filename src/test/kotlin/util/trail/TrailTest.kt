// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.util.trail

import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import dev.ashtonite365.apex.util.trail.TrailInstall
import dev.ashtonite365.apex.util.trail.TrailLine
import dev.ashtonite365.apex.util.trail.TrailRing
import dev.ashtonite365.apex.util.trail.TrailWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.config.Property
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrailTest {
	private var now = 1_800_000_000_000L
	private lateinit var dir: Path

	private fun freshDir(name: String): Path {
		val d = Path.of("trail-test").toAbsolutePath().resolve(name)
		if (Files.exists(d)) d.toFile().deleteRecursively()
		return d
	}

	private fun startTrail(name: String, capacity: Int = 100, maxAge: Long = 60_000, writer: (Path) -> TrailWriter = { TrailWriter(it, zone = ZoneId.of("Australia/Sydney")) }): TrailWriter {
		dir = freshDir(name)
		val w = writer(dir)
		Trail.start(TrailRing(capacity, maxAge), w)
		return w
	}

	@BeforeEach
	fun setUp() {
		Trail.stop()
		Trail.clock = { now }
		Trail.writeErrorsInBackground = false
	}

	@AfterEach
	fun tearDown() {
		Trail.stop()
		Trail.clock = System::currentTimeMillis
		Trail.writeErrorsInBackground = true
	}

	private fun files() = if (Files.isDirectory(dir)) Files.list(dir).use { it.sorted().toList() } else listOf()
	private fun error(msg: String) = Trail.onErrorLine(TrailLine("ERROR", "Test", msg, IllegalStateException(msg)), "Render thread")

	@Test
	fun `ring keeps the newest events up to its capacity`() {
		val ring = TrailRing(3, 60_000)
		for (i in 1..5) ring.add(now + i, Crumb.SCREEN, null, "t", i.toLong())
		assertEquals(listOf(3L, 4L, 5L), ring.snapshot(now + 5).map { it.number })
		assertEquals(5, ring.added)
	}

	@Test
	fun `ring forgets events older than its age limit`() {
		val ring = TrailRing(100, 1_000)
		ring.add(now, Crumb.SCREEN, null, "t", 1)
		ring.add(now + 600, Crumb.SCREEN, null, "t", 2)
		ring.add(now + 1_200, Crumb.SCREEN, null, "t", 3)
		assertEquals(listOf(2L, 3L), ring.snapshot(now + 1_200).map { it.number })
		assertEquals(listOf(3L), ring.snapshot(now + 2_000).map { it.number })
		assertEquals(listOf<Long>(), ring.snapshot(now + 5_000).map { it.number })
		assertEquals(0, ring.size)
	}

	@Test
	fun `crumbs are ignored while the trail is off`() {
		Trail.crumb(Crumb.SCREEN, String::class.java)
		assertFalse(Trail.isRunning)
		assertNull(Trail.trigger(TrailWriter.Problem(TrailWriter.Trigger.ERROR_LOG, "x", null, null), sync = true))
	}

	@Test
	fun `no file is written without an error`() {
		startTrail("quiet")
		Trail.crumb(Crumb.SCREEN, String::class.java)
		Trail.crumb(Crumb.WORLD_READY)
		Trail.logLine("Render thread", TrailLine("WARN", "Other", "just a warning", null))
		Trail.logLine("Render thread", TrailLine("DEBUG", "Apex", "debug detail", null))
		assertFalse(Files.exists(dir))
	}

	@Test
	fun `an error log line writes the trail with the error`() {
		startTrail("error-line")
		Trail.crumb(Crumb.SCREEN, java.util.ArrayList::class.java)
		Trail.crumb(Crumb.TEXTURE_RELOAD_START)
		error("Boom in renderer")
		val f = files().single()
		assertTrue(TrailWriter.isTrailFile(f), f.toString())
		val text = Files.readString(f)
		assertTrue(text.contains("Trigger: ERROR_LOG on thread Render thread"), text)
		assertTrue(text.contains("ERROR Test: Boom in renderer"), text)
		assertTrue(text.contains("java.lang.IllegalStateException: Boom in renderer"), text)
		assertTrue(text.contains("CRUMB screen changed to: java.util.ArrayList"), text)
		assertTrue(text.contains("CRUMB texture engine: resource reload started"), text)
		assertTrue(text.indexOf("screen changed") < text.indexOf("reload started"), "oldest first")
	}

	@Test
	fun `an uncaught exception writes a trail`() {
		startTrail("uncaught")
		Trail.onUncaught(Thread.currentThread(), RuntimeException("worker died"))
		val text = Files.readString(files().single())
		assertTrue(text.contains("Trigger: UNCAUGHT_EXCEPTION"), text)
		assertTrue(text.contains("java.lang.RuntimeException: worker died"), text)
	}

	@Test
	fun `a crash report writes a trail even inside the error rate limit`() {
		startTrail("crash")
		error("first")
		now += 1_000
		Trail.onCrashReport("Rendering overlay", NullPointerException("npe"), Path.of("crash-reports/crash.txt"))
		assertEquals(2, files().size)
		assertTrue(Files.readString(files().last()).contains("Trigger: CRASH_REPORT"))
	}

	@Test
	fun `log4j errors from any logger trigger, apex debug is buffered, outputs keep their levels`() {
		startTrail("log4j")
		val context = LoggerContext("apex-trail-test")
		context.start(DefaultConfiguration())
		try {
			val captured = mutableListOf<String>()
			val capture = object : AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
				override fun append(event: LogEvent) {
					captured += "${event.level} ${event.loggerName} ${event.message.formattedMessage}"
				}
			}
			capture.start()
			val config = context.configuration
			config.rootLogger.appenders.keys.toList().forEach { config.rootLogger.removeAppender(it) }
			config.rootLogger.level = Level.INFO
			config.addAppender(capture)
			config.rootLogger.addAppender(capture, null, null)
			context.updateLoggers()
			assertTrue(TrailInstall.attach(context))

			context.getLogger("Apex").debug("apex debug line")
			context.getLogger("Apex.Wiki").info("apex child info")
			context.getLogger("Apex/IceFill").debug("solver debug")
			context.getLogger("net.minecraft.Other").info("other info")
			context.getLogger("net.minecraft.Other").warn("other warn")
			assertFalse(Files.exists(dir), "no file before an error")

			assertEquals(listOf("INFO Apex.Wiki apex child info", "INFO net.minecraft.Other other info", "WARN net.minecraft.Other other warn"), captured)

			context.getLogger("io.netty.Something").error("socket broke", java.io.IOException("reset"))
			val text = Files.readString(files().single())
			for (s in listOf("DEBUG Apex: apex debug line", "INFO Apex.Wiki: apex child info", "DEBUG Apex/IceFill: solver debug", "WARN net.minecraft.Other: other warn", "ERROR io.netty.Something: socket broke", "java.io.IOException: reset"))
				assertTrue(text.contains(s), "$s in\n$text")
			assertFalse(text.contains("other info"), text)
		} finally {
			context.stop()
		}
	}

	@Test
	fun `error spam is rate limited and the held-back count is reported`() {
		val w = startTrail("rate") { TrailWriter(it, minGapMs = 60_000, maxPerHour = 10, maxFiles = 20) }
		repeat(50) { error("spam $it"); now += 100 }
		assertEquals(1, files().size)
		now += 60_000
		error("later")
		assertEquals(2, files().size)
		assertTrue(Files.readString(files().last()).contains("Held back by the rate limit since the previous trail file: 49"))
		assertEquals(2, w.written)
	}

	@Test
	fun `at most maxPerHour files per rolling hour, crashes included`() {
		startTrail("hourly") { TrailWriter(it, minGapMs = 0, maxPerHour = 3, maxFiles = 20) }
		repeat(5) { error("e$it"); now += 1_000 }
		Trail.onCrashReport("c", null, null)
		assertEquals(3, files().size)
		now += 3_600_000
		error("next hour")
		assertEquals(4, files().size)
	}

	@Test
	fun `only the newest maxFiles trail files are kept, other files untouched`() {
		startTrail("cap") { TrailWriter(it, minGapMs = 0, maxPerHour = 100, maxFiles = 3) }
		Files.createDirectories(dir)
		Files.writeString(dir.resolve("notes.txt"), "keep me")
		repeat(5) { error("e$it"); now += 1_000 }
		val trailFiles = files().filter { TrailWriter.isTrailFile(it) }
		assertEquals(3, trailFiles.size)
		assertTrue(Files.readString(trailFiles.first()).contains("e2"), "oldest two replaced")
		assertTrue(Files.exists(dir.resolve("notes.txt")))
	}

	@Test
	fun `background writing for error lines`() {
		startTrail("background")
		Trail.writeErrorsInBackground = true
		error("async")
		Trail.flush()
		assertEquals(1, files().size)
	}

	@Test
	fun `file names use the local timestamp and never collide`() {
		startTrail("names") { TrailWriter(it, minGapMs = 0, maxPerHour = 100, maxFiles = 20, zone = ZoneId.of("Australia/Sydney")) }
		error("a")
		error("b")
		val names = files().map { it.fileName.toString() }
		assertEquals(2, names.size)
		assertNotNull(names.firstOrNull { it == "2027-01-15_19-00-00-000.log" }, names.toString())
		assertNotNull(names.firstOrNull { it == "2027-01-15_19-00-00-000-1.log" }, names.toString())
	}
}
