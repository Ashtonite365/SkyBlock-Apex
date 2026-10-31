package dev.ashtonite365.apex.mixins.compat.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.items.recipes.ViewerGridRows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridLayout", remap = false)
public class JeiGridLayoutRowsPatch {
	@WrapOperation(require = 0, method = "calculateSize", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1))
	private static int apex$fillRows(int fitRows, int maxRows, Operation<Integer> original) {
		int vanilla = original.call(fitRows, maxRows);
		try {
			return Math.max(vanilla, ViewerGridRows.rows(fitRows, maxRows));
		} catch (Throwable t) {
			return vanilla;
		}
	}
}
