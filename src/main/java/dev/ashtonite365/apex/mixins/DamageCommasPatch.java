// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.DamageCommas;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class DamageCommasPatch<T extends Entity> {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void apex$damageNameTag(T entity, CallbackInfoReturnable<Component> cir) {
		Component shown = DamageCommas.replace(entity, cir.getReturnValue());
		if (shown != null) cir.setReturnValue(shown);
	}
}
