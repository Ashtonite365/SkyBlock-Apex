/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): Arachne calling tracker, sack value tooltips/overlay, duplicate item UUID
 * highlighter and Thunder in a Bottle charge HUD. Ideas from feature descriptions only (see CREDITS.md). Original
 * implementation, no code copied. Arachne kill timer, minis name-tag hider, Broodmother alert and Arachne highlight
 * options: clean-room, logic in features/plus/combat/CombatPlusMerges.kt.
 */
package dev.ashtonite365.apex.features.misc

import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.bareText

object GapFeatures2 {
	@Config
	object TConfig : ManagedConfig("gap-extras", Category.MISC) {
		val arachne by toggle("arachne") { true }

		val arachneKillTimer by toggle("arachne-kill-timer") { false }
		val arachneHideMinisNametags by toggle("arachne-hide-minis-nametags") { false }
		val broodmotherAlert by toggle("broodmother-alert") { false }
		val arachneHighlight by riskyToggle("arachne-highlight", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val sackValue by toggle("sack-value") { true }
		val dupeHighlight by toggle("dupe-highlight") { true }
		val thunderBottle by toggle("thunder-bottle") { true }
		val thunderHud by position("thunder-hud", 100, 10) { Vector2i(5, 530) }
	}

	private val CALLING = Regex("^☄ .+ placed an Arachne's (?:Calling|Keeper Fragment)!.*\\((\\d)/4\\)$")
	private val AWAKENING = Regex(".*Something is awakening!.*")
	fun callingProgress(line: String) = CALLING.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()
	private var awakeAt: TimeMark? = null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.arachne || SkyBlockSession.skyblockIsland != SkyBlockIsland.SPIDER) return
		val line = event.bareText
		val p = callingProgress(line)
		if (p == 4 || AWAKENING.matches(line)) {
			if (awakeAt?.passedTime()?.let { it < 30.seconds } != true) Alerts.show(Component.literal("§cArachne is spawning!"), 3.seconds)
			awakeAt = TimeMark.now()
		} else if (p != null) Client.showChat(Component.literal("§7[SkyBlock Apex] Arachne calling: §e$p/4"))
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		val at = awakeAt ?: return
		if (at.passedTime() > 120.seconds) return
		event.add(Timers.TimerEntry("arachne", "Arachne summoned", "§c${ApexFormatters.timespanText(at.passedTime())} ago", true, null))
	}

	private val STORED = Regex("^Stored: ([0-9,]+)(?:/.*)?$")
	fun storedAmount(lore: List<String>) = lore.firstNotNullOfOrNull { STORED.matchEntire(it.trim())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }

	fun isSackScreen() = Client.screenTitle?.let { it.endsWith("Sack") || it.contains("Sack ") || it == "Sacks" } == true

	fun sackValue(stack: net.minecraft.world.item.ItemStack): Double? {
		val amount = storedAmount(stack.view().loreAccordingToNbt.map { it.bareText }) ?: return null
		if (amount <= 0) return null
		val id = stack.view().repoItemId ?: ItemNameLookup.guessItemByName(stack.hoverName.bareText, false) ?: return null
		return HypixelStaticData.getPriceOfItem(id)?.times(amount)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (TConfig.sackValue && isSackScreen()) sackValue(event.stack)?.let {
			event.lines.add(Component.literal("§7Stored value: §6${ApexFormatters.withThousands(it.toLong())} coins"))
		}
		if (TConfig.thunderBottle) thunderCharge(event.stack.view().loreAccordingToNbt.map { it.bareText })?.let { (c, m) ->
			event.lines.add(Component.literal("§7Charge: §b${"%.1f".format(c * 100.0 / m)}%"))
		}
	}

	fun duplicates(uuids: List<String?>): Set<String> = uuids.filterNotNull().groupingBy { it }.eachCount().filterValues { it > 1 }.keys

	private var dupeKey = 0
	private var dupeMemo: Set<String> = emptySet()
	private var dupeMenu: Any? = null
	private fun dupesFor(slots: List<net.minecraft.world.inventory.Slot>): Set<String> {
		var h = 1
		for (s in slots) h = h * 31 + System.identityHashCode(s.item)
		if (h == dupeKey && dupeMenu === slots) return dupeMemo
		dupeKey = h; dupeMenu = slots
		dupeMemo = duplicates(slots.map { it.item.view().itemUuid?.toString() })
		return dupeMemo
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!TConfig.dupeHighlight || !SkyBlockSession.inSkyBlock) return
		val screen = Client.containerScreen ?: return
		val uuid = event.slot.item.view().itemUuid?.toString() ?: return
		val dupes = dupesFor(screen.menu.slots)
		if (uuid in dupes) event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, 0x80FF0000.toInt())
	}

	private val CHARGE = Regex("^Charge: ([0-9,]+)/([0-9,.]+[kK]?).*")
	fun thunderCharge(lore: List<String>): Pair<Double, Double>? {
		val m = lore.firstNotNullOfOrNull { CHARGE.matchEntire(it.trim()) } ?: return null
		val c = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		val maxT = m.groupValues[2].replace(",", "")
		val max = if (maxT.endsWith("k", true)) maxT.dropLast(1).toDoubleOrNull()?.times(1000) else maxT.toDoubleOrNull()
		return if (max == null || max <= 0) null else c to max
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.thunderBottle || !SkyBlockSession.inSkyBlock) return
		val inv = Client.player?.inventory ?: return
		for (i in 0 until inv.containerSize) {
			val st = inv.getItem(i)
			if (!st.hoverName.bareText.contains("Thunder")) continue
			val (c, m) = thunderCharge(st.view().loreAccordingToNbt.map { it.bareText }) ?: continue
			event.drawHudLines(TConfig.thunderHud, listOf(Component.literal("§bThunder bottle: §f${"%.1f".format(c * 100.0 / m)}%")))
			return
		}
	}
}
