package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.fixes.Fixes;
import dev.ashtonite365.apex.features.menus.RecipeViewers;
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractRecipeBookScreen.class, priority = 999) public abstract class MixinRecipeBookScreen {
	@Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

	@Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
	public void apex$noBookButton(CallbackInfo info) {
		boolean toggle = ModuleGates.fixes && Fixes.TConfig.INSTANCE.getNoRecipeBook();
		if (!RecipeViewers.hideRecipeBookNow(toggle)) return;
		if (recipeBookComponent.isVisible()) {
			recipeBookComponent.toggleVisibility();
			AccessorHandledScreen acc = (AccessorHandledScreen) this;
			acc.setX_Apex((((AbstractRecipeBookScreen<?>) (Object) this).width - acc.getBackgroundWidth_Apex()) / 2);
		}
		info.cancel();
	}
}
