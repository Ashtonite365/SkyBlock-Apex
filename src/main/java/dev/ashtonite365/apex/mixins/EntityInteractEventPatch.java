package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.EntityInteractionEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class) public abstract class EntityInteractEventPatch {
	@Unique
	private static void apex$publish(EntityInteractionEvent.InteractionKind kind, Entity target, InteractionHand hand) {
		EntityInteractionEvent.Companion.publish(new EntityInteractionEvent(kind, target, hand));
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void apex$attack(Player who, Entity target, CallbackInfo info) {
		apex$publish(EntityInteractionEvent.InteractionKind.ATTACK, target, InteractionHand.MAIN_HAND);
	}

	@Inject(method = "interact", at = @At("HEAD"))
	private void apex$interact(Player who, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> result) {
		apex$publish(EntityInteractionEvent.InteractionKind.INTERACT, target, hand);
	}
}
