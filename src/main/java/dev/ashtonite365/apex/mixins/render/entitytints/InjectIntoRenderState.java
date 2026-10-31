package dev.ashtonite365.apex.mixins.render.entitytints;

import dev.ashtonite365.apex.events.EntityRenderTintEvent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class) public abstract class InjectIntoRenderState<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void apex$tintPass(T source, S rs, float partial, CallbackInfo info) {
		var ext = EntityRenderTintEvent.HasTintRenderState.cast(rs);
		ext.reset_apex();
		if (EntityRenderTintEvent.Companion.getHasHandlers()) EntityRenderTintEvent.Companion.publish(new EntityRenderTintEvent(source, ext));
	}
}
