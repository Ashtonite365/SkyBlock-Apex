// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.plus.foraging.PlusEntityHide;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class PlusHideEntityPatch<T extends Entity> {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void apex$shHide(T entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (PlusEntityHide.shouldHide(entity)) cir.setReturnValue(false);
	}
}
