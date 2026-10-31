package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ashtonite365.apex.features.inventory.storageoverlay.HotbarStorageSelector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class HotbarStorageSelectorHighlightPatch {
	@WrapWithCondition(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1))
	private boolean apex$hideSelection(GuiGraphicsExtractor instance, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
		if (!ModuleGates.hotbarStorageSelector) return true;
		return HotbarStorageSelector.shouldDrawVanillaSelection();
	}
}
