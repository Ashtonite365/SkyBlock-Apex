package dev.ashtonite365.apex.util.render

import org.joml.Matrix4f
import org.joml.Vector3f
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.center

data class UvRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float) {
	companion object {
		val WHOLE = UvRect(0f, 0f, 1f, 1f)
	}
}

@RenderContextDSL
class WorldDraw private constructor(
	val worldPoses: PoseStack,
	private val viewer: CameraRenderState,
) {
	var depthTest = false

	var look: WaypointLooks.Look? = null
		private set

	inline fun looks(type: WaypointType, block: WorldDraw.() -> Unit) {
		val before = look
		setLook(WaypointLooks.of(type))
		try {
			block()
		} finally {
			setLook(before)
		}
	}

	@PublishedApi
	internal fun setLook(value: WaypointLooks.Look?) {
		look = value
	}

	private val axisRight = Vector3f()
	private val axisUp = Vector3f()
	private val corners = FloatArray(24)
	private val dirScratch = Vector3f()

	private fun faded(colour: Int): Int = look?.let { WaypointLooks.fade(colour, it.opacity) } ?: colour

	private fun seeThroughFillSink(): VertexConsumer = WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS)

	private fun fillSink(): VertexConsumer =
		WorldBatcher.sinkFor(if (depthTest) CustomRenderPipelines.COLORED_DEPTH_QUADS else CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS)

	private fun lineSink(): VertexConsumer =
		WorldBatcher.sinkFor(if (depthTest) CustomRenderPipelines.DEPTH_LINES else CustomRenderPipelines.OMNIPRESENT_LINES)

	private inline fun pushed(moveTo: PoseStack.() -> Unit, draw: (Matrix4f) -> Unit) {
		worldPoses.pushPose()
		worldPoses.moveTo()
		draw(worldPoses.last().pose())
		worldPoses.popPose()
	}

	private fun PoseStack.moveTo(x: Double, y: Double, z: Double) {
		translate(-viewer.pos.x, -viewer.pos.y, -viewer.pos.z)
		translate(x, y, z)
	}

	private fun PoseStack.moveToBlock(x: Int, y: Int, z: Int) = moveTo(x.toDouble(), y.toDouble(), z.toDouble())

	fun block(blockPos: BlockPos, color: Int) {
		val current = look
		if (current != null && !WaypointLooks.isPlainCube(current)) {
			marker(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5, color, current)
			return
		}
		pushed({ moveToBlock(blockPos.x, blockPos.y, blockPos.z) }) { UnitCube.all(it, fillSink(), faded(color)) }
	}

	fun marker(x: Double, y: Double, z: Double, color: Int, look: WaypointLooks.Look, scale: Float = 1f) {
		val c = WaypointLooks.fade(color, look.opacity)
		val size = (look.size * scale).toFloat()
		val shape = look.shape
		when {
			shape.flat -> flatShape(shape, x, y, z, size, c)
			shape == MarkerShape.CUBE_FILLED -> {
				val half = size / 2.0
				pushed({
					moveTo(x - half, y - half, z - half)
					scale(size, size, size)
				}) { UnitCube.all(it, fillSink(), c) }
			}
			shape == MarkerShape.CUBE_OUTLINE -> cubeOutline(x, y, z, size, c)
			else -> sphere(x, y, z, size, c, shape.filled)
		}
	}

	private fun flatShape(shape: MarkerShape, x: Double, y: Double, z: Double, size: Float, c: Int) {
		MarkerGeometry.flatAxes(WaypointStyle.TConfig.flatFacePlayer, viewer.orientation, axisRight, axisUp)
		val cx = (x - viewer.pos.x).toFloat()
		val cy = (y - viewer.pos.y).toFloat()
		val cz = (z - viewer.pos.z).toFloat()
		val pose = worldPoses.last().pose()
		val isSquare = shape == MarkerShape.SQUARE_FILLED || shape == MarkerShape.SQUARE_OUTLINE
		if (isSquare) {
			MarkerGeometry.squareCorners(cx, cy, cz, size, axisRight, axisUp, corners)
			if (shape.filled) {
				val sink = fillSink()
				for (i in 0 until 4) sink.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]).setColor(c)
			} else {
				val sink = lineSink()
				val width = markerLineWidth(x, y, z)
				for (i in 0 until 4) segment(sink, corners, i, corners, (i + 1) % 4, width, c)
			}
			return
		}
		val radius = MarkerGeometry.CIRCLE_RADIUS * size
		val n = MarkerGeometry.CIRCLE_SEGMENTS
		val rim = FloatArray((n + 1) * 3)
		for (i in 0..n) {
			val k = i % n
			val cos = MarkerGeometry.COS[k] * radius
			val sin = MarkerGeometry.SIN[k] * radius
			rim[i * 3] = axisRight.x * cos + axisUp.x * sin
			rim[i * 3 + 1] = axisRight.y * cos + axisUp.y * sin
			rim[i * 3 + 2] = axisRight.z * cos + axisUp.z * sin
		}
		if (shape.filled) {
			val sink = fillSink()
			for (i in 0 until n) {
				sink.addVertex(pose, cx, cy, cz).setColor(c)
				sink.addVertex(pose, cx, cy, cz).setColor(c)
				sink.addVertex(pose, cx + rim[i * 3], cy + rim[i * 3 + 1], cz + rim[i * 3 + 2]).setColor(c)
				sink.addVertex(pose, cx + rim[i * 3 + 3], cy + rim[i * 3 + 4], cz + rim[i * 3 + 5]).setColor(c)
			}
		} else {
			val sink = lineSink()
			val width = markerLineWidth(x, y, z)
			val pose3 = worldPoses.last()
			for (i in 0 until n) {

				val dir = dirScratch.set(rim[i * 3 + 3] - rim[i * 3], rim[i * 3 + 4] - rim[i * 3 + 1], rim[i * 3 + 5] - rim[i * 3 + 2]).normalize()
				for (k in i..i + 1)
					sink.addVertex(pose3, cx + rim[k * 3], cy + rim[k * 3 + 1], cz + rim[k * 3 + 2]).setColor(c).setLineWidth(width).setNormal(pose3, dir.x, dir.y, dir.z)
			}
		}
	}

	private fun markerLineWidth(x: Double, y: Double, z: Double): Float {
		val dx = x - viewer.pos.x
		val dy = y - viewer.pos.y
		val dz = z - viewer.pos.z
		return WorldLineScale.width(MARKER_LINE, Math.sqrt(dx * dx + dy * dy + dz * dz))
	}

	private fun segment(sink: VertexConsumer, a: FloatArray, i: Int, b: FloatArray, j: Int, width: Float, c: Int) {
		val pose = worldPoses.last()
		val ax = a[i * 3]; val ay = a[i * 3 + 1]; val az = a[i * 3 + 2]
		val bx = b[j * 3]; val by = b[j * 3 + 1]; val bz = b[j * 3 + 2]
		val dir = dirScratch.set(bx - ax, by - ay, bz - az).normalize()
		sink.addVertex(pose, ax, ay, az).setColor(c).setLineWidth(width).setNormal(pose, dir.x, dir.y, dir.z)
		sink.addVertex(pose, bx, by, bz).setColor(c).setLineWidth(width).setNormal(pose, dir.x, dir.y, dir.z)
	}

	private fun cubeOutline(x: Double, y: Double, z: Double, size: Float, c: Int) {
		val half = size / 2.0
		for (corner in 0 until 8) {
			corners[corner * 3] = (x - viewer.pos.x + if (corner and 1 != 0) half else -half).toFloat()
			corners[corner * 3 + 1] = (y - viewer.pos.y + if (corner and 2 != 0) half else -half).toFloat()
			corners[corner * 3 + 2] = (z - viewer.pos.z + if (corner and 4 != 0) half else -half).toFloat()
		}
		val sink = lineSink()
		val width = markerLineWidth(x, y, z)
		for (e in CUBE_EDGES.indices step 2) segment(sink, corners, CUBE_EDGES[e], corners, CUBE_EDGES[e + 1], width, c)
	}

	private fun sphere(x: Double, y: Double, z: Double, size: Float, c: Int, filled: Boolean) {
		val width = markerLineWidth(x, y, z)
		pushed({
			moveTo(x, y, z)
			scale(size, size, size)
		}) { sphereBody(it, c, filled, width) }
	}

	private fun sphereBody(m: Matrix4f, c: Int, filled: Boolean, width: Float) {
		val points = MarkerGeometry.SPHERE
		val rings = MarkerGeometry.SPHERE_RINGS
		val segments = MarkerGeometry.SPHERE_SEGMENTS
		fun at(ring: Int, segment: Int) = MarkerGeometry.sphereIndex(ring, segment)
		if (filled) {
			val sink = fillSink()
			for (ring in 0 until rings) for (s in 0 until segments) {
				for (k in intArrayOf(at(ring, s), at(ring, s + 1), at(ring + 1, s + 1), at(ring + 1, s)))
					sink.addVertex(m, points[k], points[k + 1], points[k + 2]).setColor(c)
			}
			return
		}
		val sink = lineSink()
		val pose = worldPoses.last()
		fun line(p: Int, q: Int) {
			val dir = dirScratch.set(points[q] - points[p], points[q + 1] - points[p + 1], points[q + 2] - points[p + 2]).normalize()
			sink.addVertex(m, points[p], points[p + 1], points[p + 2]).setColor(c).setLineWidth(width).setNormal(pose, dir.x, dir.y, dir.z)
			sink.addVertex(m, points[q], points[q + 1], points[q + 2]).setColor(c).setLineWidth(width).setNormal(pose, dir.x, dir.y, dir.z)
		}
		for (ring in 1 until rings) for (s in 0 until segments) line(at(ring, s), at(ring, s + 1))
		for (s in 0 until segments step 2) for (ring in 0 until rings) line(at(ring, s), at(ring + 1, s))
	}

	fun box(aabb: AABB, color: Int) =
		pushed({
			moveTo(aabb.minX, aabb.minY, aabb.minZ)
			scale(aabb.xsize.toFloat(), aabb.ysize.toFloat(), aabb.zsize.toFloat())
		}) { UnitCube.all(it, fillSink(), faded(color)) }

	fun markerBox(aabb: AABB, color: Int) {
		val current = look
		if (current == null || WaypointLooks.isPlainCube(current)) {
			box(aabb, color)
			return
		}
		val centre = aabb.center
		if (current.shape == MarkerShape.CUBE_FILLED) {

			val size = current.size
			box(AABB.ofSize(centre, aabb.xsize * size, aabb.ysize * size, aabb.zsize * size), color)
			return
		}
		marker(centre.x, centre.y, centre.z, color, current, maxOf(aabb.xsize, aabb.ysize, aabb.zsize).toFloat())
	}

	fun blockFace(x: Int, y: Int, z: Int, face: Int, color: Int) =
		pushed({ moveToBlock(x, y, z) }) { UnitCube.face(it, fillSink(), face, faded(color)) }

	fun voxelShell(blocks: Set<BlockPos>, color: Int) {
		val sink = seeThroughFillSink()
		val neighbour = BlockPos.MutableBlockPos()
		fun exposed(pos: BlockPos, face: Int): Boolean =
			neighbour.setWithOffset(pos, UnitCube.dx(face), UnitCube.dy(face), UnitCube.dz(face)) !in blocks
		val c = faded(color)
		for (pos in blocks) pushed({ moveToBlock(pos.x, pos.y, pos.z) }) { m ->
			for (face in 0 until 6) if (exposed(pos, face)) UnitCube.face(m, sink, face, c)
		}
	}

	enum class LineAnchor(private val firstLine: (count: Int) -> Float) {
		TOP({ 0f }),
		BOTTOM({ -it.toFloat() }),
		CENTER({ -it / 2f });

		fun offsetOf(index: Int, count: Int): Float = (index + firstLine(count)) * LINE_HEIGHT
	}

	fun waypoint(at: BlockPos, vararg label: Component) {
		val lines = label.toMutableList()
		if (WaypointStyle.TConfig.showDistance) {
			val distance = Client.player?.position()?.distanceTo(at.center()) ?: FALLBACK_DISTANCE
			lines += Component.literal("§e" + ApexFormatters.distanceText(distance))
		}
		if (lines.isEmpty()) return
		text(Vec3.atCenterOf(at), *lines.toTypedArray(), anchor = LineAnchor.CENTER, background = DEFAULT_WAYPOINT_BACKGROUND)
	}

	fun facingCamera(position: Vec3, block: BillboardContext.() -> Unit) {
		val offset = position.subtract(viewer.pos)
		val distance = offset.length()
		worldPoses.pushPose()
		worldPoses.moveTo(position.x, position.y, position.z)
		if (distance > BILLBOARD_MAX_DISTANCE) {

			val back = offset.normalize().scale(-(distance - BILLBOARD_MAX_DISTANCE))
			worldPoses.translate(back.x, back.y, back.z)
		}
		worldPoses.rotate(viewer.orientation)
		worldPoses.scale(BILLBOARD_SCALE, -BILLBOARD_SCALE, 1f)
		BillboardContext(this).block()
		worldPoses.popPose()
	}

	fun sprite(at: Vec3, sprite: TextureAtlasSprite, width: Int, height: Int) =
		texture(at, sprite.atlasLocation(), width, height, UvRect(sprite.u0, sprite.v0, sprite.u1, sprite.v1))

	fun texture(at: Vec3, texture: Identifier, width: Int, height: Int, uv: UvRect = UvRect.WHOLE) =
		facingCamera(at) { texture(texture, width, height, uv) }

	fun text(at: Vec3, vararg lines: Component, anchor: LineAnchor = LineAnchor.CENTER, background: Int = BillboardContext.SHADE) =
		facingCamera(at) { text(*lines, anchor = anchor, background = background) }

	fun tinyBlock(centre: Vec3, size: Float, color: Int) {
		val half = size / 2.0
		pushed({
			moveTo(centre.x - half, centre.y - half, centre.z - half)
			scale(size, size, size)
		}) { UnitCube.all(it, seeThroughFillSink(), faded(color)) }
	}

	fun wireframeCube(blockPos: BlockPos, lineWidth: Float = WIDE, color: Int = -1) {
		val sink = WorldBatcher.sinkFor(RenderPipelines.LINES)
		worldPoses.pushPose()
		worldPoses.moveToBlock(blockPos.x, blockPos.y, blockPos.z)
		val pose = worldPoses.last()
		val from = Vector3f()
		val to = Vector3f()
		for (e in WIRE_EDGES) {
			corner(e[0], from)
			corner(e[1], to)
			val axis = e[2]
			emitEdgeVertex(sink, pose, from, lineWidth.forPoint(blockPos, from), color, axis)
			emitEdgeVertex(sink, pose, to, lineWidth.forPoint(blockPos, to), color, axis)
		}
		worldPoses.popPose()
	}

	private fun corner(bits: Int, out: Vector3f) {
		out.set(
			if (bits and 1 != 0) 1f + WIRE_GROW else -WIRE_GROW,
			if (bits and 2 != 0) 1f + WIRE_GROW else -WIRE_GROW,
			if (bits and 4 != 0) 1f + WIRE_GROW else -WIRE_GROW,
		)
	}

	private fun Float.forPoint(block: BlockPos, local: Vector3f): Float {
		val dx = block.x + local.x - viewer.pos.x
		val dy = block.y + local.y - viewer.pos.y
		val dz = block.z + local.z - viewer.pos.z
		return WorldLineScale.width(this, Math.sqrt(dx * dx + dy * dy + dz * dz))
	}

	private fun emitEdgeVertex(buffer: VertexConsumer, pose: PoseStack.Pose, at: Vector3f, width: Float, color: Int, axis: Int) {
		buffer.addVertex(pose, at)
			.setNormal(pose, if (axis == 0) 1f else 0f, if (axis == 1) 1f else 0f, if (axis == 2) 1f else 0f)
			.setColor(color)
			.setLineWidth(width)
	}

	fun line(vararg points: Vec3, color: Int, lineWidth: Float = WIDE) = line(points.asList(), color, lineWidth)

	fun tracer(target: Vec3, color: Int, lineWidth: Float = TRACER_WIDTH) {
		val forward = Vector3f(0f, 0f, -1f).rotate(viewer.orientation)
		line(viewer.pos.add(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble()), target, color = color, lineWidth = lineWidth)
	}

	fun line(points: List<Vec3>, color: Int, lineWidth: Float = WIDE) {
		val sink = lineSink()
		worldPoses.pushPose()
		worldPoses.translate(-viewer.pos.x, -viewer.pos.y, -viewer.pos.z)
		val pose = worldPoses.last()

		var nx = 0f
		var ny = 0f
		var nz = 0f
		for (i in 0 until points.size - 1) {
			val from = points[i]
			val to = points[i + 1]
			val dir = segmentDirection(from, to)
			if (i == 0) { nx = dir.x; ny = dir.y; nz = dir.z }
			emitLineVertex(sink, pose, from, color, WorldLineScale.width(lineWidth, from.distanceTo(viewer.pos)), nx, ny, nz)
			emitLineVertex(sink, pose, to, color, WorldLineScale.width(lineWidth, to.distanceTo(viewer.pos)), dir.x, dir.y, dir.z)
			nx = dir.x; ny = dir.y; nz = dir.z
		}
		worldPoses.popPose()
	}

	private fun segmentDirection(from: Vec3, to: Vec3): Vector3f =
		dirScratch.set(to.x.toFloat() - from.x.toFloat(), to.y.toFloat() - from.y.toFloat(), to.z.toFloat() - from.z.toFloat()).normalize()

	private fun emitLineVertex(buffer: VertexConsumer, pose: PoseStack.Pose, point: Vec3, color: Int, width: Float, nx: Float, ny: Float, nz: Float) {
		buffer.addVertex(pose, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
			.setColor(color)
			.setLineWidth(width)
			.setNormal(pose, nx, ny, nz)
	}

	companion object {

		private const val WIDE = 10f

		private const val MARKER_LINE = 4f
		private const val TRACER_WIDTH = 3f
		private const val LINE_HEIGHT = 10f

		private const val WIRE_GROW = 0.001953125f

		private val CUBE_EDGES = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7)

		private val WIRE_EDGES = arrayOf(
			intArrayOf(0, 1, 0), intArrayOf(0, 2, 1), intArrayOf(0, 4, 2),
			intArrayOf(4, 5, 0), intArrayOf(4, 6, 1), intArrayOf(2, 6, 2),
			intArrayOf(2, 3, 0), intArrayOf(1, 3, 1), intArrayOf(1, 5, 2),
			intArrayOf(6, 7, 0), intArrayOf(5, 7, 1), intArrayOf(3, 7, 2),
		)
		private const val BILLBOARD_MAX_DISTANCE = 10.0
		private const val BILLBOARD_SCALE = 0.025f

		private const val DEFAULT_WAYPOINT_BACKGROUND = 0xAA202020.toInt()

		private const val FALLBACK_DISTANCE = 42069.0

		fun drawInWorld(event: WorldRenderLastEvent, type: WaypointType, block: WorldDraw.() -> Unit) =
			drawInWorld(event) { looks(type, block) }

		fun drawInWorld(event: WorldRenderLastEvent, block: WorldDraw.() -> Unit) {
			event.matrices.pushPose()
			try {
				WorldDraw(event.matrices, event.camera).block()
			} finally {
				event.matrices.popPose()
			}
		}
	}
}

private object UnitCube {

	private val FACES = arrayOf(
		intArrayOf(1, 3, 7, 5),
		intArrayOf(0, 4, 6, 2),
		intArrayOf(2, 3, 7, 6),
		intArrayOf(0, 4, 5, 1),
		intArrayOf(4, 6, 7, 5),
		intArrayOf(0, 1, 3, 2),
	)
	private val OFFSETS = arrayOf(intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))

	fun dx(face: Int) = OFFSETS[face][0]
	fun dy(face: Int) = OFFSETS[face][1]
	fun dz(face: Int) = OFFSETS[face][2]

	fun face(pose: Matrix4f, buffer: VertexConsumer, face: Int, rgba: Int) {
		for (corner in FACES[if (face in 0..5) face else 5])
			buffer.addVertex(pose, (corner and 1).toFloat(), (corner shr 1 and 1).toFloat(), (corner shr 2 and 1).toFloat()).setColor(rgba)
	}

	fun all(pose: Matrix4f, buffer: VertexConsumer, rgba: Int) {
		for (face in 0 until 6) face(pose, buffer, face, rgba)
	}
}
