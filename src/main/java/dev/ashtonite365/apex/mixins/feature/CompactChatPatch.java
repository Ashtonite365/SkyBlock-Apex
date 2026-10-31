// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.chat.CompactChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import dev.ashtonite365.apex.modules.ModuleGates;

@Mixin(ChatComponent.class)
public abstract class CompactChatPatch {
	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private Minecraft minecraft;

	@Shadow protected abstract void refreshTrimmedMessages();

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component apex$compact(Component incoming) {
		if (!ModuleGates.compactChat) return incoming;
		int before = allMessages.size();
		Component result = CompactChat.compact(incoming, allMessages, minecraft.gui.hud.getGuiTicks());
		if (allMessages.size() != before) refreshTrimmedMessages();
		return result;
	}
}
