package dev.ashtonite365.apex.mixins;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.MinecraftServicesKeyInfo;
import dev.ashtonite365.apex.features.fixes.Fixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftServicesKeyInfo.class, remap = false) public abstract class YggdrasilSignatureIgnorePatch {
	@Inject(method = "validateProperty", at = @At("HEAD"), cancellable = true, remap = false)
	public void apex$accept(Property prop, CallbackInfoReturnable<Boolean> result) {
		if (!Fixes.TConfig.INSTANCE.getAcceptUnsignedSkins()) return;
		result.setReturnValue(Boolean.TRUE);
	}
}
