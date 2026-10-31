/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): optional risky glow for items / named entities. Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.MiscRender;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class EntityGlowPatch {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void onGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && MiscRender.shouldGlow(entity)) cir.setReturnValue(true);
	}
}
