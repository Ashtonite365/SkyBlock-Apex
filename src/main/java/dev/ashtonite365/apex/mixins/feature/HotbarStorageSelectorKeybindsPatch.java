package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.inventory.storageoverlay.HotbarStorageSelector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class HotbarStorageSelectorKeybindsPatch {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void apex$hotbarStorageSelector(CallbackInfo ci) {
		if (!ModuleGates.hotbarStorageSelector) return;
		HotbarStorageSelector.beforeHandleKeybinds();
	}
}
