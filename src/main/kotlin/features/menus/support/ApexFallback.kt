// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.util.bareText

class FallbackPlanner(
	private val renderable: (title: String) -> Boolean,
	private val rootTitle: String = "SkyBlock Menu",
	val maxDepth: Int = 4,
	val windowMs: Long = 10_000L,
) {
	sealed interface Decision {

		data class GoBack(val slot: Int, val target: String?) : Decision
		data class Vanilla(val reason: String) : Decision
	}

	private val failedAt = LinkedHashMap<String, Long>()
	private val chain = mutableListOf<String>()
	private var lastHop = Long.MIN_VALUE

	fun recentlyFailed(title: String, nowMs: Long) = failedAt[norm(title)]?.let { nowMs - it <= windowMs } == true

	private fun norm(t: String) = MenuRegistry.strip(t).lowercase()

	fun decide(title: String, slots: List<SlotText>, nowMs: Long): Decision {
		val t = norm(title)
		failedAt.entries.removeAll { nowMs - it.value > windowMs }
		if (lastHop == Long.MIN_VALUE || nowMs - lastHop > windowMs) chain.clear()
		failedAt[t] = nowMs
		if (t in chain) return Decision.Vanilla("loop guard: '$title' already failed in this fallback chain")
		if (chain.size >= maxDepth) return Decision.Vanilla("loop guard: $maxDepth hops without an Apex page")
		val back = slots.firstOrNull { it.cleanName.equals("Go Back", true) }
			?: return Decision.Vanilla("no Go Back slot")
		val target = back.cleanLore.firstOrNull { it.startsWith("To ", true) }?.substring(3)?.trim()?.takeIf { it.isNotEmpty() }
		val parentOk = target != null && !recentlyFailed(target, nowMs) && renderable(target)
		val rootOk = t != norm(rootTitle) && !recentlyFailed(rootTitle, nowMs) && renderable(rootTitle)
		if (!parentOk && !rootOk) return Decision.Vanilla("no Apex parent can render (parent=${target ?: "?"}, $rootTitle unavailable)")
		chain += t; lastHop = nowMs
		return Decision.GoBack(back.slot, target)
	}
}

object ApexFallback {

	fun handle(screen: AbstractContainerScreen<*>, menuId: String, reason: String) {
		runCatching { Apex.logger.warn("[ApexFallback] $menuId -> vanilla (no auto-click): $reason; title=${screen.title.bareText}") }
	}

	@Suppress("UNUSED_PARAMETER")
	fun navigates(menuId: String) = false
}
