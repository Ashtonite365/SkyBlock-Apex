package dev.ashtonite365.apex.test.features.kit.devices

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignLogic
import dev.ashtonite365.apex.features.kit.devices.SimonSaysState

class KitDevicesLogicTest {
	private fun l(y: Int, z: Int) = BlockPos(111, y, z)

	@Test
	fun simonSecondPhase() {
		val s = SimonSaysState(); s.firstPhase = false
		s.onLanternShown(l(120, 92)); s.onLanternShown(l(121, 93))
		assertEquals(listOf(BlockPos(110, 120, 92), BlockPos(110, 121, 93)), s.remaining())
		assertTrue(s.isCorrectClick(BlockPos(110, 120, 92)))
		assertFalse(s.isCorrectClick(BlockPos(110, 121, 93)))
		s.onButtonPressed(BlockPos(110, 120, 92))
		assertEquals(listOf(BlockPos(110, 121, 93)), s.remaining())
		s.onButtonPressed(BlockPos(110, 121, 93))
		assertTrue(s.remaining().isEmpty())
	}

	@Test
	fun simonFirstPhaseQuirk() {
		val s = SimonSaysState()
		s.onLanternShown(l(120, 92)); s.onLanternShown(l(121, 92))
		assertEquals(listOf(l(121, 92), l(120, 92)), s.order)
		s.onLanternShown(l(122, 92))
		assertEquals(listOf(l(121, 92), l(122, 92)), s.order)
	}

	@Test
	fun arrowAlign() {
		assertEquals(0, ArrowAlignLogic.indexOf(BlockPos(-2, 120, 75)))
		assertEquals(24, ArrowAlignLogic.indexOf(BlockPos(-2, 124, 79)))
		assertNull(ArrowAlignLogic.indexOf(BlockPos(-1, 120, 75)))
		assertEquals(3, ArrowAlignLogic.clicksNeeded(6, 1))
		val target = ArrowAlignLogic.SOLUTIONS[6]
		val rot = target.map { if (it == -1) -1 else (it + 8 - 2) % 8 }
		val c = ArrowAlignLogic.clicks(rot)
		assertEquals(target.count { it != -1 }, c.size)
		assertTrue(c.values.all { it == 2 })
		assertTrue(ArrowAlignLogic.clicks(target).isEmpty())
	}

	@Test
	fun simonAnnounceOnlyOnRoundFinish() {
		val s = SimonSaysState().apply { firstPhase = false }
		s.onLanternShown(BlockPos(111, 120, 92)); s.onLanternShown(BlockPos(111, 121, 93))
		assertEquals(null, s.roundFinishedBy(BlockPos(110, 120, 92)))
		assertEquals(null, s.roundFinishedBy(BlockPos(110, 122, 94)))
		s.onButtonPressed(BlockPos(110, 120, 92))
		assertEquals(2, s.roundFinishedBy(BlockPos(110, 121, 93)))
		assertEquals(null, SimonSaysState().roundFinishedBy(BlockPos(110, 120, 92)))
	}

	@Test
	fun arrowBlockWrong() {
		val need = mapOf(3 to 2)
		assertTrue(ArrowAlignLogic.shouldBlock(4, need, solved = true, sneaking = false))
		assertFalse(ArrowAlignLogic.shouldBlock(3, need, solved = true, sneaking = false))
		assertFalse(ArrowAlignLogic.shouldBlock(4, need, solved = true, sneaking = true))
		assertFalse(ArrowAlignLogic.shouldBlock(4, need, solved = false, sneaking = false))
		assertFalse(ArrowAlignLogic.shouldBlock(null, need, solved = true, sneaking = false))
	}
}
