/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Shadow Assassin Finder for SkyBlock Apex (26.3 port). Written from scratch, no code copied.
 * Risky: riskyToggle (RiskLevel.RISKY), so it is off by default, shows the ⚠ label and goes through the one-time
 * RiskDisclaimer like every other through-walls highlight (BloodMobEsp, starred mobs).
 * Dungeons only. Highlights Shadow Assassins (by name tag) and, as an option, any other invisible mob, through walls.
 * Render path: WorldDraw.box (COLORED_OMNIPRESENT_QUADS) or, as an option, the 12 box edges through
 * WorldDraw.line (OMNIPRESENT_LINES), the same paths BloodMobEsp and the starred-mob outline use.
 * Jump effect (Ashton, 18 Oct 2026; option of this feature rather than a new one, its own ⚠ toggle, off by default):
 * when a Shadow Assassin teleports (moves more than [JumpTracker.MIN_JUMP] blocks between two ticks, or vanishes and
 * reappears elsewhere within [JumpTracker.VANISH_MS]), a short tidal ripple of rings is drawn on the floor where it
 * left and where it landed, for [JumpTracker.WAVE_MS]. Dungeons only. Lines through WorldDraw.line.
 * Jump path (Ashton, 25 Oct 2026): the effect now follows the whole teleport. A pulse (a hoop facing the travel
 * direction plus a short bright streak) travels from where the Shadow Assassin left to where it landed, a faint
 * trail line joins the two for the whole effect, the departure / landing ripples are bigger, and everything fades
 * out over the last part of [TConfig.jumpDuration] instead of vanishing. Colour, line thickness and duration are
 * options. The tracker records the last position seen every tick and also catches teleports the client smooths
 * over a few ticks (the per-tick steps are each short but fast and add up to a jump), and an assassin that turns
 * invisible or despawns and reappears elsewhere within [JumpTracker.VANISH_MS].
 */
package dev.ashtonite365.apex.features.dungeon

import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs
import dev.ashtonite365.apex.gui.config.ColourHandler
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object ShadowAssassinFinder {
	@Config
	object TConfig : ManagedConfig("shadow-assassin-finder", Category.DUNGEONS) {
		val enabled by riskyToggle("enabled", RiskLevel.RISKY)
		val invisibleMobs by toggle("invisible-mobs") { false }
		val outline by toggle("outline") { false }
		val colour by colour("colour") { ChromaColour.fromStaticRGB(0xAA, 0x00, 0xFF, 0x60) }
		val invisibleColour by colour("invisible-colour") { ChromaColour.fromStaticRGB(0xFF, 0xFF, 0xFF, 0x50) }

		val jumpEffect by riskyToggle("jump-effect", RiskLevel.RISKY)
		val jumpColour by colour("jump-colour") { ChromaColour.fromStaticRGB(0x00, 0xD8, 0xFF, 0xFF) }

		val jumpThickness by integer("jump-thickness", 1, 12) { DEFAULT_THICKNESS }

		val jumpDuration by duration("jump-duration", 500.milliseconds, 8000.milliseconds) { JumpTracker.DEFAULT_DURATION_MS.milliseconds }

		val jumpTrail by toggle("jump-trail") { true }

		override fun migrate(saved: JsonObject): JsonObject = dropOldDefaultJumpColour(saved)
	}

	const val DEFAULT_THICKNESS = 6

	const val OLD_DEFAULT_JUMP_COLOUR = 0xD040B0FF.toInt()

	fun dropOldDefaultJumpColour(saved: JsonObject): JsonObject {
		val el = saved["jump-colour"] ?: return saved
		val c = runCatching { Json.decodeFromJsonElement(ColourHandler.ChromaSerializer, el) }.getOrNull() ?: return saved
		if (c.timeForFullRotationInMillis != 0 || c.getEffectiveColourRGB() != OLD_DEFAULT_JUMP_COLOUR) return saved
		return JsonObject(saved.filterKeys { it != "jump-colour" })
	}

	const val SCAN_INTERVAL = 5
	const val NAME = "Shadow Assassin"

	enum class Kind { SHADOW_ASSASSIN, INVISIBLE }

	fun isActive(enabled: Boolean, inDungeon: Boolean) = enabled && inDungeon

	fun isShadowAssassinName(name: String?): Boolean = name != null && name.contains(NAME, ignoreCase = true)

	fun isInvisibleMob(invisible: Boolean, armorStand: Boolean, self: Boolean, realPlayer: Boolean) =
		invisible && !armorStand && !self && !realPlayer

	val SCENERY_TYPES = setOf("minecraft:wither", "minecraft:ender_dragon", "minecraft:giant")

	fun isInvisibleScenery(typeId: String?) = typeId != null && typeId in SCENERY_TYPES

	fun classify(
		enabled: Boolean, inDungeon: Boolean, includeInvisible: Boolean,
		name: String?, invisible: Boolean, armorStand: Boolean, self: Boolean, realPlayer: Boolean,
		typeId: String? = null,
	): Kind? {
		if (!isActive(enabled, inDungeon) || self || armorStand) return null
		if (!realPlayer && isShadowAssassinName(name)) return Kind.SHADOW_ASSASSIN
		if (includeInvisible && isInvisibleMob(invisible, armorStand, self, realPlayer) && !isInvisibleScenery(typeId))
			return Kind.INVISIBLE
		return null
	}

	data class Point(val x: Double, val y: Double, val z: Double) {
		fun distanceTo(o: Point): Double { val dx = x - o.x; val dy = y - o.y; val dz = z - o.z; return Math.sqrt(dx * dx + dy * dy + dz * dz) }
	}

	data class Jump(val id: Int, val from: Point, val to: Point, val atMs: Long)

	class JumpTracker {
		private class Move(val from: Point, var steps: Int, var jump: Jump? = null)

		private val last = HashMap<Int, Point>()
		private val moving = HashMap<Int, Move>()
		private val vanished = HashMap<Int, Pair<Point, Long>>()
		private val jumps = ArrayList<Jump>()

		fun reset() { last.clear(); moving.clear(); vanished.clear(); jumps.clear() }

		fun lastPosition(id: Int): Point? = last[id]

		fun update(seen: Map<Int, Point>, nowMs: Long): List<Jump> {
			val out = ArrayList<Jump>()
			vanished.entries.removeIf { nowMs - it.value.second > VANISH_MS }
			for ((id, p) in last) if (id !in seen) { vanished[id] = p to nowMs; moving.remove(id) }
			for ((id, p) in seen) {
				val before = last[id]
				if (before != null) {
					step(id, before, p, nowMs, out)
					continue
				}
				val gone = vanished.entries.filter { isJump(it.value.first, p) }.minByOrNull { it.value.first.distanceTo(p) }
				if (gone != null) {
					out += add(Jump(id, gone.value.first, p, nowMs))
					vanished.remove(gone.key)
				}
			}
			last.clear(); last.putAll(seen)
			return out
		}

		private fun step(id: Int, before: Point, p: Point, nowMs: Long, out: MutableList<Jump>) {
			val d = before.distanceTo(p)
			val move = moving[id]
			if (d > MAX_JUMP || d < FAST_STEP) { moving.remove(id); return }

			if (move == null || (move.jump == null && d >= MIN_JUMP)) {
				val m = Move(before, 1)
				moving[id] = m
				if (isJump(m.from, p)) { m.jump = add(Jump(id, m.from, p, nowMs)); out += m.jump!! }
				return
			}
			move.steps++
			if (move.steps > MAX_STEPS) return
			val reported = move.jump
			if (reported != null) {

				val i = jumps.indexOf(reported)
				if (i >= 0) { val moved = reported.copy(to = p); jumps[i] = moved; move.jump = moved }
			} else if (isJump(move.from, p)) {
				move.jump = add(Jump(id, move.from, p, nowMs))
				out += move.jump!!
			}
		}

		private fun add(j: Jump): Jump {
			jumps += j
			while (jumps.size > MAX_ACTIVE) jumps.removeAt(0)
			return j
		}

		fun active(nowMs: Long, durationMs: Long = DEFAULT_DURATION_MS): List<Jump> {
			jumps.removeIf { nowMs - it.atMs > durationMs || nowMs < it.atMs }
			return jumps.toList()
		}

		companion object {

			const val MIN_JUMP = 3.0

			const val MAX_JUMP = 48.0

			const val FAST_STEP = 0.95

			const val MAX_STEPS = 4

			const val VANISH_MS = 2500L
			const val DEFAULT_DURATION_MS = 3000L

			const val WAVE_MS = 1200L
			const val MAX_ACTIVE = 16

			fun isJump(a: Point, b: Point): Boolean = a.distanceTo(b) in MIN_JUMP..MAX_JUMP
		}
	}

	object Wave {
		const val RINGS = 3
		const val SEGMENTS = 48
		const val START_RADIUS = 0.3
		const val SPREAD = 3.2
		const val CREST = 0.15
		const val CRESTS = 6

		fun ringProgress(age: Double, k: Int): Double? {
			val p = age * 1.5 - k * 0.25
			return if (p in 0.0..1.0) p else null
		}

		fun ring(c: Point, age: Double, k: Int): List<Point>? {
			val p = ringProgress(age, k) ?: return null
			val r = START_RADIUS + SPREAD * p
			val crest = CREST * (1 - p)
			return (0..SEGMENTS).map { i ->
				val a = 2 * Math.PI * (i % SEGMENTS) / SEGMENTS
				Point(c.x + r * Math.cos(a), c.y + 0.05 + crest * Math.sin(a * CRESTS + age * 4 * Math.PI), c.z + r * Math.sin(a))
			}
		}

		fun alpha(base: Int, age: Double, k: Int): Int {
			val p = ringProgress(age, k) ?: return 0
			return (base * (1 - p * p)).toInt().coerceIn(0, 255)
		}
	}

	object PathAnim {
		const val TRAVEL = 0.45
		const val DEPART_RIPPLE = 0.6
		const val LAND_RIPPLE = 0.55
		const val FADE_FROM = 0.6

		const val BODY_Y = 1.0
		const val HOOP_RADIUS = 0.7
		const val HOOP_SEGMENTS = 24

		const val STREAK = 0.2
		const val TRAIL_ALPHA = 0.45

		fun age(atMs: Long, nowMs: Long, durationMs: Long): Double? {
			if (durationMs <= 0 || nowMs < atMs || nowMs - atMs > durationMs) return null
			return (nowMs - atMs).toDouble() / durationMs
		}

		fun fade(age: Double): Double {
			if (age <= FADE_FROM) return 1.0
			if (age >= 1.0) return 0.0
			val t = (age - FADE_FROM) / (1 - FADE_FROM)
			return 1 - t * t * (3 - 2 * t)
		}

		fun travel(age: Double): Double {
			val t = (age / TRAVEL).coerceIn(0.0, 1.0)
			return t * t * (3 - 2 * t)
		}

		fun departAge(age: Double) = age / DEPART_RIPPLE

		fun landAge(age: Double) = (age - TRAVEL) / LAND_RIPPLE

		fun lerp(a: Point, b: Point, t: Double) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
		fun lift(p: Point, dy: Double = BODY_Y) = Point(p.x, p.y + dy, p.z)

		fun pulse(j: Jump, age: Double): Point = lift(lerp(j.from, j.to, travel(age)))

		fun streak(j: Jump, age: Double): Pair<Point, Point>? {
			if (age >= TRAVEL) return null
			val t = travel(age)
			return lift(lerp(j.from, j.to, (t - STREAK).coerceAtLeast(0.0))) to lift(lerp(j.from, j.to, t))
		}

		fun hoop(c: Point, from: Point, to: Point, radius: Double = HOOP_RADIUS): List<Point> {
			var dx = to.x - from.x; var dy = to.y - from.y; var dz = to.z - from.z
			val len = Math.sqrt(dx * dx + dy * dy + dz * dz)
			if (len < 1e-6) { dx = 1.0; dy = 0.0; dz = 0.0 } else { dx /= len; dy /= len; dz /= len }

			var ux: Double; var uy: Double; var uz: Double
			if (Math.abs(dy) > 0.95) { ux = 0.0; uy = dz; uz = -dy } else { ux = -dz; uy = 0.0; uz = dx }
			val ul = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul
			val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
			return (0..HOOP_SEGMENTS).map { i ->
				val a = 2 * Math.PI * (i % HOOP_SEGMENTS) / HOOP_SEGMENTS
				val cs = Math.cos(a) * radius; val sn = Math.sin(a) * radius
				Point(c.x + cs * ux + sn * vx, c.y + cs * uy + sn * vy, c.z + cs * uz + sn * vz)
			}
		}

		fun alpha(base: Int, share: Double, age: Double): Int = (base * share * fade(age)).toInt().coerceIn(0, 255)
	}

	val jumps = JumpTracker()

	private var found: Map<Int, Kind> = emptyMap()

	private val tracked = HashSet<Int>()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		found = emptyMap()
		tracked.clear()
		jumps.reset()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val inDungeon = DungeonUtil.isInDungeonIsland
		if (!isActive(TConfig.jumpEffect, inDungeon)) { jumps.reset(); tracked.clear() }
		else trackJumps()
		if (event.tickCount % SCAN_INTERVAL != 0) return
		val enabled = TConfig.enabled || TConfig.jumpEffect
		if (!isActive(enabled, inDungeon)) {
			if (found.isNotEmpty()) found = emptyMap()
			return
		}
		val player = Client.player ?: return
		val level = Client.level ?: return
		val radius = Client.game.options.renderDistance().get() * 16.0
		val area = player.boundingBox.inflate(radius)
		val out = HashMap<Int, Kind>()

		for (stand in level.getEntitiesOfClass(ArmorStand::class.java, area) { it.hasCustomName() }) {
			if (!isShadowAssassinName(stand.customName?.bareText)) continue
			val body = level.getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.0, 3.0, 1.0)) {
				it !is ArmorStand && it !== player && !(it is Player && it.uuid.version() == 4)
			}.minByOrNull { it.distanceToSqr(stand) }
			out[(body ?: stand).id] = Kind.SHADOW_ASSASSIN
		}

		for (e in level.getEntitiesOfClass(LivingEntity::class.java, area) { it !is ArmorStand && it !== player }) {
			if (e.id in out || !e.isAlive) continue
			val kind = classify(
				enabled, inDungeon, TConfig.enabled && TConfig.invisibleMobs,
				name = (e.customName ?: (e as? Player)?.name)?.bareText,
				invisible = e.isInvisible, armorStand = false, self = false,
				realPlayer = e is Player && e.uuid.version() == 4,
				typeId = EntityType.getKey(e.type).toString(),
			) ?: continue
			out[e.id] = kind
		}
		found = out
		if (TConfig.jumpEffect) for ((id, kind) in out) if (kind == Kind.SHADOW_ASSASSIN) tracked += id
	}

	private fun trackJumps() {
		val level = Client.level ?: return
		val seen = HashMap<Int, Point>()
		val iter = tracked.iterator()
		while (iter.hasNext()) {
			val id = iter.next()
			val e = level.getEntity(id)

			if (e == null || !e.isAlive) { iter.remove(); continue }

			seen[id] = Point(e.x, if (e is ArmorStand) e.y - 2.0 else e.y, e.z)
		}
		jumps.update(seen, System.currentTimeMillis())
	}

	@Subscribe
	fun onJumpRender(event: WorldRenderLastEvent) {
		if (!TConfig.jumpEffect || !DungeonUtil.isInDungeonIsland) return
		val now = System.currentTimeMillis()
		val duration = TConfig.jumpDuration.inWholeMilliseconds.coerceAtLeast(1)
		val active = jumps.active(now, duration)
		if (active.isEmpty()) return
		val rgb = TConfig.jumpColour.getEffectiveColourRGB()
		val baseAlpha = (rgb ushr 24).takeIf { it > 0 } ?: 0xFF
		val width = TConfig.jumpThickness.toFloat()
		val trail = TConfig.jumpTrail
		fun col(a: Int) = (rgb and 0xFFFFFF) or (a shl 24)
		fun vec(p: Point) = Vec3(p.x, p.y, p.z)
		WorldDraw.drawInWorld(event) {
			for (j in active) {
				val age = PathAnim.age(j.atMs, now, duration) ?: continue
				val fade = PathAnim.fade(age)

				for ((spot, local) in listOf(j.from to PathAnim.departAge(age), j.to to PathAnim.landAge(age))) {
					if (local < 0.0) continue
					for (k in 0 until Wave.RINGS) {
						val ring = Wave.ring(spot, local, k) ?: continue
						val a = (Wave.alpha(baseAlpha, local, k) * fade).toInt()
						if (a > 0) line(ring.map(::vec), color = col(a), lineWidth = width)
					}
				}

				if (trail) {
					val a = PathAnim.alpha(baseAlpha, PathAnim.TRAIL_ALPHA, age)
					if (a > 0) line(listOf(vec(PathAnim.lift(j.from)), vec(PathAnim.lift(j.to))), color = col(a), lineWidth = (width * 0.6F).coerceAtLeast(1F))
				}

				val pulse = PathAnim.pulse(j, age)
				val hoopAlpha = PathAnim.alpha(baseAlpha, 1.0, age)
				if (hoopAlpha > 0) line(PathAnim.hoop(pulse, j.from, j.to).map(::vec), color = col(hoopAlpha), lineWidth = width)
				PathAnim.streak(j, age)?.let { (a, b) -> line(listOf(vec(a), vec(b)), color = col(baseAlpha), lineWidth = width * 1.5F) }

				val markAlpha = PathAnim.alpha(baseAlpha, 0.8, age)
				if (markAlpha > 0) for (spot in listOf(j.from, j.to)) {
					val ring = Wave.ring(spot, 0.0, 0) ?: continue
					line(ring.map(::vec), color = col(markAlpha), lineWidth = width)
				}
			}
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val list = found
		if (list.isEmpty() || !TConfig.enabled || !DungeonUtil.isInDungeonIsland) return
		val level = Client.level ?: return
		val saColour = TConfig.colour.getEffectiveColourRGB()
		val invColour = TConfig.invisibleColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			for ((id, kind) in list) {
				val e = level.getEntity(id) ?: continue
				if (!e.isAlive) continue
				val box = if (e is ArmorStand)
					AABB(e.x - 0.4, e.y - 2.0, e.z - 0.4, e.x + 0.4, e.y, e.z + 0.4)
				else e.boundingBox.inflate(0.1)
				val c = if (kind == Kind.SHADOW_ASSASSIN) saColour else invColour
				if (TConfig.outline) StarredMobs.boxEdges(box).forEach { (a, b) -> line(a, b, color = c or 0xFF000000.toInt(), lineWidth = 3F) }
				else box(box, c)
			}
		}
	}
}
