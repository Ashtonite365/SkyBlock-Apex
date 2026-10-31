// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import dev.ashtonite365.apex.util.render.WaypointType
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import org.joml.Vector2i

object PlusRiftExtras {
	@Config
	object TConfig : ManagedConfig("rift-extras", Category.RIFT) {
		val ubikReminder by toggle("ubik-reminder") { false }
		val geckoCombo by toggle("sun-gecko-combo") { false }
		val tentacles by toggle("bacte-tentacles") { false }
		val hud by position("hud", 120, 20) { Vector2i(5, 460) }
	}

	@Serializable
	data class Data(var ubikReadyAt: Long = 0L, var reminded: Boolean = true)

	@Config
	object DConfig : DataHolder<Data>(serializer(), "rift-extras", ::Data)

	private const val COMBO_SHOW_MS = 5000
	private const val TENTACLE_FILL = 0x50FF8800
	private val TENTACLE_LINE = 0xFFFF8800.toInt()

	private fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT
	private var combo: Int? = null
	private var comboAt = 0L

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val match = RiftRules.ubikCooldown.matchEntire(event.bareText.trim()) ?: return
		val wait = RiftRules.parseDuration(match.groups["duration"]!!.value) ?: return
		val data = DConfig.data
		data.ubikReadyAt = System.currentTimeMillis() + wait
		data.reminded = false
		DConfig.markDirty()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.ubikReminder || event.tickCount % 20 != 0) return
		val data = DConfig.data
		if (data.reminded || data.ubikReadyAt == 0L) return
		if (System.currentTimeMillis() < data.ubikReadyAt) return
		data.reminded = true
		DConfig.markDirty()
		Client.showChat(Component.literal("§a[Apex] Ubik's Cube is ready in the Rift!"))
	}

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!TConfig.geckoCombo || !inRift()) return
		val count = RiftRules.geckoCombo(event.text.bareText) ?: return
		combo = count
		comboAt = System.currentTimeMillis()
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!inRift()) return
		val recentCombo = combo?.let { if (System.currentTimeMillis() - comboAt < COMBO_SHOW_MS) it else null }
		val readyAt = DConfig.data.ubikReadyAt
		val ubik = if (TConfig.ubikReminder && readyAt != 0L) readyAt else null
		val showCombo = recentCombo != null && TConfig.geckoCombo
		if (!showCombo && ubik == null) return
		event.drawPlusLines(TConfig.hud) {
			if (TConfig.geckoCombo && recentCombo != null) add("§eSun Gecko combo: §lx$recentCombo")
			if (ubik != null) {
				val left = ubik - System.currentTimeMillis()
				val text = if (left <= 0) "§aReady!" else "§e${left / 60000}m ${left / 1000 % 60}s"
				add("§7Ubik's Cube: $text")
			}
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.tentacles || !inRift()) return
		val self = Client.player ?: return
		val world = Client.level ?: return
		val tentacles = world.getEntitiesOfClass(ArmorStand::class.java, self.boundingBox.inflate(40.0)) {
			it.customName?.bareText?.contains("Bacte Tentacle") == true
		}
		if (tentacles.isEmpty()) return
		WorldDraw.drawInWorld(event, WaypointType.RIFT) {
			tentacles.forEach { stand ->
				markerBox(stand.boundingBox.inflate(0.5, 0.0, 0.5).expandTowards(0.0, -3.0, 0.0), TENTACLE_FILL)
				tracer(stand.position(), TENTACLE_LINE)
			}
		}
	}
}
