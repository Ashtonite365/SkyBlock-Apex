/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: one shared name-tag -> body pairing for dungeon mob highlights (starred mobs, blood mobs).
 * Pairing rules follow the public behaviour of other dungeon mods (ideas only, re-implemented here; see CREDITS.md):
 *  - the body is the entity whose box overlaps the tag's armour stand box moved one block down; player bodies only
 *    when uuid.version() == 2; invisible bodies skipped.
 *  - Hypixel spawns the name stand right after its body, so the stand's entity id is the body's id + 1 ("with some
 *    exceptions"); real players have a version 4 UUID, Hypixel's player-bodied mobs and NPCs a version 2 UUID.
 */
package dev.ashtonite365.apex.features.dungeon

import kotlin.math.abs
import kotlin.math.hypot
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

object TagBodyPairing {

	const val EXPECTED_GAP = 0.0

	const val MIN_GAP = -2.0
	const val MAX_GAP = 1.0

	const val XZ_TOLERANCE = 0.5

	const val ID_ORDER_BONUS = 1.0

	enum class Kind { MOB, NPC_PLAYER, REAL_PLAYER, SELF, ARMOR_STAND }

	data class Tag(val id: Int, val x: Double, val y: Double, val z: Double)
	data class Body(
		val id: Int, val x: Double, val y: Double, val z: Double, val height: Double,
		val kind: Kind, val invisible: Boolean,
	)

	fun eligible(b: Body) = !b.invisible && (b.kind == Kind.MOB || b.kind == Kind.NPC_PLAYER)

	fun playerKind(uuidVersion: Int, isSelf: Boolean) = when {
		isSelf -> Kind.SELF
		uuidVersion == 2 -> Kind.NPC_PLAYER
		else -> Kind.REAL_PLAYER
	}

	fun score(tag: Tag, body: Body): Double? {
		if (!eligible(body)) return null
		val dx = body.x - tag.x
		val dz = body.z - tag.z
		if (abs(dx) > XZ_TOLERANCE || abs(dz) > XZ_TOLERANCE) return null
		if (body.y > tag.y + 0.1) return null
		val gap = tag.y - (body.y + body.height)
		if (gap < MIN_GAP || gap > MAX_GAP) return null
		var s = abs(gap - EXPECTED_GAP) + hypot(dx, dz)
		if (tag.id == body.id + 1) s -= ID_ORDER_BONUS
		return s
	}

	fun assign(tags: List<Tag>, bodies: List<Body>, previous: Map<Int, Int> = emptyMap()): Map<Int, Int> {
		val out = linkedMapOf<Int, Int>()
		val used = mutableSetOf<Int>()
		val bodyById = bodies.associateBy { it.id }
		for (t in tags.sortedBy { it.id }) {
			val b = previous[t.id]?.let(bodyById::get) ?: continue
			if (b.id in used || score(t, b) == null) continue
			out[t.id] = b.id
			used += b.id
		}
		val pairs = mutableListOf<Triple<Tag, Body, Double>>()
		for (t in tags) if (t.id !in out) for (b in bodies) if (b.id !in used) score(t, b)?.let { pairs += Triple(t, b, it) }
		pairs.sortWith(compareBy<Triple<Tag, Body, Double>>({ it.third }, { it.first.id }, { it.second.id }))
		for ((t, b, _) in pairs) {
			if (t.id in out || b.id in used) continue
			out[t.id] = b.id
			used += b.id
		}
		return out
	}

	fun signature(tags: List<Tag>, assigned: Map<Int, Int>): String =
		tags.map { it.id }.sorted().joinToString(",") { "$it->${assigned[it] ?: "none"}" }

	fun tagOf(stand: ArmorStand) = Tag(stand.id, stand.x, stand.y, stand.z)

	fun kindOf(e: Entity, self: Entity?): Kind = when {
		e === self -> Kind.SELF
		e is ArmorStand -> Kind.ARMOR_STAND
		e is Player -> playerKind(e.uuid.version(), false)
		else -> Kind.MOB
	}

	fun bodyOf(e: LivingEntity, self: Entity?) = Body(e.id, e.x, e.y, e.z, e.bbHeight.toDouble(), kindOf(e, self), e.isInvisible)

	fun pairEntities(stands: List<ArmorStand>, candidates: List<LivingEntity>, self: Entity?, previous: Map<Int, Int> = emptyMap()): Map<Int, LivingEntity> {
		val byId = candidates.associateBy { it.id }
		return assign(stands.map(::tagOf), candidates.map { bodyOf(it, self) }, previous).mapNotNull { (t, b) -> byId[b]?.let { t to it } }.toMap()
	}
}
