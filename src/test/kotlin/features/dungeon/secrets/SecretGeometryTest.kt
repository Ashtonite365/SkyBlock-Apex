// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon.secrets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.secrets.Pos3
import dev.ashtonite365.apex.features.dungeon.secrets.SuperboomRule
import dev.ashtonite365.apex.features.dungeon.secrets.TombBlock
import dev.ashtonite365.apex.features.dungeon.secrets.Tombs

class SecretGeometryTest {

	private val wall = (0..2).flatMap { x -> (70..72).map { y -> Pos3(x, y, 0) } }
	private val viewer = Triple(1.5, 71.0, -5.0)

	@Test
	fun ledgeWithNothingBehindIsNotSuperboom() {
		assertNull(SuperboomRule.reason(wall, emptyList(), emptyList(), viewer))

		assertNull(SuperboomRule.reason(wall, listOf(Pos3(1, 70, -3)), emptyList(), viewer))

		assertNull(SuperboomRule.reason(wall, listOf(Pos3(1, 70, 14)), emptyList(), viewer))
	}

	@Test
	fun chestBehindMakesASuperboomWall() {
		val r = SuperboomRule.reason(wall, listOf(Pos3(1, 70, 4)), emptyList(), viewer)
		assertNotNull(r); assertEquals("chest", r!!.what); assertEquals(Pos3(1, 70, 4), r.at)

		assertNotNull(SuperboomRule.reason(wall, listOf(Pos3(7, 71, 2)), emptyList(), viewer))

		assertNull(SuperboomRule.reason(wall, listOf(Pos3(1, 70, 6)), emptyList(), viewer, SuperboomRule.Params(2, 3, 3)))
	}

	@Test
	fun cryptBehindMakesASuperboomWall() {
		val crypt = setOf(Pos3(1, 70, 3), Pos3(2, 70, 3))
		assertEquals("crypt", SuperboomRule.reason(wall, emptyList(), listOf(crypt), viewer)!!.what)
		assertNull(SuperboomRule.reason(wall, emptyList(), listOf(setOf(Pos3(1, 70, 12))), viewer))
	}

	private fun world(open: Boolean = false, mossy: Boolean = true): (Int, Int, Int) -> TombBlock = { x, y, z ->
		when {
			y == 70 && z == 10 && x in 10..11 -> TombBlock.AIR
			open && y == 71 && z == 10 && x == 11 -> TombBlock.AIR
			open && y >= 71 -> TombBlock.AIR
			y == 71 && z == 10 && x == 10 -> TombBlock.CRACKED_BRICK
			!mossy -> TombBlock.OTHER_SOLID
			else -> TombBlock.BRICK
		}
	}

	@Test
	fun tombShapes() {
		val tombs = Tombs.find(listOf(Pos3(10, 71, 10)), world())
		assertEquals(1, tombs.size)
		assertEquals(setOf(Pos3(10, 70, 10), Pos3(11, 70, 10)), tombs.single().air)
		assertEquals(setOf(Pos3(10, 71, 10)), tombs.single().cracked)
		assertEquals(0, Tombs.find(listOf(Pos3(10, 71, 10)), world(open = true)).size)
		assertEquals(0, Tombs.find(listOf(Pos3(10, 71, 10)), world(mossy = false)).size)
		val unloaded: (Int, Int, Int) -> TombBlock = { x, y, z -> if (x > 11) TombBlock.UNLOADED else world()(x, y, z) }
		assertEquals(0, Tombs.find(listOf(Pos3(10, 71, 10)), unloaded).size)
	}
}
