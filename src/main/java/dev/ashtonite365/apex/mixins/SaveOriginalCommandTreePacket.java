package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.chat.QuickCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class) public abstract class SaveOriginalCommandTreePacket {
	@Inject(method = "handleCommands", at = @At(value = "RETURN"))
	private void apex$keepTree(ClientboundCommandsPacket tree, CallbackInfo info) { QuickCommands.INSTANCE.setLastReceivedTreePacket(tree); }
}
