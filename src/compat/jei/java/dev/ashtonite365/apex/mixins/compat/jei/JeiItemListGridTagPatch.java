package dev.ashtonite365.apex.mixins.compat.jei;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.features.items.recipes.ViewerGridRows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation", remap = false)
public class JeiItemListGridTagPatch {
	@Unique
	private boolean apex$itemList;

	@Inject(require = 0, method = "<init>", at = @At("RETURN"))
	private void apex$tag(CallbackInfo ci, @Local(argsOnly = true) String name) {
		this.apex$itemList = ViewerGridRows.isItemListGrid(name);
	}

	@Inject(require = 0, method = "calculateLayout", at = @At("HEAD"))
	private void apex$beginLayout(CallbackInfoReturnable<Object> cir) {
		ViewerGridRows.beginLayout(this.apex$itemList);
	}

	@Inject(require = 0, method = "calculateLayout", at = @At("RETURN"))
	private void apex$endLayout(CallbackInfoReturnable<Object> cir) {
		ViewerGridRows.endLayout();
	}
}
