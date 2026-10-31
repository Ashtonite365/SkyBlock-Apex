package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import dev.ashtonite365.apex.keybindings.SanitizeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class SanitizeKeyMappingPatch {
	@ModifyVariable(method = "setKey", at = @At("HEAD"), argsOnly = true)
	private InputConstants.Key apex$sanitizeKey(InputConstants.Key key) {
		return SanitizeInput.sanitize(key);
	}

	@Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V", at = @At("RETURN"))
	private void apex$sanitizeDefault(CallbackInfo ci) {
		KeyMapping self = (KeyMapping) (Object) this;
		InputConstants.Key k = ((AccessorKeyMappingKey) self).apex$getKey();
		InputConstants.Key s = SanitizeInput.sanitize(k);
		if (s != k) ((AccessorKeyMappingKey) self).apex$setKey(s);
	}
}
