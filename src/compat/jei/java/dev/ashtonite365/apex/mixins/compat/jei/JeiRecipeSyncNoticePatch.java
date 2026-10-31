package dev.ashtonite365.apex.mixins.compat.jei;

import dev.ashtonite365.apex.features.items.recipes.ViewerStartup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public class JeiRecipeSyncNoticePatch {
	@Inject(require = 0, method = "verifyClientRecipes", at = @At("HEAD"), cancellable = true)
	private void apex$noRecipeSyncNotice(CallbackInfo ci) {
		if (ViewerStartup.SUPPRESS_RECIPE_SYNC_NOTICE && dev.ashtonite365.apex.features.menus.style.JeiStyle.hideSyncNoticeNow()) ci.cancel();
	}
}
