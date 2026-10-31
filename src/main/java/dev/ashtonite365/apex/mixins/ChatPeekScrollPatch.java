package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.fixes.Fixes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class) public abstract class ChatPeekScrollPatch {
	@Unique
	private static boolean apex$wheelScrollsChat() { return Fixes.INSTANCE.shouldPeekChat() && Fixes.INSTANCE.shouldScrollPeekedChat(); }

	@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"), cancellable = true)
	public void apex$keepHotbar(long window, double dx, double dy, CallbackInfo info) {
		if (apex$wheelScrollsChat()) info.cancel();
	}

	@ModifyVariable(method = "onScroll", at = @At(value = "STORE"), name = "wheel")
	public int apex$scrollChat(int clicks) {
		if (apex$wheelScrollsChat()) Minecraft.getInstance().gui.hud.getChat().scrollChat(clicks);
		return clicks;
	}
}
