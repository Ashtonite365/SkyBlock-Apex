package dev.ashtonite365.apex.mixins.render.entitytints;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.events.EntityRenderTintEvent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CustomHeadLayer.class) public abstract class UseOverlayableHeadFeatureRenderer {
	@Unique private static final int OVERLAY_CELL_U = 15, OVERLAY_CELL_V = 10;

	@ModifyExpressionValue(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I", opcode = Opcodes.GETSTATIC))
	private int apex$overlayCell(int vanilla) {
		return EntityRenderTintEvent.overlayOverride == null ? vanilla : OverlayTexture.pack(OVERLAY_CELL_U, OVERLAY_CELL_V);
	}
}
