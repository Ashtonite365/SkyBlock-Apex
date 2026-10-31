package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.gui.config.KeyBindingHandler;
import dev.ashtonite365.apex.gui.config.ManagedOption;
import dev.ashtonite365.apex.keybindings.ApexKeyBindings;
import dev.ashtonite365.apex.keybindings.SavedKeyBinding;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import dev.ashtonite365.apex.modules.ModuleManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBindsList.KeyEntry.class) public abstract class ApexKeybindsInVanillaControlsPatch {
	@Mutable @Shadow @Final private Button changeButton;
	@Shadow @Final private KeyMapping key;
	@Shadow @Final private Button resetButton;

	@Unique
	private static void apex$disable(Button b) { b.active = false; }

	@Unique
	private ManagedOption<SavedKeyBinding> apex$option() {
		return ApexKeyBindings.INSTANCE.getKeyBindings().get(key);
	}

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"))
	public Button.OnPress apex$openApexEditor(Button.OnPress vanilla) {
		ManagedOption<SavedKeyBinding> option = apex$option();
		if (option == null || !(option.getHandler() instanceof KeyBindingHandler owner)) return vanilla;

		return button -> owner.getManagedConfig().showConfigEditor(Minecraft.getInstance().gui.screen());
	}

	@Inject(method = "refreshEntry", at = @At("HEAD"), cancellable = true)
	public void apex$showApexBinding(CallbackInfo info) {
		ManagedOption<SavedKeyBinding> option = apex$option();
		if (option == null) return;
		Component label = Component.translatableWithFallback("apex.keybinding.external", "%s", option.getValue().format());

		if (option.getHandler() instanceof KeyBindingHandler owner && ModuleManager.isModuleOff(owner.getManagedConfig().getName()))
			label = Component.empty().append(label).append(Component.translatableWithFallback("apex.keybinding.module-off", " (module off)"));
		changeButton.setMessage(label);
		apex$disable(resetButton);
		info.cancel();
	}
}
