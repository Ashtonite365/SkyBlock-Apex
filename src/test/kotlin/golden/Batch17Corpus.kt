// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import java.util.Base64
import kotlin.random.Random

object Batch17Sh {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]
	private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
	val sweepHeaders = listOf("Sweep Details: 12∮ Sweep", "Sweep Details: 1,234.5∮ Sweep", "Sweep Details: 7 Sweep", "Sweep Details: 7xx Sweep", "Sweep Details: ∮ Sweep",
		" Sweep Details: 3∮ Sweep", "Sweep Details: 3∮ Sweep ", "sweep details: 3∮ Sweep", "Sweep Details: 3.0.0∮ Sweep", "")
	val toughness = listOf("  Fig Tree Toughness: 3.5 18.13 Logs", " Mangrove Tree Toughness: 2 1,000 Logs", "Fig Tree Toughness: 3.5 18.13 Logs",
		"  Big Fig Tree Toughness: 1 2 Logs", "  Fig Tree Toughness: 3.5 Logs", "  Fig Tree Toughness: a 1 Logs", "\t Oak Tree Toughness: 1.0 2.0 Logs", "  Tree Toughness: 1 2 Logs")
	val sweeps = listOf(Triple("12", null, null), Triple("12", "Fig", null), Triple("1,234.5", "Fig", "18.13"), Triple("", "", ""), Triple("x", null, "5"))
	val cookieLines: List<List<String>> by lazy {
		val r = Random(1701)
		val pool = listOf("Amount: 6x", "Booster Cookie x6", "  Amount: 64x ", "Amount: x", "Booster Cookie x", "Amount: 99999999999x", "Booster Cookie x12 extra", "Cookie x3",
			"Amount:6x", "", "Amount: 0x", "Amount: -1x", "1,500 Bits", " 2,000 Bits ", "Bits", "1,500 Bits!", ",,, Bits", "99999999999999999999 Bits", "1500 bits", "Cost", "0 Bits")
		List(250) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val tenths = listOf(-5, 0, 1, 10, 11, 15, 30, 35, 1000)
	val cpb = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to -1.0, 100.0 to 3.0, 5e6 to 4800.0, Double.NaN to 1.0, 1.0 to Double.NaN, 1.0 to Double.MIN_VALUE, -2.0 to 7.0)
	val croesusLores: List<List<String>> by lazy {
		val r = Random(1702)
		val pool = listOf("No chests opened yet!", "No Chests Opened!", "NO CHESTS OPENED", "No more Chests to open!", "no more chests to open", "Opened Chest: Bedrock", "  Opened Chest: Obsidian",
			"Opened Chest:", "opened chest: x", "Click to view", "", "Floor VII", "Opened Chests: 2")
		List(250) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	private val texHashes = listOf("abcDEF0123", "0", "deadbeef")
	val textures: List<String?> = listOf(null, "", "====", "!!!!", "abc", "abc=", "abc==", "a", b64("nothing here"), b64("{}"),
		b64("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/ABCdef0123"}}}"""),
		b64("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/"}}}"""),
		b64("""{"url":"textures.minecraft.net/texture/12ffz9"}"""),
		b64("""texturesXminecraft.net/texture/aa textures.minecraft.net/texture/bb""")) +
		texHashes.flatMap { h -> val e = b64("""{"url":"http://textures.minecraft.net/texture/$h"}"""); listOf(e, e.trimEnd('='), "$e==", e + "=====") } +
		listOf(b64("\u00ff\u00fe textures.minecraft.net/texture/c0ffee"), "AAAA", "/+/+", "-_-_")
	val skulls: List<String?> = listOf(null, "", "{}", "[]", "not json", """{"THUNDER_SPARK": 5}""", """{"THUNDER_SPARK":"${textures[10]}"}""",
		"""{"OTHER":"${textures[10]}"}""", """{"THUNDER_SPARK":""}""", """{"THUNDER_SPARK":"!!"}""", """{"THUNDER_SPARK":"${textures[10]}","X":"y"}""", """{"THUNDER_SPARK":null}""")
	val vermin = listOf("VERMIN VACUUMED! You vacuumed a Silverfish!", "VERMIN VACUUMED! You vacuumed an Spider!", "VERMIN VACUUMED! You vacuumed a Fly!",
		"VERMIN VACUUMED! You vacuumed a Bug!", "VERMIN VACUUMED! You vacuumed a Giant Silverfish Spider!", "VERMIN VACUUMED! You vacuumed a Ladybug!",
		"VERMIN VACUUMED! You vacuumed a Firefly! +5 Motes", "VERMIN VACUUMED! You vacuumed a Rat!", "VERMIN VACUUMED! You vacuumed a !", "VERMIN VACUUMED! You vacuumed a Fly",
		"vermin vacuumed! You vacuumed a Fly!", "VERMIN VACUUMED! You vacuumed a fly!", "VERMIN VACUUMED! You vacuumed a Fly! Bug!", "", "You vacuumed a Fly!")
}
