// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.util.ApexClickDelay

class ApexClickDelayTest {
	@AfterEach fun reset() = ApexClickDelay.resetForTests()

	@Test fun rangeAlwaysWithin100to200() {
		assertEquals(100..200, ApexClickDelay.RANGE_MS)
		repeat(5000) { assertTrue(ApexClickDelay.nextMs() in 100L..200L) }

		for (v in listOf(Int.MIN_VALUE, -1, 0, 50, 99, 100, 150, 200, 201, 10_000, Int.MAX_VALUE)) {
			ApexClickDelay.random = { v }
			assertTrue(ApexClickDelay.nextMs() in 100L..200L, "source $v")
		}

		val seen = mutableListOf<Long>()
		ApexClickDelay.scheduler = { ms, a -> seen += ms; a() }
		ApexClickDelay.laterMs(0) {}; ApexClickDelay.laterMs(50) {}; ApexClickDelay.laterMs(5000) {}
		assertEquals(listOf(100L, 100L, 200L), seen)
	}

	@Test fun rerolledForEachClick() {
		var calls = 0
		val rnd = Random(42)
		ApexClickDelay.random = { r -> calls++; r.first + rnd.nextInt(r.last - r.first + 1) }
		val seen = mutableListOf<Long>()
		ApexClickDelay.scheduler = { ms, a -> seen += ms; a() }
		var fired = 0
		repeat(50) { ApexClickDelay.later { fired++ } }
		assertEquals(50, calls)
		assertEquals(50, fired)
		assertTrue(seen.toSet().size > 10, "delays vary between clicks: $seen")
		assertTrue(seen.all { it in 100L..200L })
	}

	@Test fun menuChainRollsPerClickThroughTheHelper() {
		val seq = ArrayDeque(listOf(110, 190, 150))
		ApexClickDelay.random = { seq.removeFirst() }
		val steps = listOf(
			MenuChain.Step(Regex("^A$"), MenuChain.Target("a")),
			MenuChain.Step(Regex("^B$"), MenuChain.Target("b")),
		)
		val c = MenuChain(steps, MenuChain.Finish.AfterLastClick, 10_000)
		c.begin(1, "A", 0)
		val d1 = (c.onContents(1, listOf(MenuChain.Label(0, "a")), 0) as MenuChain.Action.Click).delayMs
		assertTrue(c.fire(1, 1))
		c.onOpen(2, "B", 2)
		val d2 = (c.onContents(2, listOf(MenuChain.Label(0, "b")), 3) as MenuChain.Action.Click).delayMs
		assertEquals(110L, d1); assertEquals(190L, d2)
	}

	private val root = listOf("src/main", "../src/main", "../../src/main").map { File(it).absoluteFile.normalize() }.first { File(it, "kotlin/util/ApexClickDelay.kt").isFile }
	private fun src(path: String) = File(root, path).readText()

	private val automated = listOf(
		"kotlin/features/menus/MenuChain.kt",
	)
	private val directClick = Regex("""handleContainerInput|clickLeftMouseButton|clickRightMouseButton|clickMiddleMouseButton|MenuClicks\.click\(""")

	@Test fun everyChainAndSolverUsesTheHelper() {
		for (f in automated) {
			val s = src(f)
			assertTrue(s.contains("ApexClickDelay"), "$f must use ApexClickDelay")
			assertTrue(!directClick.containsMatchIn(s), "$f sends an automated click without ApexClickDelay: ${directClick.find(s)?.value}")
			assertTrue(!Regex("""coerceIn\(0L, ?50L\)|nextLong\(300""").containsMatchIn(s), "$f keeps an old per-feature delay")
		}

		assertTrue(src("kotlin/features/menus/MenuClicks.kt").contains("= ApexClickDelay.clickLater("))
	}

	@Test fun experimentSolversSendNoClicks() {
		for (f in listOf("kotlin/features/experiments/apexchrono/ApexChrono.kt", "kotlin/features/enchanting/ExperimentSolvers.kt")) {
			val s = src(f)
			assertTrue(!directClick.containsMatchIn(s) && !s.contains("ApexClickDelay") && !s.contains("SilentClick") && !s.contains("MenuClicks"), "$f must not click")
		}
		assertTrue(!File(root, "kotlin/features/experiments/ExperimentAutoClick.kt").exists())
	}

	@Test fun autoBuyAndAutoScansAreGone() {
		for (f in listOf("kotlin/features/auction/autobuy/AutoBuy.kt", "kotlin/features/auction/autobuy/AutoBuyLedger.kt",
			"kotlin/features/auction/autobuy/AutoBuySource.kt", "kotlin/features/museum/MuseumAutoScan.kt",
			"kotlin/features/museum/MuseumAutoDonate.kt", "kotlin/features/inventory/storageoverlay/StorageAutoScan.kt", "kotlin/util/ScanSupport.kt"))
			assertTrue(!File(root, f).exists(), "$f must be deleted")
		val code = root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.map { it.readText() }.joinToString("\n")
		for (bad in listOf("subcommand(\"autobuy\"", "addSubcommand(\"autobuy\"", "\"auto-buy\"", "AutoBuy.", "MuseumAutoScan", "StorageAutoScan", "MuseumAutoDonate"))
			assertTrue(!code.contains(bad), "'$bad' still in the code")
		val tr = File(root.parentFile.parentFile, "translations/en_us.json").readText()
		for (bad in listOf("apex.config.auto-buy.", "museum-helper.auto-buy", "museum-helper.auto-scan", "museum-helper.auto-donate", "storage-auto-scan"))
			assertTrue(!tr.contains(bad), "translation '$bad' still there")
	}

	@Test fun noOldDelaysAnywhere() {
		root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { f ->
			val s = f.readText()
			assertTrue(!s.contains("MenuClickDelay"), "${f.path} still uses the removed MenuClickDelay")
			if (!f.path.endsWith("ApexClickDelay.kt"))
				assertTrue(!s.contains("delayedExecutor("), "${f.path} schedules its own delayed click; use ApexClickDelay")
		}
		val tr = File(root.parentFile.parentFile, "translations/en_us.json").readText()
		assertTrue(!tr.contains("auto-buy.delay-ms"), "old auto-buy delay key still translated")
		assertTrue(!Regex("""0[-–]50 ms""").containsMatchIn(tr), "old 0-50 ms text left in translations")
	}
}
