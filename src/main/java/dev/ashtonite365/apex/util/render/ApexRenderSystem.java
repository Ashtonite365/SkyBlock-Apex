package dev.ashtonite365.apex.util.render;

import dev.ashtonite365.apex.events.WorldRenderLastEvent;
import dev.ashtonite365.apex.util.render.gui.pip.RenderCircleProgressRenderer;
import dev.ashtonite365.apex.util.render.gui.pip.RenderLineRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class ApexRenderSystem {
	private ApexRenderSystem() {}

	public static void install() {
		LevelRenderEvents.START_MAIN.register(context -> WorldBatcher.beginFrame());
		LevelRenderEvents.END_MAIN.register(ApexRenderSystem::finishMainPass);
		RenderLineRenderer.registerRenderer();
		RenderCircleProgressRenderer.registerRenderer();
	}

	private static void finishMainPass(LevelRenderContext context) {
		var event = new WorldRenderLastEvent(context.poseStack(), context.levelState().cameraRenderState);
		WorldRenderLastEvent.Companion.publish(event);
		WorldBatcher.flushFrame();
	}

	public static void shutdown() {
		WorldBatcher.release();
	}
}
