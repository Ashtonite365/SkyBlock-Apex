// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.PlusRepoSource
import dev.ashtonite365.apex.util.render.WorldDraw

object PlusRiftParkour {
	@Config
	object TConfig : ManagedConfig("rift-parkour", Category.RIFT) {
		val gunthersRace by toggle("gunthers-race") { false }
		val upsideDown by toggle("upside-down") { false }
		val lavaMaze by toggle("lava-maze") { false }
		val tubulator by toggle("tubulator") { false }
		val lookAhead by integer("look-ahead", 1, 30) { 2 }
		val rainbow by toggle("rainbow") { true }
		val colour by colour("colour") { ChromaColour.fromStaticRGB(255, 255, 255, 255) }
		val outline by toggle("outline") { true }
		val hidePlayers by toggle("hide-players") { false }
		val resetKey by keyBindingWithDefaultUnbound("reset-key")
	}

	fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	fun inArea(name: String): Boolean {
		for (line in ScoreboardUtil.simplifiedScoreboardLines) if ('⏣' in line && name in line) return true
		return false
	}

	private val TUBULATOR_ROOM = AABB(-309.0, 0.0, -112.0, -298.0, 63.0, -101.0)

	enum class Course(val path: String, val offset: RVec, val platform: Double, val range: Double, val inOrder: Boolean) {
		RACE(RiftRepo.RACE, RVec(0.0, 0.0, 0.0), 1.0, 5.0, true),

		UPSIDE_DOWN(RiftRepo.UPSIDE_DOWN, RVec(-1.0, -1.0, -1.0), 2.0, 2.0, false),
		LAVA_MAZE(RiftRepo.LAVA_MAZE, RVec(0.0, 0.0, 0.0), 1.0, 1.0, false),
		TUBULATOR(RiftRepo.TUBULATOR, RVec(0.0, 0.0, 0.0), 1.0, 2.0, false);

		fun build(): ParkourState? {
			val data = RiftRepo.loadParkour(path) ?: return null
			return ParkourState(data.locations.map { it + offset }, data.shortCuts, platform, range, inOrder)
		}
	}

	private val built = HashMap<Course, ParkourState?>()
	private var builtForGeneration = -1
	var inRace = false
		private set

	private fun routeFor(course: Course): ParkourState? {
		val gen = PlusRepoSource.generation
		if (gen != builtForGeneration) {
			built.clear()
			builtForGeneration = gen
		}

		val cached = built[course]
		if (cached != null) return cached
		val fresh = course.build()
		built[course] = fresh
		return fresh
	}

	private fun resetAll() {
		for (route in built.values) route?.reset()
	}

	fun enabled(c: Course): Boolean {
		if (!inRift()) return false
		return when (c) {
			Course.RACE -> TConfig.gunthersRace && inRace && inArea("West Village")
			Course.UPSIDE_DOWN -> TConfig.upsideDown && inArea("Mirrorverse")
			Course.LAVA_MAZE -> TConfig.lavaMaze && inArea("Mirrorverse")
			Course.TUBULATOR -> TConfig.tubulator && inArea("Mirrorverse") && Client.player?.position()?.let(TUBULATOR_ROOM::contains) == true
		}
	}

	private val RACE_STARTED = Regex("RIFT RACING Race started! Good luck!")
	private val RACE_OVER = Regex("RIFT RACING Race (finished in \\d+:\\d+.\\d+!|cancelled!).*")
	private const val UPSIDE_DOWN_FAIL = "OH NO! THE LAVA OOFED YOU BACK TO THE START!"
	private const val LAVA_MAZE_FAIL = "EEK! THE LAVA OOFED YOU!"

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		inRace = false
		resetAll()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inRift()) return
		val text = event.bareText.trim()
		if (RACE_STARTED.matches(text)) {
			inRace = true
		} else if (RACE_OVER.matches(text)) {
			inRace = false
			built[Course.RACE]?.reset()
		} else if (text == UPSIDE_DOWN_FAIL) {
			built[Course.UPSIDE_DOWN]?.reset()
		} else if (text == LAVA_MAZE_FAIL) {
			built[Course.LAVA_MAZE]?.reset()
		}
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!inRift() || !event.matches(TConfig.resetKey)) return
		resetAll()
		Client.showChat(Component.literal("§eRift parkour helpers reset."))
	}

	fun shouldHideEntity(entity: Entity): Boolean {
		if (!TConfig.hidePlayers) return false
		if (entity !is RemotePlayer || entity.uuid.version() != 4) return false
		return Course.entries.any { c -> enabled(c) && (c == Course.RACE || built[c]?.inParkour() == true) }
	}

	private fun RVec.toVec3() = Vec3(x, y, z)
	private fun RVec.toBlockPos() = BlockPos.containing(x, y, z)

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inRift()) return
		val player = Client.player ?: return
		val here = RVec(player.x, player.y, player.z)
		val plain = TConfig.colour.getEffectiveColourRGB() and 0xFFFFFF
		for (course in Course.entries) {
			if (!enabled(course)) continue
			val route = routeFor(course) ?: continue
			route.lookAhead = TConfig.lookAhead + 1
			route.update(here, player.onGround())
			val steps = route.visibleSteps()
			if (steps.isEmpty()) continue
			fun stepColour(index: Int, alpha: Int) = (alpha shl 24) or (if (TConfig.rainbow) ParkourState.rainbowRgb(index) else plain)
			WorldDraw.drawInWorld(event) {
				for ((from, to) in steps.zipWithNext())
					line(route.center(from.value).toVec3(), route.center(to.value).toVec3(), color = stepColour(from.index, 0xFF), lineWidth = 5f)
				for ((index, corner) in steps) {
					val low = corner.toVec3()
					box(AABB(low.x, low.y, low.z, low.x + route.platformSize, low.y + 1, low.z + route.platformSize), stepColour(index, 0x80))
					if (TConfig.outline) wireframeCube(corner.toBlockPos(), 2f, 0xFF000000.toInt())
				}
				for ((fromIdx, toIdx) in route.visibleShortCuts()) {
					val a = route.center(route.locations[fromIdx]).toVec3()
					val b = route.center(route.locations[toIdx]).toVec3()
					line(a, b, color = 0xFFFF0000.toInt(), lineWidth = 3f)
					text(a.add(b.subtract(a).normalize()).add(0.0, 1.0, 0.0), Component.literal("§cShortcut"))
					block(route.locations[toIdx].toBlockPos(), 0x80FF0000.toInt())
				}
			}
		}
	}
}
