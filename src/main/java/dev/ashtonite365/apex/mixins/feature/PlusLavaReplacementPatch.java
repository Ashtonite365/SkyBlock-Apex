// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Map;
import dev.ashtonite365.apex.features.plus.fishing.PlusLavaReplacement;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
public abstract class PlusLavaReplacementPatch {
	@Inject(method = "bake", at = @At("RETURN"))
	private static void apex$captureBakedFluids(CallbackInfoReturnable<Map<Fluid, FluidModel>> cir) {
		Map<Fluid, FluidModel> baked = cir.getReturnValue();
		PlusLavaReplacement.onModelsBaked(baked);
	}

	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private FluidModel apex$swapLavaModel(FluidModel model, FluidState fluid) {
		if (!ModuleGates.lavaReplacement) return model;
		return PlusLavaReplacement.replacement(fluid, model);
	}
}
