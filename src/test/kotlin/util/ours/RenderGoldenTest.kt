// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import me.shedaniel.math.Color
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import sun.misc.Unsafe
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.render.CustomRenderPipelines
import dev.ashtonite365.apex.util.render.CustomRenderTypes
import dev.ashtonite365.apex.util.render.WorldBatcher
import dev.ashtonite365.apex.util.render.CustomVertexFormats
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.render.guiSprite
import dev.ashtonite365.apex.util.render.lineBetween
import dev.ashtonite365.apex.util.render.textureRegion
import dev.ashtonite365.apex.util.render.enableScissorWithTranslation
import dev.ashtonite365.apex.util.render.enableScissorWithoutTranslation
import dev.ashtonite365.apex.util.render.gui.pip.RenderCircleProgressRenderer
import dev.ashtonite365.apex.util.render.gui.pip.RenderLineRenderer
import dev.ashtonite365.apex.util.render.progressOf
import dev.ashtonite365.apex.util.render.hasIdentityPose
import dev.ashtonite365.apex.util.render.interpolate
import dev.ashtonite365.apex.util.render.blendAngle
import dev.ashtonite365.apex.util.render.state.gui.RenderCircleProgressState
import dev.ashtonite365.apex.util.render.state.gui.RenderLineState
import dev.ashtonite365.apex.util.render.normaliseAngle
import dev.ashtonite365.apex.util.render.HALF_TURN
import dev.ashtonite365.apex.util.render.FULL_TURN

@ExtendWith(AutoBootstrapExtension::class)
class RenderGoldenTest {
	private val f = { v: Float -> Golden.render(v) }

	inner class Rec(val log: MutableList<String>) : VertexConsumer {
		override fun addVertex(x: Float, y: Float, z: Float) = also { log += "v ${f(x)} ${f(y)} ${f(z)}" }
		override fun setColor(r: Int, g: Int, b: Int, a: Int) = also { log += "c $r $g $b $a" }
		override fun setColor(argb: Int) = also { log += "c ${Integer.toHexString(argb)}" }
		override fun setUv(u: Float, v: Float) = also { log += "uv ${f(u)} ${f(v)}" }
		override fun setUv1(u: Int, v: Int) = also { log += "uv1 $u $v" }
		override fun setUv2(u: Int, v: Int) = also { log += "uv2 $u $v" }
		override fun setUv3(u: Float, v: Float) = also { log += "uv3 ${f(u)} ${f(v)}" }
		override fun setNormal(x: Float, y: Float, z: Float) = also { log += "n ${f(x)} ${f(y)} ${f(z)}" }
		override fun setLineWidth(w: Float) = also { log += "w ${f(w)}" }
	}

	private fun pipe(p: RenderPipeline?) = p?.location?.toString()

	private fun withSink(block: (MutableList<String>) -> Unit): List<String> {
		val log = ArrayList<String>()
		WorldBatcher.testSink = WorldBatcher.TestSink { pipeline, setup, alpha ->
			log += "buffer ${pipe(pipeline)} ${if (setup == TextureSetup.noTexture()) "noTexture" else setup.toString()} ${f(alpha)}"
			Rec(log)
		}
		try {
			block(log)
		} catch (e: Throwable) {
			log += "!${e.javaClass.simpleName}: ${e.message}"
		} finally {
			WorldBatcher.testSink = null
		}
		return log
	}

	private fun camera(pos: Vec3, rot: Quaternionf) = CameraRenderState().also { it.pos = pos; it.orientation = rot }

	private fun world(cam: CameraRenderState, pre: (PoseStack) -> Unit = {}, block: WorldDraw.() -> Unit): List<String> = withSink { log ->
		val ps = PoseStack()
		pre(ps)
		WorldDraw.drawInWorld(WorldRenderLastEvent(ps, cam), block)
		log += "pose-after ${ps.last().pose()} empty=${ps.isEmpty}"
	}

	@Test
	fun worldContext() = golden("render-world") {
		dev.ashtonite365.apex.util.render.WorldLineScale.TConfig.explicitDefaultLoad()
		val fz = Fuzz(99)
		val cams = listOf(
			camera(Vec3.ZERO, Quaternionf()),
			camera(Vec3(10.5, 64.25, -3.75), Quaternionf().rotateY(0.7f).rotateX(-0.3f)),
			camera(Vec3(-100.0, 5.0, 2000.0), Quaternionf().rotateZ(1.1f)),
		)
		for ((ci, cam) in cams.withIndex()) for (depth in listOf(false, true)) {
			val tag = "$ci/$depth"
			case("block $tag") { world(cam) { depthTest = depth; block(BlockPos(1, -2, 3), 0x7FFF00AA) } }
			case("box $tag") { world(cam) { depthTest = depth; box(AABB(0.5, 1.0, -2.0, 3.25, 1.5, 4.0), -1) } }
			for (face in -1..6) case("face $face $tag") { world(cam) { depthTest = depth; blockFace(4, 5, -6, face, 0x11223344) } }
			case("voxels $tag") { world(cam) { depthTest = depth; voxelShell(setOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), BlockPos(0, 1, 0), BlockPos(5, 5, 5)), 0x55AA00FF) } }
			case("voxelsEmpty $tag") { world(cam) { voxelShell(setOf(), 1) } }
			case("tiny $tag") { world(cam) { depthTest = depth; tinyBlock(Vec3(1.5, 2.5, 3.5), 0.3f, 0xFF00FF00.toInt()) } }
			case("wire $tag") { world(cam) { depthTest = depth; wireframeCube(BlockPos(7, 8, 9)) } }
			case("wire2 $tag") { world(cam) { wireframeCube(BlockPos(-1, 0, 1), 2.5f, 0x12345678) } }
			case("line $tag") { world(cam) { depthTest = depth; line(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 2.0, 3.0), Vec3(1.0, 2.0, 30.0), Vec3(-40.0, 2.0, 3.0), color = 0x7F00FF00) } }
			case("line1 $tag") { world(cam) { line(Vec3(1.0, 1.0, 1.0), color = 5) } }
			case("lineSame $tag") { world(cam) { line(listOf(Vec3(1.0, 1.0, 1.0), Vec3(1.0, 1.0, 1.0)), 5, 4f) } }
			case("tracer $tag") { world(cam) { depthTest = depth; tracer(Vec3(100.0, 70.0, 100.0), -1) } }
			case("facing $tag") {
				world(cam) {
					for (p in listOf(Vec3(1.0, 2.0, 3.0), Vec3(50.0, 60.0, 70.0), cam.pos.add(9.99, 0.0, 0.0), cam.pos.add(0.0, 10.0, 0.0)))
						facingCamera(p) { WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS).addVertex(worldPoses.last().pose(), 1f, 2f, 3f) }
				}
			}
			case("facingEmptyText $tag") { world(cam) { facingCamera(Vec3(1.0, 1.0, 1.0)) { text() } } }
			repeat(6) { i ->
				val pts = List(fz.r.nextInt(5)) { Vec3(fz.r.nextDouble(-50.0, 50.0), fz.r.nextDouble(-50.0, 50.0), fz.r.nextDouble(-50.0, 50.0)) }
				val col = fz.r.nextInt(); val w = fz.r.nextFloat() * 20
				case("fuzzline $i $tag") { world(cam) { depthTest = depth; line(pts, col, w) } }
				val a = Vec3(fz.r.nextDouble(-50.0, 50.0), fz.r.nextDouble(-50.0, 50.0), fz.r.nextDouble(-50.0, 50.0))
				val b = a.add(fz.r.nextDouble(0.0, 5.0), fz.r.nextDouble(0.0, 5.0), fz.r.nextDouble(0.0, 5.0))
				case("fuzzbox $i $tag") { world(cam) { depthTest = depth; box(AABB(a, b), col) } }
			}
		}
		case("prePosed") { world(cams[1], { it.translate(3f, 4f, 5f); it.scale(2f, 2f, 2f) }) { block(BlockPos.ZERO, -1); line(Vec3.ZERO, Vec3(1.0, 1.0, 1.0), color = -1) } }
		case("alignNames") { WorldDraw.LineAnchor.entries.toList() }
		case("buffers") {
			withSink {
				WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_DEPTH_QUADS)
				WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_DEPTH_QUADS, TextureSetup.noTexture())
				WorldBatcher.sinkFor(CustomRenderPipelines.DEPTH_LINES, TextureSetup.noTexture(), 0.5f)
			}
		}
	}

	private fun dump(o: Any?): String {
		if (o == null) return "null"
		val parts = o.javaClass.methods.filter { it.parameterCount == 0 && it.declaringClass != Any::class.java && (it.name.startsWith("get") || it.name.startsWith("is") || it.name.startsWith("has")) && it.name != "hashCode" && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
			.sortedBy { it.name }
			.map { m -> m.name + "=" + runCatching { m.invoke(o) }.fold({ if (m.name == "getBindGroupLayouts" && it is Iterable<*>) Golden.render(it.map { l -> l.toString() }.sorted()) else Golden.render(it) }, { "!" + (it.cause ?: it).javaClass.simpleName }) }
		return (parts.joinToString("; ") + " | " + o.toString()).replace(Regex("@[0-9a-f]{4,}"), "@H").replace(LAMBDA_ADDR, "LAMBDA")
	}

	@Test
	fun pipelinesAndTypes() = golden("render-pipelines") {
		for (fld in CustomRenderPipelines::class.java.declaredFields.sortedBy { it.name }) {
			fld.isAccessible = true
			case("pipeline ${fld.name}") { dump(fld.get(null)) }
		}
		case("format") { dump(CustomVertexFormats.POSITION_TEX_COLOR_RADIUS) + " " + CustomVertexFormats.POSITION_TEX_COLOR_RADIUS.elements.map { it.toString() } }
		val id = Identifier.fromNamespaceAndPath("apex", "textures/test.png")
		for (fld in CustomRenderTypes::class.java.declaredFields.sortedBy { it.name }) {
			fld.isAccessible = true
			case("type ${fld.name}") {
				val v = fld.get(null)
				val t = if (v is java.util.function.Function<*, *>) (v as java.util.function.Function<Identifier, RenderType>).apply(id) else v
				val again = if (v is java.util.function.Function<*, *>) (v as java.util.function.Function<Identifier, RenderType>).apply(id) === t else null
				dump(t) + " memo=$again"
			}
		}
	}

	private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) as Unsafe }

	class RecGui(mc: net.minecraft.client.Minecraft, s: GuiRenderState) : GuiGraphicsExtractor(mc, s, 0, 0) {
		@JvmField var log: MutableList<String>? = null
		override fun enableScissor(a: Int, b: Int, c: Int, d: Int) { log!! += "scissor $a $b $c $d" }
		override fun verticalLine(a: Int, b: Int, c: Int, d: Int) { log!! += "vline $a $b $c ${Integer.toHexString(d)}" }
		override fun horizontalLine(a: Int, b: Int, c: Int, d: Int) { log!! += "hline $a $b $c ${Integer.toHexString(d)}" }
		override fun blitSprite(p: RenderPipeline, id: Identifier, x: Int, y: Int, w: Int, h: Int) { log!! += "sprite ${p.location} $id $x $y $w $h" }
		override fun blit(p: RenderPipeline, id: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, rw: Int, rh: Int, tw: Int, th: Int) {
			log!! += "blit ${p.location} $id $x $y $u $v $w $h $rw $rh $tw $th"
		}
	}

	class RecState : GuiRenderState() {
		@JvmField var log: MutableList<String>? = null
		override fun addPicturesInPictureState(state: PictureInPictureRenderState) { log!! += "pip " + state.toString().replace(Regex("@[0-9a-f]{4,}"), "@H").replace(LAMBDA_ADDR, "LAMBDA") }
	}

	private fun gui(setup: (org.joml.Matrix3x2fStack) -> Unit = {}, block: GuiGraphicsExtractor.() -> Unit): List<String> {
		val log = ArrayList<String>()
		val state = unsafe.allocateInstance(RecState::class.java) as RecState
		state.log = log
		val g = unsafe.allocateInstance(RecGui::class.java) as RecGui
		g.log = log
		fun set(name: String, v: Any) = GuiGraphicsExtractor::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(g, v)
		set("pose", org.joml.Matrix3x2fStack(16))
		set("guiRenderState", state)
		setup(g.pose())
		try { g.block() } catch (e: Throwable) { log += "!${e.javaClass.simpleName}: ${e.message}" }
		return log
	}

	@Test
	fun guiHelpers() = golden("render-gui") {
		val poses = listOf<(org.joml.Matrix3x2fStack) -> Unit>({}, { it.translate(10f, 20f) }, { it.scale(2f, 3f) }, { it.translate(-5.5f, 7.25f).scale(0.5f) }, { it.rotate(0.3f) }, { it.translate(0f, 0f) }, { it.scale(1f, -1f) })
		val tex = Identifier.fromNamespaceAndPath("apex", "x")
		for ((pi, p) in poses.withIndex()) {
			case("untranslated $pi") { gui(p) { (this as RecGui).log!! += "u=" + hasIdentityPose() } }
			case("sprite $pi") { gui(p) { guiSprite(tex, 1, 2, 4, 5) ; guiSprite(tex, 6, 7, 8, 9) } }
			case("texture $pi") { gui(p) { textureRegion(tex, 1, 2, 0.5f, 0.25f, 10, 20, 256, 128) } }
			case("box $pi") { gui(p) { outlineBox(3, 4, 10, 20, 0x11223344); outlineBox(0, 0, -5, 0, -1) } }
			case("scissor $pi") {
				gui(p) {
					enableScissorWithTranslation(Rectangle(1, 2, 30, 40))
					enableScissorWithTranslation(1.9f, -2.5f, 30.5f, 40.99f)
					enableScissorWithoutTranslation(1.9f, -2.5f, 30.5f, 40.99f)
					enableScissorWithoutTranslation(0f, 0f, 100f, 100f)
				}
			}
			for ((li, l) in listOf(listOf(0, 0, 10, 10), listOf(10, 0, 0, 10), listOf(0, 10, 10, 0), listOf(10, 10, 0, 0), listOf(5, 5, 5, 5), listOf(-3, 7, 12, 7), listOf(4, -2, 4, 20)).withIndex())
				case("line $pi $li") { gui(p) { lineBetween(l[0], l[1], l[2], l[3], Color.ofRGBA(10, 20, 30, 40)); lineBetween(l[0], l[1], l[2], l[3], Color.ofTransparent(-1), 3.5f) } }
			case("circle $pi") {
				gui(p) {
					RenderCircleProgressRenderer.extractCircle(this, tex, 0.4f, 0f, 1f, 0f, 1f, -1)
					RenderCircleProgressRenderer.extractCircularSlice(this, CustomRenderTypes.COLORED_QUADS, 0.1f, 0.9f, 0.2f, 0.8f, 0.5f..2.5f, 0x7F7F7F7F, 0.25f)
				}
			}
		}
	}

	private fun <T : Any> allocate(c: Class<T>): T = c.cast(unsafe.allocateInstance(c))

	private fun submit(block: (SubmitNodeCollector) -> Unit): List<String> {
		val log = ArrayList<String>()
		val collector = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SubmitNodeCollector::class.java)) { _, m, args ->
			if (m.name == "submitCustomGeometry") {
				val ps = args[0] as PoseStack
				log += "submit ${(args[1] as RenderType).toString().replace(Regex("@[0-9a-f]{4,}"), "@H").replace(LAMBDA_ADDR, "LAMBDA")} pose=${ps.last().pose()}"
				(args[2] as SubmitNodeCollector.CustomGeometryRenderer).render(ps.last(), Rec(log))
				null
			} else {
				log += "call ${m.name}"
				null
			}
		} as SubmitNodeCollector
		try { block(collector) } catch (e: Throwable) { log += "!${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}" }
		return log
	}

	@Test
	fun pipRenderers() = golden("render-pip") {
		val line = allocate(RenderLineRenderer::class.java)
		val lineM = RenderLineRenderer::class.java.getDeclaredMethod("renderToTexture", RenderLineState::class.java, PoseStack::class.java, SubmitNodeCollector::class.java).also { it.isAccessible = true }
		val rect = net.minecraft.client.gui.navigation.ScreenRectangle(1, 2, 3, 4)
		for (dir in RenderLineState.LineDirection.entries) for (w in listOf(1f, 0f, -2f, Float.NaN, Float.POSITIVE_INFINITY, 4.5f)) for ((ww, hh) in listOf(10 to 20, 0 to 0, 7 to 0, 3 to 9)) {
			case("line $dir $w $ww $hh") {
				submit { c ->
					val ps = PoseStack(); ps.translate(1f, 2f, 0f)
					lineM.invoke(line, RenderLineState(0, 0, 1, 1, 1f, rect, w, ww, hh, 0x7F112233, dir), ps, c)
					c.toString()
				}
			}
		}
		case("lineLabel") { RenderLineRenderer::class.java.getDeclaredMethod("getTextureLabel").also { it.isAccessible = true }.invoke(line) }
		case("lineTranslateY") { RenderLineRenderer::class.java.getDeclaredMethod("getTranslateY", Int::class.java, Int::class.java).also { it.isAccessible = true }.let { m -> listOf(m.invoke(line, 10, 2), m.invoke(line, 7, 3)) } }
		case("lineStateClass") { line.renderStateClass.name }
		case("lineConsts") { RenderLineRenderer.DEFAULT_LINE_WIDTH }
		for ((i, v) in listOf(listOf(0f, 0f, 0f, 0f), listOf(0f, 0f, 3f, 4f), listOf(1f, 1f, 1f, 1.0000001f), listOf(-1f, 2f, 5f, -6f)).withIndex())
			case("normal $i") { RenderLineRenderer.lineNormal(v[0], v[1], v[2], v[3]).toString() }
		for (w in listOf(1f, 0f, -0f, -1f, Float.NaN, Float.NEGATIVE_INFINITY, 1e-30f, 3.3f)) case("sanitize $w") { RenderLineRenderer.sanitizeLineWidth(w) }
		case("emit") { val l = ArrayList<String>(); RenderLineRenderer.emitLine(Rec(l), PoseStack().last(), 1f, 2f, 3f, 4f, 5, 0f); l }

		val circle = allocate(RenderCircleProgressRenderer::class.java)
		val circleM = RenderCircleProgressRenderer::class.java.getDeclaredMethod("renderToTexture", RenderCircleProgressState::class.java, PoseStack::class.java, SubmitNodeCollector::class.java).also { it.isAccessible = true }
		for (range in listOf(0f..0f, 0f..1f, -1.5f..1.5f, 0f..(2 * Math.PI.toFloat()), 1f..0.5f)) for (cut in listOf(0f, 0.5f, -1f)) {
			case("circle $range $cut") {
				submit { c ->
					val ps = PoseStack()
					circleM.invoke(circle, RenderCircleProgressState(CustomRenderTypes.COLORED_QUADS, 0, 0, 2, 2, 0f, 0f, 1f, 1f, range, -1, cut, 1f, rect, null), ps, c)
				}
			}
		}
		case("circleLabel") { RenderCircleProgressRenderer::class.java.getDeclaredMethod("getTextureLabel").also { it.isAccessible = true }.invoke(circle) }
		case("circleStateClass") { circle.renderStateClass.name }
		case("lineState") { RenderLineState(1, 2, 3, 4, 1f, rect, 2f, 5, 6, 7, RenderLineState.LineDirection.TOP_LEFT_TO_BOTTOM_RIGHT).let { listOf(it.scissorArea(), it.toString()) } }
	}

	@Test
	fun lerps() = golden("render-lerp") {
		case("consts") { listOf(HALF_TURN, FULL_TURN) }
		val fz = Fuzz(7)
		val specials = listOf(0f, -0f, 1f, -1f, 3.1415927f, -3.1415927f, 6.2831855f, 7f, -7f, 100f, 1e-7f, Float.NaN, Float.POSITIVE_INFINITY)
		val vals = specials + List(150) { (fz.r.nextFloat() - 0.5f) * 40f }
		for ((i, a) in vals.withIndex()) {
			val b = vals[(i * 7 + 3) % vals.size]
			val p = vals[(i * 13 + 5) % vals.size] / 20f
			case("angle $i") { listOf(blendAngle(a, b, p), blendAngle(a, a + HALF_TURN.toFloat(), p), normaliseAngle(a), normaliseAngle(a.toDouble() * 1.5)) }
			case("lerp $i") { listOf(interpolate(a, b, p), interpolate(a.toInt(), b.toInt(), p), progressOf(a, b, p), progressOf(a, a, p)) }
		}
		repeat(80) { i ->
			val a = Color.ofTransparent(fz.r.nextInt()); val b = Color.ofTransparent(fz.r.nextInt()); val p = fz.r.nextFloat() * 1.4f - 0.2f
			case("colour $i") { interpolate(a, b, p).color }
		}
	}

	@Test
	fun circleProgressDrawsSlices() {
		val circle = allocate(RenderCircleProgressRenderer::class.java)
		val m = RenderCircleProgressRenderer::class.java.getDeclaredMethod("renderToTexture", RenderCircleProgressState::class.java, PoseStack::class.java, SubmitNodeCollector::class.java).also { it.isAccessible = true }
		val rect = net.minecraft.client.gui.navigation.ScreenRectangle(1, 2, 3, 4)
		fun draw(range: ClosedFloatingPointRange<Float>) = submit { c -> m.invoke(circle, RenderCircleProgressState(CustomRenderTypes.COLORED_QUADS, 0, 0, 2, 2, 0f, 0f, 1f, 1f, range, -1, 0.5f, 1f, rect, null), PoseStack(), c) }
		val log = draw(0f..1f)
		org.junit.jupiter.api.Assertions.assertTrue(log.any { it.startsWith("submit") }, "a real range submits geometry: $log")
		org.junit.jupiter.api.Assertions.assertTrue(log.count { it.startsWith("v ") } >= 3, "at least one triangle: $log")
		val empty = draw(0f..0f)
		org.junit.jupiter.api.Assertions.assertTrue(empty.none { it.startsWith("v ") }, "an empty range draws nothing: $empty")
		org.junit.jupiter.api.Assertions.assertFalse(RenderCircleProgressRenderer.drawsSlices(listOf(0f)))
		org.junit.jupiter.api.Assertions.assertTrue(RenderCircleProgressRenderer.drawsSlices(listOf(0f, 1f)))
	}

	@Test
	fun upwardLineKeepsWidth() {
		val down = gui { lineBetween(0, 0, 10, 10, Color.ofTransparent(-1), 3.5f) }.single { it.startsWith("pip") }
		val up = gui { lineBetween(10, 10, 0, 0, Color.ofTransparent(-1), 3.5f) }.single { it.startsWith("pip") }
		org.junit.jupiter.api.Assertions.assertEquals(down, up)
		org.junit.jupiter.api.Assertions.assertTrue("3.5" in up, up)
	}
}

private val LAMBDA_ADDR = Regex("[$][$]Lambda/0x[0-9a-f]+")
