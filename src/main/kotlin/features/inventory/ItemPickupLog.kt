// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

object ItemPickupLog {
	@Config
	object TConfig : ManagedConfig("item-pickup-log", Category.CHAT) {
		val enabled by toggle("enabled") { false }
		val displaySeconds by integer("display-seconds", 1, 30) { 5 }
		val maxLines by integer("max-lines", 1, 20) { 8 }
		val hud by position("hud", 140, 80) { Vector2i(5, 220) }
	}

	private class Entry(val name: Component, var delta: Int, var at: TimeMark)

	private var lastCounts: Map<String, Pair<Component, Int>>? = null
	private val entries = LinkedHashMap<String, Entry>()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		lastCounts = null
		entries.clear()
	}

	private fun snapshot(): Map<String, Pair<Component, Int>>? {
		val inv = Client.player?.inventory ?: return null
		val counts = HashMap<String, Pair<Component, Int>>()
		for (i in 0 until 36) {
			val stack = inv.getItem(i)
			if (stack.isEmpty) continue
			val name = stack.hoverName
			val key = name.bareText
			if (key == "SkyBlock Menu (Click)" || key.isBlank()) continue
			val old = counts[key]
			counts[key] = name to ((old?.second ?: 0) + stack.count)
		}
		return counts
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) { lastCounts = null; return }

		if (Client.screen != null) { lastCounts = null; return }
		val now = snapshot() ?: return
		val before = lastCounts
		lastCounts = now
		if (before == null) return
		for (key in before.keys + now.keys) {
			val delta = (now[key]?.second ?: 0) - (before[key]?.second ?: 0)
			if (delta == 0) continue
			val name = now[key]?.first ?: before[key]!!.first
			val e = entries.remove(key)
			if (e != null) { e.delta += delta; e.at = TimeMark.now(); if (e.delta != 0) entries[key] = e }
			else entries[key] = Entry(name, delta, TimeMark.now())
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		entries.values.removeIf { it.at.passedTime() > TConfig.displaySeconds.seconds }
		if (entries.isEmpty()) return
		val lines = entries.values.toList().takeLast(TConfig.maxLines).map {
			val sign = if (it.delta > 0) "§a+" else "§c-"
			Component.literal("$sign${it.delta.absoluteValue} ").append(it.name)
		}
		event.drawHudLines(TConfig.hud, lines)
	}
}
