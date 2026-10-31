package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ashtonite365.apex.events.ScreenChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class) public abstract class ScreenChangeEventPatch {
	@Shadow public abstract Screen screen();

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	public void apex$screenChange(Screen next, CallbackInfo info, @Local(argsOnly = true) LocalRef<Screen> nextRef) {
		var change = ScreenChangeEvent.Companion.publish(new ScreenChangeEvent(screen(), next));
		if (change.getCancelled()) {
			info.cancel();
			return;
		}
		var replacement = change.getOverrideScreen();
		if (replacement != null) nextRef.set(replacement);
	}
}
