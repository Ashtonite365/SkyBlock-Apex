// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.network.chat.Component
import org.joml.Vector2i

object SprayTracker {
	@Config
	object TConfig : ManagedConfig("plus-spray", Category.GARDEN) {
		val display by toggle("display") { false }
		val expiryNotification by toggle("expiry-notification") { false }
		val position by position("position", 160, 40) { Vector2i(4, 300) }
	}

	private const val DEFAULT_MINUTES = 30
	private fun minutesFor(id: String?): Int? = when (id) {
		"SPRAYONATOR" -> 30
		"JUICY_SPRAYONATOR" -> 45
		"SALTY_SPRAYONATOR" -> 60
		else -> null
	}

	private val sprayed by PlusPatterns.pattern(
		"garden.spray.sprayed", "SPRAYONATOR! You sprayed Plot - (?<plot>.+) with (?:\\d+ )?(?<spray>.+)!")

	data class Spray(val plot: String, val material: String, val expiresAt: Long, var notified: Boolean = false)

	val sprays = linkedMapOf<String, Spray>()

	fun parse(message: String): Pair<String, String>? {
		val m = sprayed.matcher(message)
		if (!m.matches()) return null
		return m.group("plot") to m.group("spray")
	}

	fun durationMinutes(heldId: String?): Int = minutesFor(heldId) ?: DEFAULT_MINUTES

	fun formatRemaining(ms: Long): String {
		val totalSeconds = maxOf(ms, 0L) / 1000
		return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val (plot, material) = parse(event.bareText) ?: return
		val held = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId
		val ends = System.currentTimeMillis() + durationMinutes(held) * 60_000L
		sprays[plot] = Spray(plot, material, ends)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.expiryNotification || event.tickCount % 20 != 0) return
		val now = System.currentTimeMillis()
		val due = ArrayList<Spray>()
		for (s in sprays.values) if (!s.notified && s.expiresAt <= now) due.add(s)
		if (due.isEmpty()) return
		for (s in due) s.notified = true
		val plots = due.joinToString("§7, ") { "§b${it.plot}" }
		Client.showChat(Component.literal("§7Spray expired on §aPlot §7- $plots"))
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.display || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val now = System.currentTimeMillis()
		val live = sprays.values.filter { it.expiresAt > now }
		event.drawPlusLines(TConfig.position) {
			if (live.isEmpty()) {
				add("§cNo active sprays")
			} else {
				for (s in live) add("§ePlot ${s.plot}: §a${s.material} §7- §b${formatRemaining(s.expiresAt - now)}")
			}
		}
	}
}
