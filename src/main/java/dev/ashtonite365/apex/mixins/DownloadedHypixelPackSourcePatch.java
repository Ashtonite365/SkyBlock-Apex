// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.DownloadedHypixelPack;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(PackRepository.class)
public class DownloadedHypixelPackSourcePatch {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void apex$addDownloadedHypixelPack(RepositorySource[] given, CallbackInfo ci) {
		boolean client = false;
		for (RepositorySource s : sources) if (s instanceof ClientPackSource) client = true;
		if (!client) return;
		Set<RepositorySource> copy = new LinkedHashSet<>(sources);
		copy.add(DownloadedHypixelPack.INSTANCE);
		sources = copy;
	}
}
