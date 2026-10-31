package dev.ashtonite365.apex.mixins.render.entitytints;

import dev.ashtonite365.apex.events.EntityRenderTintEvent;
import dev.ashtonite365.apex.util.render.TintOverlay;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class) public abstract class EntityRenderStateTint implements EntityRenderTintEvent.HasTintRenderState {
	@Unique int apexTint = -1;
	@Unique TintOverlay apexOverlay;
	@Unique boolean apexTinted;

	@Override public int getTint_apex() { return apexTint; }

	@Override
	public void setTint_apex(int argb) {
		apexTinted = true;
		apexTint = argb;
	}

	@Override public boolean getHasTintOverride_apex() { return apexTinted; }

	@Override public void setHasTintOverride_apex(boolean on) { apexTinted = on; }

	@Override public void reset_apex() { apexOverlay = null; apexTinted = false; }

	@Override public @Nullable TintOverlay getOverlayTexture_apex() { return apexOverlay; }

	@Override public void setOverlayTexture_apex(@Nullable TintOverlay next) { apexOverlay = next; }
}
