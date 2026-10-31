// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.TagBodyPairing
import dev.ashtonite365.apex.features.dungeon.TagBodyPairing.Body
import dev.ashtonite365.apex.features.dungeon.TagBodyPairing.Kind
import dev.ashtonite365.apex.features.dungeon.TagBodyPairing.Tag

class TagBodyPairingTest {
	private fun mob(id: Int, x: Double, y: Double, z: Double, h: Double = 1.95, kind: Kind = Kind.MOB, invisible: Boolean = false) =
		Body(id, x, y, z, h, kind, invisible)

	private val tag = Tag(11, 0.0, 71.95, 0.0)

	@Test
	fun underTagBeatsCloserNeighbour() {
		val under = mob(5, 0.1, 70.0, -0.1)

		val neighbour = mob(6, 0.8, 70.9, 0.0, h = 0.9)
		assertEquals(mapOf(11 to 5), TagBodyPairing.assign(listOf(tag), listOf(neighbour, under)))
		assertNull(TagBodyPairing.score(tag, neighbour))
	}

	@Test
	fun headTopClosestToTheExpectedOffsetWins() {
		val right = mob(20, 0.0, 70.0, 0.0)
		val tooLow = mob(21, 0.0, 66.0, 0.0)
		assertEquals(mapOf(11 to 20), TagBodyPairing.assign(listOf(tag), listOf(tooLow, right)))
		assertNull(TagBodyPairing.score(tag, tooLow))
		assertNull(TagBodyPairing.score(tag, mob(22, 0.0, 72.5, 0.0)))
	}

	@Test
	fun teammatePlayerExcluded() {
		val teammate = mob(7, 0.0, 70.0, 0.0, h = 1.8, kind = TagBodyPairing.playerKind(4, false))
		assertEquals(Kind.REAL_PLAYER, teammate.kind)
		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(teammate)).isEmpty())
		val me = mob(8, 0.0, 70.0, 0.0, h = 1.8, kind = TagBodyPairing.playerKind(4, true))
		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(me)).isEmpty())
	}

	@Test
	fun shadowAssassinTypePlayerMobIncluded() {
		val assassin = mob(9, 0.0, 70.15, 0.0, h = 1.8, kind = TagBodyPairing.playerKind(2, false))
		assertEquals(Kind.NPC_PLAYER, assassin.kind)
		assertEquals(mapOf(11 to 9), TagBodyPairing.assign(listOf(tag), listOf(assassin)))
	}

	@Test
	fun invisibleBodiesSkipped() {
		val hidden = mob(10, 0.0, 70.15, 0.0, h = 1.8, kind = Kind.NPC_PLAYER, invisible = true)
		assertFalse(TagBodyPairing.eligible(hidden))
		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(hidden)).isEmpty())
		assertFalse(TagBodyPairing.eligible(mob(12, 0.0, 70.0, 0.0, kind = Kind.ARMOR_STAND)))
	}

	@Test
	fun oneToOneAssignment() {
		val a = Tag(31, 0.0, 71.95, 0.0)
		val b = Tag(41, 0.3, 72.3, 0.0)
		val bodyA = mob(30, 0.0, 70.0, 0.0)
		val bodyB = mob(40, 0.4, 70.35, 0.0)
		val out = TagBodyPairing.assign(listOf(a, b), listOf(bodyA, bodyB))
		assertEquals(mapOf(31 to 30, 41 to 40), out)
		assertEquals(out.values.size, out.values.toSet().size)

		val single = TagBodyPairing.assign(listOf(a, b), listOf(bodyA))
		assertEquals(1, single.size)
		assertEquals(30, single[31])
	}

	@Test
	fun standIdAfterBodyIdBreaksTies() {
		val t = Tag(51, 0.0, 71.95, 0.0)
		val sibling = mob(50, 0.2, 70.0, 0.0)
		val other = mob(60, 0.0, 70.0, 0.1)
		assertEquals(mapOf(51 to 50), TagBodyPairing.assign(listOf(t), listOf(other, sibling)))
	}

	@Test
	fun trackerReleasesAWrongBodyOnRefresh() {

		val neighbour = mob(70, 1.2, 70.0, 0.0)
		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(neighbour)).isEmpty())

		val real = mob(10, 0.0, 70.0, 0.0)
		assertEquals(mapOf(11 to 10), TagBodyPairing.assign(listOf(tag), listOf(neighbour, real)))

		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(neighbour, real.copy(x = 2.0))).isEmpty())

		assertTrue(TagBodyPairing.assign(listOf(tag), listOf(real.copy(invisible = true))).isEmpty())
	}

	@Test
	fun cachedPairDoesNotHopToANeighbour() {
		val first = mob(80, 0.0, 70.0, 0.0)
		val prev = TagBodyPairing.assign(listOf(tag), listOf(first))
		assertEquals(mapOf(11 to 80), prev)

		val drifted = first.copy(x = 0.3)
		val better = mob(81, 0.05, 70.0, 0.0)
		assertEquals(mapOf(11 to 81), TagBodyPairing.assign(listOf(tag), listOf(drifted, better)))
		assertEquals(mapOf(11 to 80), TagBodyPairing.assign(listOf(tag), listOf(drifted, better), prev))

		assertEquals(mapOf(11 to 81), TagBodyPairing.assign(listOf(tag), listOf(first.copy(x = 1.5), better), prev))

		val tag2 = Tag(13, 0.1, 71.95, 0.0)
		assertEquals(mapOf(11 to 80), TagBodyPairing.assign(listOf(tag, tag2), listOf(first), mapOf(11 to 80, 13 to 80)))
	}

	@Test
	fun roomOnlyOffByDefault() {
		PuzzleSolvers.TConfig.explicitDefaultLoad()
		assertEquals(false, PuzzleSolvers.TConfig.allOptions.getValue("starred-room-only").default())
		assertFalse(PuzzleSolvers.TConfig.allOptions.getValue("starred-room-only").riskLevel.isRisky)

		assertEquals(false, PuzzleSolvers.TConfig.allOptions.getValue("starred-mobs").default())
		assertTrue(PuzzleSolvers.TConfig.allOptions.getValue("starred-mobs").riskLevel.isRisky)

		assertTrue(PuzzleSolvers.inStarredRoom(false, -190.0, -190.0, -150.0, -190.0))
		assertFalse(PuzzleSolvers.inStarredRoom(true, -190.0, -190.0, -150.0, -190.0))
		assertTrue(PuzzleSolvers.inStarredRoom(true, -190.0, -190.0, -180.0, -175.0))
	}
}
