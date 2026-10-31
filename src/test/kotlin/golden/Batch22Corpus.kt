// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch22Kit {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]
	val titles = listOf("", "(1/3) Loadout", "Loadouts (2/2) Loadout", "(0/0) Loadout", "(1/3) loadout", "(1/3)Loadout", "(a/3) Loadout", "(99999999999/3) Loadout", "(3/99999999999) Loadout",
		"(1/3) Loadout (2/3) Loadout", "( 1/3) Loadout", "(-1/3) Loadout", "Wardrobe (1/3)", "(12/12) Loadout xyz")
	val chats = listOf("Ragnarock was cancelled due to being hit!", "Ragnarock was cancelled due to taking damage!", "Ragnarock was cancelled due to being hit", " Ragnarock was cancelled due to being hit!",
		"Ragnarock was cancelled due to falling!", "", "ragnarock was cancelled due to being hit!")
	val strengthLores: List<List<String>> by lazy {
		val r = Random(2201)
		val pool = listOf("Strength: +100", "Strength: +1,250", "Damage: +100", "Strength: +", "Strength: +99999999999", "§cStrength: +50 §9(+20)", "  Strength: +7", "Strength: 100", "Strength: +,", "")
		List(150) { List(r.nextInt(0, 4)) { pick(r, pool) } }
	}
	val strengths = listOf(Int.MIN_VALUE, -3, -1, 0, 1, 3, 7, 100, 1431655765, 1431655766, Int.MAX_VALUE)
	val sounds = listOf("entity.wolf.death", "entity.wolf_angry.death", "entity.wolf.hurt", "entity.wolfdeath", "entity.wol.death", "x.entity.wolf.death", "entity.wolf.death ", "")
	val pitches = listOf(1.4920635f, 1.492f, 1.4921f, 1.49215f, 1.49197f, 1.0f, Float.NaN, -1.4920635f)
	val chestLores: List<List<String>> by lazy {
		val r = Random(2202)
		val pool = listOf("Contents", " Contents ", "Enchanted Book (Fatal Tempo I)", "Crimson Essence x100", "Burning Kuudra Core", "", "Cost", "1,000,000 Coins", "250,000 Coins", "Coins",
			"Kuudra Key", "No more chests to open!", " No more chests to open! ", ", Coins", "1e5 Coins", "999 Coins ", "contents")
		List(300) { List(r.nextInt(0, 9)) { pick(r, pool) } }
	}
	val chestNames = listOf("Kuudra's Hollow", "Kuudra's Hollow ", "Paid Chest", "")
}
