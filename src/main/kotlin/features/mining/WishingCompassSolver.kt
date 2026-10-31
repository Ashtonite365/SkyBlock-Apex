// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.UseItemEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object WishingCompassSolver {
	@Config
	object TConfig : ManagedConfig("wishing-compass", Category.MINING) {
		val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)
	}

	val WISHING_COMPASS = ItemId("WISHING_COMPASS")
	val JUNGLE_KEY = ItemId("JUNGLE_KEY")

	data class Ray(val origin: Vec3, val direction: Vec3)

	enum class HollowsZone { CRYSTAL_NUCLEUS, JUNGLE, MITHRIL_DEPOSITS, GOBLIN_HOLDOUT, PRECURSOR_REMNANTS, MAGMA_FIELDS }

	enum class CompassTarget(val label: String, val yRange: ClosedFloatingPointRange<Double>?) {
		GOBLIN_QUEEN("§eGoblin Queen", 125.0..140.0),
		GOBLIN_KING("§6King Yolkar", 82.0..168.0),
		BAL("§cBal", 0.0..75.0),
		JUNGLE_TEMPLE("§aJungle Temple", 72.0..81.0),
		ODAWA("§aOdawa", 73.0..155.0),
		PRECURSOR_CITY("§fPrecursor City", 121.0..130.0),
		MINES_OF_DIVAN("§9Mines of Divan", 97.0..102.0),
		CRYSTAL_NUCLEUS("§dCrystal Nucleus", null),
	}

	private val zones: List<Pair<HollowsZone, AABB>> = listOf(
		HollowsZone.CRYSTAL_NUCLEUS to AABB(462.0, 63.0, 461.0, 564.0, 181.0, 565.0),
		HollowsZone.JUNGLE to AABB(201.0, 63.0, 201.0, 513.0, 189.0, 513.0),
		HollowsZone.MITHRIL_DEPOSITS to AABB(512.0, 63.0, 201.0, 824.0, 189.0, 513.0),
		HollowsZone.GOBLIN_HOLDOUT to AABB(201.0, 63.0, 512.0, 513.0, 189.0, 824.0),
		HollowsZone.PRECURSOR_REMNANTS to AABB(512.0, 63.0, 512.0, 824.0, 189.0, 824.0),
		HollowsZone.MAGMA_FIELDS to AABB(201.0, 30.0, 201.0, 824.0, 64.0, 824.0),
	)
	private fun box(zone: HollowsZone) = zones.first { it.first == zone }.second

	fun zoneFor(v: Vec3): HollowsZone? = zones.firstOrNull { it.second.contains(v) }?.first

	private class Reach(val home: HollowsZone, val x: Double, val z: Double, val xBeyondMax: Boolean, val zBeyondMax: Boolean) {
		fun rulesOut(p: Vec3): Boolean {
			val b = box(home)
			val xOut = if (xBeyondMax) p.x > b.maxX + x else p.x < b.minX - x
			val zOut = if (zBeyondMax) p.z > b.maxZ + z else p.z < b.minZ - z
			return xOut || zOut
		}
	}

	private val reaches: Map<CompassTarget, Reach> = mapOf(
		CompassTarget.GOBLIN_KING to Reach(HollowsZone.GOBLIN_HOLDOUT, 59.0, 56.0, xBeyondMax = true, zBeyondMax = false),
		CompassTarget.GOBLIN_QUEEN to Reach(HollowsZone.GOBLIN_HOLDOUT, 108.0, 108.0, xBeyondMax = true, zBeyondMax = false),
		CompassTarget.JUNGLE_TEMPLE to Reach(HollowsZone.JUNGLE, 108.0, 108.0, xBeyondMax = true, zBeyondMax = true),
		CompassTarget.ODAWA to Reach(HollowsZone.JUNGLE, 53.0, 54.0, xBeyondMax = true, zBeyondMax = true),
		CompassTarget.PRECURSOR_CITY to Reach(HollowsZone.PRECURSOR_REMNANTS, 107.0, 107.0, xBeyondMax = false, zBeyondMax = false),
		CompassTarget.MINES_OF_DIVAN to Reach(HollowsZone.MITHRIL_DEPOSITS, 108.0, 108.0, xBeyondMax = false, zBeyondMax = true),
	)

	fun possibleTargets(has: (String) -> Boolean, kingsScent: Boolean, jungleKey: Boolean): MutableSet<CompassTarget> {
		val out = linkedSetOf(CompassTarget.CRYSTAL_NUCLEUS)
		if (!has("Jade")) out += CompassTarget.MINES_OF_DIVAN
		if (!has("Amber")) out += if (kingsScent) CompassTarget.GOBLIN_QUEEN else CompassTarget.GOBLIN_KING
		if (!has("Topaz")) out += CompassTarget.BAL
		if (!has("Amethyst")) out += if (jungleKey) CompassTarget.JUNGLE_TEMPLE else CompassTarget.ODAWA
		if (!has("Sapphire")) out += CompassTarget.PRECURSOR_CITY
		return out
	}

	fun narrowTargets(targets: Set<CompassTarget>, point: Vec3, amethystFound: Boolean, startZone: HollowsZone?): List<CompassTarget> {
		if (zoneFor(point) == HollowsZone.CRYSTAL_NUCLEUS) return targets.toList()
		val left = targets.filter { t ->
			t != CompassTarget.CRYSTAL_NUCLEUS && (t.yRange == null || point.y in t.yRange) && reaches[t]?.rulesOut(point) != true
		}
		val templeOverBal = CompassTarget.JUNGLE_TEMPLE in left && CompassTarget.BAL in left && !amethystFound && startZone == HollowsZone.JUNGLE
		return if (templeOverBal) left - CompassTarget.BAL else left
	}

	fun intersect(a: Ray, b: Ray): Vec3? {
		val cos = a.direction.dot(b.direction)
		val sin2 = 1 - cos * cos
		if (sin2 < 1e-6) return null
		val between = a.origin.subtract(b.origin)
		val alongA = a.direction.dot(between)
		val alongB = b.direction.dot(between)
		val ta = (cos * alongB - alongA) / sin2
		val tb = (alongB - cos * alongA) / sin2
		if (ta < 0 || tb < 0) return null
		return a.origin.add(a.direction.scale(ta)).add(b.origin.add(b.direction.scale(tb))).scale(0.5)
	}

	sealed interface Step {
		data object FirstLine : Step
		data object Parallel : Step

		data class Point(val at: Vec3, val startZone: HollowsZone?) : Step
	}

	class Triangulation {
		private var usedAt = Long.MIN_VALUE
		private val trail = mutableListOf<Vec3>()
		private val lines = mutableListOf<Ray>()
		var point: Vec3? = null
			private set

		var startZone: HollowsZone? = null
			private set

		private fun since(now: Long) = if (usedAt == Long.MIN_VALUE) Long.MAX_VALUE else now - usedAt

		fun clear() {
			trail.clear(); lines.clear(); point = null
		}

		fun use(now: Long, zoneHere: () -> HollowsZone?): Step? {
			if (since(now) < 3000) return null
			val step = endTrail()
			usedAt = now
			trail.clear()
			if (lines.size >= 2 || point != null) {
				lines.clear(); point = null
			}
			if (lines.isEmpty()) startZone = zoneHere()
			return step
		}

		fun particle(now: Long, at: Vec3): Step? {
			if (since(now) > 3000) return null
			trail += at
			return if (trail.size >= 8) endTrail() else null
		}

		private fun endTrail(): Step? {
			if (trail.size < 3) return null
			val dir = trail.last().subtract(trail.first())
			val from = trail.first()
			trail.clear()
			usedAt = Long.MIN_VALUE
			if (dir.lengthSqr() < 1e-4) return null
			val line = Ray(from, dir.normalize())
			lines += line
			if (lines.size == 1) return Step.FirstLine
			val meet = intersect(lines[lines.size - 2], line)
			if (meet == null) {
				lines.removeFirst()
				return Step.Parallel
			}
			point = meet
			return Step.Point(meet, startZone)
		}
	}

	private val triangulation = Triangulation()
	val solution: Vec3? get() = triangulation.point
	var solutionTargets: List<CompassTarget> = emptyList()
		private set

	fun isKingsScentPresent(): Boolean =
		TabListReader.currentSnapshot.footer.orElse(null)?.bareText?.contains("King's Scent I") == true

	fun hasJungleKey(): Boolean = Client.player?.inventory?.hasAnyMatching { it.view().repoItemId == JUNGLE_KEY } == true

	private fun hasCrystal(name: String) = (CrystalHollowsOverlay.DConfig.data.crystals[name] ?: 0) >= 1

	fun candidateTargets(): MutableSet<CompassTarget> = possibleTargets(::hasCrystal, isKingsScentPresent(), hasJungleKey())

	fun isEnabled() = TConfig.enabled &&
		(SkyBlockSession.skyblockIsland == SkyBlockIsland.CRYSTAL_HOLLOWS || SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES)

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		triangulation.clear()
		solutionTargets = emptyList()
	}

	@Subscribe
	fun onUse(event: UseItemEvent) {
		if (!isEnabled() || event.item.view().repoItemId != WISHING_COMPASS) return
		val step = triangulation.use(System.currentTimeMillis()) { Client.player?.position()?.let(::zoneFor) }
		step?.let(::report)
		if (triangulation.point == null) solutionTargets = emptyList()
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!isEnabled() || event.particleEffect != ParticleTypes.HAPPY_VILLAGER) return
		triangulation.particle(System.currentTimeMillis(), event.position)?.let(::report)
	}

	private fun report(step: Step) {
		when (step) {
			Step.FirstLine -> Client.showChat(Component.literal("§e[SkyBlock Apex] Wishing compass: move somewhere else and use it again."))
			Step.Parallel -> Client.showChat(Component.literal("§c[SkyBlock Apex] Wishing compass: lines are parallel, try another spot."))
			is Step.Point -> {
				solutionTargets = if (SkyBlockSession.skyblockIsland == SkyBlockIsland.CRYSTAL_HOLLOWS)
					narrowTargets(candidateTargets(), step.at, hasCrystal("Amethyst"), step.startZone) else emptyList()
				val what = solutionTargets.joinToString("§e or ") { it.label }.ifEmpty { "§fan undetermined location" }
				Client.showChat(Component.literal("§e[SkyBlock Apex] Wishing compass: $what§e near ${BlockPos.containing(step.at).toShortString()}"))
			}
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!isEnabled()) return
		val at = solution ?: return
		WorldDraw.drawInWorld(event, WaypointType.MINING_SOLVERS) {
			waypoint(BlockPos.containing(at), Component.literal(solutionTargets.singleOrNull()?.label ?: "§aWishing Compass Target"))
		}
	}
}
