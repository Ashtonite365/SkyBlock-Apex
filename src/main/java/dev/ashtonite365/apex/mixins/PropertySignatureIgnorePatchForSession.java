package dev.ashtonite365.apex.mixins;

import com.mojang.authlib.SignatureState;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import dev.ashtonite365.apex.features.fixes.Fixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftServicesSessionService.class, remap = false) public abstract class PropertySignatureIgnorePatchForSession {
	@Inject(method = "getPropertySignatureState", at = @At("HEAD"), cancellable = true, remap = false)
	public void apex$alwaysSigned(Property prop, CallbackInfoReturnable<SignatureState> result) {
		if (!Fixes.TConfig.INSTANCE.getAcceptUnsignedSkins()) return;
		result.setReturnValue(SignatureState.SIGNED);
	}
}
