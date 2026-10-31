package dev.ashtonite365.apex.mixins.compat.jade;

import dev.ashtonite365.apex.compat.jade.CustomMiningHardnessProvider;
import dev.ashtonite365.apex.util.Client;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class) public abstract class OnUpdateBreakProgress {
	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void apex$mirrorProgress(int breaker, BlockPos at, int progress, CallbackInfo info) {
		if (breaker != 0) return;
		var mode = Client.INSTANCE.getGameMode();
		if (mode != null && Objects.equals(mode.destroyBlockPos, at)) CustomMiningHardnessProvider.setBreakingInfo(at, progress);
	}
}
