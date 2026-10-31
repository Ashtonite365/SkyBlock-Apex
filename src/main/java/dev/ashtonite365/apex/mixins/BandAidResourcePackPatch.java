package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.repo.RepoModResourcePack;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ReloadableResourceManager.class) public abstract class BandAidResourcePackPatch {
	@ModifyReturnValue(method = "getResource", at = @At("RETURN"))
	private Optional<Resource> apex$repoFallback(Optional<Resource> found, @Local(argsOnly = true) Identifier id) {
		if (found.isPresent()) return found;
		return RepoModResourcePack.Companion.createResourceDirectly(id);
	}
}
