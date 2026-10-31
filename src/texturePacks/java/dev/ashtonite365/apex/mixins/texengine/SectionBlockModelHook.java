/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook (B1): the vanilla chunk compiler draws a block's replacement for its position
 * (SPEC §2.7.1). The game's own lookup runs inside the fallback scope so the position-less hook stays out of it.
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.BlockHooks;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SectionCompiler.class)
public abstract class SectionBlockModelHook {
	private static final String GET = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;";

	@ModifyArg(method = "compile", at = @At(value = "INVOKE", target = GET))
	private BlockState apex$beforeGet(BlockState state) {
		return BlockHooks.beforeRendererGet(state);
	}

	@ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE", target = GET))
	private BlockStateModel apex$afterGet(BlockStateModel chosen, @Local BlockState state, @Local(ordinal = 2) BlockPos pos) {
		return BlockHooks.afterRendererGet(chosen, state, pos);
	}
}
