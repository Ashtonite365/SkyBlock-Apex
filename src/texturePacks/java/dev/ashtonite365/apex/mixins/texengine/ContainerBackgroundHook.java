/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: plain container screens (crafting, crafter, dispenser, chest, hopper, shulker box)
 * draw only the layout background when one is active (SPEC §2.8).
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.LayoutBackground;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CraftingScreen.class, CrafterScreen.class, DispenserScreen.class, ContainerScreen.class, HopperScreen.class, ShulkerBoxScreen.class})
public abstract class ContainerBackgroundHook {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void apex$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		LayoutBackground bg = ScreenHooks.background();
		if (bg == null) return;
		bg.draw(graphics, (AbstractContainerScreen<?>) (Object) this);
		ci.cancel();
	}
}
