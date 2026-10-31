// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.ActionBarEvent;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
public class ActionBarOverlayPatch {
	@ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component apex$publishOverlay(Component message) {
		return ActionBarEvent.dispatchOverlay(message);
	}
}
