/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: the anvil's name field position/size and its repair-cost text (SPEC §2.8, §2.9).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen;
import dev.ashtonite365.apex.texengine.LayoutMover;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import dev.ashtonite365.apex.texengine.TitleMover;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenHook {
	@Shadow
	private EditBox name;

	@Inject(method = "subInit", at = @At("TAIL"))
	private void apex$nameField(CallbackInfo ci) {
		LayoutMover target = ScreenHooks.INSTANCE.nameField();
		if (target == null || name == null) return;
		AccessorHandledScreen origin = (AccessorHandledScreen) (Screen) (Object) this;
		name.setX(origin.getX_Apex() + target.getX());
		name.setY(origin.getY_Apex() + target.getY());
		Integer wide = target.getWidth();
		Integer tall = target.getHeight();
		if (wide != null) name.setWidth(wide);
		if (tall != null) name.setHeight(tall);
	}

	@WrapOperation(
		method = "extractLabels",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V")
	)
	private void apex$repairCost(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Operation<Void> original) {
		TitleMover mover = ScreenHooks.INSTANCE.repairCostTitle();
		Component shown = mover.text(text);
		original.call(graphics, font, shown, mover.x(font, shown, x), mover.y(y), mover.color(shown, color));
	}
}
