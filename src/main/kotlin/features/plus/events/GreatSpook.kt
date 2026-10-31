// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.drawPlusLines
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.joml.Vector2i

object GreatSpook {
	@Config
	object TConfig : ManagedConfig("great-spook", Category.EVENTS) {
		val primalFearTimer by toggle("primal-fear-timer") { false }
		val timerHud by position("timer-hud", 120, 12) { Vector2i(10, 300) }
		val mathSolver by riskyToggle("math-solver", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
	}

	private val FEAR_SUMMONED = Regex("^FEAR\\. A Primal Fear has been summoned!$")
	private val QUICK_MATHS = Regex("^QUICK MATHS! Solve: (.+)$")
	private val FEAR_COOLDOWN = 6.minutes
	private val HUD_TIMEOUT = 30.minutes
	private var summonedAt: TimeMark? = null

	private class Reader(private val src: String) {
		var pos = 0
		private fun peek(): Char? = if (pos < src.length) src[pos] else null

		fun sum(): Double {
			var acc = product()
			while (true) {
				val op = peek()
				if (op != '+' && op != '-') return acc
				pos++
				val rhs = product()
				acc = if (op == '+') acc + rhs else acc - rhs
			}
		}

		private fun product(): Double {
			var acc = unary()
			while (true) {
				val op = peek()
				if (op != '*' && op != '/') return acc
				pos++
				val rhs = unary()
				acc = if (op == '*') acc * rhs else acc / rhs
			}
		}

		private fun unary(): Double {
			when (peek()) {
				'-' -> { pos++; return -unary() }
				'(' -> {
					pos++
					val inner = sum()
					if (peek() != ')') throw IllegalStateException("paren")
					pos++
					return inner
				}
				else -> {
					val from = pos
					while (true) {
						val c = peek() ?: break
						if (!c.isDigit() && c != '.') break
						pos++
					}
					return src.substring(from, pos).toDouble()
				}
			}
		}

		val atEnd get() = pos == src.length
	}

	fun evaluate(expr: String): Long? {
		val reader = Reader(expr.replace(" ", "").replace('x', '*'))
		return try {
			val value = reader.sum()
			if (reader.atEnd) value.toLong() else null
		} catch (e: Throwable) {
			null
		}
	}

	fun remaining(elapsed: Duration): Duration {
		val left = FEAR_COOLDOWN - elapsed
		return if (left < Duration.ZERO) Duration.ZERO else left
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val line = event.bareText.trim()
		if (FEAR_SUMMONED.matches(line)) {
			summonedAt = TimeMark.now()
			return
		}
		if (!TConfig.mathSolver) return
		val question = QUICK_MATHS.matchEntire(line)?.groupValues?.get(1) ?: return
		val answer = evaluate(question) ?: return
		val command = "/ac $answer"
		Client.showChat(Component.literal("§e[SkyBlock Apex] The answer is: §b$answer §7(click to send)").withStyle { style ->
			style.withClickEvent(ClickEvent.RunCommand(command)).withHoverEvent(HoverEvent.ShowText(Component.literal(command)))
		})
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.primalFearTimer) return
		val start = summonedAt ?: return
		val left = remaining(start.passedTime())
		if (start.passedTime() > HUD_TIMEOUT) return
		val text = if (left == Duration.ZERO) "§5§lPrimal Fear Ready!" else "§5Next fear in: §b${ApexFormatters.timespanText(left)}"
		event.drawPlusLines(TConfig.timerHud) { add(text) }
	}
}
