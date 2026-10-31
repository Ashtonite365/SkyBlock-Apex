// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import org.joml.Matrix3x2f
import java.util.Optional

object Frost {

	const val STEPS = 2

	fun enabled(vanillaBlurriness: Int) = vanillaBlurriness >= 1

	fun strips(x: Int, y: Int, w: Int, h: Int, r: Int): List<IntArray> {
		if (w <= 0 || h <= 0) return emptyList()
		val rr = minOf(r, w / 2, h / 2)
		val out = ArrayList<IntArray>()
		var j = 0
		while (j < h) {
			val k = Shapes.cornerInset(rr, minOf(j, h - 1 - j))
			var e = j + 1
			while (e < h && Shapes.cornerInset(rr, minOf(e, h - 1 - e)) == k) e++
			out += intArrayOf(x + k, y + j, x + w - k, y + e)
			j = e
		}
		return out
	}

	fun uv(x0: Int, y0: Int, x1: Int, y1: Int, guiW: Int, guiH: Int): FloatArray =
		floatArrayOf(x0.toFloat() / guiW, x1.toFloat() / guiW, 1f - y0.toFloat() / guiH, 1f - y1.toFloat() / guiH)

	@JvmStatic var fade = 1.0

	private var levels: List<Pair<GpuTexture, GpuTextureView>> = emptyList()
	private var srcW = 0; private var srcH = 0
	private val retired = ArrayList<Pair<GpuTexture, GpuTextureView>>()
	private var requested = false

	fun draw(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, alpha: Double = 1.0) =
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.BLUR) { drawImpl(ctx, x, y, w, h, r, alpha) }

	private fun drawImpl(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, alpha: Double) {
		val mc = Minecraft.getInstance()
		if (!enabled(mc.options.getMenuBackgroundBlurriness()) || alpha <= 0.0) return
		val view = runCatching { ensure(mc) }.getOrNull() ?: return
		requested = true
		val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
		val setup = TextureSetup.singleTexture(view, sampler)
		val gw = mc.window.guiScaledWidth.coerceAtLeast(1); val gh = mc.window.guiScaledHeight.coerceAtLeast(1)
		val colour = (((alpha.coerceIn(0.0, 1.0) * 255).toInt()) shl 24) or 0xFFFFFF
		val pose = Matrix3x2f(ctx.pose())
		val scissor = ctx.scissorStack.peek()
		for (s in strips(x, y, w, h, r)) {

			val p0 = pose.transformPosition(org.joml.Vector2f(s[0].toFloat(), s[1].toFloat()))
			val p1 = pose.transformPosition(org.joml.Vector2f(s[2].toFloat(), s[3].toFloat()))
			val t = floatArrayOf(p0.x / gw, p1.x / gw, 1f - p0.y / gh, 1f - p1.y / gh)
			ctx.guiRenderState.addGuiElement(BlitRenderState(RenderPipelines.GUI_TEXTURED, setup, pose, s[0], s[1], s[2], s[3], t[0], t[1], t[2], t[3], colour, scissor))
		}
	}

	private fun ensure(mc: Minecraft): GpuTextureView? {
		val main = mc.gameRenderer.mainRenderTarget()
		if (levels.isEmpty() || main.width != srcW || main.height != srcH) {
			retired += levels
			val device = RenderSystem.getDevice()
			var w = main.width; var h = main.height
			levels = (1..STEPS).map {
				w = maxOf(1, w / 2); h = maxOf(1, h / 2)
				val usage = GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
				val tex = device.createTexture("Apex frost $it", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1)
				tex to device.createTextureView(tex)
			}
			srcW = main.width; srcH = main.height
		}
		return levels.last().second
	}

	@JvmStatic
	fun capture() {

		if (retired.isNotEmpty() && retired.none { r -> levels.any { it.first === r.first } }) {
			retired.forEach { runCatching { it.second.close(); it.first.close() } }; retired.clear()
		}
		if (!requested) return
		requested = false
		runCatching {
			val main = Minecraft.getInstance().gameRenderer.mainRenderTarget()
			if (main.width != srcW || main.height != srcH || levels.isEmpty()) return
			val enc = RenderSystem.getDevice().createCommandEncoder()
			val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
			var src: GpuTextureView = main.getColorTextureView() ?: return
			for ((_, view) in levels) {
				val pass = enc.createRenderPass({ "Apex frost" }, view, Optional.empty())
				try {
					pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT))
					RenderSystem.bindDefaultUniforms(pass)
					pass.setUniform("InSampler", src, sampler)
					pass.draw(3, 1, 0, 0)
				} finally { pass.close() }
				src = view
			}
		}
	}
}
