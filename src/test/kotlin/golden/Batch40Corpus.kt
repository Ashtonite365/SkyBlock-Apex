// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

object Batch40Dungeon {
	private val base = listOf(
		"[BOSS] The Watcher: Things feel a little more roomy now, eh?", "[BOSS] The Watcher: Let's see how you can handle this.",
		"[BOSS] The Watcher: You have proven yourself. You may pass.", "[BOSS] The Watcher: That will be enough for now.",
		"The Crystal withers your soul as you hold it in your hands!", "It doesn't seem like that is supposed to go there.",
		"[BOSS] Bonzo: Gratz", "[BOSS] Bonzo", "[BOSS] Livid: hi", "[BOSS] Hungry Livid: hi", "[BOSS] Smart Livid: x:y", "[BOSS] Livids: hi",
		"[BOSS] Wither King: You... again?", "[BOSS] King: x", "[BOSS] Sadan: ENOUGH!", "[BOSS] The Professor: Oh?", "[BOSS] Enderman: x",
		"[BOSS] Thorn: x", "[BOSS] Maxor: x", "[BOSS] Storm: x", "[BOSS] Goldor: x", "[BOSS] Necron: x", "[BOSS] Scarf: x", "[BOSS] : x", "[BOSS]",
		"[BOSS]  Bonzo: x", "[boss] Bonzo: x", " [BOSS] Bonzo: x",
		"PUZZLE FAIL! Steve killed a Blaze in the wrong order! Yikes!", "PUZZLE FAIL! Steve", "PUZZLE FAIL!  x y",
		"[STATUE] Oruo the Omniscient: Alex chose the wrong answer! I shall never forget this moment of misrememberance.",
		"[STATUE] Oruo the Omniscient: Alex answered Question #1 correctly!", "[STATUE] Oruo the Omniscient: ",
		"PUZZLE SOLVED! Steve wasn't fooled by Bob! Good job!", "PUZZLE SOLVED! Steve tied Tic Tac Toe! Good job!",
		"[NPC] Mort: Here, I found this map when I first entered the dungeon.", "[NPC] Mort: Good luck.", "[NPC] Mort: Talk to me to change your class",
		"Steve is now ready!", "Dungeon starts in 1 second.", "Dungeon starts in 5 seconds.", "Dungeon starts in seconds.",
		"Steve has started the dungeon countdown. The dungeon will begin in 1 minute.",
		"Your active Potion Effects have been paused and stored. They will be restored when you leave Dungeons!",
		"You can no longer consume or splash any potions during the remainder of this Dungeon run!", "A shiver runs down your spine...",
		"Steve has obtained Superboom TNT!", "Steve has obtained Blessing of Power!", "Steve has obtained Wither Key!", "Steve has obtained Blood Key!",
		"A Wither Key was picked up!", "A Blood Key was picked up!", "A Wither Key!", "Steve found a Wither Essence! Everyone gains an extra essence!",
		"Bob the Fairy: You killed me! Take this Revive Stone so that my death is not in vain!", "A Blessing of Wisdom was picked up!",
		"RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door!", "RIGHT CLICK on the BLOOD DOOR to open it.",
		"DUNGEON BUFF! Steve found a Blessing of Life V!", "BUFF! You have gained Healing V!", "     Granted you +10 Strength.", "    Granted you x",
		"You cannot hit the silverfish while it's moving!", "There are blocks in the way!", "This lever has already been used.",
		"A mystical force in this room prevents you from using that ability!", "You do not have the key for this door!",
		"The Zombie struck you for 1,234 damage!", "Skeleton hit you for 5.5 true damage.", "Creeper exploded, hitting you for 100 damage.",
		"Your Ice Spray hit 3 enemies for 1,000 damage.", "The Mage's Magma burnt you for 10 true damage.",
		"Steve used Healing Circle on you!", "Your Guided Sheep hit 2 enemies", "You were healed for 100 health by Steve's Wand",
		"Steve picked up your Healing Orb!", "This ability is on cooldown for 3s.", "Steve healed you for 50 health!", "Steve formed a tether with you!",
		"Your Spirit Pet healed Steve", "You need at least 50 mana to activate this!", "Your fairy healed you", "Ragnarok is now available!", "Castle of Stone Activated!",
		"Steve opened a WITHER door!", "The BLOOD DOOR has been opened!",
		" ☠ Steve was killed by Zombie and became a ghost.", " ☠ You died and became a ghost.", " ☠ Steve disconnected from the Dungeon and became a ghost.",
		" ☠ Steve fell into the void and became a ghost.", " ☠ Steve burnt to death and became a ghost.", " ☠ Steve suffocated and became a ghost.",
		" ☠ Steve was crushed and became a ghost.", "☠ Steve was killed and became a ghost.", " ☠ Steve was killed and became a ghost",
		"Healer Milestone ❷: You have healed 10,000 Health so far! 05m12s", "Tank Milestone ❸: You have tanked and dealt 1,000 Total Damage so far! 03m",
		"Milestone: You have dealt x so far! y",
		"Steve entered The Catacombs, Floor VII!", "[MVP+] Steve entered MM The Catacombs, Floor III!", "[MVP+] [x] Steve entered The Catacombs, Floor I!",
		"ThisNameIsTooLong12 entered The Catacombs, Floor I!", "Steve entered The Catacombs, Entrance",
		"The Catacombs - Floor VII", "   Master Mode The Catacombs - Floor III", " The Catacombs - Entrance", "Catacombs - Floor I", "", " ",
	)
	val lines = base + base.map { "$it " } + listOf("Steve has obtained Wither Key! extra", "x A Wither Key!", "random text")

	val players: List<String?> = listOf(null, "Steve", "", "steve", "Alex")
	val moveMillis = listOf(-1000L, 0L, 999L, 1000L, 1001L, 21_999L, 22_000L, 22_001L, 24_999L, 25_000L, 27_500L, 28_000L, 30_999L, 31_000L,
		33_999L, 34_000L, 34_001L, 40_000L, 500L, 15_000L, Long.MAX_VALUE / 4)
	val lagMillis = listOf(0L, 250L, -100L, 1500L)

	val teammates = listOf(
		"§e[H] §bFlameeke §c8,998§c❤", "§e[H] §bFlameeke §a12,345§c❤", "§e[M] §bSteve §cDEAD", "§e[M] §bSteve §7DEAD", "[A] Bob §e100",
		"§e[h] §bSteve §c1", "§e[T] §b§lSteve§r §c§l1,000", "§e[B] §bA §c,", "[B] ThisNameIsTooLong12 §c5", "x§e[B] Bob §c5", "§e[B]  Bob §c5",
		"§e[B] Bob 100", "§e[B] Bob §c", "", "§e[B] Bob §c5 extra §a7",
	)
	val tabs = listOf(listOf(), listOf("Puzzles: (3)"), listOf("x", " Puzzles: (12) y", "Puzzles: (4)"), listOf("Puzzles: ()", "Puzzles: (2)"),
		listOf("Puzzles: (99999999999)", "Puzzles: (2)"), listOf("puzzles: (1)"))
	val boards = listOf(listOf(), listOf(" ⏣ The Catacombs (M3)"), listOf("The Catacombs (E)", "The Catacombs (F7)"), listOf("The Catacombs (F10)"),
		listOf("The Catacombs ()", "x The Catacombs (F1)"), listOf("The Catacombs (X1)"), listOf("The Catacombs (M)"))
	val deaths = (-2..6).toList()
}
