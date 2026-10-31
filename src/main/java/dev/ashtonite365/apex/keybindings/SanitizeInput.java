package dev.ashtonite365.apex.keybindings;

import com.mojang.blaze3d.platform.InputConstants;

public final class SanitizeInput {
	public static final int SDL_SCANCODE_COUNT = 512;

	private SanitizeInput() {}

	public static boolean isValidKeyboardKey(int key) {
		return key > 0 && key < SDL_SCANCODE_COUNT;
	}

	public static InputConstants.Key sanitize(InputConstants.Key key) {
		if (key == null) return InputConstants.UNKNOWN;
		if (key.getType() == InputConstants.Type.KEYBOARD && !isValidKeyboardKey(key.getValue()))
			return InputConstants.UNKNOWN;
		if (key.getType() == InputConstants.Type.MOUSE && key.getValue() < 0)
			return InputConstants.UNKNOWN;
		return key;
	}
}
