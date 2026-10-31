/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook (B3): Sodium's chunk mesher draws a block's replacement for its position
 * (SPEC §2.7.1), with Sodium's own model lookup inside the fallback scope like the vanilla compiler (B1).
 */
package dev.ashtonite365.apex.mixins.sodium.texengine;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.BlockHooks;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkBuilderMeshingTask.class)
public abstract class SodiumBlockModelHook {
	private static final String EXECUTE = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;";
	private static final String GET = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;";

	@ModifyArg(method = EXECUTE, at = @At(value = "INVOKE", target = GET))
	private BlockState apex$beforeGet(BlockState state) {
		return BlockHooks.beforeRendererGet(state);
	}

	@ModifyExpressionValue(method = EXECUTE, at = @At(value = "INVOKE", target = GET))
	private BlockStateModel apex$afterGet(BlockStateModel chosen, @Local(name = "blockState") BlockState state, @Local(name = "blockPos") BlockPos.MutableBlockPos pos) {
		return BlockHooks.afterRendererGet(chosen, state, pos);
	}
}
