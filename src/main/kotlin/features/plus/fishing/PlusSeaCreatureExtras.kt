// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.fishing.SeaCreatureKind
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw

object PlusSeaCreatureExtras {
	@Config
	object TConfig : ManagedConfig("sea-creature-extras", Category.FISHING) {
		val hideCommon by toggle("hide-common") { false }
		val hideCommonOnlyWithRod by toggle("hide-common-only-with-rod") { true }
		val lootshareSphere by toggle("lootshare-sphere") { false }
	}

	const val LOOTSHARE_RANGE = 30.0
	private val RING_HEIGHTS = doubleArrayOf(-15.0, 0.0, 15.0)
	private const val IN_RANGE = 0xFF55FF55.toInt()
	private const val OUT_OF_RANGE = 0xFFFFFFFF.toInt()

	data class Tracked(val name: String, val rare: Boolean, val pos: Vec3)

	private var hiddenIds: Set<Int> = emptySet()
	private var rareNearby: List<Tracked> = emptyList()

	private fun hidingWanted(player: Player): Boolean =
		TConfig.hideCommon && (!TConfig.hideCommonOnlyWithRod || player.mainHandItem.item == Items.FISHING_ROD)

	private fun bodyOf(level: ClientLevel, stand: ArmorStand): LivingEntity? =
		level.getEntitiesOfClass(LivingEntity::class.java, stand.boundingBox.inflate(1.5, 3.0, 1.5)) { it !is ArmorStand && it !is Player }
			.minByOrNull { it.distanceToSqr(stand) }

	fun update(level: ClientLevel, player: Player, tags: List<Triple<ArmorStand, String, SeaCreatureKind>>) {
		rareNearby = tags.map { (stand, _, kind) -> Tracked(kind.name, kind.rare, stand.position()) }
		if (!hidingWanted(player)) {
			hiddenIds = emptySet()
			return
		}
		val ids = HashSet<Int>()
		for ((stand, _, _) in tags.filter { !it.third.rare }) {
			ids += stand.id
			bodyOf(level, stand)?.let { ids += it.id }
		}
		hiddenIds = ids
	}

	fun shouldHide(entity: Entity): Boolean = entity.id in hiddenIds

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		hiddenIds = emptySet()
		rareNearby = emptyList()
	}

	fun ring(c: Vec3, radius: Double, dy: Double, n: Int = 48): List<Vec3> {
		val r = sqrt(maxOf(0.0, radius * radius - dy * dy))
		return List(n + 1) { step ->
			val angle = 2 * Math.PI * step / n
			Vec3(c.x + r * cos(angle), c.y + dy, c.z + r * sin(angle))
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.lootshareSphere || !SkyBlockSession.inSkyBlock) return
		val player = Client.player ?: return
		val rare = rareNearby.filter { it.rare }
		if (rare.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (creature in rare) {
				val colour = if (creature.pos.distanceTo(player.position()) < LOOTSHARE_RANGE) IN_RANGE else OUT_OF_RANGE
				for (dy in RING_HEIGHTS) line(ring(creature.pos, LOOTSHARE_RANGE, dy), color = colour, lineWidth = 3f)
			}
		}
	}
}
