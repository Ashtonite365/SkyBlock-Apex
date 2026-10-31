package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.FinalizeResourceManagerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class) public abstract class ResourceReloaderRegistrationPatch {
	@Shadow @Final private ReloadableResourceManager resourceManager;

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;", shift = At.Shift.BEFORE))
	private void apex$registerReloaders(GameConfig config, CallbackInfo info) {
		var manager = resourceManager;
		FinalizeResourceManagerEvent.Companion.publish(new FinalizeResourceManagerEvent(manager));
	}
}
