// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fishing

enum class CreatureTier(val colour: String) { COMMON("§f"), UNCOMMON("§a"), RARE("§9"), EPIC("§5"), LEGENDARY("§6"), MYTHIC("§d") }

class SeaCreatureKind internal constructor(val name: String, val tier: CreatureTier, val rare: Boolean, val doubleHookable: Boolean, val catchLine: String) {
	val colour: String get() = tier.colour
	val displayName: String get() = tier.colour + name
	override fun toString() = name
}

object SeaCreatureBook {

	private val TABLE = """
		LEGENDARY|Water Hydra|R|The Water Hydra has come to test your strength.
		RARE|Carrot King|R|Is this even a fish? It's the Carrot King!
		COMMON|Sea Guardian|-|You stumbled upon a Sea Guardian.
		COMMON|Squid|-|A Squid appeared.
		COMMON|Night Squid|-|Pitch darkness reveals a Night Squid.
		COMMON|Sea Walker|-|You caught a Sea Walker.
		UNCOMMON|Sea Witch|-|It looks like you've disrupted the Sea Witch's brewing session. Watch out, she's furious!
		UNCOMMON|Sea Archer|-|You reeled in a Sea Archer.
		UNCOMMON|Rider of the Deep|-|The Rider of the Deep has emerged.
		RARE|Catfish|-|Huh? A Catfish!
		RARE|Sea Leech|-|Gross! A Sea Leech!
		EPIC|Guardian Defender|-|You've discovered a Guardian Defender of the sea.
		EPIC|Deep Sea Protector|-|You have awoken the Deep Sea Protector, prepare for a battle!
		RARE|Agarimoo|-|Your Chumcap Bucket trembles, it's an Agarimoo.
		UNCOMMON|Inkling|-|You get an inkling that you've caught... an Inkling!
		EPIC|Manta Ray|-|A majestic creature rises from the water. It's a Manta Ray.
		COMMON|Frog Man|-|Is it a frog? Is it a man? Well, yes, sorta, IT'S FROG MAN!!!!!!
		RARE|Snapping Turtle|-|A Snapping Turtle is coming your way, and it's ANGRY!
		LEGENDARY|Blue Ringed Octopus|R|A garish set of tentacles arise. It's a Blue Ringed Octopus!
		MYTHIC|Wiki Tiki|R|The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price.
		LEGENDARY|Great White Shark|R|Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!
		UNCOMMON|Nurse Shark|-|A tiny fin emerges from the water, you've caught a Nurse Shark.
		RARE|Blue Shark|-|You spot a fin as blue as the water it came from, it's a Blue Shark.
		EPIC|Tiger Shark|-|A striped beast bounds from the depths, the wild Tiger Shark!
		MYTHIC|Reindrake|R|A Reindrake forms from the depths.
		LEGENDARY|Yeti|R|What is this creature!?
		EPIC|Nutcracker|R|You found a forgotten Nutcracker laying beneath the ice.
		COMMON|Frozen Steve|-|Frozen Steve fell into the pond long ago, never to resurface...until now!
		UNCOMMON|Frosty|-|It's a snowman! He looks harmless.
		RARE|Grinch|-|The Grinch stole Jerry's Gifts...get them back!
		LEGENDARY|Phantom Fisher|R|The spirit of a long lost Phantom Fisher has come to haunt you.
		MYTHIC|Grim Reaper|R|This can't be! The manifestation of death himself!
		UNCOMMON|Scarecrow|-|Phew! It's only a Scarecrow.
		RARE|Nightmare|-|You hear trotting from beneath the waves, you caught a Nightmare.
		EPIC|Werewolf|-|It must be a full moon, a Werewolf appears.
		COMMON|Jumpin' Jack|-|Watch out! It's Jumpin' Jack.
		COMMON|Fried Chicken|-|Smells of burning. Must be a Fried Chicken.
		RARE|Fireproof Witch|-|Trouble's brewing, it's a Fireproof Witch!
		UNCOMMON|Magma Slug|-|From beneath the lava appears a Magma Slug.
		UNCOMMON|Moogma|-|You hear a faint Moo from the lava... A Moogma appears.
		RARE|Lava Leech|-|A small but fearsome Lava Leech emerges.
		RARE|Pyroclastic Worm|-|You feel the heat radiating as a Pyroclastic Worm surfaces.
		RARE|Lava Flame|-|A Lava Flame flies out from beneath the lava.
		RARE|Fire Eel|-|A Fire Eel slithers out from the depths.
		EPIC|Taurus|-|Taurus and his steed emerge.
		UNCOMMON|Volcanic Snail|-|You feel a burning sensation as you reel in a Volcanic Snail!
		EPIC|Magma Pillar|R|A Magma Pillar rises from the lava.
		LEGENDARY|Fiery Scuttler|R|A Fiery Scuttler inconspicuously waddles up to you, friends in tow.
		LEGENDARY|Thunder|R|You hear a massive rumble as Thunder emerges.
		MYTHIC|Lord Jawbus|R|You have angered a legendary creature... Lord Jawbus has arrived.
		MYTHIC|Plhlegblast|R|WOAH! A Plhlegblast appeared.
		MYTHIC|Ragnarok|R|The sky darkens and the air thickens. The end times are upon us: Ragnarok is here.
		EPIC|Vanquisher|RS|A Vanquisher is spawning nearby!
		UNCOMMON|Oasis Rabbit|-|An Oasis Rabbit appears from the water.
		UNCOMMON|Oasis Sheep|-|An Oasis Sheep appears from the water.
		LEGENDARY|Abyssal Miner|R|An Abyssal Miner breaks out of the water!
		RARE|Water Worm|-|A Water Worm surfaces!
		RARE|Poisoned Water Worm|-|A Poisoned Water Worm surfaces!
		RARE|Flaming Worm|-|A Flaming Worm surfaces from the depths!
		EPIC|Lava Blaze|-|A Lava Blaze has surfaced from the depths!
		EPIC|Lava Pigman|-|A Lava Pigman arose from the depths!
		UNCOMMON|Small Mithril Grubber|-|A leech of the mines surfaces... you've caught a Mithril Grubber.
		UNCOMMON|Medium Mithril Grubber|-|A leech of the mines surfaces... you've caught a Medium Mithril Grubber.
		UNCOMMON|Large Mithril Grubber|-|A leech of the mines surfaces... you've caught a Large Mithril Grubber.
		UNCOMMON|Bloated Mithril Grubber|-|A leech of the mines surfaces... you've caught a Bloated Mithril Grubber.
		COMMON|Trash Gobbler|-|The Trash Gobbler is hungry for you!
		UNCOMMON|Dumpster Diver|-|A Dumpster Diver has emerged from the swamp!
		RARE|Banshee|-|The desolate wail of a Banshee breaks the silence.
		EPIC|Bayou Sludge|-|A swampy mass of slime emerges, the Bayou Sludge!
		LEGENDARY|Alligator|R|A long snout breaks the surface of the water. It's an Alligator!
		MYTHIC|Titanoboa|R|A massive Titanoboa surfaces. Its body stretches as far as the eye can see.
		MYTHIC|Nessie|R|You've caused a disturbance in the loch. Could it be... Nessie?
		LEGENDARY|The Loch Emperor|R|The Loch Emperor arises from the depths.
		COMMON|Bogged|-|You've hooked a Bogged!
		UNCOMMON|Tadgang|-|A gang of Liltads!
		UNCOMMON|Ent|-|You've hooked an Ent, as ancient as the forest itself.
		RARE|Wetwing|-|Look! A Wetwing emerges!
		RARE|Stridersurfer|-|You caught a Stridersurfer.
		COMMON|Atoll Croaker|-|An inquisitive Atoll Croaker takes the bait!
		UNCOMMON|Lotus Guardian|-|A Lotus Guardian emerges, ready to protect the Atoll.
		RARE|gorF|-|What even is that?! A... gorF?
		EPIC|Drowned Captain|-|A Drowned Captain takes hold of your bobber!
		LEGENDARY|Puddle Jumper|RS|A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!
		MYTHIC|Frog Prince|R|Bow down before the Frog Prince... or pay the hefty price!
		COMMON|Haggard|-|A Haggard stumbles to the shore, ready for a fight!
		UNCOMMON|Brineling|-|A Brineling interrupts you with a stream of bubbles!
		RARE|Sprawl|-|A Sprawl emerges from the blue, and it's looking for you!
		EPIC|Torrid|-|The laughter of a Torrid echoes through the air.
		LEGENDARY|Silkbreeze|R|Something zips through the air - it's a Silkbreeze!
		MYTHIC|Giant Isopod|R|A Giant Isopod was dredged up from the depths!
	""".trimIndent()

	val kinds: List<SeaCreatureKind> = TABLE.lineSequence().filter { it.isNotBlank() }.map { row ->
		val (tier, name, flags, line) = row.split('|', limit = 4)
		SeaCreatureKind(name, CreatureTier.valueOf(tier), 'R' in flags, 'S' !in flags, line)
	}.toList()

	private val byLine: Map<String, SeaCreatureKind> = kinds.associateBy { it.catchLine }
	private val byName: Map<String, SeaCreatureKind> = kinds.associateBy { it.name }

	fun caughtBy(chatLine: String): SeaCreatureKind? = byLine[chatLine]

	fun named(name: String): SeaCreatureKind? = byName[name]

	fun mentionedIn(text: String): SeaCreatureKind? = kinds.firstOrNull { text.contains(it.name) }

	val rareKinds: List<SeaCreatureKind> get() = kinds.filter { it.rare }
}
