// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.chat.ChatPeek;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Hud.class)
public class ChatPeekPatch {
	@ModifyArg(method = "extractChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"), index = 5)
	private ChatComponent.DisplayMode apex$peek(ChatComponent.DisplayMode mode) {
		if (!ModuleGates.chatPeek) return mode;
		return ChatPeek.isPeeking() ? ChatComponent.DisplayMode.FOREGROUND : mode;
	}
}
