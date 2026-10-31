// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.ChatFilters
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.tr
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import org.joml.Vector2i

object FrozenTreasureTracker {
	@Config
	object TConfig : ManagedConfig("frozen-treasure", Category.EVENTS) {
		val enabled by toggle("enabled") { false }
		val showTotal by toggle("show-total") { false }
		val hideMessages by toggle("hide-messages") { false }
		val hud by position("hud", 160, 120) { Vector2i(10, 220) }

		init {
			ChatFilters.register(ChatFilters.Filter("frozen-treasure", { enabled && hideMessages && onWinter() }) { parse(it.trim()) != null })
		}
	}

	@Config
	object DConfig : ProfileSpecificDataHolder<ProfitTracker.Data>(serializer(), "frozen-treasure-tracker", { ProfitTracker.Data() })

	enum class Treasure(val id: String, val display: String, val amount: Int, val ice: Int = 0) {
		WHITE_GIFT("WHITE_GIFT", "White Gift", 1), GREEN_GIFT("GREEN_GIFT", "Green Gift", 1), RED_GIFT("RED_GIFT", "Red Gift", 1),
		PACKED_ICE("PACKED_ICE", "Packed Ice", 32, 9), ENCHANTED_ICE("ENCHANTED_ICE", "Enchanted Ice", 9, 160),
		ENCHANTED_PACKED_ICE("ENCHANTED_PACKED_ICE", "Enchanted Packed Ice", 1, 25600),
		ICE_BAIT("ICE_BAIT", "Ice Bait", 16), GLOWY_CHUM_BAIT("GLOWY_CHUM_BAIT", "Glowy Chum Bait", 16),
		GLACIAL_FRAGMENT("GLACIAL_FRAGMENT", "Glacial Fragment", 1), GLACIAL_TALISMAN("GLACIAL_TALISMAN", "Glacial Talisman", 1),
		FROZEN_BAIT("FROZEN_BAIT", "Frozen Bait", 1), EINARY_RED_HOODIE("EINARY_RED_HOODIE", "Einary's Red Hoodie", 1),
	}

	const val COMPACT = "COMPACT_ENCHANTED_ICE"
	private const val COMPACT_LINE = "COMPACT! You found an Enchanted Ice!"
	private const val ENCHANTED_ICE_VALUE = 160L

	fun parse(line: String): String? {
		if (line == COMPACT_LINE) return COMPACT
		if (!line.startsWith("FROZEN TREASURE! You found ") || !line.endsWith("!")) return null
		val name = line.removePrefix("FROZEN TREASURE! You found ").dropLast(1)
		return Treasure.entries.firstOrNull { it.display == name }?.name
	}

	fun estimatedIce(data: ProfitTracker.Data): Long {
		var ice = (data.items[COMPACT] ?: 0) * ENCHANTED_ICE_VALUE
		for (t in Treasure.entries) ice += (data.items[t.id] ?: 0) * t.ice.toLong()
		return ice
	}

	private fun onWinter() = SkyBlockSession.skyblockIsland == SkyBlockIsland.JERRY_WORKSHOP

	val tracker = ProfitTracker("Frozen Treasure Tracker", totalProvider = { DConfig.data ?: ProfitTracker.Data() }, onChange = { DConfig.markDirty() })

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled || !onWinter()) return
		val found = parse(event.bareText.trim()) ?: return
		if (found == COMPACT) {
			tracker.addItem(COMPACT, 1)
			return
		}
		val treasure = Treasure.valueOf(found)
		tracker.addItem(treasure.id, treasure.amount.toLong())
		tracker.addEvent()
	}

	private val names: Map<String, String> = buildMap {
		for (t in Treasure.entries) put(t.id, t.display)
		put(COMPACT, "Compact procs (Enchanted Ice)")
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !onWinter()) return
		tracker.mode = if (TConfig.showTotal) ProfitTracker.Mode.TOTAL else ProfitTracker.Mode.SESSION
		val data = tracker.current
		if (data.items.isEmpty()) return
		val lines = tracker.lines({ names[it] ?: it }).toMutableList()
		val header = "§6${ApexFormatters.withThousands(data.events)} Treasures Mined §7| §3${ApexFormatters.compactNumber(estimatedIce(data).toDouble())} Ice"
		lines.add(1, Component.literal(header))
		event.drawPlusLines(TConfig.hud, lines)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("resetfrozentreasure", ManagedConfig.Category.EVENTS) {
			onRun {
				tracker.reset()
				source.sendFeedback(tr("apex.frozen-treasure.reset", "Reset the Frozen Treasure tracker (current view)."))
			}
		}
	}
}
