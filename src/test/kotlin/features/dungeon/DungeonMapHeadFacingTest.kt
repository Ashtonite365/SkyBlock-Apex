// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap

class DungeonMapHeadFacingTest {
	private fun near(e: Float, a: Float) = assertEquals(e, a, 1e-4F)

	@Test
	fun yawToAngle() {
		near(180F, DungeonMap.headAngle(0F))
		near(270F, DungeonMap.headAngle(90F))
		near(0F, DungeonMap.headAngle(180F))
		near(90F, DungeonMap.headAngle(-90F))
		near(90F, DungeonMap.headAngle(630F))
	}

	@Test
	fun decorationRotation() {
		near(0F, DungeonMap.decorationYaw(0))
		near(90F, DungeonMap.decorationYaw(4))
		near(337.5F, DungeonMap.decorationYaw(15))
		near(90F, DungeonMap.teammateYaw(null, 4))
		near(12F, DungeonMap.teammateYaw(12F, 4))
	}

	@Test
	fun pipPlacement() {
		val up = DungeonMap.pipOffset(0F, 5F); near(0F, up.first); near(-5F, up.second)
		val right = DungeonMap.pipOffset(90F, 5F); near(5F, right.first); near(0F, right.second)
		val down = DungeonMap.pipOffset(DungeonMap.headAngle(0F), 5F); near(0F, down.first); near(5F, down.second)
		val left = DungeonMap.pipOffset(DungeonMap.headAngle(90F), 5F); near(-5F, left.first); near(0F, left.second)
	}

	@Test
	fun pipPixelInFrontOfEachHead() {

		assertEquals(0 to -5, DungeonMap.pipPixel(true, 0F))
		assertEquals(0 to -5, DungeonMap.pipPixel(true, 123F))

		assertEquals(0 to -5, DungeonMap.pipPixel(false, DungeonMap.headAngle(180F)))
		assertEquals(5 to 0, DungeonMap.pipPixel(false, DungeonMap.headAngle(-90F)))
		assertEquals(0 to 5, DungeonMap.pipPixel(false, DungeonMap.headAngle(0F)))
		assertEquals(-5 to 0, DungeonMap.pipPixel(false, DungeonMap.headAngle(90F)))
		assertEquals(4 to -4, DungeonMap.pipPixel(false, 45F))
	}
}
