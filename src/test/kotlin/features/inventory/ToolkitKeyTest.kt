// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.ToolkitKey
import dev.ashtonite365.apex.features.inventory.ToolkitKey.Logic
import dev.ashtonite365.apex.features.inventory.ToolkitKey.Step
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

class ToolkitKeyTest {

	private class Recorder : ToolkitKey.Actions {
		val log = mutableListOf<String>()
		override fun select(slot: Int) = run { log += "select $slot" }
		override fun use() = run { log += "use" }
		override fun noToolkit() = run { log += "message" }
	}

	private fun hotbar(vararg toolkitSlots: Int) = List(Logic.HOTBAR_SLOTS) { it in toolkitSlots }

	@Test
	fun toolkitsByIdDataOrName() {
		assertTrue(Logic.isToolkit("HUNTING_TOOLKIT", false, "Hunting Toolkit"))
		assertTrue(Logic.isToolkit("FARMING_TOOLKIT", false, null))
		assertTrue(Logic.isToolkit("SOME_NEW_TOOLKIT", false, null))
		assertTrue(Logic.isToolkit("SOMETHING_ELSE", true, "Tools"))
		assertTrue(Logic.isToolkit(null, false, "Hunting Toolkit"))
		assertFalse(Logic.isToolkit("HARMONIOUS_SURGERY_TOOLKIT", false, "Harmonious Surgery Toolkit"))
		assertFalse(Logic.isToolkit(null, false, "Harmonious Surgery Toolkit"))
		assertFalse(Logic.isToolkit("ASPECT_OF_THE_END", false, "Aspect of the End"))
		assertFalse(Logic.isToolkit(null, false, null))
	}

	@Test
	fun picksNearestToolkitAfterCurrentSlot() {
		assertEquals(5, Logic.nextToolkitSlot(2, hotbar(1, 5, 7)))
		assertEquals(1, Logic.nextToolkitSlot(7, hotbar(1, 5, 7)))
		assertEquals(0, Logic.nextToolkitSlot(8, hotbar(0)))
		assertEquals(4, Logic.nextToolkitSlot(4, hotbar(4)))
		assertNull(Logic.nextToolkitSlot(3, hotbar()))
	}

	@Test
	fun firstPressSelectsLikeANumberKey() {
		val r = Recorder()
		assertEquals(Step.Select(6), ToolkitKey.press(false, 0, hotbar(6), r))
		assertEquals(listOf("select 6"), r.log)
	}

	@Test
	fun pressingAgainBeforeHoldingOneCycles() {

		val bar = hotbar(1, 6)
		val picks = (0 until Logic.HOTBAR_SLOTS).filter { !bar[it] }.associateWith { Logic.nextToolkitSlot(it, bar) }
		assertEquals(mapOf(0 to 1, 2 to 6, 3 to 6, 4 to 6, 5 to 6, 7 to 1, 8 to 1), picks)

		assertEquals(4, Logic.nextToolkitSlot(3, hotbar(3, 4)))
		assertEquals(3, Logic.nextToolkitSlot(4, hotbar(3, 4)))
	}

	@Test
	fun firstPressSelectsSecondPressUses() {
		val r = Recorder()
		val bar = hotbar(1, 6)
		var current = 3
		val first = ToolkitKey.press(false, current, bar, r)
		assertEquals(Step.Select(6), first)
		current = (first as Step.Select).slot
		assertEquals(Step.Use, ToolkitKey.press(false, current, bar, r))
		assertEquals(listOf("select 6", "use"), r.log)
	}

	@Test
	fun holdingAToolkitGivesExactlyOneUse() {
		val r = Recorder()
		assertEquals(Step.Use, ToolkitKey.press(false, 6, hotbar(1, 6), r))
		assertEquals(listOf("use"), r.log)
	}

	@Test
	fun noToolkitShowsMessageAndDoesNothingElse() {
		val r = Recorder()
		assertEquals(Step.NoToolkit, ToolkitKey.press(false, 4, hotbar(), r))
		assertEquals(listOf("message"), r.log)
	}

	@Test
	fun exactlyOneActionPerPressAndNoneOnRepeat() {
		val bars = listOf(hotbar(), hotbar(0), hotbar(2, 8), hotbar(*IntArray(9) { it }))
		for (bar in bars) for (current in 0 until Logic.HOTBAR_SLOTS) {
			val r = Recorder()
			ToolkitKey.press(false, current, bar, r)
			assertEquals(1, r.log.size, "press at $current on $bar: ${r.log}")
			val held = Recorder()
			assertEquals(Step.Nothing, ToolkitKey.press(true, current, bar, held))
			assertTrue(held.log.isEmpty(), "auto-repeat at $current on $bar: ${held.log}")
		}
	}

	@Test
	fun keyIsUnboundByDefault() {
		ToolkitKey.TConfig.explicitDefaultLoad()
		assertEquals(SavedKeyBinding.unbound(), ToolkitKey.TConfig.key)
	}
}
