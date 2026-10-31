package dev.ashtonite365.apex.mixins.render.entitytints;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ashtonite365.apex.events.EntityRenderTintEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class) public abstract class ChangeColorOfLivingEntities<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
	@ModifyReturnValue(method = "getModelTint", at = @At("RETURN"))
	private int apex$tint(int vanilla, @Local(argsOnly = true) S rs) {
		var ext = EntityRenderTintEvent.HasTintRenderState.cast(rs);
		return ext.getHasTintOverride_apex() ? ext.getTint_apex() : vanilla;
	}

	@ModifyArg(
		method = "getOverlayCoords",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;u(F)I"),
		allow = 1
	)
	private static float apex$overlayColumn(float vanilla, @Local(argsOnly = true) LivingEntityRenderState rs) {
		var ext = EntityRenderTintEvent.HasTintRenderState.cast(rs);
		boolean styled = ext.getHasTintOverride_apex() || ext.getOverlayTexture_apex() != null;
		return styled ? 1F : vanilla;
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void apex$afterSubmit(S rs, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState cam, CallbackInfo info) {
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
	private void apex$beforeSubmit(S rs, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState cam, CallbackInfo info) {
		var overlay = EntityRenderTintEvent.HasTintRenderState.cast(rs).getOverlayTexture_apex();
		if (overlay != null) EntityRenderTintEvent.overlayOverride = overlay;
	}
}
