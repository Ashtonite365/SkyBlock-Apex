package dev.ashtonite365.apex.mixins.registry;

import dev.ashtonite365.apex.events.ComponentsLoadedEvent;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryDataCollector.class) public abstract class ComponentUpdateEventPatch {
	@Inject(method = "updateComponents", at = @At("RETURN"))
	private static void apex$componentsReady(RegistryAccess.Frozen frozen, boolean shared, CallbackInfo info) {
		var loaded = new ComponentsLoadedEvent();
		ComponentsLoadedEvent.Companion.publish(loaded);
	}
}
