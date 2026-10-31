package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.HotbarItemRenderEvent;
import dev.ashtonite365.apex.events.HudRenderEvent;
import dev.ashtonite365.apex.features.fixes.Fixes;
import dev.ashtonite365.apex.util.SkyBlockSession;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class) public abstract class HudRenderEventsPatch {
	@Inject(method = "extractSleepOverlay", at = @At(value = "HEAD"))
	public void apex$hud(GuiGraphicsExtractor g, DeltaTracker time, CallbackInfo info) {
		if (HudRenderEvent.Companion.getHasHandlers()) HudRenderEvent.Companion.publish(new HudRenderEvent(g, time));
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	public void apex$hotbarItem(GuiGraphicsExtractor g, int px, int py, DeltaTracker time, Player who, ItemStack stack, int seed, CallbackInfo info) {
		if (stack == null || stack.isEmpty() || !HotbarItemRenderEvent.Companion.getHasHandlers()) return;
		HotbarItemRenderEvent.Companion.publish(new HotbarItemRenderEvent(stack, g, px, py, time));
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	public void apex$effects(CallbackInfo info) {
		boolean hide = ModuleGates.fixes && SkyBlockSession.INSTANCE.getInSkyBlock() && Fixes.TConfig.INSTANCE.getHideEffectsHud();
		if (hide) info.cancel();
	}
}
