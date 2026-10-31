/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: a worn head item whose pack model has an explicit head variant is drawn with it
 * instead of the vanilla skull (SPEC §2.3.1). The spare render state is swapped, not reallocated, every frame.
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import dev.ashtonite365.apex.texengine.ItemModels;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HeadItemHook {
	@Shadow
	@Final
	protected ItemModelResolver itemModelResolver;

	@Unique
	private ItemStackRenderState apex$spareHead = new ItemStackRenderState();

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void apex$useExplicitHeadModel(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		ItemModels items = ApexTextureEngines.getActive().getItems();
		ItemStackRenderState spare = apex$spareHead;
		spare.clear();
		boolean headVariant;
		items.openWornHeadPass();
		try {
			itemModelResolver.updateForLiving(spare, entity.getItemBySlot(EquipmentSlot.HEAD), ItemDisplayContext.HEAD, entity);
		} finally {
			headVariant = items.closeWornHeadPass();
		}
		if (!headVariant) return;
		state.wornHeadType = null;
		apex$spareHead = state.headItem;
		state.headItem = spare;
	}
}
