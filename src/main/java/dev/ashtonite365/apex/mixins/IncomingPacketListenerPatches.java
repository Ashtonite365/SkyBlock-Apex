package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.CommandDispatcher;
import dev.ashtonite365.apex.events.MaskCommands;
import dev.ashtonite365.apex.events.ParticleSpawnEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class) public abstract class IncomingPacketListenerPatches {
	@ModifyExpressionValue(method = "handleCommands", at = @At(value = "NEW", target = "(Lcom/mojang/brigadier/tree/RootCommandNode;)Lcom/mojang/brigadier/CommandDispatcher;", remap = false))
	public CommandDispatcher apex$maskTree(CommandDispatcher created) {
		MaskCommands.Companion.publish(new MaskCommands(created));
		return created;
	}

	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
	public void apex$particle(ClientboundLevelParticlesPacket p, CallbackInfo info) {
		var where = new Vec3(p.x(), p.y(), p.z());
		var spread = new Vector3f(p.xDist(), p.yDist(), p.zDist());

		var spawn = new ParticleSpawnEvent(p.particle(), where, spread, p.alwaysShow(), p.count(), p.xMaxSpeed());
		if (ParticleSpawnEvent.Companion.publish(spawn).getCancelled()) info.cancel();
	}
}
