// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class) public abstract class StaticMenusClickPatch {
	@Inject(method = "handleContainerInput", at = @At("HEAD"))
	private void apex$staticBefore(int window, int index, int button, ContainerInput input, Player who, CallbackInfo info) {
		StaticMenus.beforeClick(window, index);
	}

	@Inject(method = "handleContainerInput", at = @At("RETURN"))
	private void apex$staticAfter(int window, int index, int button, ContainerInput input, Player who, CallbackInfo info) {
		StaticMenus.afterClick();
	}
}
