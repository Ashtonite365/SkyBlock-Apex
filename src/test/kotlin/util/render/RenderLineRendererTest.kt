// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.render

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.render.gui.pip.RenderLineRenderer

class RenderLineRendererTest {
	@Test
	fun normalFollowsTheLineDirection() {
		val n = RenderLineRenderer.lineNormal(-5F, 5F, 5F, -5F)
		assertEquals(Math.sqrt(0.5).toFloat(), n.x, 1e-5F)
		assertEquals(-Math.sqrt(0.5).toFloat(), n.y, 1e-5F)
		assertEquals(0F, n.z)
		val h = RenderLineRenderer.lineNormal(0F, 3F, 10F, 3F)
		assertEquals(1F, h.x, 1e-6F); assertEquals(0F, h.y, 1e-6F)

		val z = RenderLineRenderer.lineNormal(2F, 2F, 2F, 2F)
		assertEquals(1F, z.length(), 1e-6F)
		assertTrue(!z.x.isNaN() && !z.y.isNaN())
	}

	@Test
	fun lineWidthIsSane() {
		assertEquals(2.5F, RenderLineRenderer.sanitizeLineWidth(2.5F))
		assertEquals(RenderLineRenderer.DEFAULT_LINE_WIDTH, RenderLineRenderer.sanitizeLineWidth(0F))
		assertEquals(RenderLineRenderer.DEFAULT_LINE_WIDTH, RenderLineRenderer.sanitizeLineWidth(-1F))
		assertEquals(RenderLineRenderer.DEFAULT_LINE_WIDTH, RenderLineRenderer.sanitizeLineWidth(Float.NaN))
	}

	private val format = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH

	@Test
	fun linesFormatIsPositionColorNormalLineWidth() {
		for (e in listOf("Position", "Color", "Normal", "LineWidth")) assertTrue(format.contains(e), e)
		for (e in listOf("UV0", "UV1", "UV2")) assertTrue(!format.contains(e), e)
	}

	@Test
	fun everyRequiredElementIsEmitted() {
		ByteBufferBuilder(1024).use { bytes ->
			val builder = BufferBuilder(bytes, PrimitiveTopology.LINES, format)
			RenderLineRenderer.emitLine(builder, PoseStack().last(), -4F, 4F, 4F, -4F, 0xFF00FF00.toInt(), 2.5F)
			builder.buildOrThrow().use { mesh ->
				assertTrue(mesh.drawState().vertexCount() >= 2)
				val buf = mesh.vertexBuffer().order(ByteOrder.nativeOrder())
				val width = buf.getFloat(format.getElement("LineWidth")!!.offset())
				assertEquals(2.5F, width)
				val c = format.getElement("Color")!!.offset()
				assertNotEquals(0, buf.getInt(c))
				val n = format.getElement("Normal")!!.offset()
				assertTrue(buf.get(n).toInt() != 0 || buf.get(n + 1).toInt() != 0)
			}
		}
	}

	@Test
	fun theOldVertexWithoutColourStillFails() {

		ByteBufferBuilder(1024).use { bytes ->
			val builder = BufferBuilder(bytes, PrimitiveTopology.LINES, format)
			val pose = PoseStack().last()
			builder.addVertex(pose, 0F, 0F, 0F).setNormal(pose, 1F, 0F, 0F).setLineWidth(1F)
			val e = assertThrows(IllegalStateException::class.java) { builder.addVertex(pose, 1F, 0F, 0F) }
			assertTrue(e.message!!.contains("Missing elements"), e.message)
		}
	}
}
