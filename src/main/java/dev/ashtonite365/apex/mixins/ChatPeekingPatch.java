package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.features.fixes.Fixes;
import net.minecraft.client.gui.components.ChatComponent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class) public abstract class ChatPeekingPatch {
	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/ChatComponent$DisplayMode;showRestrictedPrompt:Z", opcode = Opcodes.GETFIELD), name = "isForeground")
	public boolean apex$foregroundWhilePeeking(boolean foreground) { return foreground || Fixes.INSTANCE.shouldPeekChat(); }

	@ModifyExpressionValue(method = "getHeight()I", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z"))
	public boolean apex$tallWhilePeeking(boolean focused) { return focused || Fixes.INSTANCE.shouldPeekChat(); }
}
