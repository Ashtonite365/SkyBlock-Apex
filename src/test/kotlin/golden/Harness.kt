// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.ServerLocation
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

object Harness {
	fun load(cfg: ManagedConfig) {
		if (cfg.sortedOptions.any { it._actualValue == null }) cfg.explicitDefaultLoad()
	}

	@Suppress("UNCHECKED_CAST")
	fun <T> withOpts(cfg: ManagedConfig, values: Map<String, Any>, block: () -> T): T {
		load(cfg)
		val saved = values.keys.associateWith { (cfg.allOptions[it] ?: error("no option $it in ${cfg.name}"))._actualValue }
		try {
			for ((k, v) in values) (cfg.allOptions[k] as ManagedOption<Any>)._actualValue = v
			return block()
		} finally {
			for ((k, v) in saved) (cfg.allOptions[k] as ManagedOption<Any>)._actualValue = v
		}
	}

	fun <T> withAll(cfg: ManagedConfig, value: Boolean, block: () -> T): T =
		withOpts(cfg, cfg.allOptions.filterValues { it._actualValue is Boolean }.mapValues { value }, block)

	fun <T> at(serverLocation: ServerLocation?, block: () -> T): T {
		val before = SkyBlockSession.serverLocation
		SkyBlockSession.serverLocation = serverLocation
		try { return block() } finally { SkyBlockSession.serverLocation = before }
	}

	fun skyblock(island: String? = null) = ServerLocation("mini1A", "SKYBLOCK", island, null)

	private val sink = mutableListOf<String>()
	private val appender = object : AbstractAppender("golden-chat", null, null, true, Property.EMPTY_ARRAY) {
		override fun append(event: LogEvent) {
			val m = event.message.formattedMessage
			if (m.startsWith("CHAT: ")) synchronized(sink) { sink += m.removePrefix("CHAT: ") }
		}
	}.also { it.start(); (LogManager.getLogger("Apex") as org.apache.logging.log4j.core.Logger).addAppender(it) }

	fun chat(block: () -> Unit): List<String> {
		appender.isStarted
		synchronized(sink) { sink.clear() }
		block()
		return synchronized(sink) { sink.toList().also { sink.clear() } }
	}

	fun dump(c: Component): String = c.toFlatList().joinToString("") { p ->
		val s = p.style
		val hover = (s.hoverEvent as? HoverEvent.ShowText)?.value?.string
		val click = when (val e = s.clickEvent) {
			is ClickEvent.RunCommand -> "run:${e.command}"
			is ClickEvent.SuggestCommand -> "suggest:${e.command}"
			is ClickEvent.OpenUrl -> "url:${e.uri}"
			null -> null
			else -> e.toString()
		}
		"[${p.string}|${s.color?.serialize()}|b=${s.isBold}|h=$hover|c=$click]"
	}

	fun msg(e: Throwable) = "THROWS ${e::class.simpleName}: ${e.message}"
}
