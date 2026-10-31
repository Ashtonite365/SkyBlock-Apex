// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.mining

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.mining.Histogram
import dev.ashtonite365.apex.features.mining.HotmPresets
import dev.ashtonite365.apex.features.mining.PickaxeAbility
import dev.ashtonite365.apex.features.mining.PristineProfitTracker
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.ShareCodes
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.skyblock.ItemType

class MiningEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun r(d: Double?) = d?.let { "%.2f".format(it) }
	private fun s(d: Duration) = (d.inWholeMilliseconds + 500) / 1000

	@Test
	fun histogramBehavesTheSame() {

		var now = TimeMark.now()
		val clock = { now }
		fun tick() { now = now.after(1.milliseconds) }
		fun ago(d: Duration) = now.before(d)
		fun age(m: TimeMark) = m.passedAt(now)
		val out = mutableListOf<String>()
		val h = Histogram<Double>(5, 60.seconds, clock)
		out += "empty size=${h.size} avg=${h.averagePer({ it }, 1.seconds)} agg=${h.aggregate(0, { a, _, _ -> a + 1 }, { a, _, _ -> a })} oldest=${s(age(h.oldestUpdate()))} latest=${h.latestUpdate() == TimeMark.farPast()}"
		for ((v, back) in listOf(1.0 to 50, 2.0 to 40, 4.0 to 30, 8.0 to 20, 16.0 to 10, 32.0 to 5)) {
			tick()
			h.record(v, ago(back.seconds))
			out += "rec $v@$back size=${h.size} avg=${r(h.averagePer({ it }, 1.seconds))} avg10=${r(h.averagePer({ it }, 10.seconds))} oldest=${s(age(h.oldestUpdate()))} latest=${s(age(h.latestUpdate()))}"
		}
		out += "agg ${h.aggregate("", { a, v, _ -> "$a,$v" }, { a, b, e -> "$a ${s(e - b)}" })}"
		tick()
		h.record(64.0, ago(90.seconds))
		out += "old record size=${h.size}"
		val same = ago(3.seconds)
		val t = Histogram<String>(10, 60.seconds, clock)
		for (x in listOf("a", "b", "c")) t.record(x, same)
		out += "ties ${t.aggregate("", { a, v, _ -> a + v }, { a, _, _ -> a })} size=${t.size}"
		val single = Histogram<Double>(10, 60.seconds, clock)
		single.record(5.0, ago(2.seconds))
		out += "single avg=${single.averagePer({ it }, 1.seconds)}"
		val zero = Histogram<Double>(0, 60.seconds, clock)
		zero.record(1.0)
		out += "zero size=${zero.size}"
		val past = Histogram<Double>(10, 1.milliseconds, clock)
		past.record(1.0, TimeMark.farPast())
		out += "farPast size=${past.size}"
		OursGolden.check("mining-histogram", out)
	}

	@Test
	fun histogramSpanBoundaryIsExact() {

		val now = TimeMark.now()
		val h = Histogram<Double>(5, 60.seconds) { now }
		h.record(1.0, now.before(50.seconds))
		h.record(2.0, now.before(40.seconds))
		org.junit.jupiter.api.Assertions.assertNull(h.averagePer({ it }, 10.seconds))
		h.record(4.0, now.before(50.seconds).before(1.milliseconds))
		org.junit.jupiter.api.Assertions.assertEquals(7.0 / 1.0001, h.averagePer({ it }, 10.seconds)!!, 1e-9)
	}

	@Test
	fun pristineParsingIsTheSame() {
		val out = mutableListOf<String>()
		out += "id ${PristineProfitTracker.identifier} pattern=${PristineProfitTracker.pristineRegex.pattern()}"
		out += "consts ${PristineProfitTracker.SECONDS_PER_HOUR} ${PristineProfitTracker.ROUGHS_PER_FLAWED} ${PristineProfitTracker.FLAWED_PER_FINE} ${PristineProfitTracker.ROUGHS_PER_FINE} ${PristineProfitTracker.sellingStrategy}"
		for (k in PristineProfitTracker.GemstoneKind.entries) out += "kind ${k.name} ${k.label} ${k.flawedId} ${k.fineId}"
		for (line in listOf("PRISTINE! You found ❈ Flawed Ruby Gemstone x2!", "PRISTINE! You found ❈ Flawed Onyx Gemstone x1,234!",
			"PRISTINE! You found  Flawed Ruby Gemstone x2!", "PRISTINE! You found ❈ Fine Ruby Gemstone x2!", "PRISTINE! You found ❈ Flawed Jasper Gemstone x10!")) {
			val m = PristineProfitTracker.pristineRegex.matcher(line)
			out += "line '$line' -> ${if (m.matches()) m.group("kind") + "/" + m.group("count") else "-"}"
		}
		out += "data ${PristineProfitTracker.BestRates()}"
		OursGolden.check("mining-pristine", out)
	}

	private fun chat(s: String) = PickaxeAbility.onChatMessage(ProcessChatEvent(Component.literal(s), false))

	@Test
	fun pickaxeAbilityTracksTheSame() {
		val out = mutableListOf<String>()
		val p = PickaxeAbility
		out += "id ${p.identifier} types=${p.pickaxeTypes.map { it.name }.sorted()}"
		for (t in PickaxeAbility.ShowOnTools.entries)
			out += "tools ${t.name} ${t.serializedName} ${t.wireName} ${listOf("SWORD", "DRILL", "PICKAXE", "AXE", "GAUNTLET", "SHOVEL", "HOE", "ROD", "__NIL").filter { t.matches(ItemType.ofName(it)) }}"
		out += "defaults ${p.defaultAbilityDurations}"
		for ((name, pat) in listOf("use" to p.abilityUsePattern, "fuel" to p.fuelPattern, "cd" to p.pickaxeAbilityCooldownPattern,
			"now" to p.nowAvailable, "lore" to p.cooldownPattern, "switch" to p.abilitySwitchPattern))
			out += "pattern $name ${pat.pattern()}"
		val lastUsage = p.lastUsage
		val saved = HashMap(lastUsage); val savedOverride = p.abilityOverride; val savedDefaults = HashMap(p.defaultAbilityDurations); val savedJoin = p.lobbyJoinTime
		try {
			lastUsage.clear(); p.abilityOverride = null

			@Suppress("UNCHECKED_CAST")
			val cfg = PickaxeAbility.TConfig.sortedOptions as List<dev.ashtonite365.apex.gui.config.ManagedOption<Any>>
			val cfgBefore = cfg.map { it._actualValue }
			PickaxeAbility.TConfig.clear()
			fun state(label: String) {
				out += "$label override=${p.abilityOverride} usage=${lastUsage.entries.sortedBy { it.key }.map { "${it.key}:${if (it.value == TimeMark.farPast()) "far" else s(it.value.passedTime()).toString()}" }}"
			}
			for (line in listOf(
				"You used your Mining Speed Boost Pickaxe Ability!",
				"Your pickaxe ability is on cooldown for 30s.",
				"You selected Pickobulus as your Pickaxe Ability. This ability will apply to all of your pickaxes!",
				"Your pickaxe ability is on cooldown for 1m.",
				"Your pickaxe ability is on cooldown for 5x.",
				"Mining Speed Boost is now available!",
				"You used your Unknown Thing Pickaxe Ability!",
				"Your pickaxe ability is on cooldown for 10s.",
				"Pickobulus is now available!",
				"random chat",
			)) {
				val r = runCatching { chat(line) }.exceptionOrNull()?.javaClass?.simpleName ?: "ok"
				state("chat '$line' $r")
			}
			cfg.forEachIndexed { i, o -> o._actualValue = cfgBefore[i] }
			lastUsage.clear()
			lastUsage["A"] = TimeMark.ago(30.seconds)
			lastUsage["B"] = TimeMark.farPast()
			for ((n, cd) in listOf("A" to 60.seconds, "A" to 10.seconds, "B" to 60.seconds, "C" to 60.seconds, "A" to Duration.ZERO))
				out += "pct $n $cd ${r(p.getCooldownPercentage(n, cd))}"
			p.lobbyJoinTime = TimeMark.ago(20.seconds)
			p.profileChanged(dev.ashtonite365.apex.events.ProfileSwitchEvent(null, null))
			state("profileSwitch")
			p.newLobby(dev.ashtonite365.apex.events.WorldReadyEvent())
			out += "worldReady override=${p.abilityOverride} join=${s(p.lobbyJoinTime.passedTime())}"
			out += "data ${PickaxeAbility.PickaxeAbilityData("X", 5.seconds)}"
		} finally {
			lastUsage.clear(); lastUsage.putAll(saved); p.abilityOverride = savedOverride
			p.defaultAbilityDurations.clear(); p.defaultAbilityDurations.putAll(savedDefaults); p.lobbyJoinTime = savedJoin
		}
		OursGolden.check("mining-pickaxe", out)
	}

	@Test
	fun hotmPresetFormatIsTheSame() {
		val out = mutableListOf<String>()
		out += "prefix ${HotmPresets.SHARE_PREFIX} inv=${HotmPresets.hotmInventoryName} tier=${HotmPresets.tierRegex.pattern()}"
		val preset = HotmPresets.HotmPreset(listOf(HotmPresets.PerkPreset("Mining Speed"), HotmPresets.PerkPreset("Pickobulus")))
		val enc = ShareCodes.toShareCode(HotmPresets.SHARE_PREFIX, preset)
		out += "encode $enc"
		out += "decode ${ShareCodes.fromShareCode<HotmPresets.HotmPreset>(HotmPresets.SHARE_PREFIX, enc)}"
		out += "decodeBad ${ShareCodes.fromShareCode<HotmPresets.HotmPreset>(HotmPresets.SHARE_PREFIX, "nope")}"
		for (n in listOf("Tier 1", "Tier 10", "Tier X", "§aTier 3")) {
			val m = HotmPresets.tierRegex.matcher(n)
			out += "tier '$n' ${if (m.matches()) m.group("tier") else "-"}"
		}
		OursGolden.check("mining-hotm", out)
	}
}
