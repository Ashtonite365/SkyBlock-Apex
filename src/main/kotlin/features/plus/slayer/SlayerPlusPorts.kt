// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.slayer

import org.joml.Vector2i
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText

object PlusOwnSlayerBoss {
	var boss: LivingEntity? = null; private set

	var tags: List<String> = emptyList(); private set

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 4 != 0) return
		boss = null
		tags = emptyList()
		if (SlayerPlusPorts.anyEnabled()) scan()
	}

	private fun scan() {
		val me = Client.player ?: return
		val world = Client.level ?: return
		val label = "Spawned by: ${me.gameProfile.name}"
		val marker = world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(40.0)) {
			it.customName?.bareText?.trim() == label
		}.firstOrNull() ?: return
		boss = world.getEntitiesOfClass(LivingEntity::class.java, marker.boundingBox.inflate(1.5, 4.0, 1.5)) {
			it !== me && it !is ArmorStand
		}.minByOrNull { it.distanceToSqr(marker) }
		tags = world.getEntitiesOfClass(ArmorStand::class.java, marker.boundingBox.inflate(0.6, 3.0, 0.6)) { it.hasCustomName() }
			.mapNotNull { it.customName?.bareText }
	}
}

object SlayerPlusPorts {
	@Config
	object TConfig : ManagedConfig("slayer-extras", Category.SLAYERS) {
		val blazeDaggerHelper by riskyToggle("blaze-dagger-helper", RiskLevel.GREY_AREA)
		val blazeDaggerHud by position("blaze-dagger-hud", 160, 12) { Vector2i(200, 120) }
		val firePillar by toggle("fire-pillar") { false }
		val firePillarHud by position("fire-pillar-hud", 100, 12) { Vector2i(200, 135) }
		val blazeClearView by riskyToggle("blaze-clear-view", RiskLevel.GREY_AREA)
		val endermanBeacon by riskyToggle("enderman-beacon", RiskLevel.GREY_AREA)
		val endermanNukekubi by riskyToggle("enderman-nukekubi", RiskLevel.GREY_AREA)
		val lineToSeraph by riskyToggle("line-to-seraph", RiskLevel.GREY_AREA)
		val lineToSpider by riskyToggle("line-to-spider", RiskLevel.GREY_AREA)
		val spiderEggSacs by riskyToggle("spider-egg-sacs", RiskLevel.GREY_AREA)
		val vampireTwinclaws by riskyToggle("vampire-twinclaws", RiskLevel.GREY_AREA)
		val vampireSteak by riskyToggle("vampire-steak", RiskLevel.GREY_AREA)
	}

	fun anyEnabled(): Boolean {
		val c = TConfig
		return listOf(
			c.blazeDaggerHelper, c.firePillar, c.blazeClearView, c.endermanBeacon, c.endermanNukekubi,
			c.lineToSeraph, c.lineToSpider, c.spiderEggSacs, c.vampireTwinclaws, c.vampireSteak,
		).any { it }
	}

	private enum class Family(vararg val markers: String) {
		BLAZE("Inferno Demonlord"),
		BLAZE_ANY("Inferno Demonlord", "Quazii", "Typhoeus"),
		ENDERMAN("Voidgloom"),
		SPIDER("Tarantula", "Conjoined Brood"),
		VAMPIRE("Bloodfiend");

		fun matches(): Boolean {
			val title = PlusOwnSlayerBoss.tags.firstOrNull { '❤' in it } ?: ""
			return markers.any { title.contains(it) }
		}
	}

	private val smokeTypes = setOf(ParticleTypes.FLAME, ParticleTypes.LAVA, ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE)

	private var beaconSpots: List<BlockPos> = emptyList()
	private var skullStands: List<ArmorStand> = emptyList()
	private var sacStands: List<ArmorStand> = emptyList()
	private var pillarLeft: Int? = null
	private var beaconAlertAt = TimeMark.farPast()
	private var twinclawsAlertAt = TimeMark.farPast()
	private var steakShown = false

	private fun dropMarkers() {
		beaconSpots = emptyList()
		skullStands = emptyList()
		sacStands = emptyList()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		val target = PlusOwnSlayerBoss.boss
		val world = Client.level ?: return
		val me = Client.player ?: return
		pillarLeft = if (!TConfig.firePillar) null else
			world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(20.0)) { it.hasCustomName() }
				.firstNotNullOfOrNull { stand -> stand.customName?.bareText?.let { PlusSlayerLogic.firePillarSeconds(it) } }
		if (target == null) {
			dropMarkers()
			steakShown = false
			return
		}
		updateBeacons(world, target)
		val ender = Family.ENDERMAN.matches()
		skullStands = if (!(TConfig.endermanNukekubi && ender)) emptyList() else
			world.getEntitiesOfClass(ArmorStand::class.java, target.boundingBox.inflate(15.0)) {
				!it.hasCustomName() && it.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)
			}
		sacStands = if (!(TConfig.spiderEggSacs && Family.SPIDER.matches())) emptyList() else
			world.getEntitiesOfClass(ArmorStand::class.java, target.boundingBox.inflate(20.0)) {
				it.customName?.bareText.equals("SHOOT ME!", ignoreCase = true)
			}
		if (Family.VAMPIRE.matches()) vampireAlerts(target)
	}

	private fun updateBeacons(world: Level, target: LivingEntity) {
		if (!TConfig.endermanBeacon || !Family.ENDERMAN.matches()) {
			beaconSpots = emptyList()
			return
		}
		val centre = target.blockPosition()
		val now = BlockPos.betweenClosed(centre.offset(-12, -4, -12), centre.offset(12, 6, 12))
			.filter { world.getBlockState(it).`is`(Blocks.BEACON) }
			.map(BlockPos::immutable)
		val appeared = beaconSpots.isEmpty() && now.isNotEmpty()
		if (appeared && beaconAlertAt.passedTime() > 3.seconds) {
			beaconAlertAt = TimeMark.now()
			Alerts.show(Component.literal("§4Beacon"))
		}
		beaconSpots = now
	}

	private fun vampireAlerts(target: LivingEntity) {
		val twinclawsUp = TConfig.vampireTwinclaws && PlusOwnSlayerBoss.tags.any { PlusSlayerLogic.isTwinclaws(it) }
		if (twinclawsUp && twinclawsAlertAt.passedTime() > 2.seconds) {
			twinclawsAlertAt = TimeMark.now()
			Alerts.show(Component.literal("§6§lTWINCLAWS"), 1.seconds)
		}
		if (TConfig.vampireSteak && !steakShown && target.health / target.maxHealth < 0.2f) {
			steakShown = true
			Alerts.show(Component.literal("§c§lSTEAK!"))
		}
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.blazeClearView || !Family.BLAZE.matches()) return
		val target = PlusOwnSlayerBoss.boss ?: return
		if (event.particleEffect.type !in smokeTypes) return
		if (event.position.distanceToSqr(target.position()) < 15.0 * 15.0) event.cancel()
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val target = PlusOwnSlayerBoss.boss
		val me = Client.player ?: return
		WorldDraw.drawInWorld(event) {
			for (spot in beaconSpots) {
				block(spot, 0x80AA0000.toInt())
				text(Vec3.atCenterOf(spot).add(0.0, 1.0, 0.0), Component.literal("§4Beacon"))
			}
			for (stand in skullStands) box(stand.boundingBox, 0x60FFAA00)
			for (stand in sacStands) box(stand.boundingBox, 0x60FF55FF)
			val wantLine = (TConfig.lineToSeraph && Family.ENDERMAN.matches()) || (TConfig.lineToSpider && Family.SPIDER.matches())
			if (target != null && wantLine) {
				val from = me.eyePosition.add(me.lookAngle)
				val to = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
				line(from, to, color = 0xFFFF55FF.toInt(), lineWidth = 3f)
			}
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val pillar = pillarLeft
		if (TConfig.firePillar && pillar != null) event.drawPlusLines(TConfig.firePillarHud) { add("§cFire Pillar: §b${pillar}s") }
		if (!TConfig.blazeDaggerHelper || !Family.BLAZE_ANY.matches()) return
		val shield = PlusOwnSlayerBoss.tags.firstNotNullOfOrNull { PlusSlayerLogic.HellionShield.inName(it) } ?: return
		event.drawPlusLines(TConfig.blazeDaggerHud) { add("${shield.colour}§l${shield.label.uppercase()} §7- ${shield.dagger}") }
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		dropMarkers()
		pillarLeft = null
	}
}
