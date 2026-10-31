package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ashtonite365.apex.features.chat.CopyChat;
import dev.ashtonite365.apex.util.ClipboardUtils;
import dev.ashtonite365.apex.util.Client;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class) public abstract class CopyChatPatch {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void apex$copyLine(MouseButtonEvent mouse, boolean twice, CallbackInfoReturnable<Boolean> result) {
		if (!ModuleGates.copyChat) return;
		boolean wanted = mouse.button() == InputConstants.MOUSE_BUTTON_RIGHT && CopyChat.TConfig.INSTANCE.getCopyChat();
		if (!wanted) return;
		var hud = Minecraft.getInstance().gui.hud;
		ChatComponent chat = hud.getChat();
		var finder = new CopyChat.HoveredTextLineCollector((int) mouse.x(), (int) mouse.y());
		chat.captureClickableText(finder, Client.INSTANCE.getWindow().getGuiScaledHeight(), hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
		var line = finder.getResult();
		if (line == null) return;
		String plain = CopyChat.INSTANCE.orderedTextToString(line);
		ClipboardUtils.INSTANCE.setTextContent(plain);
		chat.addClientSystemMessage(Component.literal("Copied: ").append(plain).withStyle(ChatFormatting.GRAY));
		result.setReturnValue(true);
	}
}
