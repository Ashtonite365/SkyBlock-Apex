/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.mining

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle

object CrystalHollowsMinimap {
	@Config
	object TConfig : ManagedConfig("crystal-hollows-minimap", Category.MINING) {
		val enabled by riskyToggle("enabled", RiskLevel.RISKY)
		val hud by position("hud", 80, 80) { Vector2i(4, 4) }
		val showPlaces by toggle("show-places") { true }
	}

	private const val MIN = 202
	private const val MAX = 823
	private const val SIZE = 80

	private fun project(v: Double) = ((v - MIN) / (MAX - MIN) * SIZE).toInt().coerceIn(0, SIZE - 1)

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		val player = Client.player ?: return
		val ctx = event.context
		ctx.pose().pushMatrix()
		TConfig.hud.applyTo(ctx.pose())
		HudStyle.drawBackground(ctx, -2, -2, SIZE + 2, SIZE + 2)
		val h = SIZE / 2

		ctx.fill(0, 0, h, h, 0x5022AA22)
		ctx.fill(h, 0, SIZE, h, 0x5022AAAA)
		ctx.fill(0, h, h, SIZE, 0x50AA8822)
		ctx.fill(h, h, SIZE, SIZE, 0x508888AA)
		val n = CrystalHollowsWaypoints.nucleus
		ctx.fill(project(n.x - 30.0), project(n.z - 30.0), project(n.x + 30.0), project(n.z + 30.0), 0x80FF55FF.toInt())
		if (TConfig.showPlaces) for ((name, pos) in CrystalHollowsWaypoints.discovered) {
			val x = project(pos.x.toDouble()); val z = project(pos.z.toDouble())
			ctx.fill(x - 1, z - 1, x + 2, z + 2, 0xFFFFFF55.toInt())
		}
		val px = project(player.x); val pz = project(player.z)
		ctx.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFFFF2222.toInt())
		ctx.pose().popMatrix()
		if (player.y < 64) {

			ctx.pose().pushMatrix()
			val fit = TConfig.hud.applyTextTransformations(ctx.pose())
			ctx.text(Client.font, Component.literal("§cMagma Fields"), 1, (fit.localH - 9).toInt(), -1, true)
			ctx.pose().popMatrix()
		}
	}
}
