package dev.ashtonite365.apex.util.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ashtonite365.apex.Apex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class WorldBatcher {

	public interface TestSink {
		VertexConsumer begin(RenderPipeline pipeline, TextureSetup textureSetup, float alphaMultiplier);
	}

	public static @Nullable TestSink testSink;

	private record PendingDraw(StagedVertexBuffer.Draw draw, RenderPipeline pipeline, TextureSetup textureSetup, float alphaMultiplier) {
		boolean sameKey(RenderPipeline otherPipeline, TextureSetup otherSetup, float otherAlpha) {
			return pipeline == otherPipeline && textureSetup.equals(otherSetup) && alphaMultiplier == otherAlpha;
		}
	}

	private static final class Staging {

		static final StagedVertexBuffer BUFFER = new StagedVertexBuffer(() -> "Apex world geometry", 1 << 20);
	}

	private static final List<PendingDraw> PENDING = new ArrayList<>();

	private static final Set<String> REPORTED_PIPELINES = new HashSet<>();
	private static @Nullable PendingDraw current;

	public static VertexConsumer sinkFor(RenderPipeline pipeline) {
		return sinkFor(pipeline, TextureSetup.noTexture(), 1f);
	}

	public static VertexConsumer sinkFor(RenderPipeline pipeline, TextureSetup textureSetup) {
		return sinkFor(pipeline, textureSetup, 1f);
	}

	public static VertexConsumer sinkFor(RenderPipeline pipeline, TextureSetup textureSetup, float alphaMultiplier) {
		TestSink sink = testSink;
		if (sink != null) return sink.begin(pipeline, textureSetup, alphaMultiplier);
		StagedVertexBuffer buffer = Staging.BUFFER;
		if (current == null || !current.sameKey(pipeline, textureSetup, alphaMultiplier)) {

			StagedVertexBuffer.Draw draw = buffer.appendDraw(pipeline.getVertexFormatBinding(0), pipeline.getPrimitiveTopology());
			current = new PendingDraw(draw, pipeline, textureSetup, alphaMultiplier);
			PENDING.add(current);
		}
		return buffer.getVertexBuilder(current.draw());
	}

	protected static void beginFrame() {

		if (!PENDING.isEmpty() || current != null) Staging.BUFFER.endDraw();
		PENDING.clear();
		current = null;
	}

	protected static void flushFrame() {
		current = null;
		if (PENDING.isEmpty()) return;
		ProfilerFiller profiler = Profiler.get();
		profiler.push("apexRendererFlush");
		List<PendingDraw> draws = new ArrayList<>(PENDING);
		PENDING.clear();
		try {
			WorldBatchFlush.flush(draws, GAME_STAGING, DRAWER);
		} finally {
			profiler.pop();
		}
	}

	private static final WorldBatchFlush.Staging<PendingDraw, StagedVertexBuffer.ExecuteInfo> GAME_STAGING = new WorldBatchFlush.Staging<>() {
		@Override public void requestIndexCount(PendingDraw d) { Staging.BUFFER.requestIndexCount(d.draw()); }
		@Override public void upload() { Staging.BUFFER.upload(); }
		@Override public StagedVertexBuffer.@Nullable ExecuteInfo executeInfo(PendingDraw d) { return Staging.BUFFER.getExecuteInfo(d.draw()); }
		@Override public int indexCount(StagedVertexBuffer.ExecuteInfo info) { return info.indexCount(); }
		@Override public void resizeIndexBuffers() { RenderSystem.resizeAllAutoStorageIndexBuffers(); }
		@Override public void endDraw() { Staging.BUFFER.endDraw(); }
		@Override public void endFrame() { Staging.BUFFER.endFrame(); }
	};

	private static final WorldBatchFlush.Drawer<PendingDraw, StagedVertexBuffer.ExecuteInfo, RenderPass, GpuBufferSlice> DRAWER = new WorldBatchFlush.Drawer<>() {
		@Override
		public @Nullable GpuBufferSlice prepare(PendingDraw pending, StagedVertexBuffer.ExecuteInfo info) {
			RenderPipeline pipeline = pending.pipeline();
			int targets = pipeline.getColorTargetStates().size();
			if (targets != 1) {
				if (REPORTED_PIPELINES.add(pipeline.getLocation().toString()))
					Apex.INSTANCE.getLogger().error("Skipping WorldBatcher draw: pipeline {} has {} colour targets, pass has 1", pipeline.getLocation(), targets);
				return null;
			}
			return transformsWithAlpha(pending.alphaMultiplier());
		}

		@Override public RenderPass openPass() { return openMainPass(); }

		@Override
		public void issue(RenderPass pass, PendingDraw pending, StagedVertexBuffer.ExecuteInfo info, GpuBufferSlice transforms) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pending.pipeline()));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			bindTextures(pending.textureSetup(), pass);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());

			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}

		@Override public void closePass(RenderPass pass) { pass.close(); }
	};

	private static RenderPass openMainPass() {
		RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Apex world geometry",
			target.getColorTextureView(),
			Optional.empty(),
			target.hasDepth() ? target.getDepthTextureView() : null,
			OptionalDouble.empty());
	}

	private static void bindTextures(TextureSetup setup, RenderPass pass) {
		if (setup.texure0() != null) pass.setUniform("Sampler0", setup.texure0(), setup.sampler0());
		if (setup.texure1() != null) pass.setUniform("Sampler1", setup.texure1(), setup.sampler1());
		if (setup.texure2() != null) pass.setUniform("Sampler2", setup.texure2(), setup.sampler2());
	}

	private static GpuBufferSlice transformsWithAlpha(float alpha) {
		Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
		RenderSystem.getProjectionType().applyLayeringTransform(modelView, 1f);
		return RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1f, 1f, 1f, alpha));
	}

	public static void release() {
		PENDING.clear();
		current = null;
		Staging.BUFFER.close();
	}
}
