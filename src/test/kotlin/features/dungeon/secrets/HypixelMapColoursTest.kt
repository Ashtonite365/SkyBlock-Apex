package dev.ashtonite365.apex.test.features.dungeon.secrets

import net.minecraft.world.level.material.MapColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours
import dev.ashtonite365.apex.features.dungeon.secrets.MapLayout
import dev.ashtonite365.apex.features.dungeon.secrets.MapLayout.DoorKind

class HypixelMapColoursTest {
	@Test
	fun coloursMatchTheVanillaMapColours() {
		for (id in listOf(18, 30, 34, 62, 63, 66, 74, 82, 85, 119))
			assertEquals(MapColor.getColorFromPackedId(id), HypixelMapColours.argb(id), "packed id $id")
		assertNull(HypixelMapColours.argb(0))
	}

	@Test
	fun doorKindsFromHypixelsIds() {
		assertEquals(DoorKind.WITHER, HypixelMapColours.doorKind(119))
		assertEquals(DoorKind.BLOOD, HypixelMapColours.doorKind(18))
		assertEquals(DoorKind.ENTRANCE, HypixelMapColours.doorKind(30))
		assertEquals(DoorKind.NORMAL, HypixelMapColours.doorKind(82))
		assertEquals(DoorKind.NORMAL, HypixelMapColours.doorKind(63))
		assertEquals(DoorKind.NORMAL, HypixelMapColours.doorKind(66))
		assertNull(HypixelMapColours.doorKind(null))
	}

	@Test
	fun doorColourIsTheStripMajorityNotOnePixel() {
		assertEquals(119, HypixelMapColours.doorColour(List(12) { 119 } + listOf(63, 0, 0, 0)))
		assertEquals(63, HypixelMapColours.doorColour(listOf(119) + List(9) { 63 } + List(6) { 0 }))
		assertEquals(119, HypixelMapColours.doorColour(listOf(119, 119, 63, 63)))
		assertNull(HypixelMapColours.doorColour(List(16) { 0 }))
	}

	@Test
	fun doorsUseHypixelColours() {
		assertEquals(MapColor.getColorFromPackedId(119), MapLayout.WITHER_DOOR)
		assertEquals(MapColor.getColorFromPackedId(18), MapLayout.BLOOD_DOOR)
		assertEquals(MapColor.getColorFromPackedId(30), MapLayout.ENTRANCE_DOOR)
	}

	@Test
	fun unenteredRoomsAreRecognised() {
		assert(HypixelMapColours.isUnentered(85))
		assert(HypixelMapColours.isUnentered(34))
		assert(!HypixelMapColours.isUnentered(63) && !HypixelMapColours.isUnentered(18))
	}
}
