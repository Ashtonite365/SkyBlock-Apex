package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.events.WorldMouseMoveEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class) public abstract class DispatchMouseInputEventsPatch {
	@WrapWithCondition(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	public boolean apex$turn(LocalPlayer me, double yawBy, double pitchBy) {
		return !WorldMouseMoveEvent.Companion.publish(new WorldMouseMoveEvent(yawBy, pitchBy)).getCancelled();
	}
}
