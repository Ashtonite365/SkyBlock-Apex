/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.foraging

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import net.minecraft.tags.BlockTags
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

object TreecapitatorCooldown {
	@Config
	object TConfig : ManagedConfig("treecapitator", Category.FORAGING) {

		val monkeyPet by toggle("monkey-pet") { false }
	}

	private val TREECAP = ItemId("TREECAPITATOR_AXE")
	private var lastUse: TimeMark? = null

	@Subscribe
	fun onAttack(event: AttackBlockEvent) {
		if (!Timers.isShown("treecapitator")) return
		if (event.player.mainHandItem.view().repoItemId != TREECAP) return
		if (!event.world.getBlockState(event.blockPos).`is`(BlockTags.LOGS)) return
		val last = lastUse
		if (last == null || last.passedTime() > cooldown()) lastUse = TimeMark.now()
	}

	private fun cooldown() = if (TConfig.monkeyPet) 1500.milliseconds else 2.seconds

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		val last = lastUse ?: return
		val left = cooldown() - last.passedTime()
		if (left.isPositive()) event.cooldown("treecapitator", "Treecapitator", left)
	}
}
