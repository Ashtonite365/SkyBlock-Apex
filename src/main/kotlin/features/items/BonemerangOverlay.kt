package dev.ashtonite365.apex.features.items

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityRenderTintEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.TintOverlay
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.tr

object BonemerangOverlay {
	@Config
	object TConfig : ManagedConfig("bonemerang-overlay", Category.COMBAT) {
		val targetCount by toggle("bonemerang-overlay") { false }
		val targetCountHud by position("bonemerang-overlay-hud", 80, 10) { Vector2i() }
		val tintTargets by toggle("highlight-hit-entities") { false }
	}

	val throwableWeapons = listOf(SkyBlockItems.BONE_BOOMERANG, SkyBlockItems.STARRED_BONE_BOOMERANG, SkyBlockItems.TRIBAL_SPEAR)

	private const val REACH = 15.0
	private const val HIT_MARGIN = 0.35

	private fun holdingThrowable() = Client.mainHandStack.view().repoItemId in throwableWeapons

	fun mobsInPath(): Set<LivingEntity> {
		val viewer = Client.cameraEntity as? Player ?: return emptySet()
		val level = Client.player?.level() ?: return emptySet()
		val from = viewer.eyePosition
		val to = from.add(viewer.lookAngle.normalize().scale(REACH))
		return level.getEntities(viewer, AABB(from, to).inflate(1.0))
			.filterIsInstance<LivingEntity>()
			.filter { it !is ArmorStand && !it.isInvisible && it.boundingBox.inflate(HIT_MARGIN).clip(from, to).isPresent }
			.toSet()
	}

	private val blueTint by lazy { TintOverlay().tinted(CommonColors.BLUE) }

	@Subscribe
	fun tintTarget(event: EntityRenderTintEvent) {
		if (!TConfig.tintTargets || !holdingThrowable()) return
		if (event.entity !in mobsInPath()) return
		event.renderState.overlayTexture_apex = blueTint
	}

	@Subscribe
	fun drawCount(event: HudRenderEvent) {
		if (!TConfig.targetCount || !holdingThrowable()) return
		event.drawHudLines(TConfig.targetCountHud, listOf(Component.literal(countLine(mobsInPath().size))))
	}

	internal fun countLine(count: Int, template: String = tr("apex.bonemerang-overlay.bonemerang-overlay.display", "Bonemerang Targets: %s").string) =
		template.format(count)
}
