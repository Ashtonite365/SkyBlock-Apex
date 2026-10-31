// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch48Chat {
	val r = Random(4848)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	private val numTokens = listOf("1", "12", "999", "1000", "1,000", "2,650,000", "12.5", "1.000.000", "0.5", "1k", "2M", "3B", "1,000k", "99999999999999999999",
		",", ".", "coins", "Coins", "x", "-1500", "1500.", "1500,", "1,5", "10,000,000,000", "7", "  ", "§6", "1.2.3", "5000000000000000")
	val numbers = listOf("", " ", "1500 coins", "1500", "You sold 2,650,000 coins worth", "1,000 1,000 1,000 ", "a1500 b", "1500k coins", "999 coins",
		"1000.5 x", "12,345,678.9 coins", "1e5 coins", "NaN coins", "1,,,,, x", "1.. x") +
		List(1500) { (0 until r.nextInt(1, 8)).joinToString(listOf(" ", "", " ", ",").pick()) { numTokens.pick() } }

	private const val HINT = "! Press TAB or type /effects to view your active effects!"
	private val effects = listOf("Speed V", "Poisoned Candy I", "Haste III", "", "Strength with Speed", "Jump Boost II!", "a\nb", "Poisoned Candy II")
	private val players = listOf("Steve", "[MVP+] Bob", "", "with", "Alex with Steve", "a\u2028b")
	val potions = buildList {
		for (e in effects) {
			add("BUFF! You have gained $e$HINT"); add("BUFF! You splashed yourself with $e$HINT"); add("BUFF! You have gained $e!")
			add("BUFF! You splashed yourself with $e!")
			for (p in players) { add("BUFF! You were splashed by $p with $e$HINT"); add("BUFF! You were splashed by $p with $e!") }
		}
		add("BUFF! You have gained Speed V! Press TAB or type /effects to view your active effects"); add("BUFF!"); add("buff! You have gained Speed$HINT")
		add(" BUFF! You have gained Speed V$HINT"); add("BUFF! You were splashed by  with $HINT")
	}

	private val daPool = listOf("[NPC] Sirius: First up we have a Midas Staff, the starting bid is 1,000,000 Coins!",
		"[NPC] Sirius: Next up we have an Enchanted Book, the starting bid is 50 Coins!", "[NPC] Sirius: Next up we have a A, B, the starting bid is 5 Coins!",
		"[NPC] Sirius: The highest bidder was Bob with a bid of 1,200,000 Coins!", "[NPC] Sirius: No one made any bids! Sad.", "[NPC] Sirius: No one made any bids!",
		"[NPC] Sirius: The highest bidder was B o b with a bid of 1 Coins!", "[NPC] Sirius: Next up we have an , the starting bid is 5 Coins!", "Hello", "")
	val daSeqs = List(150) { List(r.nextInt(1, 7)) { daPool.pick() } }

	private val partPool = listOf("You earned ", "1,500,000", " coins", "2650000 ", "999 ", "Bits", "12,345 coins!", "1500", " and 2,000 more ", "")
	val styled: List<List<Pair<String, Char?>>> = List(200) {
		List(r.nextInt(1, 5)) { partPool.pick() to listOf<Char?>('6', '6', 'e', null, 'a').pick() }
	} + listOf(listOf(daPool[0] to null), listOf(daPool[3] to null), listOf("BUFF! You have gained Speed V$HINT" to '6'), listOf("1500 coins" to '6'))

	val bestiaryPool = listOf("BESTIARY", "  BESTIARY  ", "Zombie 4➡5", "Zombie 1000➡5", "  Rewards", "+1 Combat Wisdom", "", "▬".repeat(21), "▬".repeat(20),
		"▬".repeat(30) + "x", "Some text", "Ghost 9➡10", "Slayer")
	val bestiarySeqs = List(200) { List(r.nextInt(1, 25)) { if (r.nextInt(5) == 0) "BESTIARY" else bestiaryPool.pick() } }

	val jacobPool = listOf("FARMING CONTEST REWARDS CLAIMED", "» Wheat Contest", "» ", "»x", "+10 Jacob's Tickets", "", "▬".repeat(25), "▬".repeat(10), "Gold medal")
	val jacobSeqs = List(150) { List(r.nextInt(1, if (r.nextInt(10) == 0) 80 else 15)) { if (r.nextInt(6) == 0) jacobPool[0] else jacobPool.pick() } }

	val stashPool = listOf("You have 1,234 items stashed away!", "You have 1 materials stashed away!", "You have x items stashed away!",
		"(This totals 1 type of items stashed!)", "(This totals 5 types of materials stashed!)", "(This totals 5 type of items stashed!)",
		">>> CLICK HERE to pick them up! <<<", ">>> CLICK HERE to pick it up! <<<", ">>> CLICK HERE to pick up! <<<", "Hello", "", "  You have 3 items stashed away!  ")
	val stashSeqs = List(200) { List(r.nextInt(1, 8)) { stashPool.pick() } }

	val titles = listOf("Superpairs (Gold)", "Chronomatron ➜ Stakes", "Ultrasequencer Rewards", "Experimentation Table", "Superpairs", "Chest", "Superpairs (", null)
	val expPool = listOf("You claimed the Superpairs rewards!", "You claimed the  rewards!", " You claimed the X rewards! ", "+Enchanted Book", " +2 Experience Bottles", "  +x",
		"+", "Hello", "")

	val expSteps = List(400) { Triple(if (r.nextInt(3) == 0) titles.pick() else "-", List(r.nextInt(0, 4)) { expPool.pick() }, r.nextInt(0, 5)) }
}
