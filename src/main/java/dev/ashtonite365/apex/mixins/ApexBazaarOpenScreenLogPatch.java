package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.apexbzmenu.BzPacketLog;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ApexBazaarOpenScreenLogPatch {
	@Inject(method = "handleOpenScreen", at = @At("HEAD"))
	private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		BzPacketLog.INSTANCE.onOpenScreen(packet);
	}
}
