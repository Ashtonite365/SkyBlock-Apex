/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: entity-render hook for the H2 ports (dungeon/slayer/combat hiders). Original implementation.
 */
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.plus.combat.PlusH2EntityHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class PlusH2EntityHidePatch {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void onShouldRenderH2(E entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (PlusH2EntityHider.shouldHide(entity)) cir.setReturnValue(false);
	}
}
