// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuFailover
import dev.ashtonite365.apex.features.menus.MenuLog

class NoAutoBackOnFailureTest {
	@Test
	fun renderThrowFailsOnceAndOnlyReports() {
		val reports = mutableListOf<String>()
		val f = MenuFailover { reason, _ -> reports += MenuLog.fallbackLine("skills", "Your Skills", reason) }
		val r = f.guard("render", "orElse") { throw IllegalStateException("boom") }
		assertEquals("orElse", r)
		assertTrue(f.failed)

		assertEquals("orElse", f.guard("render", "orElse") { "apex" })
		f.fail("parse failed")
		assertEquals(1, reports.size)
		assertTrue(reports[0].startsWith("[Apex] skills: render threw IllegalStateException: boom"), reports[0])
	}

	@Test
	fun logLineFormat() {
		val l = MenuLog.fallbackLine("bank", "§6Bank Deposit", "no balance item")
		assertTrue(l.startsWith("[Apex] bank: no balance item"), l)
		assertTrue(l.contains("'Bank Deposit'"), l)
	}

	private val projectRoot: File by lazy {
		generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "src/main/kotlin").isDirectory }
			?: error("No project root (settings.gradle.kts + src/main/kotlin) above ${System.getProperty("user.dir")}")
	}

	private fun src(path: String) = File(projectRoot, "src/main/kotlin/$path").readText()
		.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
		.replace(Regex("(?m)(^|(?<=\\s))//.*$"), "")

	@Test
	fun failurePathsSendNoClicksAndNeverClose() {
		val fallback = src("features/menus/support/ApexFallback.kt").substringAfter("object ApexFallback")
		for (bad in listOf("clickLater", "ApexClickDelay", "closeContainer", "sendCommand", "runCommand", "setScreen", "MC.screen =", "Client.screen ="))
			assertFalse(fallback.contains(bad), "ApexFallback.handle must not '$bad'")

		assertFalse(src("features/menus/bank/BankMenu.kt").contains("BankOwner"))
		for (scan in listOf("features/inventory/storageoverlay/StorageAutoScan.kt", "features/museum/MuseumAutoScan.kt"))
			assertFalse(File(projectRoot, "src/main/kotlin/$scan").exists(), "$scan must be deleted")
	}
}
