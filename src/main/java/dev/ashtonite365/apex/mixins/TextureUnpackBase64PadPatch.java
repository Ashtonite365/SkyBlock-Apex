package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import dev.ashtonite365.apex.util.Base64Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MinecraftServicesSessionService.class, remap = false) public abstract class TextureUnpackBase64PadPatch {
	@ModifyExpressionValue(method = "unpackTextures", remap = false, at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/properties/Property;value()Ljava/lang/String;"))
	private String apex$pad(String encoded) {
		boolean aligned = encoded.length() % 4 == 0;
		return aligned ? encoded : Base64Util.INSTANCE.padToValidBase64(encoded);
	}
}
