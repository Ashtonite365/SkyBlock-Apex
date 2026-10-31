package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.features.fixes.Fixes;
import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class) public abstract class DisableHurtCam {
	@ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;hurtTime:F", opcode = Opcodes.GETFIELD))
	private float apex$hurtTime(float vanilla) { return ModuleGates.fixes && Fixes.TConfig.INSTANCE.getSuppressHurtTilt() ? 0F : vanilla; }
}
