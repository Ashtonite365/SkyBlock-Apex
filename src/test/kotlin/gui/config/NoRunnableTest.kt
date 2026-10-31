// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.config

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.ClickHandler

class NoRunnableTest {
	@Test fun minusOneIsNone() {
		assertTrue(ClickHandler.isNoRunnable(-1))
		assertFalse(ClickHandler.isNoRunnable(0))
		assertFalse(ClickHandler.isNoRunnable(7))
	}

	@Test fun editorChecksBeforeReporting() {
		val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set"))
		val file = project.resolve("archive/old-firmament-menu/compat-moulconfig/java/MCConfigEditorIntegration.kt")
		org.junit.jupiter.api.Assumptions.assumeTrue(file.isFile, "archive not in this source tree")
		val src = file.readText()
		val body = src.substringAfter("override fun executeRunnable(").substringBefore("override fun")
		assertTrue(body.indexOf("isNoRunnable(runnableId)) return") in 0 until body.indexOf("reportFault"), body)
	}
}
