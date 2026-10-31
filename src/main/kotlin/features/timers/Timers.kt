// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.util.render.drawHudLines

object Timers {
	@Config
	object TConfig : ManagedConfig("timers-overlay", Category.TIMERS) {

		val enabled by toggle("enabled") { false }
		val openCalendar by keyBindingWithDefaultUnbound("open-calendar")
		val onlyShowPending by toggle("only-pending") { false }
		val hud by position("hud", 160, 90) { Vector2i(5, 210) }
		val showCustom = toggle("show-custom") { true }

		val showGoldenFish = toggle("show-golden-fish") { true }

		val showJacobsContest = toggle("show-jacobs-contest") { true }

		val showEvents = toggle("show-events") { true }
		val showDarkAuction = toggle("show-dark-auction") { true }
		val showSpookyFestival = toggle("show-spooky-festival") { true }
		val showSeasonOfJerry = toggle("show-season-of-jerry") { true }
		val showNewYear = toggle("show-new-year") { true }
		val showTravelingZoo = toggle("show-traveling-zoo") { true }
		val showCake = toggle("show-cake") { true }
		val showJerry = toggle("show-jerry") { true }
		val showArachne = toggle("show-arachne") { true }

		val showPuzzler = toggle("show-puzzler") { true }
		val showFetchur = toggle("show-fetchur") { true }
		val showMithrilPowder = toggle("show-mithril-powder") { true }
		val showGemstonePowder = toggle("show-gemstone-powder") { true }
		val showPickaxeAbility = toggle("show-pickaxe-ability") { true }
		val showSkyMall = toggle("show-sky-mall") { true }
		val showMiningEvent = toggle("show-mining-event") { true }
		val showMineshaft = toggle("show-mineshaft") { true }

		val showSlayer = toggle("show-slayer") { true }
		val showSlayerQuest = toggle("show-slayer-quest") { true }
		val showSlayerBoss = toggle("show-slayer-boss") { true }
		val showSlayerPb = toggle("show-slayer-pb") { true }
		val slayerPbPace by toggle("slayer-pb-pace") { true }
		val slayerLastKill by toggle("slayer-last-kill") { true }

		val showRiftTime = toggle("show-rift-time") { true }
		val showInfusion = toggle("show-infusion") { true }

		val showCrimsonMinibosses = toggle("show-crimson-minibosses") { true }
		val showCrimsonQuests = toggle("show-crimson-quests") { true }
		val showHeavyPearls = toggle("show-heavy-pearls") { true }

		val showTreecapitator = toggle("show-treecapitator") { true }
		val showExperiments = toggle("show-experiments") { true }

		val showCookie = toggle("show-cookie") { true }
		val cookieWarning by toggle("cookie-warning") { true }
		val cookieWarningMinutes by integer("cookie-warning-minutes", 10, 5760) { 1440 }
		val cookieExpiredWarning by toggle("cookie-expired-warning") { true }
		val cookieWarningTitle by toggle("cookie-warning-title") { false }

		val cookieHud by toggle("cookie-hud") { true }
		val cookieHudPosition by position("cookie-hud-position", 120, 12) { org.joml.Vector2i(5, 196) }
		val showGodPotion = toggle("show-god-potion") { true }
	}

	val shownOptions: Map<String, ManagedOption<Boolean>> by lazy {
		with(TConfig) {
			listOf(showCustom, showGoldenFish, showJacobsContest, showEvents, showDarkAuction, showSpookyFestival,
			       showSeasonOfJerry, showNewYear, showTravelingZoo, showCake, showJerry, showArachne, showPuzzler,
			       showFetchur, showMithrilPowder, showGemstonePowder, showPickaxeAbility, showSkyMall, showMiningEvent,
			       showMineshaft, showSlayer, showSlayerQuest, showSlayerBoss, showSlayerPb, showRiftTime, showInfusion,
			       showCrimsonMinibosses, showCrimsonQuests, showHeavyPearls, showTreecapitator, showExperiments,
			       showCookie, showGodPotion)
		}.associateBy { it.propertyName }
	}

	data class TimerEntry(
		val id: String,
		val label: String,
		val status: String,
		val pending: Boolean,
		val remaining: Duration? = null,
	)

	data class TimerCollectEvent(val entries: MutableList<TimerEntry> = mutableListOf()) : ApexEvent() {
		fun add(entry: TimerEntry) = entries.add(entry)

		fun cooldown(id: String, label: String, remaining: Duration?) =
			add(TimerEntry(id, label,
			               if (remaining != null && remaining.isPositive()) "§a${ApexFormatters.timespanText(remaining)}" else "§cReady",
			               remaining == null || !remaining.isPositive(), remaining))

		companion object : ApexEventBus<TimerCollectEvent>()
	}

	fun isShown(id: String) = TimerCatalog.isShown(id) { key -> shownOptions[key]?.value ?: true }

	fun collect(): List<TimerEntry> =
		TimerCollectEvent.publish(TimerCollectEvent()).entries
			.filter { isShown(it.id) }
			.filter { !TConfig.onlyShowPending || it.pending }

	private fun containerOpen() =
		dev.ashtonite365.apex.features.misc.SolidMenus.TConfig.hideTimersInMenus && Client.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>

	fun routed(): TimerRouting.Routed =
		TimerRouting.route(TimerBoards.items(collect()), TimerBoards::showOn, TimerBoards.TConfig.inventoryIncludeBoard1)

	private fun boardStyle(panel: dev.ashtonite365.apex.util.hud.PanelStyle, scale: Double, opacity: Int, shadow: Boolean) = HudStyle.Override(
		backgroundOpacity = HudStyle.TConfig.backgroundOpacity * opacity / 100,
		textColour = TimerRouting.fade(HudStyle.textColour(), opacity),
		textShadow = shadow,
		scale = HudStyle.TConfig.scale / 100F * scale.toFloat(),
		panelStyle = panel,
	)

	private fun lines(items: List<TimerRouting.Item>, sort: BoardSort) = TimerRouting.sort(items, sort).map { Component.literal(it.text) }

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val b = TimerBoards.TConfig
		val routed = routed()

		if (TConfig.enabled && !(containerOpen() && b.inventoryIncludeBoard1))
			event.drawHudLines(TConfig.hud, lines(routed.board1, b.board1Sort),
				boardStyle(HudStyle.TConfig.timerStyle, b.board1Scale, b.board1Opacity, b.board1TextShadow))
		if (b.board2Enabled)
			event.drawHudLines(b.board2Position, lines(routed.board2, b.board2Sort),
				boardStyle(b.board2Background, b.board2Scale, b.board2Opacity, b.board2TextShadow))
		val ownStyle = boardStyle(HudStyle.TConfig.timerStyle, b.board1Scale, b.board1Opacity, b.board1TextShadow)
		for ((id, items) in routed.own) if (TimerRouting.needsNewOwnHud(id))
			event.drawHudLines(TimerBoards.ownHud(id), items.map { Component.literal(it.text) }, ownStyle)
	}

	@Subscribe
	fun onScreenRender(event: dev.ashtonite365.apex.events.ScreenRenderPostEvent) {
		if (!SkyBlockSession.inSkyBlock || !dev.ashtonite365.apex.features.misc.SolidMenus.TConfig.hideTimersInMenus) return
		val screen = event.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> ?: return
		val b = TimerBoards.TConfig
		val items = routed().inventory.let { if (TConfig.enabled || !b.inventoryIncludeBoard1) it else it.filter { i -> TimerBoards.showOn(i.id) != ShowOn.BOARD_1 } }
		val rows = items.map { val at = it.text.indexOf("§r: "); if (at < 0) SidePanel.Row(it.text) else SidePanel.Row(it.text.substring(0, at), it.text.substring(at + 4)) }
		if (rows.isEmpty()) return
		val palette = SidePanel.withOpacity(when (b.inventoryTheme) {
			InventoryTheme.FOLLOW_MENUS -> SidePanel.palette
			InventoryTheme.DARK -> SidePanel.DARK
			InventoryTheme.LIGHT -> SidePanel.LIGHT
		}, b.inventoryBackgroundOpacity)
		val scale = b.inventoryScale.toFloat()

		SidePanel.defer {
			val placed = when (val anchor = b.inventoryAnchor) {
				InventoryAnchor.FREE -> b.inventoryFreePosition.topLeft.let { SidePanel.placeAt("timers", screen, it.x(), it.y(), "§6Timers", rows, scale) }
				else -> {
					val side = when (anchor) {
						InventoryAnchor.RIGHT -> SidePanel.Side.RIGHT
						InventoryAnchor.TOP -> SidePanel.Side.TOP
						InventoryAnchor.BOTTOM -> SidePanel.Side.BOTTOM
						else -> SidePanel.Side.LEFT
					}
					SidePanel.place("timers", screen, side, "§6Timers", rows, userScale = scale)?.let { SidePanel.offset(it, b.inventoryOffsetX, b.inventoryOffsetY, screen) }
				}
			} ?: return@defer
			SidePanel.draw(event.drawContext, placed, pal = palette)
		}
	}
}
