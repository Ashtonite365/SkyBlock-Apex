// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.DungeonSplits
import dev.ashtonite365.apex.features.dungeon.DungeonStatusHuds
import dev.ashtonite365.apex.features.dungeon.DungeonStatusLogic
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.TerminalExtras
import dev.ashtonite365.apex.features.dungeon.TerminalSimulatorScreen
import dev.ashtonite365.apex.features.dungeon.TerminalSolver
import dev.ashtonite365.apex.features.dungeon.TerminalTracker
import dev.ashtonite365.apex.features.plus.dungeon.DungeonPlusPorts
import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonState
import dev.ashtonite365.apex.test.golden.Batch40Dungeon
import dev.ashtonite365.apex.test.golden.FakeMinecraft
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.kit.ServerTicks
import kotlin.random.Random
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.Holder
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Guardian
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch52GoldenTest {
	companion object {
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		fun <T> alloc(c: Class<T>): T = c.cast(unsafe.allocateInstance(c))
		private val alertField = Alerts::class.java.getDeclaredField("current").also { it.isAccessible = true }
		fun takeAlert(): String {
			val c = alertField.get(Alerts) as Component?
			alertField.set(Alerts, null)
			return c?.let(Harness::dump) ?: "-"
		}
		val dungeon = Harness.skyblock("dungeon")
		val locs = listOf(null, Harness.skyblock("hub"), dungeon)
		fun chatEv(s: String) = ProcessChatEvent(Component.literal(s), false)
		fun board(vararg lines: String) { ScoreboardUtil.simplifiedScoreboardLines = lines.toList(); ScoreboardUtil.scoreboardLines = lines.map { Component.literal(it) } }

		fun mask(s: String) = s.replace(Regex("\\d+\\.\\d+s"), "N.NNs").replace(Regex("(?<![\\d.])\\d+s\\b"), "Ns")
		fun sound(path: String, pitch: Float = 1f, volume: Float = 1f) =
			SoundReceiveEvent(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path))), SoundSource.MASTER, Vec3.ZERO, pitch, volume, 0L)

		private val logSink = mutableListOf<String>()
		private val logAppender = object : AbstractAppender("golden-b52-log", null, null, true, Property.EMPTY_ARRAY) {
			override fun append(event: LogEvent) { synchronized(logSink) { logSink += event.message.formattedMessage } }
		}.also { a -> a.start(); listOf("Apex/ApexWeirdos", "Apex/Boulder").forEach { (LogManager.getLogger(it) as org.apache.logging.log4j.core.Logger).addAppender(a) } }
		fun logs(block: () -> Unit): List<String> { synchronized(logSink) { logSink.clear() }; block(); return synchronized(logSink) { logSink.toList().also { logSink.clear() } } }

		private val titleField = Screen::class.java.getDeclaredField("title").also { it.isAccessible = true }
		private val menuField = AbstractContainerScreen::class.java.declaredFields.first { it.type == AbstractContainerMenu::class.java }
		private val slotsField = AbstractContainerMenu::class.java.declaredFields.first { it.name == "slots" }

		@Suppress("DEPRECATION")
		fun screen(title: String, items: List<ItemStack>): ContainerScreen {
			val s = alloc(ContainerScreen::class.java)
			titleField.set(s, Component.literal(title))
			val menu = alloc(ChestMenu::class.java)
			val box = SimpleContainer(items.size)
			items.forEachIndexed { i, it -> box.setItem(i, it) }
			val list = NonNullList.create<Slot>()
			for (i in items.indices) list.add(Slot(box, i, 0, 0).also { it.index = i })
			val inv = alloc(Inventory::class.java)
			for (i in 0 until 3) list.add(Slot(inv, i, 0, 0).also { it.index = items.size + i })
			unsafe.putObject(menu, unsafe.objectFieldOffset(slotsField), list)
			unsafe.putObject(s, unsafe.objectFieldOffset(menuField), menu)
			return s
		}
		fun head(name: String) = ItemStack(Items.PLAYER_HEAD).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }
	}

	@Test
	fun terminals() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5252)
		val pool = listOf("[BOSS] Goldor: Who dares trespass into my domain?", "The gate has been destroyed!", "The Core entrance is opening!",
			"[BOSS] Goldor: Who dares trespass into my domain", "The gate has been destroyed", " The Core entrance is opening!", "hello")
		fun comp(): String {
			val name = listOf("Steve", "Alex", "ThisNameIsTooLong12", "A", "Bob Bob").random(r)
			val act = listOf("activated", "completed", "finished").random(r)
			val type = listOf("terminal", "lever", "device", "door").random(r)
			val tot = listOf(4, 5, 7, 8, 10).random(r)
			val cur = r.nextInt(0, tot + 1)
			return "$name $act a $type! ($cur/$tot)" + listOf("", "", " ", "!").random(r)
		}
		for (seq in 0 until 120) {
			val t = TerminalTracker()
			var now = 1000L * seq
			for (step in 0 until 40) {
				val line = if (r.nextInt(3) == 0) pool.random(r) else comp()
				now += r.nextLong(0, 5000)
				val c = t.onChat(line, now)
				out += "tt|$seq|$step|$line" to "$c|${t.section},${t.terminals},${t.levers},${t.device},${t.gate},${t.active},${t.termsNeeded},${t.phaseStart},${t.sectionStart},${t.sectionTimes}"
				if (step == 30 && seq % 7 == 0) { t.reset(); out += "ttreset|$seq" to "${t.section},${t.active},${t.sectionTimes},${t.phaseStart}" }
			}
		}
		val templates = listOf("&6{name} &8[&c{current}&8/&a{total}&8]", "", "{name}{name}", "&z{type}&A{action}&k", "{current}/{total} &&6", "plain", "{NAME}")
		val comps = listOf(TerminalTracker.Completion("Steve", "activated", "terminal", 1, 4), TerminalTracker.Completion("&6x", "completed", "lever", 0, 0),
			TerminalTracker.Completion("{total}", "a", "b", -1, 99))
		for (tp in templates) for (c in comps) out += "fmt|$tp|$c" to TerminalTracker.formatTitle(tp, c)
		for (ms in listOf(0L, 1L, 5L, 999L, 1000L, 1005L, 12345L, -1L, -1500L, Long.MAX_VALUE / 8)) out += "sec|$ms" to TerminalTracker.seconds(ms)
		out += "regex" to listOf(TerminalTracker.completedRegex, TerminalTracker.goldorRegex, TerminalTracker.coreRegex, TerminalTracker.gateRegex).joinToString("\n") { it.pattern }

		val keys = listOf("splits", "titles", "title-self-only", "sounds")
		val script = listOf("hello", "Steve activated a terminal! (1/4)", "[BOSS] Goldor: Who dares trespass into my domain?", "Steve activated a terminal! (1/4)",
			"Alex completed a lever! (2/4)", "The gate has been destroyed!", "Steve completed a device! (4/4)", "Alex activated a terminal! (5/5)",
			"The gate has been destroyed!", "Steve activated a terminal! (3/5)", "The Core entrance is opening!", "Steve activated a terminal! (1/4)")
		FakeMinecraft.with("Steve") {
			for (loc in locs) for (mask in 0 until 16) Harness.withOpts(TerminalExtras.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
				Harness.at(loc) {
					TerminalExtras.onWorld(WorldReadyEvent())
					for ((i, line) in script.withIndex()) {
						var res = ""
						val chat = Harness.chat { res = Golden.run { TerminalExtras.onChat(chatEv(line)); "ok" } }
						val ev = ModifyChatEvent(Component.literal(line))
						val mod = Golden.run { TerminalExtras.onModifyChat(ev); ev.replaceWith?.let(Harness::dump) ?: "-" }
						val t = TerminalExtras.tracker
						out += "te|${loc?.mode}|$mask|$i|$line" to mask("$res|${chat.map(::mask)}|$mod|${t.section},${t.terminals},${t.levers},${t.device},${t.gate},${t.active},${t.sectionTimes.size}")
					}
				}
			}
			TerminalExtras.onWorld(WorldReadyEvent())
		}
		for (o in TerminalSolver.RubixModeOption.entries) out += "rubix|$o" to "${o.id}|${o.serializedName}|${o.mode}"
		for (m in TerminalSimulatorScreen.Mode.entries) out += "simmode|$m" to "${m.label}|${m.next()}"
		val n = Golden.verify("batch52-terminals", out, minCases = 5423)
		assertTrue(n >= 5423)
	}

	@Test
	fun status() {
		val out = mutableListOf<Pair<String, String>>()
		val L = DungeonStatusLogic
		val r = Random(5253)
		val romans = listOf("", "I", "II", "III", "IV", "V", "VI", "IX", "X", "XL", "XC", "C", "CD", "IIII", "IC", "VX", "LC", "MC", "i", "I I", "XIV", "XLIX", "CCC") +
			List(80) { (1..r.nextInt(1, 7)).map { "IVXLCx".random(r) }.joinToString("") }
		for (s in romans) out += "roman|$s" to "${L.romanToInt(s)}"
		val footers = listOf("", "Blessing of Power V", "Blessing of Power V Blessing of Time II\nBlessing of Stone IX", "Blessing of Power V, Blessing of Power X",
			"Blessing of Life IIII Blessing of Wisdom XL", "Blessing of power V", "Blessing of Power", "Blessing of Time M", "xBlessing of Stone IVy",
			"Blessing of Life I Blessing of Power III Blessing of Time II") +
			List(60) { (0 until r.nextInt(0, 6)).joinToString(" ") { "Blessing of ${listOf("Power", "Time", "Stone", "Life", "Wisdom", "Luck").random(r)} ${romans.random(r)}" } }
		for (f in footers) out += "bless|$f" to L.parseBlessings(f).toString()
		val lores = listOf(listOf(), listOf("Charges: 5/20⸕"), listOf("x", "Charges: 0/0⸕", "Charges: 3/4⸕"), listOf("Charges: 5/20"), listOf("Charges: a/20⸕"),
			listOf("§7Charges: 12/20⸕ extra"), listOf("Cooldown: 30s"), listOf("Cooldown: 30s", "Cooldown: 12s"), listOf("Cooldown: 30s ", "Cooldown: 99999999999s"),
			listOf("Cooldown: s"), listOf("Cooldown: 7s", "x"))
		for (l in lores) out += "lore|$l" to "${L.parseCharges(l)}|${L.loreCooldown(l)}"
		val inv = listOf("Second Wind Activated! Your Spirit Mask saved your life!", "Your Bonzo's Mask saved your life!", "Your ⚚ Bonzo's Mask saved your life!",
			"Your Phoenix Pet saved you from certain death!", "Your xx Bonzo's Mask saved your life!", " Your Bonzo's Mask saved your life!", "nope")
		for (e in DungeonStatusLogic.Invincibility.entries) e.reset()
		for (s in inv) out += "invmatch|$s" to "${DungeonStatusLogic.Invincibility.match(s)}"
		for (e in DungeonStatusLogic.Invincibility.entries) {
			for (ov in listOf(null, 0, 5, -1)) {
				e.proc(ov)
				val trace = (0 until 70).map { e.tick(); "${e.active}/${e.cooldown}" }.filterIndexed { i, _ -> i % 7 == 0 }
				out += "invtick|$e|$ov" to "${e.label},${e.activeTicks},${e.cooldownSeconds},$trace"
				e.reset()
			}
		}

		for (loc in locs) for (alert in listOf(false, true)) Harness.withOpts(DungeonStatusHuds.TConfig, mapOf("invincibility-alert" to alert)) {
			Harness.at(loc) {
				DungeonStatusHuds.onWorld(WorldReadyEvent())
				for ((i, s) in (inv + inv).withIndex()) {
					val res = Golden.run { DungeonStatusHuds.onChat(chatEv(s)); "ok" }
					val tick = Golden.run { DungeonStatusHuds.onTick(TickEvent(i)); "ok" }
					out += "shud|${loc?.mode}|$alert|$i|$s" to "$res|$tick|" + DungeonStatusLogic.Invincibility.entries.joinToString(",") { "${it.active}/${it.cooldown}" }
				}
				DungeonStatusHuds.onWorld(WorldReadyEvent())
				out += "shudw|${loc?.mode}|$alert" to DungeonStatusLogic.Invincibility.entries.joinToString(",") { "${it.active}/${it.cooldown}" }
			}
		}
		val n = Golden.verify("batch52-status", out, minCases = 283)
		assertTrue(n >= 283)
	}

	private fun splitsState(): String {
		val run = DungeonSplits::class.java.getDeclaredField("run").also { it.isAccessible = true }.get(DungeonSplits) ?: return "idle"
		fun f(o: Any, n: String) = o.javaClass.getDeclaredField(n).also { it.isAccessible = true }.get(o)
		@Suppress("UNCHECKED_CAST") val marks = f(run, "marks") as Map<DungeonSplits.Split, Any>
		val startTick = f(run, "startTick") as Long
		val boss = f(run, "boss") as dev.ashtonite365.apex.features.kit.splits.SplitRun?
		return "run ${marks.keys} ${marks.keys.map { (f(marks.getValue(it), "tick") as Long) - startTick }} boss=${boss?.rows(0L, ServerTicks.totalTicks)?.map { "${it.name}:${it.ticks}:${it.isCurrent}" }}"
	}

	@Test
	fun splits() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5254)
		val lines = listOf("[NPC] Mort: Here, I found this map when I first entered the dungeon.", "The BLOOD DOOR has been opened!",
			"[BOSS] The Watcher: You have proven yourself. You may pass.", "[BOSS] The Watcher: Oh.. hello?", "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
			"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "[BOSS] Storm: Pathetic Maxor, just like expected.", "[BOSS] Goldor: Who dares trespass into my domain?",
			"[BOSS] Necron: You went further than any human before, congratulations.", "                             > EXTRA STATS <", "> EXTRA STATS <",
			"  ☠ Defeated Maxor, Storm, Goldor, and Necron in 05m12s", "Team Score: 300 (S+)", "Team Score: 280 (S) (NEW RECORD!)", "+1,234.5 Catacombs Experience",
			"+20 Bits", "Total Damage as Mage: 12,345,678 (NEW RECORD!)", "Ally Healing: 1,000", "Enemies Killed: 120", "Deaths: 2", "Secrets Found: 40",
			"▬▬▬▬▬▬▬▬", "hello", " The BLOOD DOOR has been opened! ", "[BOSS] Sadan: So you made it all the way here...")
		ServerTicks.reset()
		val keys = listOf("boss-phases", "run-summary", "auto-extra-stats", "tick-time")
		for (loc in listOf(null, dungeon)) for (b in listOf(listOf(), listOf(" ⏣ The Catacombs (F7)"), listOf("The Catacombs (M3)"))) for (mask in 0 until 16) {
			board(*b.toTypedArray())
			Harness.at(loc) { PlusDungeonState.onWorld(WorldReadyEvent()); PlusDungeonState.onTick(TickEvent(0)) }
			Harness.withOpts(DungeonSplits.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
				Harness.at(loc) {
					for (i in 0 until 50) {
						val line = if (i == 0) lines[0] else lines.random(r)
						repeat(r.nextInt(0, 200)) { ServerTicks.accept(it % 2) }
						val chat = Harness.chat { out += "split-run|${loc?.mode}|$b|$mask|$i" to Golden.run { DungeonSplits.onChat(chatEv(line)); "ok" } }
						out += "split|${loc?.mode}|$b|$mask|$i|$line" to "$chat|${splitsState()}"
					}
				}
			}
		}
		board()
		Harness.at(null) { PlusDungeonState.onWorld(WorldReadyEvent()) }
		for (fixed in listOf(false, true)) for (rows in listOf(listOf("§eA:" to "1", "B" to "§a2s"), listOf("x" to ""), listOf("" to "")))
			out += "layout|$fixed|$rows" to Golden.run { DungeonSplits.layoutRows(rows, fixed).map(Harness::dump).toString() }
		for (s in DungeonSplits.Split.entries) out += "splitenum|$s" to s.label
		val n = Golden.verify("batch52-splits", out, minCases = 9600)
		assertTrue(n >= 9600)
	}

	@Test
	fun ports() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5255)
		val D = DungeonPlusPorts
		val S = PlusDungeonState
		val lines = Batch40Dungeon.lines + listOf("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", "Steve entered The Catacombs, Floor VII!",
			"Alex entered MM The Catacombs, Floor III!")
		fun st() = "${S.floor},${S.inBoss},${S.bossName},${S.runStarted != null}"

		for (loc in locs) Harness.at(loc) {
			S.onWorld(WorldReadyEvent())
			for ((bi, b) in Batch40Dungeon.boards.withIndex()) for (t in listOf(0, 7, 20, 40)) {
				board(*b.toTypedArray()); S.onTick(TickEvent(t)); out += "sfloor|${loc?.mode}|$bi|$t" to st()
			}
			for (line in lines) { S.onChat(chatEv(line)); out += "schat|${loc?.mode}|$line" to st() }
			S.onWorld(WorldReadyEvent()); out += "sworld|${loc?.mode}" to st()
		}
		board()
		for (i in 0 until 300) {
			val id = listOf("block.chest.open", "block.note_block.harp", "block.anvil.break", "block.lever.click", "block.note_block.pling", "x").random(r)
			val vol = listOf(0.5f, 1f, 2f, 0.4f, 1.0001f).random(r)
			val pitch = listOf(0.7936508f, 0.8888889f, 1f, 1.0952381f, 1.1904762f, 1.6984127f, 0.4920635f, 0.49207f, 0.5f, 1.00005f, 1.0002f).random(r)
			out += "secret|$id|$vol|$pitch" to "${D.isSecretSound(id, vol, pitch)}"
		}
		val keys = listOf("blood-timer", "death-counter", "milestones", "clean-end", "hide-boss-messages", "chat-filter", "chat-filter-damage", "chat-filter-ability",
			"architect-notifier", "creation-cooldown", "creation-cooldown-block", "mute-secret-sounds", "terracotta-hide-particles", "hide-items-in-boss",
			"hide-damage-splash-in-boss", "terracotta-hide-damage-splash", "low-health-alert", "shadow-assassin", "clean-end-f3-guardians", "trinity-helper")
		val ents: List<Pair<String, () -> Entity>> = listOf("stand" to { alloc(ArmorStand::class.java) }, "item" to { alloc(ItemEntity::class.java) },
			"guardian" to { alloc(Guardian::class.java) }, "player" to { alloc(RemotePlayer::class.java) })
		FakeMinecraft.with("Steve") {
			Harness.withOpts(Alerts.TConfig, mapOf("titles" to true, "sounds" to false)) {
				for (loc in listOf(null, dungeon)) for (round in 0 until 24) {
					val opts = keys.associateWith { r.nextBoolean() }
					val floorBoard = listOf(" ⏣ The Catacombs (F6)", "The Catacombs (F3)", "The Catacombs (M7)", "").random(r)
					Harness.withOpts(DungeonPlusPorts.TConfig, opts) {
						Harness.at(loc) {
							takeAlert()
							D.onWorld(WorldReadyEvent()); S.onWorld(WorldReadyEvent())
							board(floorBoard, "§e[H] §bFlameeke §c8,998§c❤", "§e[M] §bSteve §cDEAD", "§e[T] §b§lSteve§r §c§l1,000")
							S.onTick(TickEvent(0))
							val seq = List(60) { lines.random(r) }
							for ((i, line) in seq.withIndex()) {
								S.onChat(chatEv(line))
								var res = ""
								val chat = Harness.chat { res = Golden.run { D.onChatData(chatEv(line)); "ok" } }
								val allow = AllowChatEvent(Component.literal(line))
								val hide = Golden.run { D.onChatHide(allow); "${allow.cancelled}" }
								val tick = Harness.chat { res += "/" + Golden.run { D.onTick(TickEvent(i)); "ok" } }
								val pe = ParticleSpawnEvent(ParticleTypes.CRIT, Vec3.ZERO, Vector3f(), false, 1, 0f)
								val part = Golden.run { D.onParticle(pe); "${pe.cancelled}" }
								val sounds = listOf(sound("block.note_block.harp", 1f, 1f), sound("block.chest.open", 1f, 0.5f), sound("block.note_block.bass"), sound("entity.generic.eat"))
									.joinToString(",") { e -> Golden.run { D.onSound(e); "${e.cancelled}" } }
								val hidden = ents.joinToString(",") { (n, e) -> "$n=" + Golden.run { "${D.shouldHide(e())}" } }
								val cmd = OutgoingPacketEvent(ServerboundChatCommandPacket(listOf("joininstance catacombs_floor_one", "warp hub").random(r)))
								val cmdChat = Harness.chat { res += "/" + Golden.run { D.onCommand(cmd); "${cmd.cancelled}" } }
								out += "dp|${loc?.mode}|$round|$i|$line" to mask("$res|$chat|$hide|$tick|$part|$sounds|$hidden|${cmdChat}|${takeAlert()}|${st()}")
							}
						}
					}
				}
			}
		}
		board()
		Harness.at(null) { D.onWorld(WorldReadyEvent()); S.onWorld(WorldReadyEvent()) }
		val n = Golden.verify("batch52-ports", out, minCases = 3935)
		assertTrue(n >= 3935)
	}

	@Test
	fun puzzles() {
		val out = mutableListOf<Pair<String, String>>()
		val P = PuzzleSolvers
		val r = Random(5256)
		if (runCatching { dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.data }.isFailure) dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.explicitDefaultLoad()
		Harness.load(dev.ashtonite365.apex.features.items.ItemCustomizer.TConfig)
		val keysPool = listOf("leap-class-keybinds", "blaze", "blaze-highest-first", "ice-fill", "blaze-solver", "ice-fill-solver", "spirit-leap-keybinds",
			"water-board", "water-board-mode", "water-board-hud", "water-board-solver", "creeper-beams", "creeper-beams-solver", "other")
		val vals = listOf(JsonPrimitive(true), JsonPrimitive(false), JsonPrimitive("true"), JsonPrimitive(1), JsonObject(emptyMap()))
		val migrate = PuzzleSolvers.TConfig::class.java.declaredMethods.firstOrNull { it.name == "migrate" }?.also { it.isAccessible = true }
		for (i in 0 until 400) {
			val o = JsonObject(keysPool.shuffled(r).take(r.nextInt(0, 6)).associateWith { vals.random(r) })
			out += "fold|$o" to listOf(P.foldLeapClassKeybinds(o), P.foldOldBlaze(o), P.foldOldIceFill(o), P.foldOldWaterBoard(o),
				Golden.run { migrate!!.invoke(PuzzleSolvers.TConfig, o).toString() }).joinToString(" | ")
		}
		out += "consts" to "${P.OLD_LEAP_CLASS_KEY},${P.OLD_BLAZE_KEY},${P.OLD_BLAZE_HIGHEST_KEY},${P.OLD_ICE_FILL_KEY},${P.OLD_WATER_BOARD_KEYS}"
		for (s in PuzzleSolvers.LeapSort.entries) out += "leapsort|$s" to "${s.id}|${s.serializedName}"
		for (i in 0 until 200) {
			val v = DoubleArray(4) { r.nextDouble(-300.0, 100.0) }
			out += "room|${i}" to "${P.inStarredRoom(false, v[0], v[1], v[2], v[3])},${P.inStarredRoom(true, v[0], v[1], v[2], v[3])}"
		}

		val names = listOf("Steve", "alex", "Bob", "zed", "Ann", "§aMage", "carl")
		for (title in listOf("Spirit Leap", " Infinileap ", "Spirit leap", "Chest")) for (n in 0 until 7) for (sort in PuzzleSolvers.LeapSort.entries) {
			val items = List(n + 2) { i -> if (i % 3 == 2) ItemStack(Items.STONE) else head(names[(i + n) % names.size]) }
			Harness.withOpts(PuzzleSolvers.TConfig, mapOf("leap-sort" to sort)) {
				out += "leap|$title|$n|$sort" to Golden.run { P.leapHeads(screen(title, items))?.map { "${it.index}:${it.item.hoverName.string}" }.toString() }
			}
		}

		val leapLines = listOf("You have teleported to Steve!", "You have teleported to S!", "You have teleported to ThisNameIsTooLong12!", " You have teleported to Alex! ", "You have teleported to Bob Bob!")
		val weird = listOf("[NPC] Baxter: The reward is not in my chest!", "[NPC] Ardis: My chest has the reward and I'm telling the truth!",
			"[NPC] Hope: One of us is telling the truth!", "[NPC] Baxter: hello there", "PUZZLE SOLVED! Steve wasn't fooled by Baxter! Good job!",
			"[NPC] Ardis: The reward isn't in any of our chests.", "§e[NPC] §cBaxter§f: We are all telling the truth!", "[NPC] Hope: Both of them are telling the truth. Also, Ardis has the reward in their chest!")
		Harness.withOpts(Alerts.TConfig, mapOf("titles" to true, "sounds" to false)) {
			takeAlert()
			for (loc in locs) for (mask in 0 until 4) Harness.withOpts(PuzzleSolvers.TConfig, mapOf("leap-alert" to (mask and 1 == 1), "three-weirdos" to (mask and 2 == 2))) {
				Harness.at(loc) {
					P.onWeirdosWorld(WorldReadyEvent())
					for (l in leapLines) out += "leapalert|${loc?.mode}|$mask|$l" to Golden.run { P.onLeapChat(chatEv(l)); takeAlert() }
					for (k in 0 until 3) for (w in weird.shuffled(r)) {
						val log = logs { out += "weird-run|${loc?.mode}|$mask|$k|$w" to Golden.run { P.onChat(chatEv(w)); "ok" } }
						out += "weird|${loc?.mode}|$mask|$k|$w" to log.toString()
					}
					out += "boulder|${loc?.mode}|$mask" to Golden.run { logs { P.onBoulderTick(TickEvent(10)); P.onBoulderPathTick(TickEvent(2)); P.onStarredTick(TickEvent(5)) }.toString() }
				}
			}
			P.onWeirdosWorld(WorldReadyEvent())
		}
		val n = Golden.verify("batch52-puzzles", out, minCases = 1297)
		assertTrue(n >= 1297)
	}
}
