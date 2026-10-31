package dev.ashtonite365.apex.test.features.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageNavButtons
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePageSlot
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageNavButtons.Nav

class StorageNavButtonsTest {
	private val ox = 200
	private val oy = 50

	private val page1 = listOf("Close", "Go Back", null, null, null, "First Page", "Previous Page", "Next Page", "Last Page") +
		List(45) { "Enchanted Book" }

	private val page2 = listOf("Next Page", "Go Back", "Last Page", null, null, null, null, null, "Close") + List(45) { null }

	@Test
	fun eachButtonHitTestsItsOwnDrawnRect() {
		for (nav in Nav.entries) {
			val r = StorageNavButtons.rect(ox, oy, nav)
			assertEquals(nav, StorageNavButtons.hit(ox, oy, r[0].toDouble(), r[1].toDouble()), "$nav top-left")
			assertEquals(nav, StorageNavButtons.hit(ox, oy, r[0] + r[2] - 0.5, r[1] + r[3] - 0.5), "$nav bottom-right")
			assertNull(StorageNavButtons.hit(ox, oy, (r[0] + r[2]).toDouble(), r[1] + 1.0), "$nav right edge exclusive")
		}

		val first = StorageNavButtons.rect(ox, oy, Nav.FIRST)
		assertNull(StorageNavButtons.hit(ox, oy, first[0] + 1.0, first[1] + first[3] + 0.5))
		assertNull(StorageNavButtons.hit(ox, oy, ox + 5.0, oy + 5.0))
	}

	@Test
	fun orderIsBackFirstPreviousNextLast() {
		assertEquals(listOf(Nav.BACK, Nav.FIRST, Nav.PREVIOUS, Nav.NEXT, Nav.LAST), Nav.entries.sortedBy { StorageNavButtons.rect(ox, oy, it)[1] })
	}

	@Test
	fun slotsMatchedByNameNotIndex() {
		assertEquals(mapOf(Nav.FIRST to 5, Nav.LAST to 8, Nav.PREVIOUS to 6, Nav.NEXT to 7, Nav.BACK to 1),
			StorageNavButtons.resolve(page1))
		assertEquals(mapOf(Nav.LAST to 2, Nav.NEXT to 0, Nav.BACK to 1), StorageNavButtons.resolve(page2))
		assertEquals(mapOf(Nav.NEXT to 3), StorageNavButtons.resolve(listOf(null, "Close", "x", " next page ")))
	}

	@Test
	fun missingSlotDisablesButton() {
		val r = StorageNavButtons.rect(ox, oy, Nav.PREVIOUS)
		assertNull(StorageNavButtons.target(ox, oy, r[0] + 2.0, r[1] + 2.0, true, page2))
	}

	@Test
	fun eventRoutingOnlyLeftClicksForward() {
		val r = StorageNavButtons.rect(ox, oy, Nav.NEXT)
		assertEquals(7, StorageNavButtons.target(ox, oy, r[0] + 2.0, r[1] + 2.0, true, page1))
		assertNull(StorageNavButtons.target(ox, oy, r[0] + 2.0, r[1] + 2.0, false, page1))
		assertNull(StorageNavButtons.target(ox, oy, ox + 20.0, oy + 20.0, true, page1))
	}

	@Test
	fun clicksStillWorkAfterPageSwitch() {
		val next = StorageNavButtons.rect(ox, oy, Nav.NEXT)
		val back = StorageNavButtons.rect(ox, oy, Nav.BACK)
		assertEquals(7, StorageNavButtons.target(ox, oy, next[0] + 1.0, next[1] + 1.0, true, page1))

		assertEquals(0, StorageNavButtons.target(ox, oy, next[0] + 1.0, next[1] + 1.0, true, page2))
		assertEquals(1, StorageNavButtons.target(ox, oy, back[0] + 1.0, back[1] + 1.0, true, page2))

		assertEquals(7, StorageNavButtons.target(ox, oy, next[0] + 1.0, next[1] + 1.0, true, page1))
	}

	private fun bp(n: Int) = StoragePageSlot(9 + n - 1)
	private fun ec(n: Int) = StoragePageSlot(n - 1)

	private val unlocked = (1..3).map { ec(it) } + (1..18).map { bp(it) }

	@Test
	fun realHypixelArrowNamesResolve() {
		val names = listOf("Close", "Back", null, null, null, "« First Page", "← Previous Page", "Next Page →", "Last Page »")
		assertEquals(mapOf(Nav.BACK to 1, Nav.FIRST to 5, Nav.PREVIOUS to 6, Nav.NEXT to 7, Nav.LAST to 8), StorageNavButtons.resolve(names))
		assertEquals(mapOf(Nav.NEXT to 0), StorageNavButtons.resolve(listOf("§aNext Page §7→")))
	}

	@Test
	fun firstGoesToBackpackOne() {
		assertEquals(bp(1), StorageNavButtons.backpackTarget(Nav.FIRST, bp(7), unlocked))
		assertNull(StorageNavButtons.backpackTarget(Nav.FIRST, bp(1), unlocked))
	}

	@Test
	fun lastGoesToHighestUnlockedBackpackDetectedFromData() {
		assertEquals(bp(18), StorageNavButtons.backpackTarget(Nav.LAST, bp(3), unlocked))
		assertEquals(bp(5), StorageNavButtons.backpackTarget(Nav.LAST, bp(2), (1..5).map { bp(it) }))
		assertNull(StorageNavButtons.backpackTarget(Nav.LAST, bp(18), unlocked))
	}

	@Test
	fun nextAndPreviousStepAndClamp() {
		assertEquals(bp(5), StorageNavButtons.backpackTarget(Nav.NEXT, bp(4), unlocked))
		assertEquals(bp(3), StorageNavButtons.backpackTarget(Nav.PREVIOUS, bp(4), unlocked))
		assertNull(StorageNavButtons.backpackTarget(Nav.NEXT, bp(18), unlocked))
		assertNull(StorageNavButtons.backpackTarget(Nav.PREVIOUS, bp(1), unlocked))
	}

	@Test
	fun enderChestPagesExcluded() {

		assertNull(StorageNavButtons.backpackTarget(Nav.PREVIOUS, bp(1), unlocked))
		for (nav in listOf(Nav.FIRST, Nav.PREVIOUS, Nav.NEXT, Nav.LAST)) for (n in 1..18)
			StorageNavButtons.backpackTarget(nav, bp(n), unlocked)?.let { assert(it.isBackPack) { "$nav from $n -> $it" } }

		assertEquals(bp(1), StorageNavButtons.backpackTarget(Nav.FIRST, ec(3), unlocked))
		assertEquals(bp(1), StorageNavButtons.backpackTarget(Nav.NEXT, ec(3), unlocked))
		assertEquals(bp(18), StorageNavButtons.backpackTarget(Nav.LAST, ec(2), unlocked))
		assertNull(StorageNavButtons.backpackTarget(Nav.PREVIOUS, ec(1), unlocked))

		assertNull(StorageNavButtons.backpackTarget(Nav.NEXT, ec(1), (1..3).map { ec(it) }))
		assertNull(StorageNavButtons.backpackTarget(Nav.BACK, bp(2), unlocked))
	}

	@Test
	fun currentBackpackCountsEvenIfNotYetSaved() {
		assertEquals(bp(2), StorageNavButtons.backpackTarget(Nav.PREVIOUS, bp(4), listOf(bp(1), bp(2))))
	}
}
