// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.ChatFormatting
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.golden.HeadlessGui
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.render.MarkerShape
import dev.ashtonite365.apex.util.render.UvRect
import dev.ashtonite365.apex.util.render.WaypointLooks
import dev.ashtonite365.apex.util.render.WaypointType
import dev.ashtonite365.apex.util.render.WorldBatcher
import dev.ashtonite365.apex.util.render.WorldDraw

@ExtendWith(AutoBootstrapExtension::class)
class WorldBillboardEquivalenceTest {
	private fun pipe(p: RenderPipeline?) = p?.location?.toString()

	private fun world(cam: CameraRenderState, block: WorldDraw.() -> Unit): String {
		val log = ArrayList<String>()
		WorldBatcher.testSink = WorldBatcher.TestSink { pipeline, setup, alpha ->
			log += "buffer ${pipe(pipeline)} ${if (setup == TextureSetup.noTexture()) "noTexture" else setup.toString().replace(Regex("@[0-9a-f]{4,}"), "@H")} $alpha"
			HeadlessGui.VertexLog(log)
		}
		try {
			HeadlessGui.withFakeGame { WorldDraw.drawInWorld(WorldRenderLastEvent(PoseStack(), cam), block) }
		} catch (e: Throwable) {
			log += "!${e.javaClass.simpleName}"; if (System.getenv("WB_TRACE") != null) System.err.println("TRACE " + e + " " + e.stackTrace.take(6).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
		} finally {
			WorldBatcher.testSink = null
		}
		return log.joinToString(" ; ")
	}

	private fun camera(pos: Vec3, rot: Quaternionf) = CameraRenderState().also { it.pos = pos; it.orientation = rot }

	@Test
	fun billboards() {
		dev.ashtonite365.apex.util.render.WorldLineScale.TConfig.explicitDefaultLoad()
		dev.ashtonite365.apex.util.render.WaypointStyle.TConfig.explicitDefaultLoad()
		WaypointLooks.TConfig.explicitDefaultLoad()
		dev.ashtonite365.apex.util.hud.HudStyle.TConfig.explicitDefaultLoad()
		val out = ArrayList<String>()
		fun case(label: String, s: String) { out += "$label\t$s" }
		val cams = listOf(camera(Vec3.ZERO, Quaternionf()), camera(Vec3(10.5, 64.25, -3.75), Quaternionf().rotateY(0.7f).rotateX(-0.3f)))
		val texts = listOf(
			listOf(Component.literal("Hi")),
			listOf(Component.literal("Two"), Component.literal("lines").withStyle(ChatFormatting.RED)),
			listOf(Component.literal("a"), Component.literal("bb"), Component.literal("ccc")),
			listOf(Component.literal("Bold").withStyle(Style.EMPTY.withBold(true))),
			listOf(Component.empty()),
		)
		val tex = Identifier.fromNamespaceAndPath("apex", "x")
		for ((ci, cam) in cams.withIndex()) for (depth in listOf(false, true)) {
			val tag = "$ci/$depth"
			for ((ti, t) in texts.withIndex()) {
				for (anchor in WorldDraw.LineAnchor.entries) {
					case("text $ti $anchor $tag", world(cam) { depthTest = depth; text(Vec3(1.0, 2.0, 3.0), *t.toTypedArray(), anchor = anchor) })
					case("bbtext $ti $anchor $tag", world(cam) { depthTest = depth; facingCamera(Vec3(-4.0, 70.0, 8.5)) { text(*t.toTypedArray(), anchor = anchor) } })
				}
				for (bg in listOf(0, 0x40000000, -1)) case("bbtextBg $ti $bg $tag", world(cam) { facingCamera(Vec3(0.0, 1.0, 0.0)) { text(*t.toTypedArray(), background = bg) } })
			}
			case("texture $tag", world(cam) { depthTest = depth; texture(Vec3(1.0, 2.0, 3.0), tex, 16, 8) })
			case("textureUv $tag", world(cam) { texture(Vec3(1.0, 2.0, 3.0), tex, 5, 7, UvRect(0.25f, 0.5f, 0.75f, 1f)) })
			case("bbtexture $tag", world(cam) { facingCamera(Vec3(2.0, 2.0, 2.0)) { texture(tex, 4, 4) } })
			for (b in listOf(BlockPos(0, 0, 0), BlockPos(100, 64, -100), BlockPos(10, 64, -4)))
				case("bbwaypoint $b $tag", world(cam) { depthTest = depth; facingCamera(Vec3.atCenterOf(b)) { waypoint(b, Component.literal("Spot")) } })
			for (b in listOf(BlockPos(0, 0, 0), BlockPos(100, 64, -100), BlockPos(10, 64, -4))) {
				case("waypoint0 $b $tag", world(cam) { depthTest = depth; waypoint(b) })
				case("waypoint2 $b $tag", world(cam) { depthTest = depth; waypoint(b, Component.literal("A"), Component.literal("B")) })
			}
			for (shape in MarkerShape.entries) for (op in listOf(0, 40, 100)) for (size in listOf(0.5, 1.0, 2.5)) {
				val look = WaypointLooks.Look(shape, op, size)
				case("marker $shape $op $size $tag", world(cam) { depthTest = depth; marker(3.5, 65.0, -2.5, 0x7F11AA22, look) })
				case("markerS $shape $op $size $tag", world(cam) { marker(3.5, 65.0, -2.5, -1, look, scale = 1.5f) })
				case("lookBlock $shape $op $size $tag", world(cam) { setLook(look); block(BlockPos(1, 2, 3), 0x7FFF00AA); box(AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0), -1); markerBox(AABB(0.5, 1.0, 0.5, 1.5, 3.0, 2.0), 0x40FFFFFF) })
			}
			case("markerBox $tag", world(cam) { depthTest = depth; markerBox(AABB(0.5, 1.0, 0.5, 1.5, 3.0, 2.0), 0x40FFFFFF) })
			for (type in WaypointType.entries) case("looks $type $tag", world(cam) { looks(type) { block(BlockPos(1, 2, 3), -1); markerBox(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), 0x7F00FF00) } })
		}
		OursGolden.checkDigests("world-billboard", out.map { it.substringBefore('\t') to it.substringAfter('\t') })
	}
}
