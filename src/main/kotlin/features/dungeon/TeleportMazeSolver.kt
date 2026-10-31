/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Teleport Maze rendering/glue for TeleportMaze.kt. Visual only: no clicks, no packets, no movement.
 * Draws pad states (start / departed / landed / unknown / likely / back / leave), the learned pad connections of the
 * room you are in (numbered, colour-matched, arrowheads per travelled direction) and exactly one guide target with
 * the only tracer. One label line per pad (state + link number), the guide hint above it.
 *
 * Room gate (fix-tpmazeroom-1025): nothing here runs or draws unless the dungeon room detector says the player is
 * inside the Teleport Maze room ([TeleportMaze.Gate]); only end portal frames inside that room's bounds count as
 * pads. Other rooms with frames (e.g. the Altar) no longer start the tracker. Gate changes log "[ApexTpMaze] gate".
 *
 * Connection lines are thin flat quads on CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS (the same POSITION_COLOR
 * pipeline as WorldDraw.block(), which the visited-pad highlight already uses on 26.3), NOT the line
 * renderers, so they do not touch the 26.3 line vertex-format crash.
 */
package dev.ashtonite365.apex.features.dungeon

import org.joml.Matrix4f
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.CustomRenderPipelines
import dev.ashtonite365.apex.util.render.WorldBatcher
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Pad
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.State
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.isLeave
import kotlin.math.sqrt

object TeleportMazeSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	val tracker = TeleportMaze.Tracker()
	private var lastPos: Vec3? = null
	private var guide: TeleportMaze.Guide = TeleportMaze.Guide.Nothing
	private var states: Map<Pad, State> = emptyMap()
	private var pairs: List<TeleportMaze.PadPair> = emptyList()

	private var shownPairs: List<TeleportMaze.PadPair> = emptyList()

	private var padTags: Map<Pad, TeleportMaze.PadPair> = emptyMap()
	private var dirty = true
	val gate = TeleportMaze.Gate()

	private fun clearState() {
		tracker.reset(); lastPos = null; guide = TeleportMaze.Guide.Nothing; states = emptyMap()
		pairs = emptyList(); shownPairs = emptyList(); padTags = emptyMap(); dirty = true
	}

	private fun reset() {
		if (gate.open) Apex.logger.info("[ApexTpMaze] gate closed (reset: world change / dungeon end)")
		gate.reset(); clearState()
	}

	private fun onGate(c: TeleportMaze.Gate.Change) {
		val r = c.room?.let { "${it.minX},${it.minZ}..${it.maxX},${it.maxZ}" } ?: "-"
		if (c.open) Apex.logger.info("[ApexTpMaze] gate open room=${c.roomName} bounds=$r ${if (c.resumed) "(resumed parked state, pads=${tracker.allPads.size} visited=${tracker.visited.size})" else "(fresh)"}")
		else Apex.logger.info("[ApexTpMaze] gate closed why=${c.why} room=${c.roomName ?: "-"} bounds=$r ${if (c.discard) "(cleared)" else "(parked pads=${tracker.allPads.size} visited=${tracker.visited.size})"}")
		if (c.discard) clearState() else { lastPos = null; dirty = true }
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (event.bareText.contains("> EXTRA STATS <")) reset()
	}

	private fun padAt(b: BlockPos): Pad? {
		val level = Client.level ?: return null
		val room = gate.room ?: return null
		val p = Pad(b.x, b.y, b.z)
		if (!TeleportMaze.padsInside(listOf(p), room).contains(p)) return null
		return if (level.getBlockState(b).`is`(Blocks.END_PORTAL_FRAME)) p else null
	}

	private fun padUnder(pos: Vec3): Pad? {
		val feet = BlockPos.containing(pos)
		return padAt(feet) ?: padAt(feet.below())
	}

	private fun scanRoom(room: TeleportMaze.Bounds): Set<Pad> {
		val out = mutableSetOf<Pad>()
		val p = BlockPos.MutableBlockPos()
		val level = Client.level ?: return out
		for (x in room.minX..room.maxX) for (z in room.minZ..room.maxZ) for (y in room.minY..room.maxY)
			if (level.getBlockState(p.set(x, y, z)).`is`(Blocks.END_PORTAL_FRAME)) out += Pad(x, y, z)
		return TeleportMaze.padsInside(out, room)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val player = Client.player ?: return
		val enabled = cfg.teleportMaze; val inDungeon = DungeonUtil.isInDungeonIsland
		val detected = if (enabled && inDungeon) DungeonRoomTracker.currentRoom() else null
		val now = player.position()
		gate.update(enabled, inDungeon, detected?.info?.name, detected?.cells, now.x, now.z)?.let(::onGate)
		if (!gate.open) return
		val mazeRoom = gate.room ?: return
		val prev = lastPos
		lastPos = now
		val pad = padUnder(now)
		val teleported = prev != null && prev.distanceToSqr(now) > 9.0
		if (tracker.room == null && pad == null) return
		if (tracker.room == null && pad != null) tracker.setRoomPads(scanRoom(mazeRoom), mazeRoom)
		if (!tracker.tick(now.x, now.y, now.z, pad, teleported, player.yRot, player.xRot)) { clearState(); lastPos = now; return }
		if (teleported) {
			tracker.room?.let { tracker.setRoomPads(scanRoom(it), it) }
			val tp = tracker.lastTeleport
			Apex.logger.info("[ApexTpMaze] teleport from=${tp?.from?.let(::fmt)} to=${tp?.to?.let(::fmt)}${if (tp?.exit == true) " (exit)" else ""} yaw=${"%.1f".format(player.yRot)} pads=${tracker.allPads.size} visited=${tracker.visited.size}")
		}
		if (teleported || pad != null || dirty) recompute(now)
	}

	private fun fmt(p: Pad) = "${p.x},${p.y},${p.z}"

	private fun recompute(now: Vec3) {
		dirty = false
		states = TeleportMaze.states(tracker.allPads, tracker.links, tracker.exits, tracker.firstPad, tracker.likely, tracker.previous)
		pairs = TeleportMaze.pairs(tracker.links, tracker.observed)
		val origin = TeleportMaze.originPad(now.x, now.y, now.z)
		guide = TeleportMaze.guide(
			tracker.allPads, tracker.visited, tracker.links, origin,
			tracker.exits, tracker.likely, tracker.firstPad, tracker.lastYaw, tracker.previous,
		)
		val cell = TeleportMaze.cells(tracker.allPads)
		val here = tracker.allPads.minWithOrNull(compareBy({ it.distTo(origin) }, { it.x }, { it.z }))?.let { cell[it] }
		shownPairs = pairs.filter { cell[it.a] == here || cell[it.b] == here }
		padTags = buildMap { for (pr in pairs) { putIfAbsent(pr.a, pr); putIfAbsent(pr.b, pr) } }
	}

	private fun rgb(c: Int) = c and 0xFFFFFF
	private fun alpha(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!cfg.teleportMaze || !gate.open || tracker.allPads.isEmpty() || tracker.visited.isEmpty()) return
		val pads = gate.visible(tracker.allPads)
		if (pads.isEmpty()) return
		val visitedColour = cfg.teleportMazeColour.getEffectiveColourRGB()
		val showLinks = cfg.teleportMazePadLinks
		WorldDraw.drawInWorld(event) {
			for (p in pads) {
				val bp = BlockPos(p.x, p.y, p.z)
				val st = states[p] ?: State.UNKNOWN
				val fill = when {
					st == State.START -> 0x50A0A0A0
					st.isLeave -> 0x80FF2020.toInt()
					p in tracker.departed -> visitedColour
					p in tracker.landed -> alpha(visitedColour, 0x38)
					st == State.LIKELY -> 0x70FFFF40
					else -> 0x50FFFFFF
				}
				block(bp, fill)
				val label = when (st) {
					State.START -> "Start" to 0xC0C0C0
					State.LEAVE_EXIT -> "✖ leave (exit)" to 0xFF4040
					State.BACK_START -> "↩ back to start" to 0xB0B0B0
					State.BACK_PREVIOUS -> "↩ back to previous room" to 0xB0B0B0
					State.LEAVE_DEAD_END -> "✖ leave (dead end)" to 0xFF4040
					State.LIKELY -> "? likely" to 0xFFFF40
					State.UNKNOWN -> "?" to 0xFFFFFF
					State.KNOWN -> null
				}
				val tag = if (showLinks) padTags[p] else null
				if (label != null || tag != null) {
					val line = Component.empty()
					label?.let { (s, c) -> line.append(Component.literal(s).withColor(c)) }
					tag?.let { line.append(Component.literal("${if (label != null) " " else ""}#${it.number}${if (it.bothTravelled) " ⇄" else ""}").withColor(rgb(it.colour))) }
					text(Vec3(p.x + 0.5, p.y + 1.35, p.z + 0.5), line)
				}
			}
			if (showLinks) {
				for (pr in shownPairs) {
					if (pr.a !in pads || pr.b !in pads) continue
					val a = Vec3(pr.a.x + 0.5, pr.a.y + 1.05, pr.a.z + 0.5)
					val b = Vec3(pr.b.x + 0.5, pr.b.y + 1.05, pr.b.z + 0.5)
					val buf = WorldBatcher.sinkFor(CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS)
					val m = worldPoses.last().pose()
					ribbon(m, buf, a, b, alpha(pr.colour, 0xC0))
					if (pr.aToB) arrowHead(m, buf, a, b, alpha(pr.colour, 0xFF))
					if (pr.bToA) arrowHead(m, buf, b, a, alpha(pr.colour, 0xFF))
				}
			}
			if (!cfg.teleportMazeGuide) return@drawInWorld
			val gc = cfg.teleportMazeGuideColour.getEffectiveColourRGB()
			when (val g = guide) {
				is TeleportMaze.Guide.Step -> {
					if (g.next !in pads) return@drawInWorld
					val top = Vec3(g.next.x + 0.5, g.next.y + 2.0, g.next.z + 0.5)
					val why = when (g.why) {
						TeleportMaze.Guide.Why.LIKELY -> "▼ likely"
						TeleportMaze.Guide.Why.UNKNOWN -> "▼ nearest untried"
						TeleportMaze.Guide.Why.UNKNOWN_ELSEWHERE -> "▼ untried pad"
						TeleportMaze.Guide.Why.FALLBACK -> "▼"
					}
					val via = if (g.teleports > 0 && g.next != g.target) " (via ${g.teleports} teleport${if (g.teleports > 1) "s" else ""})" else ""
					text(top, Component.literal(why + via).withColor(rgb(gc)))
					if (cfg.teleportMazeGuideLine) tracer(Vec3(g.next.x + 0.5, g.next.y + 1.0, g.next.z + 0.5), gc or 0xFF000000.toInt())
				}
				TeleportMaze.Guide.Nothing -> {}
			}
		}
	}

	private fun ribbon(m: Matrix4f, buf: VertexConsumer, a: Vec3, b: Vec3, c: Int, w: Double = 0.06) {
		val dx = b.x - a.x; val dz = b.z - a.z
		val len = sqrt(dx * dx + dz * dz).takeIf { it > 1e-6 } ?: return
		val px = -dz / len * w; val pz = dx / len * w
		quad(m, buf, a.x + px, a.y, a.z + pz, b.x + px, b.y, b.z + pz, b.x - px, b.y, b.z - pz, a.x - px, a.y, a.z - pz, c)
		quad(m, buf, a.x, a.y + w, a.z, b.x, b.y + w, b.z, b.x, b.y - w, b.z, a.x, a.y - w, a.z, c)
	}

	private fun arrowHead(m: Matrix4f, buf: VertexConsumer, from: Vec3, to: Vec3, c: Int) {
		val (tipX, tipZ, baseX, baseZ) = TeleportMaze.arrowHead(from.x, from.z, to.x, to.z)?.toList() ?: return
		val len = sqrt((tipX - baseX) * (tipX - baseX) + (tipZ - baseZ) * (tipZ - baseZ))
		val ux = (tipX - baseX) / len; val uz = (tipZ - baseZ) / len
		val px = -uz * 0.35; val pz = ux * 0.35
		val y = to.y + 0.01
		quad(m, buf, tipX, y, tipZ, baseX + px, y, baseZ + pz, baseX - px, y, baseZ - pz, tipX, y, tipZ, c)
	}

	private fun quad(
		m: Matrix4f, buf: VertexConsumer,
		x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double,
		x3: Double, y3: Double, z3: Double, x4: Double, y4: Double, z4: Double, c: Int,
	) {
		buf.addVertex(m, x1.toFloat(), y1.toFloat(), z1.toFloat()).setColor(c)
		buf.addVertex(m, x2.toFloat(), y2.toFloat(), z2.toFloat()).setColor(c)
		buf.addVertex(m, x3.toFloat(), y3.toFloat(), z3.toFloat()).setColor(c)
		buf.addVertex(m, x4.toFloat(), y4.toFloat(), z4.toFloat()).setColor(c)
	}
}
