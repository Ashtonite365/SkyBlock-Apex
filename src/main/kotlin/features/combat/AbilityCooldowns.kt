/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: item ability cooldowns on the shared Timers HUD (built on AbilityUtils). Display only: right clicks are
 * watched but never cancelled or sent. Pickaxe / drill abilities belong to PickaxeAbility, so they are skipped here.
 * The extra options (hotbar overlay, chicken head, fire freeze, fire veil, Crown of Avarice) are read by CombatPlusMerges.
 */
package dev.ashtonite365.apex.features.combat

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.UseItemEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.skyblock.AbilityUtils
import dev.ashtonite365.apex.util.skyblock.ItemType
import kotlin.time.Duration.Companion.milliseconds

@OptIn(NeedsComponents::class)
object AbilityCooldowns {
	@Config
	object TConfig : ManagedConfig("ability-cooldowns", Category.COMBAT) {
		val enabled by toggle("enabled") { true }
		val hotbarOverlay by toggle("hotbar-overlay") { false }
		val chickenHead by toggle("chicken-head") { false }
		val fireFreeze by toggle("fire-freeze") { false }
		val hideFireVeilParticles by toggle("hide-fire-veil-particles") { false }
		val crownOfAvarice by toggle("crown-of-avarice") { false }
		val extrasHud by position("extras-hud", 120, 30) { org.joml.Vector2i(200, 420) }
	}

	private val readyAt = linkedMapOf<String, Long>()
	private val ON_COOLDOWN = Regex("This ability is on cooldown for (\\d+)s\\.")
	private const val KEEP_EXPIRED_MS = 5000
	private var lastAbility: String? = null

	private fun pickAbility(abilities: List<AbilityUtils.ItemAbility>, sneaking: Boolean): AbilityUtils.ItemAbility? {
		val wanted = if (sneaking) AbilityUtils.AbilityActivation.SNEAK_RIGHT_CLICK else AbilityUtils.AbilityActivation.RIGHT_CLICK
		return abilities.firstOrNull { it.activation == wanted }
			?: abilities.firstOrNull { it.activation == AbilityUtils.AbilityActivation.RIGHT_CLICK }
	}

	@Subscribe
	fun onUse(event: UseItemEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val player = Client.player ?: return
		val item = event.item.view()
		when (ItemType.fromItemStack(item)) {
			ItemType.PICKAXE, ItemType.DRILL -> return
			else -> {}
		}
		val ability = pickAbility(AbilityUtils.abilitiesIn(item), player.isShiftKeyDown) ?: return
		val cooldown = ability.cooldown ?: return
		lastAbility = ability.name
		val now = System.currentTimeMillis()
		val ready = readyAt[ability.name] ?: 0L
		if (ready <= now) readyAt[ability.name] = now + cooldown.inWholeMilliseconds
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val m = ON_COOLDOWN.find(event.bareText) ?: return
		val name = lastAbility ?: return
		readyAt[name] = System.currentTimeMillis() + m.groupValues[1].toLong() * 1000
	}

	fun secondsLeft(abilityNames: List<String>): Long? {
		val now = System.currentTimeMillis()
		var latest: Long? = null
		for (n in abilityNames) {
			val at = readyAt[n] ?: continue
			if (latest == null || at > latest) latest = at
		}
		val secs = (latest ?: return null) - now + 999
		val whole = secs / 1000
		return if (whole > 0) whole else null
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		if (!TConfig.enabled) return
		val now = System.currentTimeMillis()
		readyAt.values.removeIf { it < now - KEEP_EXPIRED_MS }
		for ((name, at) in readyAt) event.cooldown("abilities", name, (at - now).milliseconds)
	}
}
