/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: after vanilla loads item definitions, the engine may add more (legacy
 * models/item files, SPEC §2.2). Runs on the reload's background executor.
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientItemInfoLoader.class)
public abstract class ItemDefinitionsHook {
	@ModifyReturnValue(method = "scheduleLoad", at = @At("RETURN"))
	private static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> apex$addEngineDefinitions(
		CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> loading,
		@Local(argsOnly = true) ResourceManager manager,
		@Local(argsOnly = true) Executor executor
	) {
		return loading.thenApplyAsync(
			infos -> new ClientItemInfoLoader.LoadedClientInfos(
				ApexTextureEngines.getActive().getItems().extraItemDefinitions(manager, infos.contents())),
			executor);
	}
}
