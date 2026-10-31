/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook (B4): every other model lookup (far terrain, particles, other mods) gets the
 * island's replacement without a position (SPEC §2.7.1). Not inside a chunk renderer's own lookup (fallback scope).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.BlockHooks;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockStateModelSet.class)
public abstract class BlockModelSetHook {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private BlockStateModel apex$positionless(BlockStateModel chosen, @Local(argsOnly = true) BlockState state) {
		return BlockHooks.positionless(chosen, state);
	}
}
