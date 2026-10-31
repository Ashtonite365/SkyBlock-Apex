/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): fire overlay setting (MiscRender fire-overlay): hidden, or "Low on Fire"
 * (the normal animated fire texture minus its top pixel row, squashed to the bottom 20% of the screen; idea from the "Low On Fire" resource pack by Haikis, no
 * assets used). Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ashtonite365.apex.features.misc.LowFireOverlay;
import dev.ashtonite365.apex.features.misc.MiscRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class HideFireOverlayPatch {

	@Unique
	private static final int FIRE_COLOUR = 0xE5FFFFFF;

	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void onSubmitFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (MiscRender.hideFire()) ci.cancel();
	}

	@Inject(method = "buildFireQuad", at = @At("HEAD"), cancellable = true)
	private static void onBuildFireQuad(TextureAtlasSprite sprite, VertexConsumer consumer, Matrix4f pose, CallbackInfo ci) {
		if (!MiscRender.lowFire()) return;
		LowFireOverlay.Quad q = LowFireOverlay.INSTANCE.quad(sprite.getV0(), sprite.getV1(), sprite.contents().height(),
			LowFireOverlay.TRIM_ROWS, LowFireOverlay.BOTTOM_Y, LowFireOverlay.TOP_Y);
		float x0 = -0.5F, x1 = 0.5F, z = -0.5F;
		float uLeft = sprite.getU1(), uRight = sprite.getU0();
		consumer.addVertex(pose, x0, q.getYBottom(), z).setUv(uLeft, q.getVBottom()).setColor(FIRE_COLOUR);
		consumer.addVertex(pose, x1, q.getYBottom(), z).setUv(uRight, q.getVBottom()).setColor(FIRE_COLOUR);
		consumer.addVertex(pose, x1, q.getYTop(), z).setUv(uRight, q.getVTop()).setColor(FIRE_COLOUR);
		consumer.addVertex(pose, x0, q.getYTop(), z).setUv(uLeft, q.getVTop()).setColor(FIRE_COLOUR);
		ci.cancel();
	}
}
