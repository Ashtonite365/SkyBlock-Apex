/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.fishing

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.joml.Vector2i
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import net.minecraft.client.gui.screens.ChatScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.tracker.SessionTracker

object FishingTracker {
	enum class HudMode(val id: String) : StringRepresentable {
		SESSION("session"), LIFETIME("lifetime");

		override fun getSerializedName() = id
	}

	enum class TrophyAlert(val id: String) : StringRepresentable {
		OFF("off"), DIAMOND("diamond"), NEW_OR_DIAMOND("new-or-diamond"), ALL("all");

		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig("fishing-tracker", Category.FISHING) {
		val enabled by toggle("enabled") { true }
		val hud by position("hud", 160, 90) { Vector2i(0, 70) }
		val hudMode by choice("hud-mode") { HudMode.SESSION }
		val showSinceRare by toggle("since-rare") { true }
		val rareAlert by toggle("rare-alert") { true }
		val doubleHookAlert by toggle("double-hook-alert") { false }
		val trophyTracker by toggle("trophy-tracker") { true }
		val trophyHud by position("trophy-hud", 160, 90) { Vector2i(0, 170) }
		val trophyTotals by toggle("trophy-totals") { true }
		val trophyCountInChat by toggle("trophy-count-in-chat") { true }

		val trophyTotalInChat by toggle("trophy-total-in-chat") { false }
		val trophyHideBronzeAfter by integer("trophy-hide-bronze-after", 0, 1000) { 0 }
		val trophyHideSilverAfter by integer("trophy-hide-silver-after", 0, 1000) { 0 }
		val trophyAlert by choice("trophy-alert") { TrophyAlert.NEW_OR_DIAMOND }
		val sharkCounter by toggle("shark-counter") { true }
		val partyAnnounce by toggle("party-announce") { false }
	}

	@Serializable
	data class Data(
		val totals: MutableMap<String, Int> = mutableMapOf(),
		val sinceLast: MutableMap<String, Int> = mutableMapOf(),
		var doubleHooks: Int = 0,
		val trophies: MutableMap<String, Int> = mutableMapOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "fishing-tracker", ::Data)

	val session = SessionTracker(idleTimeout = 2.minutes, resetAfter = 10.minutes)
	private var pendingDoubleHook = false
	private val DOUBLE_HOOK = Regex("^It's a Double Hook!.*")
	val TROPHY = Regex("^. TROPHY FISH! You caught an? (.+?) (BRONZE|SILVER|GOLD|DIAMOND)!$")

	fun apply(data: Data, line: String): SeaCreatureKind? {
		if (DOUBLE_HOOK.matches(line)) {
			pendingDoubleHook = true; return null
		}
		TROPHY.matchEntire(line)?.let {
			data.trophies.merge("${it.groupValues[1]} ${it.groupValues[2]}", 1, Int::plus); return null
		}
		val sc = SeaCreatureBook.caughtBy(line) ?: return null
		val count = if (pendingDoubleHook && sc.doubleHookable) 2 else 1
		if (count == 2) data.doubleHooks++
		pendingDoubleHook = false
		data.totals.merge(sc.name, count, Int::plus)
		for (rare in SeaCreatureBook.rareKinds) if (rare != sc && rare.name in data.totals)
			data.sinceLast.merge(rare.name, count, Int::plus)
		if (sc.rare) data.sinceLast[sc.name] = 0
		session.add("Sea creatures", count.toDouble())
		session.add(sc.displayName, count.toDouble())
		if (count == 2) session.add("§bDouble hooks")
		return sc
	}

	private val SHARKS = setOf("Nurse Shark", "Blue Shark", "Tiger Shark", "Great White Shark")
	private val TIER_COLOUR = mapOf("BRONZE" to "§8", "SILVER" to "§7", "GOLD" to "§6", "DIAMOND" to "§b")

	fun trophyTotal(data: Data, fish: String) =
		data.trophies.entries.filter { it.key.substringBeforeLast(' ') == fish }.sumOf { it.value }

	@Subscribe
	fun onModifyChat(event: ModifyChatEvent) {
		if (!TConfig.enabled || !TConfig.trophyTracker || !(TConfig.trophyCountInChat || TConfig.trophyTotalInChat)) return
		val m = TROPHY.matchEntire(event.bareText.trim()) ?: return

		val data = DConfig.data
		val n = (data.trophies["${m.groupValues[1]} ${m.groupValues[2]}"] ?: 0) + 1
		var out = event.replaceWith.copy()
		if (TConfig.trophyCountInChat) out = out.append(Component.literal(" §7(#$n)"))
		if (TConfig.trophyTotalInChat) out = out.append(Component.literal(" §7(${trophyTotal(data, m.groupValues[1]) + 1} total)"))
		event.replaceWith = out
	}

	@Subscribe
	fun onAllowChat(event: dev.ashtonite365.apex.events.AllowChatEvent) {
		if (!TConfig.enabled || !TConfig.trophyTracker) return
		val m = TROPHY.matchEntire(event.bareText.trim()) ?: return
		val have = DConfig.data.trophies["${m.groupValues[1]} ${m.groupValues[2]}"] ?: 0
		if (dev.ashtonite365.apex.features.plus.fishing.PlusFishingMerges.hideTrophy(m.groupValues[2], have, TConfig.trophyHideBronzeAfter, TConfig.trophyHideSilverAfter))
			event.cancel()
	}

	private fun trophyAlert(data: Data, fish: String, tier: String) {
		val count = data.trophies["$fish $tier"] ?: 0
		val show = when (TConfig.trophyAlert) {
			TrophyAlert.OFF -> false
			TrophyAlert.DIAMOND -> tier == "DIAMOND"
			TrophyAlert.NEW_OR_DIAMOND -> tier == "DIAMOND" || count == 1
			TrophyAlert.ALL -> true
		}
		if (!show) return
		val prefix = if (count == 1) "§a§lNEW " else ""
		Alerts.show(Component.literal("$prefix${TIER_COLOUR[tier] ?: ""}§l$tier §f$fish"), 2.seconds, sound = tier == "DIAMOND" || count == 1)
	}

	private fun announceToParty(sc: SeaCreatureKind, double: Boolean) {

		val text = "/pc ${if (double) "Double " else ""}${sc.name} caught!"
		Client.game.execute { Client.screen = ChatScreen(text, false) }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		val data = DConfig.data
		val line = event.bareText.trim()
		val dh = pendingDoubleHook
		val sc = apply(data, line)
		if (sc == null && !DOUBLE_HOOK.matches(line) && !TROPHY.matches(line)) return
		DConfig.markDirty()
		TROPHY.matchEntire(line)?.let { trophyAlert(data, it.groupValues[1], it.groupValues[2]) }
		if (sc != null && sc.name in SHARKS) session.add("§9Sharks")
		if (sc != null && sc.rare && TConfig.partyAnnounce && Client.screen == null) announceToParty(sc, dh && sc.doubleHookable)
		if (sc != null && sc.rare && TConfig.rareAlert)
			Alerts.show(Component.literal("§l" + sc.displayName + if (dh && sc.doubleHookable) " §c§lX2" else ""), 3.seconds)
		else if (sc != null && dh && TConfig.doubleHookAlert)
			Alerts.show(Component.literal("§b§lDOUBLE HOOK! " + sc.displayName), 2.seconds)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		val data = DConfig.data
		val lines = mutableListOf<Component>()
		when (TConfig.hudMode) {
			HudMode.SESSION -> if (!session.isEmpty) {
				lines.add(Component.literal("§b§lSea Creatures §7(session)"))
				lines.addAll(session.lines().filter { TConfig.sharkCounter || !it.string.contains("Sharks:") })
			}

			HudMode.LIFETIME -> if (data.totals.isNotEmpty()) {
				lines.add(Component.literal("§b§lSea Creatures §7(total)"))
				data.totals.entries.sortedByDescending { it.value }.take(12).forEach { (name, n) ->
					val colour = SeaCreatureBook.named(name)?.colour ?: "§f"
					lines.add(Component.literal("$colour$name: §f${ApexFormatters.withThousands(n.toLong())}"))
				}
				lines.add(Component.literal("§bDouble hooks: §f${data.doubleHooks}"))
			}
		}
		if (TConfig.showSinceRare && data.sinceLast.isNotEmpty()) {
			lines.add(Component.literal("§7Catches since last:"))
			data.sinceLast.forEach { (name, n) ->
				val colour = SeaCreatureBook.named(name)?.colour ?: "§f"
				lines.add(Component.literal(" $colour$name: §f$n"))
			}
		}
		if (lines.isNotEmpty()) event.drawHudLines(TConfig.hud, lines)
		if (TConfig.trophyTracker && data.trophies.isNotEmpty()) {
			val tier = mapOf("BRONZE" to "§8", "SILVER" to "§7", "GOLD" to "§6", "DIAMOND" to "§b")
			val t = mutableListOf<Component>(Component.literal("§6§lTrophy Fish"))
			if (TConfig.trophyTotals) {
				data.trophies.keys.map { it.substringBeforeLast(' ') }.distinct().sorted().forEach { fish ->
					val parts = listOf("BRONZE", "SILVER", "GOLD", "DIAMOND").joinToString("§7/") { tierName ->
						"${tier[tierName]}${data.trophies["$fish $tierName"] ?: 0}"
					}
					t.add(Component.literal("§f$fish: $parts §7= §f${trophyTotal(data, fish)}"))
				}
				t.add(Component.literal("§6Total (Odger): §f${data.trophies.values.sum()}"))
			} else data.trophies.entries.sortedBy { it.key }.forEach { (k, n) ->
				val tierName = k.substringAfterLast(' ')
				t.add(Component.literal("§f${k.substringBeforeLast(' ')} ${tier[tierName] ?: ""}$tierName§7: §f$n"))
			}
			event.drawHudLines(TConfig.trophyHud, t)
		}
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("resetfishing", dev.ashtonite365.apex.util.data.ManagedConfig.Category.FISHING) {
			onRun {
				session.reset()
				source.sendFeedback(tr("apex.fishing.reset", "Reset the fishing session tracker."))
			}
		}
	}
}
