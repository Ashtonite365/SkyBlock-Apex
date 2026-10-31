/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Croesus menu helpers (options of the dungeon chest profit feature).
 * - Runs are tinted by state: green = no chest opened, yellow = some opened.
 * - A green "K" marks runs where a Kismet Feather was used (struck-through Kismet line).
 * - Reroll confirmation: the first click on "Reroll Chest" is swallowed with a hint; a second click within 3 s goes
 *   through. Nothing is ever clicked for the player.
 */
package dev.ashtonite365.apex.features.dungeon

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText
import java.util.Optional
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Inventory

object CroesusHelpers {
	enum class RunState { UNOPENED, PARTIAL, DONE }

	private const val UNOPENED_TINT = 0x6000FF00
	private const val PARTIAL_TINT = 0x60FFFF00

	fun runState(lore: List<String>): RunState? {

		if (lore.any { it.contains("No chests opened", ignoreCase = true) }) return RunState.UNOPENED
		if (lore.any { it.contains("No more Chests to open", ignoreCase = true) }) return RunState.DONE
		if (lore.any { it.trim().startsWith("Opened Chest:") }) return RunState.PARTIAL
		return null
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		val config = DungeonChestProfit.TConfig
		if (!config.croesusHighlightUnopened || Client.screenTitle != "Croesus") return
		val slot = event.slot
		if (slot.container is Inventory) return
		val loreLines = slot.item.view().loreAccordingToNbt
		if (config.croesusKismetMark && loreLines.any(::isUsedKismetLine))
			event.context.text(Client.font, "§aK", slot.x + 10, slot.y, -1, true)
		val tint = when (runState(loreLines.map { it.bareText.trim() })) {
			RunState.UNOPENED -> UNOPENED_TINT
			RunState.PARTIAL -> PARTIAL_TINT
			else -> return
		}
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint)
	}

	fun isUsedKismetLine(line: Component): Boolean {
		var found = false
		line.visit(FormattedText.StyledContentConsumer<Unit> { style, text ->
			found = found || (style.isStrikethrough && "Kismet Feather" in text)
			Optional.empty()
		}, Style.EMPTY)
		return found
	}

	private var firstClickAt = TimeMark.farPast()
	private val CONFIRM_WINDOW = 3.seconds

	@Subscribe
	fun onSlotProtect(event: IsSlotProtectedEvent) {
		if (!DungeonChestProfit.TConfig.rerollConfirm) return
		val slot = event.slot ?: return
		if (slot.container is Inventory || "Reroll Chest" !in slot.item.hoverName.bareText) return
		if (firstClickAt.passedTime() < CONFIRM_WINDOW) {
			firstClickAt = TimeMark.farPast()
			return
		}
		firstClickAt = TimeMark.now()
		event.protectSilent()
		Client.showChat(tr("apex.croesus.reroll-confirm", "§e[SkyBlock Apex] Click Reroll Chest again within 3 seconds to use a Kismet Feather."))
	}
}
