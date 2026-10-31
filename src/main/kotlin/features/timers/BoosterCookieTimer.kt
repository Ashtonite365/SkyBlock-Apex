// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic.Status
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object BoosterCookieTimer {
	var status: Status = Status.Unknown
		private set
	private val warner = BoosterCookieLogic.Warner()
	private val warnBelow get() = Timers.TConfig.cookieWarningMinutes.minutes

	private var fromMenu: Status = Status.Unknown
	private var fromMenuAt = 0L

	private val menusWithCookie = setOf("SkyBlock Menu", "Booster Cookie")

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = warner.reset()

	private fun menuStatusNow(): Status {
		val seen = fromMenu
		val left = (seen as? Status.Active)?.remaining ?: return seen
		val now = left - (System.currentTimeMillis() - fromMenuAt).milliseconds
		return if (now.isPositive()) Status.Active(now.toString(), now) else Status.Inactive
	}

	private fun tabLines(): List<String> {
		val tab = TabListReader.currentSnapshot
		return tab.body.map { it.bareText } + tab.footer.map { it.bareText }.orElse("").split("\n")
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0 || !SkyBlockSession.inSkyBlock) return
		status = BoosterCookieLogic.combine(BoosterCookieLogic.readStatus(tabLines()), menuStatusNow())
		val settings = Timers.TConfig
		val message = when (warner.update(status, warnBelow, settings.cookieWarning, settings.cookieExpiredWarning)) {
			null -> return
			BoosterCookieLogic.Warning.EXPIRED -> tr("apex.cookiewarning.expired", "§cYour Booster Cookie has expired!")
			BoosterCookieLogic.Warning.LOW -> tr("apex.cookiewarning.low",
				"§cYour Booster Cookie is running low: ${(status as? Status.Active)?.text} left.")
		}
		Client.showChat(message)
		if (settings.cookieWarningTitle) Alerts.show(message, 3.seconds)
	}

	@Subscribe
	fun onInventory(event: ChestInventoryUpdateEvent) {
		val title = (event.inventory as? AbstractContainerScreen<*>)?.title?.string?.trim() ?: return
		if (title !in menusWithCookie) return
		val slots = (event as? ChestInventoryUpdateEvent.Multi)?.contents ?: return
		val cookie = slots.firstOrNull { it.hoverName.string.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim() == "Booster Cookie" } ?: return
		val lore = runCatching { cookie.view().loreAccordingToNbt.map { it.bareText } }.getOrElse { emptyList() }
		val read = BoosterCookieLogic.readLore(lore)
		if (read == Status.Unknown) return
		fromMenu = read
		fromMenuAt = System.currentTimeMillis()
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!Timers.TConfig.cookieHud || !SkyBlockSession.inSkyBlock) return
		BoosterCookieLogic.statusLine(status)?.let { event.drawHudLines(Timers.TConfig.cookieHudPosition, listOf(Component.literal(it))) }
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		val s = status
		if (s == Status.Unknown) return
		val due = BoosterCookieLogic.isPending(s, warnBelow)
		val (text, left: Duration?) = when (s) {
			is Status.Active -> (if (due) "§c" else "§a") + s.text to s.remaining
			else -> "§cInactive" to null
		}
		event.add(Timers.TimerEntry("booster-cookie", "Booster Cookie", text, due, left))
	}
}
