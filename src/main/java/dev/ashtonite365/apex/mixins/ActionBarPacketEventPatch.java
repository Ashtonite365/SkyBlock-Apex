// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.ActionBarEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ActionBarPacketEventPatch {
	@Inject(method = "setActionBarText", at = @At("HEAD"))
	private void apex$tagActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		ActionBarEvent.pendingSource = ActionBarEvent.SOURCE_ACTION_BAR_PACKET;
	}
}
