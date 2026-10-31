package dev.ashtonite365.apex.mixins.compat.jade;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.compat.jade.CustomMiningHardnessProvider;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import snownee.jade.JadeClient;

@Mixin(JadeClient.class) public abstract class PatchBreakingBarSpeedJade {
	@ModifyExpressionValue(require = 0, method = "drawBreakingProgress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyProgress:F", opcode = Opcodes.GETFIELD))
	private static float apex$progress(float vanilla) { return CustomMiningHardnessProvider.replaceBreakProgress(vanilla); }

	@ModifyExpressionValue(require = 0, method = "drawBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
	private static float apex$speed(float vanilla) { return CustomMiningHardnessProvider.replaceBlockBreakSpeed(vanilla); }
}
