// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.chat

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object BangShortcuts {

	@Config
	object TConfig : ManagedConfig("bang-shortcuts", Category.CHAT) {
		val enabled by toggle("enabled") { false }
		val warp by toggle("warp") { true }
		val allInvite by toggle("allinvite") { true }
		val transfer by toggle("transfer") { true }
		val promote by toggle("promote") { true }
		val demote by toggle("demote") { true }
		val kick by toggle("kick") { true }
		val kickOffline by toggle("kickoffline") { true }
		val invite by toggle("invite") { true }
		val reinvite by toggle("reinvite") { true }
		val instances by toggle("instances") { true }
		val coords by toggle("coords") { true }
		val fps by toggle("fps") { true }
		val location by toggle("location") { true }
		val holding by toggle("holding") { true }
		val time by toggle("time") { true }
		val coinFlip by toggle("coinflip") { true }
		val dice by toggle("dice") { true }
		val eightBall by toggle("eightball") { true }
		val downtime by toggle("downtime") { true }
		val help by toggle("help") { true }
		val othersPrompts by toggle("others-prompts") { false }
		val dmInvitePrompt by toggle("dm-invite-prompt") { false }
	}

	enum class Args { NONE, NAME, TEXT }

	enum class Kind { PARTY_COMMAND, SAY, LOCAL }

	enum class Shortcut(val names: List<String>, val args: Args, val kind: Kind, val toggle: () -> Boolean, val summary: String) {
		WARP(listOf("warp", "w", "pw", "pwarp", "partywarp"), Args.NONE, Kind.PARTY_COMMAND, { TConfig.warp }, "/p warp"),
		ALL_INVITE(listOf("allinvite", "allinv"), Args.NONE, Kind.PARTY_COMMAND, { TConfig.allInvite }, "/p settings allinvite"),
		TRANSFER(listOf("pt", "ptme", "transfer"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.transfer }, "/p transfer <name>"),
		PROMOTE(listOf("promote"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.promote }, "/p promote <name>"),
		DEMOTE(listOf("demote"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.demote }, "/p demote <name>"),
		KICK(listOf("kick", "k"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.kick }, "/p kick <name>"),
		KICK_OFFLINE(listOf("kickoffline", "ko"), Args.NONE, Kind.PARTY_COMMAND, { TConfig.kickOffline }, "/p kickoffline"),
		INVITE(listOf("invite", "inv"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.invite }, "/p invite <name>"),
		REINVITE(listOf("reinvite", "reinv"), Args.NAME, Kind.PARTY_COMMAND, { TConfig.reinvite }, "/p invite <name>"),
		INSTANCE(instanceIds.keys.toList(), Args.NONE, Kind.PARTY_COMMAND, { TConfig.instances }, "/joininstance <floor>"),
		COORDS(listOf("coords", "co"), Args.NONE, Kind.SAY, { TConfig.coords }, "your coordinates"),
		FPS(listOf("fps"), Args.NONE, Kind.SAY, { TConfig.fps }, "your FPS"),
		LOCATION(listOf("location"), Args.NONE, Kind.SAY, { TConfig.location }, "your island"),
		HOLDING(listOf("holding"), Args.NONE, Kind.SAY, { TConfig.holding }, "your held item"),
		TIME(listOf("time"), Args.NONE, Kind.SAY, { TConfig.time }, "your local time"),
		COIN_FLIP(listOf("cf"), Args.NONE, Kind.SAY, { TConfig.coinFlip }, "heads or tails"),
		DICE(listOf("dice", "roll"), Args.NONE, Kind.SAY, { TConfig.dice }, "a 1-6 roll"),
		EIGHT_BALL(listOf("8ball"), Args.TEXT, Kind.SAY, { TConfig.eightBall }, "an 8-ball answer"),
		DOWNTIME(listOf("dt", "downtime"), Args.TEXT, Kind.LOCAL, { TConfig.downtime }, "local end-of-run reminder"),
		UNDO_DOWNTIME(listOf("undt", "undowntime"), Args.NONE, Kind.LOCAL, { TConfig.downtime }, "clear the reminder"),
		HELP(listOf("help"), Args.NONE, Kind.LOCAL, { TConfig.help }, "this list"),
		;

		val label: String get() = if (this == INSTANCE) "!f1-7 / !m1-7 / !t1-5" else names.joinToString(" / ") { "!$it" }

		companion object {
			val byName: Map<String, Shortcut> = entries.flatMap { s -> s.names.map { it to s } }.toMap()
		}
	}

	val instanceIds: Map<String, String> by lazy {
		buildMap {
			QuickCommands.dungeonLevelNames.forEachIndexed { i, floor ->
				put("f${i + 1}", "CATACOMBS_FLOOR_$floor")
				put("m${i + 1}", "MASTER_CATACOMBS_FLOOR_$floor")
			}
			QuickCommands.kuudraLevelNames.forEachIndexed { i, tier -> put("t${i + 1}", "KUUDRA_$tier") }
		}
	}

	data class Channel(val prefix: String?, val party: Boolean) {
		fun line(text: String): String = if (prefix == null) text else "$prefix $text"
	}

	data class Match(val shortcut: Shortcut, val word: String, val arg: String, val channel: Channel)

	sealed interface Outcome {

		data class Command(val command: String) : Outcome

		data class Say(val line: String) : Outcome

		data class Local(val text: String) : Outcome
	}

	interface Env {
		fun coords(): String
		fun fps(): Int
		fun location(): String
		fun holding(): String
		fun now(): ZonedDateTime = ZonedDateTime.now()
		fun millis(): Long = System.currentTimeMillis()
		val selfName: String
		val random: Random get() = Random.Default
	}

	object GameEnv : Env {
		override val selfName: String get() = Client.playerName

		override fun coords(): String {
			val pos = Client.player?.blockPosition() ?: return "unknown"
			return "x: ${pos.x}, y: ${pos.y}, z: ${pos.z}"
		}

		override fun fps(): Int = Client.game.fps

		override fun location(): String {
			val here = SkyBlockSession.serverLocation ?: return "unknown"
			return here.map ?: here.skyblockIsland?.toString() ?: here.mode ?: here.server
		}

		override fun holding(): String {
			val stack = Client.player?.mainHandItem ?: return "nothing"
			return if (stack.isEmpty) "nothing" else stack.hoverName.bareText
		}
	}

	var env: Env = GameEnv

	private val playerName = Regex("[A-Za-z0-9_]{1,16}")

	private val chatCommands = mapOf("pc" to 0, "gc" to 0, "ac" to 0, "cc" to 0, "oc" to 0, "r" to 0, "msg" to 1, "w" to 1, "tell" to 1)

	private val partyChannels = setOf("pc")

	fun matchText(text: String, channel: Channel): Match? {
		val body = text.trim()
		if (body.length < 2 || body[0] != '!') return null
		val word = body.substring(1).substringBefore(' ').lowercase()
		val arg = body.substring(1).substringAfter(' ', "").trim()
		val shortcut = Shortcut.byName[word] ?: return null
		when (shortcut.args) {
			Args.NONE -> if (arg.isNotEmpty()) return null
			Args.NAME -> if (!playerName.matches(arg)) return null
			Args.TEXT -> Unit
		}
		if (shortcut.kind == Kind.PARTY_COMMAND && !channel.party) return null
		return Match(shortcut, word, arg, channel)
	}

	fun matchChat(message: String): Match? = matchText(message, Channel(null, true))

	fun matchCommand(command: String): Match? {
		val words = command.split(' ')
		val head = words.first().lowercase()
		val targets = chatCommands[head] ?: return null
		if (words.size < 2 + targets) return null
		val prefix = words.take(1 + targets).joinToString(" ")
		val text = words.drop(1 + targets).joinToString(" ")
		return matchText(text, Channel(prefix, head in partyChannels))
	}

	fun isOn(match: Match): Boolean = TConfig.enabled && match.shortcut.toggle()

	private val eightBallAnswers = listOf(
		"Absolutely.", "No chance.", "Ask after the next run.", "The RNG gods say yes.", "The RNG gods say no.",
		"Only if you stop mining.", "Looks likely.", "Doubtful.", "Try again with more magic find.", "100%.",
		"Not today.", "Flip a coin instead.",
	)

	val downtimeReminders: MutableMap<String, String> = linkedMapOf()

	val downtimeReason: String? get() = downtimeReminders[SELF]

	private const val SELF = "you"

	fun clearReminders() = downtimeReminders.clear()

	fun resolve(match: Match): Outcome {
		val e = env
		val arg = match.arg
		fun say(text: String) = Outcome.Say(match.channel.line(text))
		return when (match.shortcut) {
			Shortcut.WARP -> Outcome.Command("p warp")
			Shortcut.ALL_INVITE -> Outcome.Command("p settings allinvite")
			Shortcut.TRANSFER -> Outcome.Command("p transfer $arg")
			Shortcut.PROMOTE -> Outcome.Command("p promote $arg")
			Shortcut.DEMOTE -> Outcome.Command("p demote $arg")
			Shortcut.KICK -> Outcome.Command("p kick $arg")
			Shortcut.KICK_OFFLINE -> Outcome.Command("p kickoffline")
			Shortcut.INVITE, Shortcut.REINVITE -> Outcome.Command("p invite $arg")
			Shortcut.INSTANCE -> Outcome.Command("joininstance ${instanceIds.getValue(match.word)}")
			Shortcut.COORDS -> say(e.coords())
			Shortcut.FPS -> say("FPS: ${e.fps()}")
			Shortcut.LOCATION -> say("Location: ${e.location()}")
			Shortcut.HOLDING -> say("Holding: ${e.holding()}")
			Shortcut.TIME -> say("Time: " + e.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")))
			Shortcut.COIN_FLIP -> say("Coin flip: " + if (e.random.nextBoolean()) "heads" else "tails")
			Shortcut.DICE -> say("Rolled a ${e.random.nextInt(1, 7)}")
			Shortcut.EIGHT_BALL -> say("8-ball: " + eightBallAnswers[e.random.nextInt(eightBallAnswers.size)])
			Shortcut.DOWNTIME -> {
				val reason = arg.ifEmpty { NO_REASON }
				downtimeReminders[SELF] = reason
				Outcome.Local("Downtime reminder set ($reason); shown here when the run ends. Nothing is sent.")
			}
			Shortcut.UNDO_DOWNTIME -> {
				val had = downtimeReminders.remove(SELF) != null
				Outcome.Local(if (had) "Downtime reminder cleared." else "No downtime reminder was set.")
			}
			Shortcut.HELP -> Outcome.Local(helpText())
		}
	}

	fun helpText(): String = "Typed ! shortcuts (only what you type yourself; party ones in chat or /pc):\n" +
		Shortcut.entries.joinToString("\n") { s -> (if (s.toggle()) "" else "(off) ") + "${s.label} -> ${s.summary}" }

	fun outcomeFor(match: Match?): Outcome? = match?.takeIf(::isOn)?.let(::resolve)

	private var pendingSay: Pair<String, String>? = null

	fun onTypedChat(message: String, send: (String) -> Unit, show: (String) -> Unit): Boolean =
		onTyped(message, matchChat(message), send, show)

	fun onTypedCommand(command: String, show: (String) -> Unit): Boolean =
		onTyped(command, matchCommand(command), null, show)

	private fun onTyped(typed: String, match: Match?, send: ((String) -> Unit)?, show: (String) -> Unit): Boolean {
		pendingSay = null
		val command = match?.channel?.prefix != null
		return when (val outcome = outcomeFor(match)) {
			null -> true
			is Outcome.Say -> { pendingSay = typed to outcome.line; true }
			is Outcome.Local -> { show(outcome.text); false }

			is Outcome.Command -> if (command || send == null) { pendingSay = typed to outcome.command; true } else { send(outcome.command); false }
		}
	}

	fun modified(typed: String): String {
		val pending = pendingSay
		pendingSay = null
		return if (pending != null && pending.first == typed) pending.second else typed
	}

	private fun showLocal(text: String) {
		for (line in text.lines()) Client.showChat(Component.literal("§6[!] §r$line"))
	}

	init {
		ClientSendMessageEvents.ALLOW_CHAT.register { typed -> onTypedChat(typed, { Client.runCommand(it) }, ::showLocal) }
		ClientSendMessageEvents.MODIFY_CHAT.register { typed -> modified(typed) }
		ClientSendMessageEvents.ALLOW_COMMAND.register { typed -> onTypedCommand(typed, ::showLocal) }
		ClientSendMessageEvents.MODIFY_COMMAND.register { typed -> modified(typed) }
	}

	fun isRunEnd(line: String): Boolean = "> EXTRA STATS <" in line || line.trim() == "KUUDRA DOWN!"

	private const val NO_REASON = "no reason given"

	data class Prompt(val sender: String, val typed: String, val command: String, val text: String)

	val othersShortcuts: Set<Shortcut> = setOf(
		Shortcut.WARP, Shortcut.ALL_INVITE, Shortcut.TRANSFER, Shortcut.KICK, Shortcut.KICK_OFFLINE, Shortcut.PROMOTE,
		Shortcut.DEMOTE, Shortcut.REINVITE, Shortcut.INSTANCE, Shortcut.DOWNTIME, Shortcut.COORDS, Shortcut.COIN_FLIP,
		Shortcut.EIGHT_BALL, Shortcut.DICE, Shortcut.FPS, Shortcut.TIME, Shortcut.LOCATION, Shortcut.HOLDING,
	)

	private val defaultsToSender = setOf(Shortcut.TRANSFER, Shortcut.PROMOTE, Shortcut.DEMOTE, Shortcut.REINVITE)

	const val REMINDER_COMMAND = "apex dtreminder"

	fun promptForParty(sender: String, message: String): Prompt? {
		val body = message.trim()
		if (body.length < 2 || body[0] != '!') return null
		val word = body.substring(1).substringBefore(' ').lowercase()
		val arg = body.substring(1).substringAfter(' ', "").trim()
		if (word == "boop") {
			if (!playerName.matches(arg)) return null
			return Prompt(sender, body, "/boop $arg", "$sender asked: $body, click to run /boop $arg")
		}
		val shortcut = Shortcut.byName[word]?.takeIf { it in othersShortcuts } ?: return null
		if (!shortcut.toggle()) return null
		val name = when (shortcut.args) {
			Args.NONE -> if (arg.isNotEmpty()) return null else ""
			Args.NAME -> when {
				playerName.matches(arg) -> arg
				arg.isEmpty() && shortcut in defaultsToSender -> sender
				else -> return null
			}
			Args.TEXT -> arg
		}
		if (shortcut == Shortcut.DOWNTIME) {
			val reason = arg.ifEmpty { NO_REASON }
			return Prompt(sender, body, "/$REMINDER_COMMAND $sender $reason", "$sender asked: $body, click to set a local end-of-run reminder (nothing is sent)")
		}
		val outcome = resolve(Match(shortcut, word, name, Channel("pc", true)))
		val command = when (outcome) {
			is Outcome.Command -> outcome.command
			is Outcome.Say -> outcome.line
			is Outcome.Local -> return null
		}
		val verb = if (outcome is Outcome.Say) "reply in party chat: ${command.removePrefix("pc ")}" else "run /$command"
		return Prompt(sender, body, "/$command", "$sender asked: $body, click to $verb")
	}

	fun promptForDm(sender: String, message: String): Prompt? {
		val body = message.trim()
		if (body.lowercase() != "!invite" && body.lowercase() != "!inv") return null
		return Prompt(sender, body, "/p invite $sender", "Invite $sender to party")
	}

	fun promptFor(incoming: PartyCommands.Incoming): Prompt? {
		if (!TConfig.enabled) return null
		if (incoming.name.equals(env.selfName, ignoreCase = true)) return null
		return when (incoming.channel) {
			PartyCommands.Channel.PARTY -> if (TConfig.othersPrompts) promptForParty(incoming.name, incoming.message) else null
			PartyCommands.Channel.PRIVATE -> if (TConfig.dmInvitePrompt) promptForDm(incoming.name, incoming.message) else null
			else -> null
		}
	}

	object PromptLimit {
		const val PER_SENDER_MS = 3_000L
		const val WINDOW_MS = 10_000L
		const val BURST = 4
		private val lastBySender = HashMap<String, Long>()
		private val recent = ArrayDeque<Long>()

		fun allow(sender: String, now: Long): Boolean {
			while (recent.isNotEmpty() && now - recent.first() >= WINDOW_MS) recent.removeFirst()
			val last = lastBySender[sender.lowercase()]
			if (last != null && now - last < PER_SENDER_MS) return false
			if (recent.size >= BURST) return false
			lastBySender[sender.lowercase()] = now
			recent.addLast(now)
			return true
		}

		fun reset() { lastBySender.clear(); recent.clear() }
	}

	fun promptLine(prompt: Prompt): Component =
		Component.literal("§6[Apex] §e${prompt.text} §7[click]")
			.withStyle { it.withClickEvent(ClickEvent.RunCommand(prompt.command)).withHoverEvent(HoverEvent.ShowText(Component.literal(prompt.command))) }

	fun promptLineFor(line: String): Component? {
		val incoming = PartyCommands.parse(line) ?: return null
		val prompt = promptFor(incoming) ?: return null
		if (!PromptLimit.allow(prompt.sender, env.millis())) return null
		return promptLine(prompt)
	}

	fun addReminder(rest: String) {
		val who = rest.substringBefore(' ').ifEmpty { return }
		downtimeReminders[who] = rest.substringAfter(' ', "").ifBlank { NO_REASON }
		Client.showChat(tr("apex.bang-shortcuts.reminder-set", "Downtime reminder set for $who; shown here when the run ends."))
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.CHAT, "dtreminder", "Set a local end-of-run downtime reminder (the ! prompts use it)") {
			onArgument("rest", RestArgumentType) { rest -> onRun { addReminder(this[rest]) } }
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val text = event.bareText
		if (downtimeReminders.isNotEmpty() && isRunEnd(text)) {
			val all = downtimeReminders.entries.joinToString(", ") { (who, why) -> "$who: $why" }
			clearReminders()
			Client.showChat(tr("apex.bang-shortcuts.downtime-reminder", "Downtime reminder: $all"))
		}
		promptLineFor(text)?.let(Client::showChat)
	}
}
