// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.diana.DianaWaypoints
import dev.ashtonite365.apex.features.events.helpers.EventHelpers
import dev.ashtonite365.apex.features.plus.events.DianaPlusPorts
import dev.ashtonite365.apex.features.plus.events.EventPlusPorts
import dev.ashtonite365.apex.features.plus.events.SeasonalPlusPorts
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ServerLocation
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.ProfitTracker
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

object P2dCorpus {
	private val r = Random(0x2D2D)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	private val chat = ArrayList<String>()
	private val appender = object : AbstractAppender("p2d-chat", null, null, true, Property.EMPTY_ARRAY) {
		override fun append(event: LogEvent) {
			val m = event.message.formattedMessage
			if (m.startsWith("CHAT: ")) synchronized(chat) { chat += m.removePrefix("CHAT: ") }
		}
	}
	private fun attach() {
		appender.start()
		(Apex.logger as? org.apache.logging.log4j.core.Logger)?.addAppender(appender)
	}
	private fun detach() {
		(Apex.logger as? org.apache.logging.log4j.core.Logger)?.removeAppender(appender)
	}
	private val alertField = Alerts::class.java.getDeclaredField("current").apply { isAccessible = true }
	private fun takeAlert(): String? = (alertField.get(Alerts) as Component?)?.string.also { Alerts.clear() }
	private fun takeChat(): List<String> = synchronized(chat) { chat.toList().also { chat.clear() } }
	private fun io() = "chat=${takeChat()} alert=${takeAlert()}"

	private fun configShape(vararg cfgs: ManagedConfig): String {
		P2Opts.load(*cfgs)
		return cfgs.joinToString(" / ") { c -> c.name + ":" + c.sortedOptions.joinToString(",") { "${it.propertyName}=${it._actualValue}" } }
	}

	private fun <T> withAll(sets: List<Pair<ManagedConfig, Map<String, Any>>>, block: () -> T): T =
		if (sets.isEmpty()) block() else P2Opts.with(sets[0].first, sets[0].second) { withAll(sets.drop(1), block) }

	private fun ProfitTracker.Data.show() = "items=$items events=$events coins=$coins"
	private fun EventHelpers.DianaData.show() = "b=$burrows c=$coins m=$mobs d=$drops"
	private fun chatEv(s: String) = ProcessChatEvent(Component.literal(s), false)

	private val islands = listOf(null, ServerLocation("s", "SKYBLOCK", "hub"), ServerLocation("s", "SKYBLOCK", "winter"), ServerLocation("s", "SKYBLOCK", "combat_1"),
		ServerLocation("s", "BEDWARS", "hub"))

	private val mobs = listOf("Minos Hunter", "Siamese Lynxes", "Minotaur", "Gaia Construct", "Minos Champion", "Minos Inquisitor", "Bagheera")
	private val items = listOf("Griffin Feather", "Daedalus Stick", "Crown of Greed", "Washed-up Souvenir", "Dwarf Turtle Shelmet",
		"Antique Remedies", "Crochet Tiger Plushie", "Chimera I", "Minos Relic", "Ancient Claw", "Enchanted Ancient Claw", "Bob's Thing!")
	private val ritualLines: List<String> = buildList {
		for (n in listOf("0", "1", "250", "1,000", "12,500", "1,234,567", "9,999,999,999", "99,999,999,999,999,999,999", ",", "1,,0")) add("Wow! You dug out $n coins!")
		for (m in mobs) for (exc in listOf("Uh oh", "Oi", "Yikes", "Woah", "Oh", "Danger", "Good Grief", "Hey")) add("$exc! You dug out ${if (m[0] in "AEIOU") "an" else "a"} $m!")
		for (i in items) {
			add("RARE DROP! You dug out a $i!"); add("RARE DROP! You dug out an $i!"); add("RARE DROP! You dug out a $i! (+250% ✯ Magic Find)")
			for (t in listOf("RARE", "VERY RARE", "CRAZY RARE", "INSANE", "PRAY")) {
				add("$t DROP! $i!"); add("$t DROP! $i (+1,234% ✯ Magic Find)!"); add("$t DROP! (3x $i) (+99% ✯ Magic Find)!"); add("$t DROP! (12x $i)")
			}
		}
		add("You dug out a Griffin Burrow! (1/4)"); add("You dug out a Griffin burrow!"); add("You finished the Griffin burrow chain! (4/4)")
		add("You finished the Griffin Burrow! (2/4)"); add("You dug out the Griffin Burrow!"); add("You dug out a Griffin Burrow!")
		add(""); add("hello"); add(" Wow! You dug out 5 coins! "); add("Wow! You dug out -5 coins!"); add("RARE DROP! You dug out a !")
	}
	private val seasonalLines: List<String> = buildList {
		for (a in listOf("SPOOKY", "PARTY", "spooky")) for (c in listOf("Trick or Treat Chest", "Party Chest", "Chest")) add("$a! A $c has appeared!")
		add("You consumed a Refined Bottle of Jyrre and gained +300✎ Intelligence for 60m!")
		add("You consumed a Refined Bottle of Jyrre and gained +300X Intelligence for 60m!")
		add("You consumed a Refined Bottle of Jyrre and gained +300 Intelligence for 60m!")
		add("You consumed a Bottle of Jyrre and gained +100✎ Intelligence for 60m!")
		add("You claimed a New Year Cake!"); add("You claimed a New Year Cake")
		for (who in listOf("[MVP+] Foo", "Bar", "")) for (what in listOf("a Reindrake", "TWO Reindrakes", "three Reindrakes")) add("WOAH! $who summoned $what from the depths!")
		add("Reindrake"); add("hello")
	}
	private val eventLines = listOf("✆ Hoppity ✆ ring ring", "✆ Hoppity ✆", "Hoppity ✆", "HOPPITY'S HUNT You found a Chocolate Breakfast Egg in the garden!",
		"HOPPITY'S HUNT You found an Egg!", "HOPPITY'S HUNT You found a Rabbit!", "hello")
	private val sidebars = listOf(emptyList(), listOf("Late Winter 31st", " New Year Event! 12:30"), listOf("Spring 1st"), listOf("New Year Event!"),
		listOf("New Year Event! "), listOf("  New Year Event! 0:01  "), listOf("x New Year Event! 1"))

	private fun diana(steps: Int): String {
		val log = StringBuilder()
		val eh = mapOf("diana-tracker" to r.nextBoolean(), "diana-profit" to r.nextBoolean(), "diana-profit-total" to r.nextBoolean())
		val saved = SkyBlockSession.serverLocation
		try {
			withAll(listOf(EventHelpers.TConfig to eh)) {
				log.append("$eh\n")
				repeat(steps) {
					val res = if (r.nextInt(6) == 0) {
						val i = r.nextInt(islands.size); SkyBlockSession.serverLocation = islands[i]; "L$i"
					} else {
						val l = ritualLines.pick()
						val ev = chatEv(l)
						"C${ritualLines.indexOf(l)} " + Golden.run { EventHelpers.onChat(ev); "a" } + " " + Golden.run { DianaPlusPorts.onChat(ev); "b" }
					}
					log.append("$res tot=${EventHelpers.DConfig.data.show()} sess=${EventHelpers.sessionTracker.counters} " +
						"profit=${DianaPlusPorts.tracker.current.show()} mode=${DianaPlusPorts.tracker.mode} ${io()}\n")
				}
			}
		} finally {
			SkyBlockSession.serverLocation = saved
		}
		return log.toString()
	}

	private fun seasonal(steps: Int): String {
		val log = StringBuilder()
		val opts = mapOf("spooky-chest-alert" to r.nextBoolean(), "spooky-chest-compact" to r.nextBoolean(), "jyrre-timer" to r.nextBoolean(),
			"reindrake-warp" to r.nextBoolean(), "new-year-cake" to r.nextBoolean())
		val saved = SkyBlockSession.serverLocation
		val savedSb = ScoreboardUtil.simplifiedScoreboardLines
		try {
			withAll(listOf(SeasonalPlusPorts.TConfig to opts, Alerts.TConfig to mapOf("sounds" to false))) {
				log.append("$opts\n")
				repeat(steps) {
					val res = when (r.nextInt(8)) {
						0 -> { val i = r.nextInt(islands.size); SkyBlockSession.serverLocation = islands[i]; "L$i" }
						1 -> { val i = r.nextInt(sidebars.size); ScoreboardUtil.simplifiedScoreboardLines = sidebars[i]; "S$i" }
						2 -> { val t = if (r.nextBoolean()) 20 * r.nextInt(10) else r.nextInt(0, 200); "T$t " + Golden.run { SeasonalPlusPorts.onTick(TickEvent(t)); "ok" } }
						else -> { val l = seasonalLines.pick(); "C${seasonalLines.indexOf(l)} " + Golden.run { SeasonalPlusPorts.onChat(chatEv(l)); "ok" } }
					}
					log.append("$res ${io()}\n")
				}
			}
		} finally {
			SkyBlockSession.serverLocation = saved
			ScoreboardUtil.simplifiedScoreboardLines = savedSb
		}
		return log.toString()
	}

	private fun events(steps: Int): String {
		val log = StringBuilder()
		val opts = mapOf("hoppity-call-warning" to r.nextBoolean(), "mythic-rabbit-warning" to r.nextBoolean())
		val dw = mapOf("griffin-pet-warning" to r.nextBoolean(), "highlight-rare-mobs" to r.nextBoolean(), "rare-mob-alert" to r.nextBoolean())
		val saved = SkyBlockSession.serverLocation
		try {
			withAll(listOf(EventPlusPorts.TConfig to opts, DianaWaypoints.TConfig to dw, Alerts.TConfig to mapOf("sounds" to false))) {
				log.append("$opts $dw\n")
				repeat(steps) {
					val res = when (r.nextInt(6)) {
						0 -> { val i = r.nextInt(islands.size); SkyBlockSession.serverLocation = islands[i]; "L$i" }
						1 -> { val t = r.nextInt(0, 100); "T$t " + Golden.run { EventPlusPorts.onTick(TickEvent(t)); DianaPlusPorts.onTick(TickEvent(t)); "ok" } }
						else -> { val l = eventLines.pick(); "C${eventLines.indexOf(l)} " + Golden.run { EventPlusPorts.onChat(chatEv(l)); "ok" } }
					}
					log.append("$res ${io()}\n")
				}
			}
		} finally {
			SkyBlockSession.serverLocation = saved
		}
		return log.toString()
	}

	fun cases(): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>()

		fun add(k: String, f: () -> Any?) { out += k to Golden.run { f().toString() }.replace("THROWS ServiceConfigurationError", "THROWS NoClassDefFoundError") }
		attach()
		try {
			for (h in listOf(EventHelpers.DConfig, DianaPlusPorts.DConfig)) if (!h.isLoaded) h.explicitDefaultLoad()
			add("holders") { "${EventHelpers.DConfig.data.show()} ${DianaPlusPorts.DConfig.data.show()}" }
			add("configs") { configShape(EventHelpers.TConfig, SeasonalPlusPorts.TConfig, EventPlusPorts.TConfig, DianaWaypoints.TConfig) }
			add("eggmodes") { EventPlusPorts.EggSoundMode.entries.map { "${it.name}=${it.serializedName}" } }
			add("raremobs") { DianaPlusPorts.rareMobs }
			add("sessioncfg") { "${EventHelpers.sessionTracker.idleTimeout} ${EventHelpers.sessionTracker.resetAfter}" }

			for ((i, l) in ritualLines.withIndex()) {
				add("apply|$i") { val d = EventHelpers.DianaData(); "${EventHelpers.applyDiana(d, l)} ${d.show()}" }
				add("parse|$i") { DianaPlusPorts.parse(l) }
				add("idof|$i") { DianaPlusPorts.idOf(l) }
			}
			for (i in 0 until 200) add("applyseq|$i") {
				val d = EventHelpers.DianaData(); val hits = List(r.nextInt(1, 25)) { EventHelpers.applyDiana(d, ritualLines.pick()) }; "$hits ${d.show()}"
			}
			val names = items + mobs + listOf("", "'", "a'b c", "__x__", "Ünïcode Thing", "  spaced  ", "UPPER", "x-y-z", "9 Lives", "Ice-Cold!!")
			for ((i, n) in names.withIndex()) add("idn|$i") { DianaPlusPorts.idOf(n) }
			val tags = listOf("§2Minos Inquisitor §a40M§c❤", "Sphinx", "[Lv100] King Minos 1M", "Manticore and Sphinx", "minos inquisitor", "Minos Hunter", "",
				"Sphinx Manticore", "§cKing Minos§r", "Minos Inquisitor King Minos")
			for ((i, t) in tags.withIndex()) add("rare|$i") { DianaPlusPorts.rareMobName(t) }

			val costs = listOf(0.0, 1.0, 10.0, 999.0, 1000.0, 12345.0, 1e6, 7.5e8, 3e9, 1e12, -5.0)
			val balances = listOf(null, 0.0, 400.0, 999.0, 1000.0, 1e6, 5e9)
			val rates = listOf(null, -1.0, 0.0, 0.5, 1.0, 10.0, 1234.5, 1e6)
			for (idx in -1..7) for (c in costs) for (b in balances) for (p in rates) add("tip|$idx|$c|$b|$p") { EventHelpers.employeeTooltip(idx, c, b, p) }

			for ((i, l) in seasonalLines.withIndex()) for (compact in listOf(false, true)) add("spooky|$i|$compact") { SeasonalPlusPorts.spookyChestTitle(l, compact) }
			for ((i, l) in seasonalLines.withIndex()) add("seas|$i") { "${SeasonalPlusPorts.isJyrreMessage(l)} ${SeasonalPlusPorts.isReindrakeSpawn(l)}" }
			for ((i, s) in sidebars.withIndex()) add("cake|$i") { SeasonalPlusPorts.isCakeTime(s) }
			val elapsed = listOf(Duration.ZERO, 1.milliseconds, Duration.parse("10m"), Duration.parse("59m59s"), Duration.parse("60m"), Duration.parse("61m"),
				Duration.parse("-5m"), Duration.INFINITE) + List(20) { r.nextLong(0, 4_000_000).milliseconds }
			for ((i, e) in elapsed.withIndex()) add("jyrre|$i") { SeasonalPlusPorts.jyrreRemaining(e) }
			for (n in -1..7) for (h in listOf(false, true)) add("treasure|$n|$h") { SeasonalPlusPorts.isTreasureStand(n, h) }

			val sounds = listOf("music", "music.game", "music_disc.cat", "musician", "block.note_block.bit", "", "MUSIC")
			val vals = listOf(1f, 0.5f, 0f, 2f)
			val coords = listOf(0.0, -0.0, 1.0, 70.0)
			for (s in sounds) for (p in vals) for (v in vals) for (x in coords) for (y in coords) for (z in listOf(0.0, 5.0))
				add("spade|$s|$p|$v|$x|$y|$z") { EventPlusPorts.isBuggedSpadeSound(s, p, v, x, y, z) }
			for (pn in listOf(null, "Rabbit", "Mythic Rabbit", "rabbit", "Griffin", "")) for (ra in listOf(null, "MYTHIC", "LEGENDARY", "mythic"))
				add("rabbit|$pn|$ra") { EventPlusPorts.needsMythicRabbit(pn, ra) }

			for (i in 0 until 300) add("diana|$i") { diana(r.nextInt(3, 30)) }
			for (i in 0 until 300) add("seasonal|$i") { seasonal(r.nextInt(3, 30)) }
			for (i in 0 until 120) add("events|$i") { events(r.nextInt(3, 20)) }
			add("holders-after") { EventHelpers.DConfig.data.show() }
		} finally {
			detach()
		}
		return out
	}
}
