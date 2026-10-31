/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: block replacement models are added to model discovery so they get loaded and
 * baked with the game's own (SPEC §2.7.2 step 3).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import java.util.Map;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelManager.class)
public abstract class BlockModelRootsHook {
	@Inject(method = "discoverModelDependencies", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelDiscovery;addSpecialModel(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/model/UnbakedModel;)V", shift = At.Shift.AFTER))
	private static void apex$replacementRoots(
		Map<Identifier, UnbakedModel> models,
		BlockStateModelLoader.LoadedModels blockStates,
		ClientItemInfoLoader.LoadedClientInfos items,
		CallbackInfoReturnable<?> cir,
		@Local ModelDiscovery discovery
	) {
		ApexTextureEngines.getActive().getBlocks().addModelRoots(discovery);
	}
}
