/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: draws the mage beam as one clean line in place of its firework particle trail. Uses the existing
 * ParticleSpawnEvent hook (no new mixin). Cosmetic only.
 */
package dev.ashtonite365.apex.features.kit.dungeon

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3

object MageBeam {

	class Beam(first: Vec3, val startTick: Int) {
		val points = mutableListOf(first)
		var lastTick = startTick
	}

	class Tracker {
		val beams = mutableListOf<Beam>()
		var tick = 0

		fun inDirection(points: List<Vec3>, p: Vec3): Boolean {
			if (points.size < 2) return true
			val tail = points[points.size - 1]
			val heading = tail.subtract(points[0]).normalize()
			val step = p.subtract(tail).normalize()
			return heading.dot(step) > 0.99
		}

		fun add(p: Vec3) {
			val current = beams.lastOrNull()
			val extends = current != null && tick - current.lastTick < 1 && inDirection(current.points, p)
			if (extends) {
				current!!.points.add(p)
				current.lastTick = tick
			} else {
				beams.add(Beam(p, tick))
			}
		}

		fun advance(duration: Int) {
			tick += 1
			beams.removeIf { tick - it.startTick >= duration }
		}

		fun segments(from: Vec3, minPoints: Int): List<Pair<Vec3, Vec3>> {
			val out = ArrayList<Pair<Vec3, Vec3>>()
			for (beam in beams) {
				if (beam.points.size < minPoints) continue
				var near = beam.points[0]
				var nearD = near.distanceToSqr(from)
				var far = near
				var farD = nearD
				for (i in 1 until beam.points.size) {
					val q = beam.points[i]
					val d = q.distanceToSqr(from)
					if (d.compareTo(nearD) < 0) { near = q; nearD = d }
					if (d.compareTo(farD) > 0) { far = q; farD = d }
				}
				if (near != far) out.add(near to far)
			}
			return out
		}
	}

	private val tracker = Tracker()

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!DungeonExtras.TConfig.mageBeam || !DungeonUtil.isInDungeonIsland) return
		if (event.particleEffect.type != ParticleTypes.FIREWORK) return
		tracker.add(event.position)
		if (DungeonExtras.TConfig.mageBeamHideParticles) event.cancel()
	}

	@Subscribe
	fun onTick(event: TickEvent) = tracker.advance(DungeonExtras.TConfig.mageBeamDuration)

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!DungeonExtras.TConfig.mageBeam || tracker.beams.isEmpty()) return
		val eye = dev.ashtonite365.apex.util.Client.player?.position() ?: return
		val colour = DungeonExtras.TConfig.mageBeamColour.getEffectiveColourRGB() or 0xFF000000.toInt()
		val lines = tracker.segments(eye, DungeonExtras.TConfig.mageBeamMinPoints)
		if (lines.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for ((a, b) in lines) line(a, b, color = colour, lineWidth = 8F)
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		tracker.beams.clear()
		tracker.tick = 0
	}
}
