package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent;
import dev.ashtonite365.apex.keybindings.GenericInputAction;
import dev.ashtonite365.apex.keybindings.InputModifiers;
import dev.ashtonite365.apex.util.InventoryKeyClose;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class) public abstract class ScreenInputEvents {
	@Unique
	private Screen apex$screen() { return (Screen) (Object) this; }

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void apex$keyBinding(KeyEvent key, CallbackInfoReturnable<Boolean> result) {
		var pressed = new HandledScreenKeyPressedEvent(apex$screen(), GenericInputAction.of(key), InputModifiers.of(key));
		if (InventoryKeyClose.publishScreenKey(apex$screen(), pressed)) result.setReturnValue(true);
	}

	@SuppressWarnings("MissingUnique")
	public boolean onMouseClicked_apex_generic(MouseButtonEvent mouse, boolean twice) {
		var pressed = new HandledScreenKeyPressedEvent(apex$screen(), GenericInputAction.mouse(mouse), InputModifiers.current());
		return HandledScreenKeyPressedEvent.Companion.publish(pressed).getCancelled();
	}
}
