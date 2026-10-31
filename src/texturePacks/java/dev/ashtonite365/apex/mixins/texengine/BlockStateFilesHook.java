/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: once the game has listed every blockstate file, block replacements that have a
 * blockstate file of their own get per-state models built from it (SPEC §2.7.2 step 2).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockStateModelLoader.class)
public abstract class BlockStateFilesHook {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@ModifyExpressionValue(method = "loadBlockStates", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private static CompletableFuture<Map<Identifier, List<Resource>>> apex$replacementStateFiles(
		CompletableFuture<Map<Identifier, List<Resource>>> listed,
		@Local(argsOnly = true) Executor executor,
		@Local Function<Identifier, StateDefinition<Block, BlockState>> definitions
	) {
		return ApexTextureEngines.getActive().getBlocks().loadBlockStates(listed, (Function) definitions, executor);
	}
}
