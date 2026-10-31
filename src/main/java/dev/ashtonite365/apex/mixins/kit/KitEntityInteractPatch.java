/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: cancellable entity interaction for the kit features. Original implementation.
 */
package dev.ashtonite365.apex.mixins.kit;

import dev.ashtonite365.apex.util.kit.EntityInteractCancellableEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class KitEntityInteractPatch {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void onInteractKit(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		var event = new EntityInteractCancellableEvent(entity, hand);
		EntityInteractCancellableEvent.Companion.publish(event);
		if (event.getCancelled()) cir.setReturnValue(InteractionResult.FAIL);
	}
}
