package dev.ashtonite365.apex.test.features.misc

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip

class ItemModelFallbackTest {
	private val stick = Identifier.parse("minecraft:stick")
	private val hyp = Identifier.parse("hypixel_skyblock:item/island_relevant/winter/weapons/ice_spray_wand")

	@Test
	fun unloadedHypixelModelFallsBackWithSkipOff() {

		assertEquals(stick, ServerResourcePackSkip.resolveItemModel(hyp, stick, setOf()))
		assertEquals(stick, ServerResourcePackSkip.resolveItemModel(Identifier.parse("hypixel:aspect_of_the_end"), stick, setOf(stick)))
	}

	@Test
	fun loadedModelUnchanged() {
		assertEquals(hyp, ServerResourcePackSkip.resolveItemModel(hyp, stick, setOf(hyp)))
		val sword = Identifier.parse("minecraft:diamond_sword")
		assertEquals(sword, ServerResourcePackSkip.resolveItemModel(sword, stick, setOf()))
	}
}
