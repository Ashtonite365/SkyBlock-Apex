package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.fixes.Fixes;
import java.util.Collection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class) public abstract class HideStatusEffectsPatch {
	@Unique
	private static boolean apex$hidden() { return ModuleGates.fixes && Fixes.TConfig.INSTANCE.getHideEffectIcons(); }

	@Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
	private void apex$noRoom(CallbackInfoReturnable<Boolean> result) {
		if (apex$hidden()) result.setReturnValue(false);
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void apex$noDraw(GuiGraphicsExtractor g, Collection<MobEffectInstance> effects, int left, int step, int mx, int my, int width, CallbackInfo info) {
		if (apex$hidden()) info.cancel();
	}
}
