package dev.ashtonite365.apex.mixins.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class) public abstract class IncreaseStackLimitSizeInDrawContext {
	@Unique private static final int MIN_DEPTH = 48;

	@ModifyArg(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;II)V", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;<init>(I)V"))
	private static int apex$depth(int vanilla) { return vanilla < MIN_DEPTH ? MIN_DEPTH : vanilla; }
}
