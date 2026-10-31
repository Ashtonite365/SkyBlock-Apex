package dev.ashtonite365.apex.test.keybindings

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

class InputModifiersTest {
	@Test
	fun leftOrRightSideMatchesBothSidesBinding() {
		val saved = InputModifiers.of(shift = true)

		assertTrue(saved.matches(InputModifiers.of(1), false))
		assertTrue(saved.matches(InputModifiers(2), false))
		assertTrue(InputModifiers(1).matches(saved, false))
		val leftCtrl = InputConstants.MOD_CONTROL and -InputConstants.MOD_CONTROL
		assertTrue(InputModifiers.of(ctrl = true).matches(InputModifiers(leftCtrl), false))
		assertFalse(saved.matches(InputModifiers.none(), false))
		assertFalse(InputModifiers.none().matches(InputModifiers(1), false))

		assertTrue(saved.matches(InputModifiers(1 or InputConstants.MOD_NUM_LOCK or InputConstants.MOD_CAPS_LOCK), false))
	}

	@Test
	fun shiftLKeybindMatchesLeftShiftEvent() {
		val binding = SavedKeyBinding.keyWithMods(InputConstants.KEY_L, InputModifiers.of(shift = true))
		assertTrue(binding.matches(GenericInputAction.key(InputConstants.KEY_L, 0), InputModifiers(1)))
		assertFalse(binding.matches(GenericInputAction.key(InputConstants.KEY_L, 0), InputModifiers.none()))
		val wardrobeSlot = SavedKeyBinding.keyWithoutMods(InputConstants.KEY_1)
		assertTrue(wardrobeSlot.matches(GenericInputAction.key(InputConstants.KEY_1, 0), InputModifiers(InputConstants.MOD_NUM_LOCK)))
	}
}
