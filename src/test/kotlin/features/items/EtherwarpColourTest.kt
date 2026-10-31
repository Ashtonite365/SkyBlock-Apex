package dev.ashtonite365.apex.test.features.items

import io.github.notenoughupdates.moulconfig.ChromaColour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EtherwarpColourTest {
	@Test
	fun pickedColourKeepsArgbAndWireIsOpaque() {
		val picked = ChromaColour.fromStaticRGB(10, 200, 30, 60).getEffectiveColourRGB()
		assertEquals(0x3C0AC81E, picked)
		assertEquals(0xFF0AC81E.toInt(), picked or (0xFF shl 24))
	}
}
