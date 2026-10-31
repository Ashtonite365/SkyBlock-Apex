/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: when the model manager switches to a newly baked model set, the engine's item
 * model index is rebuilt from that set's item models.
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.ItemModelIndex;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public abstract class ItemModelIndexHook {
	@Inject(method = "apply", at = @At("TAIL"))
	private void apex$rebuildItemModelIndex(ModelManager.ReloadState applied, CallbackInfo ci) {
		ItemModelIndex.rebuild(applied.bakedModels().itemStackModels());
	}
}
