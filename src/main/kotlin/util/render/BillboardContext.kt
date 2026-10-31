// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.center

@RenderContextDSL
class BillboardContext(val worldContext: WorldDraw) {
	val worldPoses: PoseStack get() = worldContext.worldPoses

	fun waypoint(block: BlockPos, title: Component) {
		val distance = Client.player?.position()?.distanceTo(block.center()) ?: NO_PLAYER_DISTANCE
		text(title, anchor = WorldDraw.LineAnchor.BOTTOM)
		text(Component.literal("§e" + ApexFormatters.distanceText(distance)), anchor = WorldDraw.LineAnchor.TOP)
	}

	private class SeeThroughGlyphs(private val pose: Matrix4f) : Font.GlyphVisitor {
		override fun acceptRenderable(glyph: TextRenderable) {
			val setup = TextureSetup.singleTextureWithLightmap(glyph.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
			glyph.render(pose, WorldBatcher.sinkFor(RenderPipelines.TEXT_SEE_THROUGH, setup), FULL_BRIGHT, false)
		}
	}

	private fun corner(buffer: VertexConsumer, pose: Matrix4f, x: Float, y: Float, colour: Int) {
		buffer.addVertex(pose, x, y, 0f).setColor(colour).setLight(FULL_BRIGHT)
	}

	fun text(vararg texts: Component, anchor: WorldDraw.LineAnchor = WorldDraw.LineAnchor.CENTER, background: Int = SHADE) {
		assert(texts.isNotEmpty())
		val font = Client.font
		val pose = worldPoses.last().pose()
		for ((i, line) in texts.withIndex()) {
			val y = anchor.offsetOf(i, texts.size)
			val width = font.width(line)
			val x = -width / 2f
			val box = WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS)
			corner(box, pose, x - 1, y - 1, background)
			corner(box, pose, x - 1, y + 9, background)
			corner(box, pose, x + width, y + 9, background)
			corner(box, pose, x + width, y - 1, background)
			val glyphPose = Matrix4f(pose).translate(0f, 0f, GLYPH_LIFT)
			font.prepareText(line.visualOrderText, x, y, -1, false, false, 0).visit(SeeThroughGlyphs(glyphPose))
		}
	}

	fun texture(id: Identifier, width: Int, height: Int, uv: UvRect = UvRect.WHOLE) {
		val texture = Client.game.textureManager.getTexture(id)
		val buffer = WorldBatcher.sinkFor(CustomRenderPipelines.GUI_TEXTURED_NO_DEPTH_TRIANGLES, TextureSetup.singleTexture(texture.textureView, texture.sampler))
		val pose = worldPoses.last().pose()
		val hw = width / 2f
		val hh = height / 2f
		buffer.addVertex(pose, -hw, -hh, 0f).setColor(-1).setUv(uv.u0, uv.v0)
		buffer.addVertex(pose, -hw, hh, 0f).setColor(-1).setUv(uv.u0, uv.v1)
		buffer.addVertex(pose, hw, hh, 0f).setColor(-1).setUv(uv.u1, uv.v1)
		buffer.addVertex(pose, hw, -hh, 0f).setColor(-1).setUv(uv.u1, uv.v0)
	}

	companion object {

		const val SHADE = 0x70808080
		private const val FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT
		private const val GLYPH_LIFT = 0.01f
		private const val NO_PLAYER_DISTANCE = 42069.0
	}
}
