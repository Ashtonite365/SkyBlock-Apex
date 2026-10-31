package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.repo.RepoModResourcePack;
import java.util.List;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.impl.resource.pack.ModPackResourcesSorter;
import net.fabricmc.fabric.impl.resource.pack.ModPackResourcesUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModPackResourcesUtil.class) public abstract class AppendRepoAsResourcePack {
	@Inject(method = "getModResourcePacks", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/resource/pack/ModPackResourcesSorter;getPacks()Ljava/util/List;"), require = 0)
	private static void apex$addRepoPack(FabricLoader loader, PackType kind, @Nullable String sub, CallbackInfoReturnable<List<ModResourcePack>> result, @Local ModPackResourcesSorter packs) {
		RepoModResourcePack.Companion.append(packs);
	}
}
