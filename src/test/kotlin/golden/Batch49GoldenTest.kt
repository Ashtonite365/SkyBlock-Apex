// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.chat.AutoCompletions
import dev.ashtonite365.apex.features.plus.commands.PlusCommandExtras
import dev.ashtonite365.apex.features.plus.visualwords.PlusLoreTweaks
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch49GoldenTest {
	private fun stack(kind: String): ItemStack {
		val st = ItemStack(Items.PAPER)
		val tag = CompoundTag()
		when (kind) {
			"memento", "memento-earned" -> { tag.putString("id", "WIZARD_PORTAL_MEMENTO"); tag.putString("recipient_name", "Bob") }
			"memento-noname" -> tag.putString("id", "WIZARD_PORTAL_MEMENTO")
			"other" -> { tag.putString("id", "HYPERION"); tag.putString("recipient_name", "Bob") }
		}
		if (!tag.isEmpty) st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
		return st
	}

	@Test
	fun lore() {
		val S = Batch49Lore
		val L = PlusLoreTweaks
		val out = mutableListOf<Pair<String, String>>()
		out += "icons" to L.statIcons.entries.joinToString()
		for (x in S.romans) out += "r2i|$x" to Golden.run { "${L.romanToInt(x)}" }
		for (n in S.ints) out += "i2r|$n" to Golden.run { L.intToRoman(n) }
		for (t in S.romanTexts) out += "rt|$t" to Golden.run { L.replaceRoman(t) }
		for (t in S.hexTexts) out += "hex|$t" to Golden.run { "${L.findHexColours(t)}" }
		for (t in S.statLines) for (strip in listOf(false, true)) out += "stat|$strip|$t" to Golden.run { "${L.statIconReplacement(t, strip)}" }
		for (t in S.chimera) out += "chim|$t" to Golden.run { "${L.chimeraReplacement(t)}" }
		for (n in -1..120) out += "chimN|$n" to "${L.newChimeraValue(n)}"
		for (d in listOf("2026-04-01", "2026-04-02", "2025-03-01", "2024-04-01", "2026-01-04")) out += "af|$d" to "${L.isAprilFools(java.time.LocalDate.parse(d))}"
		val keys = listOf("roman-numerals", "hex-colours", "stat-icons", "wizard-portal-lore", "stat-icons-strip-units", "chimera-fix")
		for (loc in listOf(Harness.skyblock("hub"), null)) for (mask in 0 until 64) {
			if (loc == null && mask % 7 != 0) continue
			Harness.withOpts(PlusLoreTweaks.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
				Harness.at(loc) {
					for ((i, lore) in S.lore.withIndex()) {
						if ((i + mask) % 4 != 0) continue
						val item = S.items[(i + mask) % S.items.size]
						val lines = lore.map { (t, c) ->
							val base = Component.literal(t)
							when {
								c == null -> base
								c.startsWith("#") -> base.withStyle { it.withColor(TextColor.parseColor(c).getOrThrow()) }
								else -> base.withStyle(ChatFormatting.getByCode(c[0])!!)
							}
						}.toMutableList<Component>()
						if (item == "memento-earned") lines.add(Component.literal("Earned by: Someone"))
						val before = lines.toList()
						val ev = ItemTooltipEvent(stack(item), Item.TooltipContext.EMPTY, TooltipFlag.NORMAL, lines)
						val res = Golden.run { L.onTooltip(ev); "ok" }
						out += "tip|${loc != null}|$mask|$i|$item" to "$res ${ev.lines.indices.map { ev.lines[it] === before.getOrNull(it) }} ${ev.lines.joinToString(" / ") { Harness.dump(it) }}"
					}
				}
			}
		}
		val n = Golden.verify("batch49-lore", out, minCases = 9744)
		assertTrue(n >= 9744)
	}

	@Test
	fun commands() {
		val S = Batch49Lore
		val C = PlusCommandExtras
		val out = mutableListOf<Pair<String, String>>()
		for (c in S.viewRecipes) out += "vr|$c" to Golden.run { "${C.fixViewRecipe(c)}" }
		C.handleStorage("ec 1")
		for (c in S.storage) out += "st|$c" to Golden.run { "${C.handleStorage(c)} ${C.lastStorage}" }
		out += "types" to PlusCommandExtras.StorageType.entries.joinToString { "$it ${it.pages} ${it.commands} ${it.target}" }
		for (c in S.cooldown) out += "cd|$c" to "${C.cooldownPattern.matcher(c).matches()}"
		val keys = listOf("prevent-early-commands", "view-recipe-lowercase", "open-last-storage")
		var tick = 0
		for (loc in listOf(Harness.skyblock("hub"), null)) for (mask in 0 until 8) {
			Harness.withOpts(AutoCompletions.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
				Harness.at(loc) {
					C.onWorld(WorldReadyEvent()); C.onTick(TickEvent(tick))
					for ((i, step) in S.cmdSteps.withIndex()) {
						val kind = step.substringBefore(':'); val arg = step.substringAfter(':')
						var res = ""
						val chat = Harness.chat {
							res = Golden.run {
								when (kind) {
									"cmd" -> OutgoingPacketEvent(ServerboundChatCommandPacket(arg)).also { C.onOutgoing(it) }.cancelled.toString()
									"chat" -> ProcessChatEvent(Component.literal(arg), false).also { C.onChat(it) }.cancelled.toString()
									"tick" -> { tick += arg.toInt(); C.onTick(TickEvent(tick)); "t" }
									else -> { C.onWorld(WorldReadyEvent()); "w" }
								}
							}
						}
						out += "ev|${loc != null}|$mask|$i|$step" to "$res ${C.lastStorage} $chat"
					}
				}
			}
		}
		val n = Golden.verify("batch49-commands", out, minCases = 11838)
		assertTrue(n >= 11838)
	}
}
