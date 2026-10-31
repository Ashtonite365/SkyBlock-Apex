// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.skyblock

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import kotlin.math.abs
import net.minecraft.network.chat.Component

object Ragnarock {
	@Config
	object TConfig : ManagedConfig("ragnarock-alert", Category.COMBAT) {
		val castAlert by toggle("cast-alert") { false }
		val cancelAlert by toggle("cancel-alert") { false }
		val strengthGained by toggle("strength-gained") { false }
	}

	object Logic {
		val cancelRegex = Regex("Ragnarock was cancelled due to (?:being hit|taking damage)!")
		private val STRENGTH = Regex("Strength: \\+([\\d,]+)")

		const val CAST_PITCH = 1.4920635f

		fun isCancel(message: String): Boolean = cancelRegex.matches(message)

		fun strengthFromLore(lore: List<String>): Int? {
			for (line in lore) {
				val m = STRENGTH.find(line) ?: continue
				return m.groupValues[1].replace(",", "").toIntOrNull()
			}
			return null
		}

		fun gained(strength: Int): Int = (strength * 1.5).toInt()

		fun isCastSound(path: String, pitch: Float): Boolean {
			if (!path.startsWith("entity.wolf") || !path.endsWith("death")) return false
			return abs(pitch - CAST_PITCH) < 1e-4f
		}
	}

	private fun showTitle(text: String) = Client.hud.setTitle(Component.literal(text))

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.cancelAlert) return
		if (Logic.isCancel(event.bareText)) showTitle("§cRagnarock Cancelled!")
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		val wantCast = TConfig.castAlert
		val wantStrength = TConfig.strengthGained
		if (!wantCast && !wantStrength) return
		val held = Client.mainHandStack
		if (held.view().repoItemId?.skyblockId != "RAGNAROCK_AXE") return
		if (!Logic.isCastSound(event.sound.value().location().path, event.pitch)) return
		if (wantCast) showTitle("§aCasted Rag")
		if (!wantStrength) return
		val strength = Logic.strengthFromLore(held.view().loreAccordingToNbt.map { it.bareText }) ?: return

		Client.showChat(Component.literal("§7Gained strength: §4${Logic.gained(strength)}"))
	}
}
