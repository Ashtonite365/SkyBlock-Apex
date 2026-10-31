// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object DungeonStatusLogic {
	enum class Blessing(val label: String) { POWER("Power"), TIME("Time"), STONE("Stone"), LIFE("Life"), WISDOM("Wisdom") }

	private val blessingLine = Regex("Blessing of (Power|Time|Stone|Life|Wisdom) ([IVXLC]+)")
	private val breakerCharges = Regex("Charges: (\\d+)/(\\d+)⸕")
	private val cooldownLine = Regex("^Cooldown: (\\d+)s$")
	private val numeral = intArrayOf(1, 5, 10, 50, 100)
	private const val NUMERAL_LETTERS = "IVXLC"

	fun romanToInt(s: String): Int {
		val values = IntArray(s.length)
		for ((i, ch) in s.withIndex()) {
			val k = NUMERAL_LETTERS.indexOf(ch)
			if (k < 0) return 0
			values[i] = numeral[k]
		}
		var sum = 0
		for (i in values.indices) {
			val v = values[i]
			sum += if (i + 1 < values.size && v < values[i + 1]) -v else v
		}
		return sum
	}

	fun parseBlessings(footer: String): Map<Blessing, Int> {
		val out = LinkedHashMap<Blessing, Int>()
		for (m in blessingLine.findAll(footer)) out[Blessing.valueOf(m.groupValues[1].uppercase())] = romanToInt(m.groupValues[2])
		return out
	}

	fun parseCharges(lore: List<String>): Pair<Int, Int>? {
		for (line in lore) {
			val m = breakerCharges.find(line) ?: continue
			return m.groupValues[1].toInt() to m.groupValues[2].toInt()
		}
		return null
	}

	enum class Invincibility(val label: String, val regex: Regex, val activeTicks: Int, val cooldownSeconds: Int) {
		SPIRIT("Spirit Mask", Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"), 60, 30),
		BONZO("Bonzo's Mask", Regex("^Your (?:. )?Bonzo's Mask saved your life!$"), 60, 180),
		PHOENIX("Phoenix", Regex("^Your Phoenix Pet saved you from certain death!$"), 80, 60);

		var active = 0; private set
		var cooldown = 0; private set

		fun proc(cooldownSecondsOverride: Int? = null) {
			active = activeTicks
			cooldown = 20 * (cooldownSecondsOverride ?: cooldownSeconds)
		}

		fun tick() {

			if (active > 0) active -= 1
			if (cooldown > 0) cooldown -= 1
		}

		fun reset() {
			active = 0
			cooldown = 0
		}

		companion object {
			fun match(line: String): Invincibility? = entries.find { it.regex.matches(line) }
		}
	}

	fun loreCooldown(lore: List<String>): Int? {
		for (i in lore.indices.reversed()) {
			val v = cooldownLine.matchEntire(lore[i])?.groupValues?.get(1)?.toIntOrNull()
			if (v != null) return v
		}
		return null
	}
}

object DungeonStatusHuds {
	val identifier: String
		get() = "dungeon-status-huds"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val blessings by toggle("blessings") { true }
		val blessingsHud by position("blessings-hud", 80, 50) { Vector2i(4, 260) }
		val breaker by toggle("breaker") { true }
		val breakerShowFull by toggle("breaker-show-full") { false }
		val breakerHud by position("breaker-hud", 50, 10) { Vector2i(4, 320) }
		val invincibility by toggle("invincibility") { true }
		val invincibilityHud by position("invincibility-hud", 90, 30) { Vector2i(4, 340) }
		val invincibilityAlert by toggle("invincibility-alert") { true }
	}

	private var blessingLevels: Map<DungeonStatusLogic.Blessing, Int> = emptyMap()
	private var breakerCharges: Pair<Int, Int>? = null

	private fun footerText() = TabListReader.currentSnapshot.footer.map { it.bareText }.orElse("")

	private fun findCharges(inv: net.minecraft.world.entity.player.Inventory): Pair<Int, Int>? {
		for (slot in 0 until inv.containerSize) {
			val stack = inv.getItem(slot)
			if (stack.view().repoItemId?.skyblockId != "DUNGEONBREAKER") continue
			return DungeonStatusLogic.parseCharges(stack.view().loreAccordingToNbt.map { it.bareText })
		}
		return null
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		for (it in DungeonStatusLogic.Invincibility.entries) it.tick()
		if (!DungeonUtil.isInDungeonIsland) return
		if (event.tickCount % 10 != 0) return
		if (TConfig.blessings) blessingLevels = DungeonStatusLogic.parseBlessings(footerText())
		if (!TConfig.breaker) return
		val inv = Client.player?.inventory ?: return
		breakerCharges = findCharges(inv)
	}

	private fun bonzoCooldown(): Int? {
		val head = Client.player?.getItemBySlot(EquipmentSlot.HEAD) ?: return null
		return DungeonStatusLogic.loreCooldown(head.view().loreAccordingToNbt.map { it.bareText })
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		val item = DungeonStatusLogic.Invincibility.match(event.bareText.trim()) ?: return
		item.proc(if (item == DungeonStatusLogic.Invincibility.BONZO) bonzoCooldown() else null)
		if (!TConfig.invincibilityAlert) return
		Client.hud.setTitle(Component.literal("§c${item.label}"))
		Client.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
	}

	private fun timerText(item: DungeonStatusLogic.Invincibility): String = when {
		item.active > 0 -> "§6" + String.format("%.1f", item.active / 20f) + "s"
		item.cooldown > 0 -> "§c" + String.format("%.1f", item.cooldown / 20f) + "s"
		else -> "§a✔"
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		if (TConfig.blessings && blessingLevels.isNotEmpty()) {
			val lines = DungeonStatusLogic.Blessing.entries.mapNotNull { b -> blessingLevels[b]?.let { Component.literal("§6${b.label} §a$it") } }
			event.drawHudLines(TConfig.blessingsHud, lines)
		}
		val ch = breakerCharges
		if (ch != null && TConfig.breaker && ch.second > 0 && (TConfig.breakerShowFull || ch.first < ch.second))
			event.drawHudLines(TConfig.breakerHud, listOf(Component.literal("§e${ch.first}§7/§e${ch.second}§c⸕")))
		if (TConfig.invincibility)
			event.drawHudLines(TConfig.invincibilityHud, DungeonStatusLogic.Invincibility.entries.map { Component.literal("§7${it.label}: ${timerText(it)}") })
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		for (it in DungeonStatusLogic.Invincibility.entries) it.reset()
		blessingLevels = emptyMap()
		breakerCharges = null
	}
}
