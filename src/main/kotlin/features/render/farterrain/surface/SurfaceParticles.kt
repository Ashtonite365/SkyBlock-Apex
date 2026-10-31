/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, particles from far blocks (torch flames, campfire smoke, lava pops, drips, portal
 * swirl...), so far terrain looks alive like near terrain while staying cheap.
 *
 * The blocks with a client animation are found once, when a region is meshed ([SurfaceAnimatedBlocks]); nothing scans
 * terrain at run time. Every client tick, only regions drawn last frame (on screen) are considered, each block rolls a
 * chance that falls off with distance, a hard per-tick budget caps the work, blocks the game animates itself (within its
 * own 32-block range around you, in loaded chunks) are skipped so nothing doubles, and the Particles setting (All /
 * Decreased / Minimal) applies as in the game. The block's own animation code runs (through the game's level, with the
 * real block state when its chunk is loaded); its particles go to the game's particle engine and its sounds are muted.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ParticleStatus
import net.minecraft.util.RandomSource

object SurfaceParticlePlan {

	const val BASE_CHANCE = 0.02

	const val FULL_RATE_DISTANCE = 32.0

	const val MAX_PER_TICK = 48

	const val MAX_SCANNED_PER_TICK = 20_000

	const val VANILLA_RANGE = 31

	fun chance(distance: Double, particleDistance: Int): Double {
		if (particleDistance <= 0 || distance > particleDistance) return 0.0
		return if (distance <= FULL_RATE_DISTANCE) BASE_CHANCE else BASE_CHANCE * FULL_RATE_DISTANCE / distance
	}

	fun vanillaAnimates(px: Int, py: Int, pz: Int, x: Int, y: Int, z: Int, loaded: Boolean): Boolean =
		loaded && Math.abs(x - px) <= VANILLA_RANGE && Math.abs(y - py) <= VANILLA_RANGE && Math.abs(z - pz) <= VANILLA_RANGE

	enum class Status { ALL, DECREASED, MINIMAL }

	fun keep(status: Status, alwaysShow: Boolean, roll: (Int) -> Int): Boolean {
		var s = status
		if (alwaysShow && s == Status.MINIMAL && roll(10) == 0) s = Status.DECREASED
		if (s == Status.DECREASED && roll(3) == 0) s = Status.MINIMAL
		return s != Status.MINIMAL
	}

	fun expectedPerTick(count: Int, distance: Double, particleDistance: Int): Double =
		minOf(MAX_PER_TICK.toDouble(), count * chance(distance, particleDistance))
}

object SurfaceParticles {

	@JvmStatic
	var animating = false
		private set

	private val random = RandomSource.create()
	private var cursor = 0

	var lastRun = 0
		private set
	var lastSpawned = 0
		private set
	private var spawned = 0

	fun tick(level: ClientLevel, particleDistance: Int) {
		lastRun = 0
		lastSpawned = spawned
		spawned = 0
		val mc = Minecraft.getInstance()
		if (particleDistance <= 0 || mc.options.particles().get() == ParticleStatus.MINIMAL) return
		val cam = mc.gameRenderer.mainCamera().position()
		val player = mc.player ?: return
		val px = player.blockX; val py = player.blockY; val pz = player.blockZ
		val keys = SurfaceRenderer.drawnRegionKeys.toLongArray()
		if (keys.isEmpty()) return
		var run = 0
		var scanned = 0
		val pos = BlockPos.MutableBlockPos()

		val start = Math.floorMod(cursor++, keys.size)
		for (n in keys.indices) {
			val a = SurfaceRenderer.animatedIn(keys[(start + n) % keys.size]) ?: continue
			for (i in 0 until a.size) {
				if (run >= SurfaceParticlePlan.MAX_PER_TICK || scanned >= SurfaceParticlePlan.MAX_SCANNED_PER_TICK) { lastRun = run; return }
				scanned++
				pos.set(a.positions[i])
				val dx = pos.x + 0.5 - cam.x; val dy = pos.y + 0.5 - cam.y; val dz = pos.z + 0.5 - cam.z
				val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
				val p = SurfaceParticlePlan.chance(dist, particleDistance)
				if (p <= 0.0 || random.nextDouble() >= p) continue
				val loaded = level.hasChunk(pos.x shr 4, pos.z shr 4)
				if (SurfaceParticlePlan.vanillaAnimates(px, py, pz, pos.x, pos.y, pos.z, loaded)) continue
				val state = if (loaded) level.getBlockState(pos) else a.states[i]
				animating = true
				try {
					state.block.animateTick(state, level, pos, random)
					val fluid = state.fluidState
					if (!fluid.isEmpty) fluid.animateTick(level, pos, random)
				} catch (_: Throwable) {

				} finally {
					animating = false
				}
				run++
			}
		}
		lastRun = run
	}

	@JvmStatic
	fun spawn(particle: ParticleOptions, alwaysShow: Boolean, x: Double, y: Double, z: Double, xd: Double, yd: Double, zd: Double) {
		val mc = Minecraft.getInstance()
		val status = when (mc.options.particles().get()) {
			ParticleStatus.DECREASED -> SurfaceParticlePlan.Status.DECREASED
			ParticleStatus.MINIMAL -> SurfaceParticlePlan.Status.MINIMAL
			else -> SurfaceParticlePlan.Status.ALL
		}
		if (!SurfaceParticlePlan.keep(status, alwaysShow) { random.nextInt(it) }) return
		mc.particleEngine.createParticle(particle, x, y, z, xd, yd, zd)
		spawned++
	}
}
