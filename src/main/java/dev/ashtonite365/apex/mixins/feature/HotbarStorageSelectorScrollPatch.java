package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.inventory.storageoverlay.HotbarStorageSelector;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class HotbarStorageSelectorScrollPatch {
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
	private int apex$hotbarStorageSelector(double wheel, int current, int size, Operation<Integer> original) {
		int result = original.call(wheel, current, size);
		if (!ModuleGates.hotbarStorageSelector) return result;
		return HotbarStorageSelector.onHotbarScroll((int) Math.signum(wheel), current, result);
	}
}
