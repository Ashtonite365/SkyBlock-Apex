// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.reminders

import org.joml.Vector2i
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.drawPlusLines

object MiscPlusPorts {
	@Config
	object TConfig : ManagedConfig("misc-tweaks", Category.MISC) {
		val todos by toggle("todos") { false }
		val todosHud by position("todos-hud", 140, 60) { Vector2i(4, 440) }
		val inGameDate by toggle("in-game-date") { false }
		val inGameDateHud by position("in-game-date-hud", 120, 12) { Vector2i(4, 505) }
		val movementSpeed by toggle("movement-speed") { false }
		val movementSpeedHud by position("movement-speed-hud", 80, 12) { Vector2i(4, 518) }
		val inWater by toggle("in-water") { false }
		val inWaterHud by position("in-water-hud", 60, 12) { Vector2i(4, 531) }
		val kickDuration by toggle("kick-duration") { false }
		val serverRestartTitle by toggle("server-restart-title") { false }
		val lastServers by toggle("last-servers") { false }
		val lastServersMinutes by integer("last-servers-minutes", 1, 60) { 5 }
	}

	@Serializable
	data class Data(val todos: MutableList<String> = mutableListOf())

	@Config
	object TodoData : DataHolder<Data>(serializer(), "todos", ::Data)

	private val todoList get() = TodoData.data.todos

	private fun say(text: String) = Client.showChat(Component.literal(text))

	private fun addTodo(text: String) {
		todoList.add(text)
		TodoData.markDirty()
		say("§a[SkyBlock Apex] Added to-do #${todoList.size}: $text")
	}

	private fun finishTodo(raw: String) {
		val index = raw.trim().toIntOrNull()?.let { it - 1 }
		if (index == null || index !in todoList.indices) {
			say("§c[SkyBlock Apex] No to-do #$raw")
			return
		}
		val removed = todoList.removeAt(index)
		TodoData.markDirty()
		say("§a[SkyBlock Apex] Done: $removed")
	}

	private fun listTodos() {
		if (todoList.isEmpty()) say("§7[SkyBlock Apex] No to-dos. /apex todo add <text>")
		for ((i, t) in todoList.withIndex()) say("§e${i + 1}. §f$t")
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MISC, "todo", "Local to-do list shown on the HUD (CustomTodos)") {
			onLiteral("add") {
				onArgument("text", RestArgumentType) { text -> onRun { addTodo(this[text]) } }
			}
			onLiteral("done") {
				onArgument("number", RestArgumentType) { number -> onRun { finishTodo(this[number]) } }
			}
			onLiteral("clear") {
				onRun {
					todoList.clear()
					TodoData.markDirty()
				}
			}
			onRun { listTodos() }
		}
	}

	private var previousPos: Vec3? = null
	private var smoothedSpeed = 0.0

	private fun trackMovement() {
		val now = Client.player?.position() ?: return
		val before = previousPos
		if (before != null) smoothedSpeed = 0.8 * smoothedSpeed + 0.2 * PlusMiscLogic.speed(now.x - before.x, now.z - before.z)
		previousPos = now
	}

	private var kickTime: TimeMark? = null
	private var restartAlertTime = TimeMark.farPast()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.kickDuration) return
		if (!PlusMiscLogic.isKick(event.bareText.trim())) return
		kickTime = TimeMark.now()
		say("§eKicked from SkyBlock; you can rejoin in about 60 seconds.")
	}

	private fun checkKickTimer() {
		val kicked = kickTime ?: return
		if (kicked.passedTime() < 60.seconds) return
		kickTime = null
		if (TConfig.kickDuration) Alerts.show(Component.literal("§aYou can join SkyBlock again!"))
	}

	private fun checkRestart() {
		if (!(TConfig.serverRestartTitle && SkyBlockSession.inSkyBlock)) return
		val left = PlusMiscLogic.restartSeconds(ScoreboardUtil.simplifiedScoreboardLines) ?: return
		if (restartAlertTime.passedTime() <= 30.seconds) return
		if (left > 30 && left % 60 != 0) return
		restartAlertTime = TimeMark.now()
		Alerts.show(Component.literal("§cServer restart in §b${left / 60}m ${left % 60}s"))
	}

	private val visitedAt = linkedMapOf<String, Long>()
	private var currentServer: String? = null

	private fun checkServerChange() {
		if (!SkyBlockSession.inSkyBlock) return
		val server = SkyBlockSession.serverLocation?.server ?: return
		if (server == currentServer) return
		val now = System.currentTimeMillis()
		if (TConfig.lastServers) {
			val window = TConfig.lastServersMinutes.minutes.inWholeMilliseconds
			if (server in PlusMiscLogic.recentServers(visitedAt, now, window)) {
				val secondsAgo = (now - visitedAt.getValue(server)) / 1000
				say("§eYou were already on server §b$server §e${secondsAgo / 60}m ${secondsAgo % 60}s ago.")
			}
		}
		currentServer?.let { visitedAt[it] = now }
		currentServer = server
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		trackMovement()
		if (event.tickCount % 20 != 0) return
		checkKickTimer()
		checkRestart()
		checkServerChange()
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (TConfig.todos && todoList.isNotEmpty()) {
			event.drawPlusLines(TConfig.todosHud) {
				add("§e§lTo-do")
				for ((i, t) in todoList.withIndex()) add("§7${i + 1}. §f$t")
			}
		}
		if (!SkyBlockSession.inSkyBlock) return
		if (TConfig.inGameDate) {
			val date = PlusMiscLogic.dateLine(ScoreboardUtil.simplifiedScoreboardLines)
			if (date != null) event.drawPlusLines(TConfig.inGameDateHud) { add("§e$date") }
		}
		if (TConfig.movementSpeed) event.drawPlusLines(TConfig.movementSpeedHud) { add("§fSpeed: §e${"%.2f".format(smoothedSpeed)} b/s") }
		if (TConfig.inWater) {
			val wet = Client.player?.isInWater == true
			event.drawPlusLines(TConfig.inWaterHud) { add("§fIn Water: ${if (wet) "§aYes" else "§cNo"}") }
		}
	}
}
