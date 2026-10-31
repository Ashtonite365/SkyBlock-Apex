package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.inventory.storageoverlay.HotbarStorageSelector;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public class HotbarStorageSelectorHeldItemPatch {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack apex$overrideHeldItem(LocalPlayer instance, Operation<ItemStack> original) {
		if (!ModuleGates.hotbarStorageSelector) return original.call(instance);
		return HotbarStorageSelector.heldItemOverride(original.call(instance));
	}
}
