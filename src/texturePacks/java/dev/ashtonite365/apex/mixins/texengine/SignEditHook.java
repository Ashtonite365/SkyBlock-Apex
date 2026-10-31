/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: a screen layout's sign background and the four sign line offsets.
 * The background replaces the vanilla board and is drawn at its own coordinates, relative to the sign centre
 * (the pose vanilla set up for the board). Each line's text, selection highlight and cursor move by the
 * line's offset, so editing still lines up with the moved text.
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.LayoutBackground;
import dev.ashtonite365.apex.texengine.LayoutMover;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditHook {

	@Unique
	private static LayoutMover apex$line(int index) {
		return ScreenHooks.signLine(index);
	}

	@Unique
	private static int apex$dx(LayoutMover mover) {
		return mover == null ? 0 : mover.getX();
	}

	@Unique
	private static int apex$dy(LayoutMover mover) {
		return mover == null ? 0 : mover.getY();
	}

	@WrapWithCondition(
		method = "extractSign",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractSignEditScreen;extractSignBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
	private boolean apex$background(AbstractSignEditScreen screen, GuiGraphicsExtractor graphics) {
		LayoutBackground background = ScreenHooks.background();
		if (background == null) return true;
		background.drawDirect(graphics);
		return false;
	}

	@WrapOperation(
		method = "extractSignText",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
	private void apex$lineText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int colour, boolean shadow,
			Operation<Void> original, @Local(ordinal = 4) int line) {
		LayoutMover mover = apex$line(line);
		original.call(graphics, font, text, x + apex$dx(mover), y + apex$dy(mover), colour, shadow);
	}

	@WrapOperation(
		method = "extractSignText",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
	private void apex$lineHighlight(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, boolean invert,
			Operation<Void> original, @Local(ordinal = 4) int line) {
		LayoutMover mover = apex$line(line);
		int dx = apex$dx(mover);
		int dy = apex$dy(mover);
		original.call(graphics, x0 + dx, y0 + dy, x1 + dx, y1 + dy, invert);
	}

	@WrapOperation(
		method = "extractSignText",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
	private void apex$appendCursor(GuiGraphicsExtractor graphics, Font font, int x, int y, int colour, boolean shadow,
			Operation<Void> original, @Local(ordinal = 4) int line) {
		LayoutMover mover = apex$line(line);
		original.call(graphics, font, x + apex$dx(mover), y + apex$dy(mover), colour, shadow);
	}

	@WrapOperation(
		method = "extractSignText",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
	private void apex$insertCursor(GuiGraphicsExtractor graphics, int x, int y, int colour, int height,
			Operation<Void> original, @Local(ordinal = 4) int line) {
		LayoutMover mover = apex$line(line);
		original.call(graphics, x + apex$dx(mover), y + apex$dy(mover), colour, height);
	}
}
