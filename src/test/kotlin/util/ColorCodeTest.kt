package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import dev.ashtonite365.apex.util.stripSectionCodes

class ColorCodeTest {
	private class Case(val input: String, val expected: String, val keepFormatting: Boolean = false)

	private fun run(vararg cases: Case) = cases.map { case ->
		DynamicTest.dynamicTest("${case.input} (keep=${case.keepFormatting})") {
			assertEquals(case.expected, case.input.stripSectionCodes(case.keepFormatting))
		}
	}

	@TestFactory
	fun withoutCodes() = run(
		Case("", ""),
		Case("plain text 123", "plain text 123"),
		Case("no marker ✪ here", "no marker ✪ here"),
	)

	@TestFactory
	fun strippingEverything() = run(
		Case("§9", ""),
		Case("§6Gold§r §7grey", "Gold grey"),
		Case("§r§a[VIP§6+§a] Steve§f: hi", "[VIP+] Steve: hi"),
		Case("§5Hyperion §d✦§d✦", "Hyperion ✦✦"),
		Case("§lbold stays out§o", "bold stays out"),
	)

	@TestFactory
	fun danglingAndDoubledMarkers() = run(
		Case("§", ""),
		Case("x§", "x"),
		Case("§ex§", "x"),
		Case("§§x", "x"),
		Case("q§§", "q"),
		Case("a§§§bc", "ac"),
		Case("§", "", keepFormatting = true),
	)

	@TestFactory
	fun keepingFormattingCodes() = run(
		Case("§c§lBold§r red", "§lBold§r red", keepFormatting = true),
		Case("§k§m§n§0§1", "§k§m§n", keepFormatting = true),
		Case("w§l", "w§l", keepFormatting = true),
		Case("§o§§z", "§oz", keepFormatting = true),
		Case("§L§Oup§aper", "§L§Oupper", keepFormatting = true),
		Case("§2§r§b", "§r", keepFormatting = true),
	)
}
