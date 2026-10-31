// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.kuudra.KuudraInfo
import dev.ashtonite365.apex.features.kuudra.KuudraLogic
import dev.ashtonite365.apex.features.kuudra.KuudraLogic.Supply
import dev.ashtonite365.apex.features.plus.combat.CombatPlusPorts
import dev.ashtonite365.apex.features.plus.combat.CombatPlusMerges
import dev.ashtonite365.apex.features.plus.combat.PlusCombatLogic
import dev.ashtonite365.apex.features.plus.slayer.PlusOwnSlayerBoss
import dev.ashtonite365.apex.features.plus.slayer.SlayerPlusMerges
import dev.ashtonite365.apex.features.plus.slayer.SlayerPlusPorts
import dev.ashtonite365.apex.features.skills.SlayerOverlay
import dev.ashtonite365.apex.features.combat.AbilityCooldowns
import dev.ashtonite365.apex.features.misc.GapFeatures2
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ServerLocation
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.ProfitTracker
import kotlin.random.Random
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

object P2cCorpus {
	private val r = Random(0x2C2C)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	private val chat = ArrayList<String>()
	private val appender = object : AbstractAppender("p2c-chat", null, null, true, Property.EMPTY_ARRAY) {
		override fun append(event: LogEvent) {
			val m = event.message.formattedMessage
			if (m.startsWith("CHAT: ")) synchronized(chat) { chat += m.removePrefix("CHAT: ") }
		}
	}
	private var attached = false
	private fun attach() {
		if (attached) return
		attached = true
		appender.start()
		(Apex.logger as? org.apache.logging.log4j.core.Logger)?.addAppender(appender)
	}
	private fun detach() {
		(Apex.logger as? org.apache.logging.log4j.core.Logger)?.removeAppender(appender)
		attached = false
	}
	private val alertField = Alerts::class.java.getDeclaredField("current").apply { isAccessible = true }
	private fun takeAlert(): String? = (alertField.get(Alerts) as Component?)?.string.also { Alerts.clear() }
	private fun takeChat(): List<String> = synchronized(chat) { chat.toList().also { chat.clear() } }
	private val timeLike = Regex("\\d+(?:\\.\\d+)?(?:ms|s|m|h)\\b")
	private fun norm(s: String) = timeLike.replace(s, "<t>")
	private fun io() = "chat=${takeChat().map(::norm)} alert=${takeAlert()?.let(::norm)}"

	private fun configShape(vararg cfgs: ManagedConfig): String {
		P2Opts.load(*cfgs)
		return cfgs.joinToString(" / ") { c -> c.name + ":" + c.sortedOptions.joinToString(",") { "${it.propertyName}=${it._actualValue}" } }
	}

	private fun <T> withAll(sets: List<Pair<ManagedConfig, Map<String, Any>>>, block: () -> T): T =
		if (sets.isEmpty()) block() else P2Opts.with(sets[0].first, sets[0].second) { withAll(sets.drop(1), block) }

	private fun ProfitTracker.Data.show() = "items=${items} events=$events coins=$coins"

	private val hps = listOf(0f, 1f, -5f, 999.9f, 9999f, 10000f, 10001f, 24999.5f, 25000f, 25001f, 49999f, 50000f, 50001f, 75000f, 75001f, 98999f,
		99000f, 99001f, 100000f, 123456f, Float.NaN, Float.POSITIVE_INFINITY) + List(40) { r.nextFloat() * 110000f }
	private val spotPoints: List<Pair<Double, Double>> = buildList {
		for (s in Supply.entries) for (d in listOf(0.0, 10.0, 14.9, 15.0, 15.1, 17.9, 18.0, 19.9, 20.0, 29.9, 30.0, 30.1))
			for ((ax, az) in listOf(1.0 to 0.0, 0.0 to 1.0, -1.0 to 0.0, 0.7071 to -0.7071)) add(s.x + ax * d to s.z + az * d)
		repeat(300) { add(r.nextDouble(-170.0, -40.0) to r.nextDouble(-180.0, -50.0)) }
	}
	private fun giantNear(): Pair<Double, Double> {
		val s = Supply.entries.pick()
		val d = r.nextDouble(0.0, 24.0)
		val a = r.nextDouble(0.0, 6.2832)
		return s.x + d * kotlin.math.cos(a) to s.z + d * kotlin.math.sin(a)
	}
	private val partyLines = listOf("Party > Steve: No Triangle!", "Party > [MVP+] Alex_99: No X!", "Party > [VIP] a: NoEquals!", "Party > b: No Slash!",
		"Party > [MVP++] cc: No xCannon!", "Party > dd: No Square!", "Party > ee: No Shop!", "Party > ff: No None!", "Party > gg: no shop!",
		"Party > [YOUTUBE] hh: FRESH", "Party > ii: FRESH", "Party > ii: FRESH!", "Party > aaaaaaaaaaaaaaaaa: FRESH", "Party > [MVP+]  jj: FRESH",
		"Guild > kk: FRESH", "Party > [MVP+] ll: No Triangle", " Kuudra's Hollow (T5)", "Kuudra's Hollow (T1) ", "Kuudra's Hollow (T)", "  ⏣ Kuudra's Hollow (T3)")

	private val slayerChat = listOf(
		"Wolf Slayer LVL 7 - Next LVL in 50,000 XP!", "Wolf Slayer LVL 7 - Next LVL in 49,000 XP!", "Wolf Slayer LVL 7 - Next LVL in 49,000 XP!",
		"Wolf Slayer LVL 8 - Next LVL in 120,000 XP!", "  Zombie Slayer LVL 9 - LVL MAXED OUT!", "Spider Slayer LVL 3 - Next LVL in 1,234 XP!",
		"Spider Slayer LVL 3 - Next LVL in 1,000 XP!", "Spider Slayer LVL 3 - Next LVL in 1,000 XP! extra", "Enderman Slayer LVL 0 - Next LVL in 5 XP!",
		"Wolf Slayer LVL - Next LVL in 5 XP!", "RNG Meter - 123,456 Stored XP", "   RNG Meter - 5 Stored XP", "RNG Meter - x Stored XP", "    RNG Meter - 1 Stored XP",
		"SLAYER QUEST COMPLETE!", "  NICE! SLAYER BOSS SLAIN!", "SLAYER QUEST FAILED!", "Your Slayer Quest has been cancelled!", "SLAYER QUEST STARTED!",
		"RARE DROP! (Foul Flesh) (+100% ✯ Magic Find)", "DROP! 2x Wolf Tooth", "PRAY TO RNGESUS DROP! (Overflux Capacitor)", "VERY RARE DROP!  (2x Revenant Flesh)",
		"slayer quest complete!", "SLAYER QUEST COMPLETE", "hello", "")
	private val boards = listOf(
		listOf("Slayer Quest", "Revenant Horror IV", "(10/1,500) Combat XP"), listOf("Slayer Quest", "Revenant Horror IV", "(900/1,500) Combat XP"),
		listOf("  Slayer Quest ", " Sven Packmaster III", "Slay the boss!"), listOf("Slayer Quest", "Sven Packmaster III", "Slay the boss!"),
		listOf("Slayer Quest", "Tarantula Broodfather II", "(1.2k/2.4k) Combat XP"), listOf("Slayer Quest"), listOf("Slayer Quest", "Voidgloom Seraph I"),
		listOf("Purse: 1,000", "Slayer Quest", "Inferno Demonlord IV", "(1,450/1,500) Combat XP"), emptyList(), listOf("Objective", "Something"))
	private val progresses = listOf(null, "(100/1,500) Combat XP", "(400/1,500) Combat XP", "(1,200/1,500) Combat XP", "(1,450/1,500) Combat XP", "(1.2k/2.4k) Combat XP",
		"Slay the boss!", "(0/0) Combat XP", "500/1500 Combat XP", "(1,500/1,500) Combat XP", "junk")

	private val combatChat = listOf("ENDER NODE! You found 3x Enchanted Ender Pearl!", "ENDER NODE! You found Magical Rune I!", "ENDER NODE! You found !",
		"You found an Endermite Nest!", "  ENDER NODE! You found 12x Enchanted Obsidian!  ", "☬ The Superior Dragon has spawned!", "☬ The Old Dragon has spawned!",
		"☬ The  Dragon has spawned!", "LEGENDARY DROP! Ender Dragon (Legendary)", "RARE DROP! (1x) Dragon Claw", "DROP! Summoning Eye", "EPIC DROP! Dragon Horn (+5% ✯ Magic Find)",
		"You laid an egg!", PlusCombatLogic.ARACHNE_CALLING, PlusCombatLogic.ARACHNE_CRYSTAL, "                ARACHNE DOWN!", "ARACHNE DOWN", "noise")
	private val summonChat = listOf("You have spawned your Revenant soul! (100 Mana)", "You have spawned your  soul! (100 Mana)", "You have despawned your monster!",
		"You have despawned your monsters!", "The Seraph recalled your 3 summoned allies!", " You have despawned your monster! ", "The Seraph recalled your x summoned allies!", "nope")

	private val islands = listOf(null, ServerLocation("s", "SKYBLOCK", "combat_3"), ServerLocation("s", "SKYBLOCK", "combat_1"), ServerLocation("s", "SKYBLOCK", "kuudra"),
		ServerLocation("s", "SKYBLOCK", "hub"), ServerLocation("s", "BEDWARS", "combat_3"))

	private fun chatEv(s: String) = ProcessChatEvent(Component.literal(s), false)

	private fun slayerOverlayRun(steps: Int): String {
		val log = StringBuilder()
		val opts = mapOf("track-pbs" to r.nextBoolean(), "pb-alert" to r.nextBoolean(), "drop-profit" to r.nextBoolean(), "boss-spawn-alert" to r.nextBoolean())
		val ops = List(steps) { if (r.nextInt(3) == 0) "B" + r.nextInt(boards.size) else "C" + r.nextInt(slayerChat.size) }
		val savedBoard = ScoreboardUtil.simplifiedScoreboardLines
		try {
			withAll(listOf(SlayerOverlay.TConfig to opts, Alerts.TConfig to mapOf("sounds" to false, "titles" to true))) {
				log.append(opts).append('\n')
				for (op in ops) {
					pinBossRecords()
					val res = Golden.run {
						if (op[0] == 'B') {
							ScoreboardUtil.simplifiedScoreboardLines = boards[op.drop(1).toInt()]
							SlayerOverlay.onTick(TickEvent(r.nextInt(100)))
						} else SlayerOverlay.onChat(chatEv(slayerChat[op.drop(1).toInt()]))
						"ok"
					}
					val o = SlayerOverlay
					log.append("$op $res pb=${Golden.run { SlayerOverlay.DConfig.data.bestBossMillis.keys.toString() }} q=${o.questName} p=${o.progress} qs=${o.questStart != null} bs=${o.bossSpawn != null} lb=${o.lastBossTime != null} " +
						"k=${o.bossesKilled} xp=${o.slayerXpLine} rng=${o.rngMeterLine} drops=${o.dropTracker.counters} ${io()}\n")
				}
			}
		} finally {
			ScoreboardUtil.simplifiedScoreboardLines = savedBoard
		}
		return log.toString()
	}

	private fun pinBossRecords() {
		runCatching { SlayerOverlay.DConfig.data.bestBossMillis.replaceAll { _, _ -> 0L } }
	}

	private fun slayerMergeRun(steps: Int): String {
		val log = StringBuilder()
		val opts = mapOf("item-profit-tracker" to r.nextBoolean(), "time-messages" to r.nextBoolean(), "quest-warning" to r.nextBoolean(),
			"boss-spawn-soon" to r.nextBoolean(), "boss-spawn-soon-percent" to listOf(50, 75, 90, 99).pick(), "remaining-kills" to r.nextBoolean(),
			"hide-rng-meter-chat" to r.nextBoolean())
		val ops = List(steps) {
			when (r.nextInt(6)) {
				0 -> "Q" + r.nextInt(4)
				1, 2 -> "P" + r.nextInt(progresses.size)
				3 -> "T" + listOf(0, 10, 20, 7, 30).pick()
				4 -> "W"
				else -> "C" + r.nextInt(slayerChat.size)
			}
		}
		val quests = listOf(null, "Revenant Horror IV", "Sven Packmaster III", "Voidgloom Seraph II")
		withAll(listOf(SlayerOverlay.TConfig to opts, Alerts.TConfig to mapOf("sounds" to false, "titles" to true))) {
			log.append(opts).append('\n')
			for (op in ops) {
				var hidden: Boolean? = null
				val res = Golden.run {
					val n = op.drop(1)
					when (op[0]) {
						'Q' -> SlayerOverlay.questName = quests[n.toInt()]
						'P' -> SlayerOverlay.progress = progresses[n.toInt()]
						'T' -> SlayerPlusMerges.onTick(TickEvent(n.toInt()))
						'W' -> SlayerPlusMerges.onWorld(WorldReadyEvent())
						else -> {
							val line = slayerChat[n.toInt()]
							SlayerPlusMerges.onChat(chatEv(line))
							val ev = AllowChatEvent(Component.literal(line))
							SlayerPlusMerges.onChatHide(ev)
							hidden = ev.cancelled
						}
					}
					"ok"
				}
				val hud = Golden.run {
					val rows = ArrayList<Component>()
					P2Opts.with(SlayerOverlay.TConfig, mapOf("item-profit-tracker" to false)) { SlayerPlusMerges.extraHudLines(rows) }
					rows.joinToString("|") { it.string }
				}
				log.append("$op $res hide=$hidden hud=$hud session=${Golden.run { SlayerPlusMerges.profit.session.show() }} ${io()}\n")
			}
		}
		return log.toString()
	}

	private fun combatRun(steps: Int): String {
		val log = StringBuilder()
		val sh = mapOf("ender-node-tracker" to r.nextBoolean(), "dragon-profit" to r.nextBoolean(), "summons-hide-chat" to r.nextBoolean())
		val gap = mapOf("arachne-kill-timer" to r.nextBoolean())
		val saved = SkyBlockSession.serverLocation
		try {
			withAll(listOf(CombatPlusPorts.TConfig to sh, GapFeatures2.TConfig to gap, Alerts.TConfig to mapOf("sounds" to false))) {
				log.append("$sh $gap\n")
				repeat(steps) {
					val res: String
					if (r.nextInt(5) == 0) {
						val i = r.nextInt(islands.size)
						SkyBlockSession.serverLocation = islands[i]
						res = "L$i"
					} else if (r.nextInt(4) == 0) {
						val line = summonChat.pick()
						val ev = AllowChatEvent(Component.literal(line))
						res = "S ${Golden.run { CombatPlusPorts.onChatHide(ev); "${ev.cancelled}" }}"
					} else {
						val line = combatChat.pick()
						res = "C${combatChat.indexOf(line)} " + Golden.run {
							CombatPlusPorts.onChat(chatEv(line))
							CombatPlusMerges.onChat(chatEv(line))
							"ok"
						}
					}
					if (r.nextInt(15) == 0) Golden.run { CombatPlusMerges.onWorld(WorldReadyEvent()); "w" }
					log.append("$res nodes=${Golden.run { CombatPlusPorts.enderNodes.session.show() }} dragon=${Golden.run { CombatPlusPorts.dragonLoot.session.show() }} ${io()}\n")
				}
			}
		} finally {
			SkyBlockSession.serverLocation = saved
		}
		return log.toString()
	}

	private fun kuudraRun(steps: Int): String {
		val log = StringBuilder()
		val opts = mapOf("crate-priority" to r.nextBoolean(), "crate-priority-advanced" to r.nextBoolean(), "pre-alert" to r.nextBoolean())
		val lines = partyLines + listOf("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!", "[NPC] Elle: OMG! Great work collecting my supplies!",
			"[NPC] Elle: Not again!", "[NPC] Elle: Head over to the main platform, I will join you when I get a bite!",
			"Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!")
		val saved = SkyBlockSession.serverLocation
		try {
			withAll(listOf(KuudraInfo.TConfig to opts)) {
				log.append("$opts\n")
				repeat(steps) {
					val res = if (r.nextInt(6) == 0) {
						val i = r.nextInt(islands.size); SkyBlockSession.serverLocation = islands[i]; "L$i"
					} else {
						val l = lines.pick()
						"C${lines.indexOf(l)} " + Golden.run { KuudraInfo.onChat(chatEv(l)); "ok" }
					}
					if (r.nextInt(10) == 0) Golden.run { KuudraInfo.onWorld(WorldReadyEvent()); "w" }
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
			takeChat(); Alerts.clear()
			for (h in listOf(SlayerOverlay.DConfig, SlayerPlusMerges.ProfitData, CombatPlusPorts.EnderNodeData, CombatPlusPorts.DragonData))
				if (!h.isLoaded) h.explicitDefaultLoad()
			add("holders") { listOf(SlayerOverlay.DConfig.data, SlayerPlusMerges.ProfitData.data.show(), CombatPlusPorts.EnderNodeData.data.show()) }

			add("supply") { Supply.entries.map { "${it.name}(${it.x},${it.z})" } }
			for (hp in hps) for (t in -1..6) for (sc in listOf(true, false)) add("hp|$hp|$t|$sc") { KuudraLogic.healthDisplay(hp, t, sc) }
			for (hp in hps.take(10)) add("hpdef|$hp") { KuudraLogic.healthDisplay(hp, 5) }
			for ((i, p) in spotPoints.withIndex()) add("pre|$i") { "${KuudraLogic.preSpotAt(p.first, p.second)} ${Supply.entries.map { s -> s.distSq(p.first, p.second) }}" }
			for (s in Supply.entries) {
				add("miss|$s|none") { KuudraLogic.missingCall(s, emptyList()) }
				repeat(60) { i ->
					val giants = List(r.nextInt(1, 6)) { giantNear() }
					add("miss|$s|$i") { KuudraLogic.missingCall(s, giants) }
				}
				for (o in Supply.entries) add("miss2|$s|$o") { KuudraLogic.missingCall(s, listOf(o.x.toDouble() to o.z.toDouble())) }
			}
			for (m in Supply.entries) for (p in Supply.entries) for (a in listOf(false, true)) add("crate|$m|$p|$a") { KuudraLogic.cratePriority(m, p, a) }
			for ((i, l) in partyLines.withIndex()) add("kre|$i") {
				listOf(KuudraLogic.partyMissingRegex.matchEntire(l)?.groupValues, KuudraLogic.partyFreshRegex.matchEntire(l)?.groupValues, KuudraLogic.tierRegex.find(l)?.groupValues)
			}
			add("kid") { KuudraInfo.identifier }
			for (i in 0 until 40) add("krun|$i") { kuudraRun(r.nextInt(5, 30)) }

			add("minis") { SlayerOverlay.minibosses }
			for (i in 0 until 400) add("sover|$i") { slayerOverlayRun(r.nextInt(3, 30)) }
			for (i in 0 until 300) add("smerge|$i") { slayerMergeRun(r.nextInt(3, 30)) }
			val toggles = listOf("blaze-dagger-helper", "fire-pillar", "blaze-clear-view", "enderman-beacon", "enderman-nukekubi", "line-to-seraph", "line-to-spider",
				"spider-egg-sacs", "vampire-twinclaws", "vampire-steak")
			for (mask in listOf(0, 1023) + toggles.indices.map { 1 shl it } + List(60) { r.nextInt(1024) }) add("any|$mask") {
				P2Opts.with(SlayerPlusPorts.TConfig, toggles.withIndex().associate { (i, t) -> t to (mask shr i and 1 == 1) }) {
					SlayerPlusPorts.anyEnabled().also { PlusOwnSlayerBoss.onTick(TickEvent(mask * 4)) }.toString() + " ${PlusOwnSlayerBoss.boss} ${PlusOwnSlayerBoss.tags}"
				}
			}

			for (i in 0 until 300) add("combat|$i") { combatRun(r.nextInt(3, 30)) }

			add("configs") { configShape(SlayerOverlay.TConfig, SlayerPlusPorts.TConfig, CombatPlusPorts.TConfig, KuudraInfo.TConfig) }
			add("configdeps") { configShape(AbilityCooldowns.TConfig, GapFeatures2.TConfig) }
		} finally {
			detach()
		}
		return out
	}
}
