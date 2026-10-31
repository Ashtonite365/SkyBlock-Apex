// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText
import net.minecraft.world.entity.monster.Creeper
import org.joml.Vector2i

object PlusRiftAreas {
	@Config
	object TConfig : ManagedConfig("rift-areas", Category.RIFT) {
		val verminTracker by toggle("vermin-tracker") { false }
		val voltHighlight by riskyToggle("volt-highlight", RiskLevel.GREY_AREA)
		val hud by position("hud", 120, 40) { Vector2i(5, 340) }
	}

	private val vacuumed by PlusPatterns.group("rift.vermin").pattern("chat.vacuumed", "VERMIN VACUUMED! You vacuumed an? (?<type>.+?)!.*")
	val counts = linkedMapOf<String, Int>()

	private val FAMILIES = listOf("Silverfish" to "Silverfish", "Spider" to "Spider", "Fly" to "Fly", "Bug" to "Fly")

	fun verminType(msg: String): String? = vacuumed.matchMatcher(msg) {
		val type = group("type")
		FAMILIES.firstOrNull { it.first in type }?.second ?: type
	}

	private fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.verminTracker || !inRift()) return
		val type = verminType(event.bareText.trim()) ?: return
		counts.merge(type, 1, Int::plus)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.verminTracker || !inRift() || counts.isEmpty()) return
		event.drawPlusLines(TConfig.hud) {
			add("§eVermin vacuumed")
			counts.forEach { (type, n) -> pair(type, n) }
			pair("Total", counts.values.sum())
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.voltHighlight || !inRift()) return
		val player = Client.player ?: return
		val world = Client.level ?: return
		val volts = world.getEntitiesOfClass(Creeper::class.java, player.boundingBox.inflate(40.0)) { it.isPowered }
		if (volts.isEmpty()) return
		WorldDraw.drawInWorld(event) { volts.forEach { box(it.boundingBox, 0x6000FFFF) } }
	}
}
