// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.enchanting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.enchanting.SuperpairsFlipDetector

class SuperpairsCardFlipTest {
	@Test
	fun flipOnlyAfterSeenHidden() {
		val d = SuperpairsFlipDetector()
		assertEquals(emptyList<Int>(), d.update(mapOf(10 to false, 11 to true), 20))
		assertEquals(listOf(10), d.update(mapOf(10 to true, 11 to true), 19))
		assertEquals(emptyList<Int>(), d.update(mapOf(10 to true), 19))
	}

	@Test
	fun frozenAtZeroClicks() {
		val d = SuperpairsFlipDetector()
		d.update(mapOf(10 to false, 12 to false), 1)
		assertEquals(listOf(10), d.update(mapOf(10 to true, 12 to false), 0))
		assertEquals(emptyList<Int>(), d.update(mapOf(10 to true, 12 to true), 0))
	}

	@Test
	fun grid() {
		val g = SuperpairsFlipDetector.gridSlots("Superpairs (Metaphysical)")!!
		assertEquals(28, g.size); assertTrue(9 !in g && 10 in g && 44 !in g)
		assertEquals(20, SuperpairsFlipDetector.gridSlots("Superpairs (Grand)")!!.size)
		assertEquals(14, SuperpairsFlipDetector.gridSlots("Superpairs (Beginner)")!!.size)
		assertNull(SuperpairsFlipDetector.gridSlots("Superpairs ➜ Stakes"))
	}

	@Test
	fun hiddenAndBook() {
		assertTrue(SuperpairsFlipDetector.isHiddenName("?"))
		assertTrue(SuperpairsFlipDetector.isHiddenName("Click a second button!"))
		assertFalse(SuperpairsFlipDetector.isHiddenName("Enchanted Book"))
		assertEquals("Smite VII", SuperpairsFlipDetector.ultraRareBook(listOf("XX ULTRA-RARE BOOK! XX", "", "Smite VII")))
		assertNull(SuperpairsFlipDetector.ultraRareBook(listOf("Smite V")))
	}
}
