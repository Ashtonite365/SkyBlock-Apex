// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.craft

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.craft.CraftHandoff
import dev.ashtonite365.apex.features.menus.craft.CraftRequest

class CraftHandoffTest {
	private var t = 0L
	private val sent = mutableListOf<String>()
	private var open = false
	private val e = "CORE_SENTINEL_EMPTY" to 0.0

	private val variantA = CraftRequest.of("ENCHANTED_OAK_LOG", 1, List(5) { "ENCHANTED_OAK_WOOD" to 32.0 } + List(4) { e })
	private val variantB = CraftRequest.of("ENCHANTED_OAK_LOG", 1, listOf("OAK_LOG" to 32.0, "OAK_LOG" to 32.0, e, "OAK_LOG" to 32.0, "OAK_LOG" to 32.0, e, e, e, e))

	@BeforeEach fun setUp() {
		CraftHandoff.resetForTests(); t = 1_000_000L
		CraftHandoff.now = { t }; CraftHandoff.sendCommand = { sent += it }; CraftHandoff.craftWindowOpen = { open }
	}
	@AfterEach fun tearDown() = CraftHandoff.resetForTests()

	@Test
	fun theExactVariantIsDeliveredNotJustTheOutput() {
		assertEquals(variantA.outputId, variantB.outputId); assertNotEquals(variantA.cells, variantB.cells)
		CraftHandoff.request(variantB)
		val got = CraftHandoff.take()!!
		assertEquals(variantB, got)
		assertEquals(listOf("OAK_LOG" to 32, "OAK_LOG" to 32, null, "OAK_LOG" to 32, "OAK_LOG" to 32, null, null, null, null), got.cells)
		assertNull(CraftHandoff.take())
	}

	@Test
	fun opensCraftOnceThenLoadsWhenTheWindowArrives() {
		assertEquals(CraftHandoff.Outcome.OPENING, CraftHandoff.request(variantA))
		t += 200; CraftHandoff.request(variantB)
		assertEquals(listOf("craft"), sent)
		open = true; t += 300
		assertEquals(variantB, CraftHandoff.take())
	}

	@Test
	fun alreadyOpenSendsNothing() {
		open = true
		assertEquals(CraftHandoff.Outcome.LOADED, CraftHandoff.request(variantA))
		assertEquals(emptyList<String>(), sent); assertEquals(variantA, CraftHandoff.take())
	}

	@Test
	fun staleRequestsAreDroppedNeverReplaced() {
		CraftHandoff.request(variantA)
		t += CraftHandoff.TIMEOUT_MS + 1
		assertNull(CraftHandoff.take()); assertNull(CraftHandoff.peek())
	}

	@Test
	fun layoutAlwaysHasNineCellsAndWindowCheck() {
		assertEquals(9, CraftRequest.of("STICK", 4, listOf("OAK_PLANKS" to 1.0, "OAK_PLANKS" to 1.0)).cells.size)
		assertEquals(true, CraftHandoff.isCraftWindow("Craft Item", 90))
		assertEquals(false, CraftHandoff.isCraftWindow("Craft Item", 63))
		assertEquals(false, CraftHandoff.isCraftWindow("Wood Minion I", 90))
	}
}

class CraftLayoutTest {
	private val L = dev.ashtonite365.apex.features.menus.craft.CraftLayout

	@Test
	fun noLeftPanelWithJei() {
		assertEquals(false, L.showList(jeiLoaded = true)); assertEquals(0, L.listWidth(false))
		assertEquals(true, L.showList(jeiLoaded = false)); assertEquals(L.LIST_W, L.listWidth(true))
	}

	@Test
	fun jeiLayoutIsCentredAndFits() {
		val x = 100; val w = 470; val pad = 6
		val gx = L.gridX(x, w, pad, showList = false)
		val left = gx - 14 - x; val right = x + w - (gx - 14 + L.CONTENT_W)
		assertTrue(kotlin.math.abs(left - right) <= 1, "centred: $left vs $right")
		assertTrue(gx < L.gridX(x, w, pad, showList = true))
		assertTrue(L.gridX(x, 200, pad, false) >= x + pad + 5)
	}

	@Test
	fun jeiOverlayOnTopOfOpenCraftWindowCountsAsOpen() {
		assertEquals(true, CraftHandoff.stillOpen(7, 7))
		assertEquals(false, CraftHandoff.stillOpen(7, 8))
		assertEquals(false, CraftHandoff.stillOpen(null, 7))
		assertEquals(false, CraftHandoff.stillOpen(7, 0))
		assertEquals(false, CraftHandoff.stillOpen(7, null))
	}
}
