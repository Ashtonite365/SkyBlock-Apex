// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.h6.NpcShopChain
import dev.ashtonite365.apex.features.menus.museum.MuseumTabStep
import dev.ashtonite365.apex.features.menus.support.KeyRepeatGate

class OneInputOneClickTest {
	private val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin")
	private val CLICK = Regex("""clickSlot\(|\bforward\(|MenuClicks\.|ApexClickDelay\.(send|clickLater)|handleContainerInput|sendCommand\(|sendServerCommand|sendServerChat|runCommand\(|runCommandUnchecked|chatAsPlayer|click(Left|Right|Middle)MouseButton|swapWithHotBar""")

	private val TICK_REVIEWED = setOf("MenuChain.kt", "Client.kt", "PlusCommands.kt", "PlusCommandExtras.kt")

	private fun kt() = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

	private fun bodies(text: String, name: String): List<String> {
		val out = mutableListOf<String>()
		val lines = text.lines()
		lines.forEachIndexed { i, l ->
			if (!Regex("""\bfun $name\(""").containsMatchIn(l)) return@forEachIndexed
			val indent = l.takeWhile { it == '\t' || it == ' ' }
			if (l.trimEnd().endsWith("{").not() && !l.contains("{")) { out += l; return@forEachIndexed }
			if (!l.trimEnd().endsWith("{")) { out += l; return@forEachIndexed }
			val b = StringBuilder(l)
			for (j in i + 1 until lines.size) { b.append('\n').append(lines[j]); if (lines[j] == "$indent}") break }
			out += b.toString()
		}
		return out
	}

	@Test fun scrollDragAndReleaseNeverClick() {
		val bad = mutableListOf<String>()
		for (f in kt()) {
			val t = f.readText()
			for (n in listOf("mouseScrolled", "mouseDragged", "mouseReleased"))
				for (b in bodies(t, n)) if (CLICK.containsMatchIn(b)) bad += "${f.name}:$n"
		}
		assertEquals(emptyList<String>(), bad, "scroll-to-click / drag-to-click / release-click found")
	}

	@Test fun tickHandlersNeverClick() {

		val bad = kt().filter { f ->
			val t = f.readText()
			(t.contains("TickEvent") || t.contains("ClientTickEvents")) && CLICK.containsMatchIn(t) && f.name !in TICK_REVIEWED
		}.map { it.name }
		assertEquals(emptyList<String>(), bad)
	}

	@Test fun noRepeatOrDelayedFollowUpTimers() {

		val later = Regex("""ApexClickDelay\.(later|laterMs)\(""")
		val users = kt().filter { later.containsMatchIn(it.readText()) }.map { it.name }.toSet()
		assertEquals(setOf("MenuChain.kt", "ApexClickDelay.kt") intersect users, users)
		assertFalse(File(src, "features/inventory/InventoryMouseTweaks.kt").exists())
		assertFalse(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/java/dev/ashtonite365/apex/mixins/MouseTweaksLikePatch.java").exists())
	}

	@Test fun heldKeyRepeatSendsOnce() {
		val g = KeyRepeatGate()
		var down = true
		assertTrue(g.press(262))
		repeat(20) { g.frame { down }; assertFalse(g.press(262)) }
		down = false; g.frame { down }
		assertTrue(g.press(262))
		assertTrue(g.press(263))
	}

	@Test fun museumTabIsOneStep() {
		assertEquals(20, MuseumTabStep.decide(isOverview = true, categorySlot = 20, backSlot = 48, active = false))
		assertEquals(48, MuseumTabStep.decide(isOverview = false, categorySlot = 20, backSlot = 48, active = false))
		assertNull(MuseumTabStep.decide(isOverview = false, categorySlot = 20, backSlot = 48, active = true))
		assertNull(MuseumTabStep.decide(isOverview = false, categorySlot = 20, backSlot = null, active = false))
	}

	@Test fun npcShopTabIsOneGoBack() {
		assertEquals(NpcShopChain.Step(45), NpcShopChain.goBack(45))
	}

	@Test fun bazaarHasNoCommandStep() {

		for (f in File(src, "features/apexbzmenu").listFiles()!!.filter { it.extension == "kt" }) {
			val t = f.readText()
			for (bad in listOf("runCommand", "sendCommand", "sendServerCommand", "MenuChain", "clickLater", "SignText", "setMessage("))
				assertFalse(t.contains(bad), "${f.name} contains $bad")
		}
	}

	@Test fun lavaHookFloatIsGone() {
		val t = File(src, "features/fishing/FishingRisky.kt").readText()
		assertFalse(t.contains("lava-hook-float") || t.contains("lavaHookFloat") || t.contains("setDeltaMovement"))
		assertFalse(dev.ashtonite365.apex.test.testutil.RepoFile("translations/en_us.json").readText().contains("lava-hook-float"))
	}

	@Test fun noPacketOnlyContainerClicks() {

		val raw = (kt() + dev.ashtonite365.apex.test.testutil.RepoFile("src/main/java").walkTopDown().filter { it.isFile && it.extension == "java" })
			.filter { val t = it.readText(); t.contains("ServerboundContainerClickPacket(") || t.contains("SilentClick") }.map { it.name }
		assertEquals(emptyList<String>(), raw)
	}
}
