/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: visual tweaks. Damage splash hiding, hiding players that stand next to NPCs (hiding players in
 * general is Render > Hide Players, features/render/HidePlayers.kt), player armour hiding, the fire overlay mode
 * (normal / Low on Fire / hidden), Enderman portal particles, a zoom key, and the risky dropped-item glow, entity and
 * party outlines, far-entity, exp-orb, particle and temporary armour stand hiders. The Low on Fire mode is inspired by
 * the "Low On Fire" resource pack by Haikis (idea only, no assets used). Apex implementation.
 */
package dev.ashtonite365.apex.features.misc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ExperienceOrb
import java.util.UUID
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.render.HidePlayers
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.skyblock.PartyUtil
import dev.ashtonite365.apex.util.bareText

object MiscRender {
	@Config
	object TConfig : ManagedConfig("misc-render", Category.MISC) {
		val hideDamageSplash by toggle("hide-damage-splash") { false }
		val hidePlayersNearNpcs by toggle("hide-players-near-npcs") { false }
		val npcHideRadius by integer("npc-hide-radius", 1, 10) { 3 }
		val hidePlayerArmour by toggle("hide-player-armour") { false }

		val fireOverlay by choice("fire-overlay") { FireOverlay.NORMAL }
		val hideEndermanParticles by toggle("hide-enderman-particles") { false }
		val zoomKey by keyBindingWithDefaultUnbound("zoom-key")
		val zoomFactor by integer("zoom-factor", 2, 10) { 4 }
		val itemGlow by riskyToggle("item-glow", RiskLevel.GREY_AREA)
		val entityOutlines by riskyToggle("entity-outlines", RiskLevel.GREY_AREA)
		val hideExpOrbs by riskyToggle("hide-exp-orbs", RiskLevel.GREY_AREA)
		val hideFarEntities by riskyToggle("hide-far-entities", RiskLevel.GREY_AREA)
		val farEntityDistance by integer("far-entity-distance", 8, 128) { 40 }
		val partyOutlines by riskyToggle("party-outlines", RiskLevel.GREY_AREA)
		val outlineNames by string("outline-names") { "" }
		val hideFarParticles by riskyToggle("hide-far-particles", RiskLevel.GREY_AREA)
		val hideCloseRedstone by riskyToggle("hide-close-redstone", RiskLevel.GREY_AREA)
		val hideFireballParticles by riskyToggle("hide-fireball-particles", RiskLevel.GREY_AREA)
		val hideTempArmorStands by riskyToggle("hide-temp-armor-stands", RiskLevel.GREY_AREA)

		override fun migrate(saved: JsonObject) = foldOldHideFire(saved)
	}

	enum class FireOverlay(val id: String) : StringRepresentable {
		NORMAL("normal"),
		LOW_ON_FIRE("low-on-fire"),
		HIDDEN("hidden"),
		;

		override fun getSerializedName(): String = id
	}

	const val OLD_HIDE_FIRE_KEY = "hide-fire-overlay"

	fun foldOldHideFire(saved: JsonObject): JsonObject {
		val old = saved[OLD_HIDE_FIRE_KEY] ?: return saved
		val migrated = LinkedHashMap(saved)
		migrated.remove(OLD_HIDE_FIRE_KEY)
		val wasOn = old is JsonPrimitive && old.content == "true"
		if (wasOn && !saved.containsKey("fire-overlay")) migrated["fire-overlay"] = JsonPrimitive(FireOverlay.HIDDEN.id)
		return JsonObject(migrated)
	}

	private val DAMAGE_TAG = Regex("^[✧✯]?[0-9][0-9,.]*[kKmMbB]?[✧✯❤♞☄⚔✷]*$")

	fun isDamageSplash(name: String) = DAMAGE_TAG.matches(name.trim().replace(" ", ""))

	fun isRealPlayer(e: Entity) = e is Player && e.uuid.version() == 4
	fun isNpc(e: Entity) = e is Player && e.uuid.version() == 2

	private val MOB_TAG = Regex("""^\[Lv\s*\d+]|[❤♥]\s*$|\d[\d,.]*[kKmM]?/\d[\d,.]*[kKmM]?""")

	fun isMobTag(name: String) = MOB_TAG.containsMatchIn(name.trim())

	fun isRealNpc(isPlayerEntity: Boolean, uuid: UUID, inTabList: Boolean, tagsAbove: List<String>): Boolean =
		isPlayerEntity && uuid.version() == 2 && !inTabList && tagsAbove.none(::isMobTag)

	private fun tagsAbove(entity: Entity): List<String> {
		val level = Client.game.level ?: return emptyList()
		val box = entity.boundingBox.inflate(0.5, 0.0, 0.5).expandTowards(0.0, 3.0, 0.0)
		return level.getEntitiesOfClass(ArmorStand::class.java, box) { it.hasCustomName() }
			.mapNotNull { it.customName?.bareText }
	}

	private fun isWorldNpc(entity: Entity): Boolean =
		isNpc(entity) && isRealNpc(true, entity.uuid, Client.connection?.getPlayerInfo(entity.uuid) != null, tagsAbove(entity))

	fun isFar(distanceSq: Double, distance: Int) = distanceSq > distance.toDouble() * distance

	fun isTempArmorStand(ticks: Int, named: Boolean, empty: Boolean) = !named && empty && ticks < 10

	fun hideParticleByDistance(distanceSq: Double, isDust: Boolean, far: Boolean, closeRedstone: Boolean): Boolean {
		if (far && distanceSq > 1600.0) return true
		return closeRedstone && isDust && distanceSq < 4.0
	}

	fun nameMatches(name: String, names: String) =
		names.split('|', ',').any { part -> part.trim().let { it.isNotEmpty() && name.contains(it, ignoreCase = true) } }

	@JvmStatic
	fun isAnimationBlock(entity: Entity) = entity is FallingBlockEntity || entity is Display

	private var npcSpots: List<Vec3> = emptyList()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!(TConfig.hidePlayersNearNpcs && SkyBlockSession.inSkyBlock)) {
			npcSpots = emptyList()
			return
		}
		if (event.tickCount % 20 == 0)
			npcSpots = Client.game.level?.entitiesForRendering()?.filter(::isWorldNpc)?.map(Entity::position) ?: emptyList()
	}

	private fun isSplashStand(entity: Entity): Boolean {
		if (entity !is ArmorStand || !entity.isInvisible) return false
		val name = entity.customName?.bareText ?: return false
		return isDamageSplash(name)
	}

	private fun isTempStand(entity: Entity) = entity is ArmorStand &&
		isTempArmorStand(entity.tickCount, entity.hasCustomName(), EquipmentSlot.entries.all { entity.getItemBySlot(it).isEmpty })

	private fun tooFar(entity: Entity): Boolean {
		if (entity === Client.player) return false
		val me = Client.player ?: return false
		return isFar(entity.distanceToSqr(me), TConfig.farEntityDistance)
	}

	private fun standsByNpc(entity: Entity): Boolean = nearAny(npcSpots, entity.position(), TConfig.npcHideRadius)

	fun nearAny(spots: List<Vec3>, at: Vec3, radius: Int): Boolean {
		val limit = (radius * radius).toDouble()
		return spots.any { it.distanceToSqr(at) <= limit }
	}

	@JvmStatic
	fun shouldHide(entity: Entity): Boolean {
		if (entity is Player && HidePlayers.shouldHide(entity)) return true
		if (!SkyBlockSession.inSkyBlock) return false
		return when {
			TConfig.hideDamageSplash && isSplashStand(entity) -> true
			TConfig.hideTempArmorStands && isTempStand(entity) -> true
			TConfig.hideExpOrbs && entity is ExperienceOrb -> true
			TConfig.hideFarEntities && tooFar(entity) -> true
			entity === Client.player || !isRealPlayer(entity) -> false
			else -> TConfig.hidePlayersNearNpcs && standsByNpc(entity)
		}
	}

	@JvmStatic fun hideArmour() = TConfig.hidePlayerArmour && SkyBlockSession.inSkyBlock
	@JvmStatic fun hideFire() = TConfig.fireOverlay == FireOverlay.HIDDEN
	@JvmStatic fun lowFire() = TConfig.fireOverlay == FireOverlay.LOW_ON_FIRE

	@JvmStatic
	fun zoomedFov(fov: Float): Float {
		val zooming = Client.screen == null && TConfig.zoomKey.isPressed()
		return zoomFov(fov, zooming, TConfig.zoomFactor)
	}

	fun zoomFov(fov: Float, zooming: Boolean, factor: Int): Float = if (zooming) fov / factor.coerceAtLeast(1) else fov

	private fun isPartyMember(entity: Entity): Boolean {
		if (!isRealPlayer(entity) || entity === Client.player) return false
		val name = entity.name.bareText
		return PartyUtil.party?.members?.any { it.name.equals(name, true) } == true
	}

	private fun matchesOutlineNames(entity: Entity): Boolean {
		val names = TConfig.outlineNames
		if (names.isBlank()) return false
		return nameMatches(entity.customName?.bareText ?: entity.name.bareText, names)
	}

	@JvmStatic
	fun shouldGlow(entity: Entity): Boolean {
		if (!SkyBlockSession.inSkyBlock || isAnimationBlock(entity)) return false
		if (TConfig.itemGlow && entity is ItemEntity) return true
		if (TConfig.partyOutlines && isPartyMember(entity)) return true
		return TConfig.entityOutlines && matchesOutlineNames(entity)
	}

	private fun anyNear(cls: Class<out Entity>, at: Vec3, size: Double): Boolean? {
		val level = Client.game.level ?: return null
		return level.getEntitiesOfClass(cls, AABB.ofSize(at, size, size, size)).isNotEmpty()
	}

	@Subscribe
	fun onParticleSh(event: ParticleSpawnEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val me = Client.player ?: return
		val type = event.particleEffect.type
		val distSq = me.position().distanceToSqr(event.position)
		if (hideParticleByDistance(distSq, type == ParticleTypes.DUST, TConfig.hideFarParticles, TConfig.hideCloseRedstone)) {
			event.cancel()
			return
		}
		val smoke = type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE
		if (TConfig.hideFireballParticles && smoke && anyNear(SmallFireball::class.java, event.position, 10.0) == true) event.cancel()
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.hideEndermanParticles || event.particleEffect.type != ParticleTypes.PORTAL) return
		if (anyNear(Enderman::class.java, event.position, 4.0) == true) event.cancel()
	}
}
