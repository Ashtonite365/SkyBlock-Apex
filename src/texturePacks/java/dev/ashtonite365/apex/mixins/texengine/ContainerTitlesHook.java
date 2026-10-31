/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: a container screen's own title and the player inventory title are drawn through
 * the layout's title movers, or the no-layout mover that only applies the text colour table (SPEC §2.8, §2.9).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import dev.ashtonite365.apex.texengine.TitleMover;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTitlesHook {
	@Shadow
	@Final
	protected Component playerInventoryTitle;

	@WrapOperation(
		method = "extractLabels",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
		require = 1)
	private void apex$title(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Void> original) {
		TitleMover mover;
		if (text == ((Screen) (Object) this).getTitle()) mover = ScreenHooks.containerTitle();
		else if (text == playerInventoryTitle) mover = ScreenHooks.playerTitle();
		else {
			original.call(graphics, font, text, x, y, color, shadow);
			return;
		}
		original.call(graphics, font, mover.text(text), mover.x(font, text, x), mover.y(y), mover.color(text, color), shadow);
	}
}
