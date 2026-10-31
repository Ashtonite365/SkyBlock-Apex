package dev.ashtonite365.apex.test.features.museum

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.math.Rectangle
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.util.ItemId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MuseumBuyClickTest {
	private fun withSender(block: (MutableList<String>) -> Unit) {
		val sent = mutableListOf<String>(); val old = MuseumHelper.sendCommand
		MuseumHelper.sendCommand = { sent += it }
		try { block(sent) } finally { MuseumHelper.sendCommand = old }
	}
	private val row = MuseumHelper.Missing("§fLapis Pickaxe", listOf(ItemId("LAPIS_PICKAXE")), "Weapons", 0)

	@Test fun leftClickOnBuySendsAhs() = withSender { sent ->
		val targets = listOf(Rectangle(100, 50, 24, 12) to MuseumHelper.buyAction(row))
		assertTrue(MuseumHelper.dispatchClick(targets, true, 110.0, 55.0, InputConstants.MOUSE_BUTTON_LEFT))
		assertEquals(listOf("ahs Lapis Pickaxe"), sent)
	}

	@Test fun leftIsNotZeroOn263() = withSender { sent ->

		val targets = listOf(Rectangle(0, 0, 50, 50) to MuseumHelper.buyAction(row))
		MuseumHelper.dispatchClick(targets, true, 5.0, 5.0, InputConstants.MOUSE_BUTTON_RIGHT)
		assertEquals(emptyList<String>(), sent)
		MuseumHelper.dispatchClick(targets, true, 5.0, 5.0, InputConstants.MOUSE_BUTTON_LEFT)
		assertEquals(1, sent.size)
	}

	@Test fun outsidePanelNotConsumed() = withSender { sent ->
		assertEquals(false, MuseumHelper.dispatchClick(emptyList(), false, 0.0, 0.0, InputConstants.MOUSE_BUTTON_LEFT))
		assertEquals(emptyList<String>(), sent)
	}
}
