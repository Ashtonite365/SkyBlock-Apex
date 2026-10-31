// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.features.chat.ChatSearch;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import dev.ashtonite365.apex.modules.ModuleGates;

@Mixin(ChatComponent.class)
public class ChatSearchPatch {
	@WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> apex$rememberLines(GuiMessage message, Font font, int width, Operation<List<FormattedCharSequence>> original) {
		List<FormattedCharSequence> lines = original.call(message, font, width);
		ChatSearch.rememberLines(message, lines);
		return lines;
	}

	@WrapOperation(method = "lambda$extractRenderState$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"))
	private static void apex$highlight(ChatComponent.ChatGraphicsAccess access, int x1, int y1, int x2, int y2, int colour,
	                                        Operation<Void> original, @Local(argsOnly = true) GuiMessage.Line line) {
		if (!ModuleGates.chatSearch) { original.call(access, x1, y1, x2, y2, colour); return; }
		Integer hl = ChatSearch.lineBackground(line.content());
		original.call(access, x1, y1, x2, y2, hl != null ? hl : colour);
	}
}
