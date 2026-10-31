// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.misc

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object MiscPlusPorts {
	@Config
	object TConfig : ManagedConfig("plus-misc", Category.MISC) {
		val gfsPiggyBank by toggle("gfs-piggy-bank") { false }
		val chargeBottle by toggle("charge-bottle") { false }
	}

	private val PIGGY_BROKE = Regex("^You died(, lost [\\d.,]* coins)? and your piggy bank (cracked|broke)!$")
	private const val PORK_COMMAND = "/gfs ENCHANTED_PORK 8"

	fun isPiggyBreak(line: String) = PIGGY_BROKE.matches(line)

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.gfsPiggyBank || !SkyBlockSession.inSkyBlock || !isPiggyBreak(event.bareText.trim())) return
		val suggestion = Component.literal("§e[SkyBlock Apex] Piggy bank broke. §aClick to get 8 Enchanted Pork from your sacks.")
			.withStyle { style ->
				style.withClickEvent(ClickEvent.RunCommand(PORK_COMMAND)).withHoverEvent(HoverEvent.ShowText(Component.literal(PORK_COMMAND)))
			}
		Client.showChat(suggestion)
	}

	private val BOTTLES = linkedMapOf(
		"THUNDER_IN_A_BOTTLE" to "Thunder in a Bottle",
		"STORM_IN_A_BOTTLE" to "Storm in a Bottle",
		"HURRICANE_IN_A_BOTTLE" to "Hurricane in a Bottle",
	)

	fun chargeBottleMessage(ids: Collection<String>): String? {
		if (ids.any { it.endsWith("_IN_A_BOTTLE_EMPTY") }) return null
		val full = BOTTLES.entries.filter { it.key in ids }.map { it.value }
		if (full.isEmpty()) return null
		val verb = if (full.size == 1) "is" else "are"
		return "You are currently fishing, but ${full.joinToString(", ")} $verb full"
	}

	private var checkedAt = TimeMark.farPast()
	private val CHECK_EVERY = 10.seconds

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.chargeBottle || !SkyBlockSession.inSkyBlock || checkedAt.passedTime() < CHECK_EVERY) return
		val player = Client.player ?: return
		if (player.fishing == null) return
		checkedAt = TimeMark.now()
		val inventory = player.inventory
		val ids = ArrayList<String>()
		for (slot in 0 until inventory.containerSize) inventory.getItem(slot).view().repoItemId?.skyblockId?.let(ids::add)
		val note = chargeBottleMessage(ids) ?: return
		Client.showChat(Component.literal("§e[SkyBlock Apex] $note"))
	}
}
