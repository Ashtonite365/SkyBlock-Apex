package dev.ashtonite365.apex.mixins.compat.jade;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.compat.jade.CustomFakeBlockProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import snownee.jade.addon.harvest.HarvestToolProvider;
import snownee.jade.api.BlockAccessor;

@Mixin(HarvestToolProvider.class) public abstract class EnforceToolDisplayForCustomBlocksInHarvestToolProvider {
	@ModifyExpressionValue(require = 0, method = "getText", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;requiresCorrectToolForDrops()Z"))
	private boolean apex$customNeedsTool(boolean vanilla, @Local(argsOnly = true) BlockAccessor looking) {
		return vanilla || CustomFakeBlockProvider.hasCustomBlock(looking);
	}
}
