package dev.ashtonite365.apex.mixins.customgui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.util.customgui.CustomGui;
import dev.ashtonite365.apex.util.customgui.CustomGuiHost;
import dev.ashtonite365.apex.util.customgui.HasCustomGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class) public abstract class PatchGenericScreen implements HasCustomGui {
	@Shadow public int width;
	@Shadow public int height;
	@Shadow @Final protected Minecraft minecraft;

	@Shadow protected abstract void init();
	@Shadow protected abstract void extractMenuBackground(GuiGraphicsExtractor g);
	@Shadow protected abstract void extractBlurredBackground(GuiGraphicsExtractor g);
	@Shadow protected abstract void extractPanorama(GuiGraphicsExtractor g, float partial);
	@Shadow public abstract void extractTransparentBackground(GuiGraphicsExtractor g);
	@Shadow public abstract boolean isInGameUi();

	@Unique private @Nullable CustomGui apexGui;

	@Override public @Nullable CustomGui getCustomGui_Apex() { return apexGui; }

	@Override public void setCustomGui_Apex(@Nullable CustomGui next) { apexGui = next; }

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void apex$drawGui(GuiGraphicsExtractor g, int mx, int my, float partial, CallbackInfo info) {
		CustomGui gui = apexGui;
		if (gui != null) CustomGuiHost.drawFront((Screen) (Object) this, gui, g, mx, my, partial);
	}

	@WrapWithCondition(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private boolean apex$vanillaBackgroundUnlessGui(Screen self, GuiGraphicsExtractor g, int mx, int my, float partial) {
		boolean vanilla = apexGui == null;
		if (!vanilla) apex$baseBackground(g, partial);
		return vanilla;
	}

	@Unique
	private void apex$baseBackground(GuiGraphicsExtractor g, float partial) {

		if (dev.ashtonite365.apex.features.menus.style.MenuStyle.INSTANCE.getPalette().getClearOutside() && minecraft.level != null) {
			minecraft.gui.hud.extractDeferredSubtitles();
			return;
		}
		boolean overWorld = isInGameUi();
		if (overWorld) extractTransparentBackground(g);
		if (!overWorld && minecraft.level == null) extractPanorama(g, partial);
		if (!overWorld) {
			extractBlurredBackground(g);
			extractMenuBackground(g);
		}
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@SuppressWarnings("MissingUnique")
	public boolean mouseScrolled_apex(double x, double y, double dx, double dy) {
		return apexGui != null && apexGui.mouseScrolled(x, y, dx, dy);
	}

	@SuppressWarnings("MissingUnique")
	public boolean keyReleased_apex(KeyEvent key) { return CustomGuiHost.keyUp((Screen) (Object) this, apexGui, key); }

	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/events/AbstractContainerEventHandler;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"), cancellable = true)
	public void apex$guiKey(KeyEvent key, CallbackInfoReturnable<Boolean> result) {
		Boolean handled = CustomGuiHost.keyDown(apexGui, key);
		if (handled != null) result.setReturnValue(handled);
	}

	@SuppressWarnings("MissingUnique")
	public boolean charTyped_apex(CharacterEvent ch) { return CustomGuiHost.typed(apexGui, ch); }

	@SuppressWarnings("MissingUnique")
	public boolean mouseClicked_apex_customGui(MouseButtonEvent button, boolean twice) { return CustomGuiHost.press(apexGui, button, twice); }

	@SuppressWarnings("MissingUnique")
	public boolean mouseReleased_apex(MouseButtonEvent button) { return apexGui != null && apexGui.mouseReleased(button); }

	@SuppressWarnings("MissingUnique")
	public boolean mouseDragged_apex(MouseButtonEvent button, double byX, double byY) {
		return apexGui != null && apexGui.mouseDragged(button, byX, byY);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void apex$guiInit(CallbackInfo info) {
		if (apexGui != null) apexGui.onInit();
	}

	@Inject(at = @At("HEAD"), method = "onClose", cancellable = true)
	private void apex$guiMayKeepOpen(CallbackInfo info) {
		if (apexGui != null && !apexGui.onVoluntaryExit()) info.cancel();
	}
}
