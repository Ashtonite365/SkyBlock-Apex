/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: with "Skip Hypixel Resource Pack" and "Keep Textures of Newer Items" on, Hypixel's server
 * pack is opened through an items-only view (ItemsOnlyPackResources). Original implementation.
 * require = 0: a changed target only means the full pack shows, never a crash.
 */
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(Pack.class)
public abstract class ItemsOnlyServerPackPatch {
	@ModifyReturnValue(method = "open", at = @At("RETURN"), require = 0)
	private Stream<PackResources> apex$itemsOnly(Stream<PackResources> original) {
		return ServerResourcePackSkip.itemsOnlyView((Pack) (Object) this, original);
	}
}
