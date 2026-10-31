/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: block replacement models are baked alongside the game's, and baking only counts as
 * done once they are (SPEC §2.7.2 step 4).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBakery.class)
public abstract class BlockModelBakeHook {
	@ModifyReturnValue(method = "bakeModels", at = @At("RETURN"))
	private CompletableFuture<ModelBakery.BakingResult> apex$bakeReplacements(
		CompletableFuture<ModelBakery.BakingResult> result,
		@Local ModelBakery.ModelBakerImpl baker,
		@Local(argsOnly = true) Executor executor
	) {
		CompletableFuture<Void> replacements = ApexTextureEngines.getActive().getBlocks().bake(baker, executor);
		return result.thenCombine(replacements, (baked, ignored) -> baked);
	}
}
