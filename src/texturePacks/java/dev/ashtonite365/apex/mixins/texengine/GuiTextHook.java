/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: every GUI text draw (plain, centred, word-wrapped), tooltip and hover text goes
 * through the engine's text replacements first (SPEC §2.10).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiTextHook {
	@ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true)
	private Component apex$text(Component text) {
		return ScreenHooks.text(text);
	}

	@ModifyVariable(method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), argsOnly = true)
	private Component apex$centredText(Component text) {
		return ScreenHooks.text(text);
	}

	@ModifyVariable(method = "textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText apex$wrappedText(FormattedText text) {
		return ScreenHooks.text(text);
	}

	@ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), argsOnly = true)
	private List<Component> apex$componentTooltip(List<Component> lines) {
		return ScreenHooks.lines(lines);
	}

	@ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V", at = @At("HEAD"), argsOnly = true)
	private List<Component> apex$tooltip(List<Component> lines) {
		return ScreenHooks.lines(lines);
	}

	@ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V", at = @At("HEAD"), argsOnly = true)
	private Component apex$singleTooltip(Component text) {
		return ScreenHooks.text(text);
	}

	@ModifyExpressionValue(method = "componentHoverEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/HoverEvent$ShowText;value()Lnet/minecraft/network/chat/Component;"))
	private Component apex$hoverText(Component text) {
		return ScreenHooks.text(text);
	}
}
