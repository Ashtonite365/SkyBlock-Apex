package dev.ashtonite365.apex.keybindings

import java.util.BitSet
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent

object ApexKeyboardState {
	private val held = BitSet()

	fun isScancodeDown(scancode: Int): Boolean = synchronized(held) { held[scancode] }

	fun maintainState(keyInput: KeyEvent, action: Int) {
		val down = when (action) {
			InputConstants.RELEASE -> false
			InputConstants.PRESS -> true
			else -> return
		}
		synchronized(held) { held.set(keyInput.key, down) }
	}
}
