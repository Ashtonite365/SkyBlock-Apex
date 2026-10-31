// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.render

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw.Companion.drawInWorld
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object GyroWandRing {
	@Config
	object TConfig : ManagedConfig("gyro-wand-ring", Category.RENDER) {
		val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)
		val showCooldown by toggle("show-cooldown") { true }
	}

	object Logic {
		const val RADIUS = 10.0
		const val RANGE = 25.0
		const val COOLDOWN_MS = 30_000L

		val gravityStormRegex = Regex("(?s).*-\\d+ Mana \\(Gravity Storm\\).*")

		fun isCast(actionBar: String): Boolean = gravityStormRegex.matches(actionBar)

		fun onCooldown(castAt: Long, now: Long): Boolean = castAt != 0L && now - castAt < COOLDOWN_MS

		fun ring(center: Vec3, radius: Double = RADIUS, segments: Int = 64): List<Vec3> {
			val points = ArrayList<Vec3>(maxOf(segments + 1, 0))
			for (i in 0..segments) {
				val angle = 2 * Math.PI * i / segments
				points.add(center.add(radius * cos(angle), 0.0, radius * sin(angle)))
			}
			return points
		}
	}

	private const val NORMAL = 0xFFAA00AA.toInt()
	private const val COOLING = 0xFFFF5555.toInt()
	private var lastCast = 0L

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!Logic.isCast(event.original.bareText)) return
		lastCast = System.currentTimeMillis()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		lastCast = 0L
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.enabled) return
		val player = Client.player ?: return
		if (Client.mainHandStack.view().repoItemId?.skyblockId != "GYROKINETIC_WAND") return
		val target = player.pick(Logic.RANGE, 0f, false) as? BlockHitResult ?: return
		if (target.type != HitResult.Type.BLOCK) return
		val centre = Vec3.atBottomCenterOf(target.blockPos).add(0.0, 1.05, 0.0)
		val cooling = TConfig.showCooldown && Logic.onCooldown(lastCast, System.currentTimeMillis())
		val colour = if (cooling) COOLING else NORMAL
		drawInWorld(event) {
			line(Logic.ring(centre), color = colour, lineWidth = 3f)
		}
	}
}
