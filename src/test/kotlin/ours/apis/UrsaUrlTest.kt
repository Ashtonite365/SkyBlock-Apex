// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.apis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.apis.UrsaManager

class UrsaUrlTest {
	@Test
	fun urlKeepsTheOriginalShape() {
		assertEquals("https://h.example/v1/hypixel/player", UrsaManager.urlFor("h.example", listOf("v1", "/hypixel/", "player/")).toString())
		assertEquals("https://h.example/", UrsaManager.urlFor("h.example", listOf()).toString())
		assertEquals("https://h.example/a/b/c", UrsaManager.urlFor("h.example", "a/b/c".split("/")).toString())
		assertEquals("https://h.example//x", UrsaManager.urlFor("h.example", listOf("", "x")).toString())
	}
}
