// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.chat.ChatCentring;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import dev.ashtonite365.apex.modules.ModuleGates;

@Mixin(ChatComponent.class)
public class ChatCentringHook {
	@WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> apex$centredLines(GuiMessage message, Font font, int width, Operation<List<FormattedCharSequence>> vanilla) {
		if (!ModuleGates.hypixelChatLayout) return vanilla.call(message, font, width);
		List<FormattedCharSequence> lines = ChatCentring.splitForChat(message.content(), font, width);
		return lines == null ? vanilla.call(message, font, width) : lines;
	}
}
