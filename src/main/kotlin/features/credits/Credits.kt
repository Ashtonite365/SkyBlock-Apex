// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.credits

import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object Credits {
	data class Entry(val name: String, val authors: String, val licence: String, val link: String, val used: List<String>)

	val entries: List<Entry> by lazy {
		runCatching {
			val stream = Credits::class.java.getResourceAsStream("/META-INF/apex/credits.json") ?: return@runCatching emptyList()
			val root = stream.reader().use { JsonParser.parseReader(it).asJsonObject }
			root.getAsJsonArray("entries").map { el ->
				val o = el.asJsonObject
				Entry(
					o["name"].asString, o["authors"].asString, o["licence"].asString,
					o["link"]?.asString ?: "", o.getAsJsonArray("used").map { it.asString },
				)
			}
		}.getOrDefault(emptyList())
	}

	val ideasOnly: String by lazy {
		runCatching {
			val stream = Credits::class.java.getResourceAsStream("/META-INF/apex/credits.json") ?: return@runCatching ""
			stream.reader().use { JsonParser.parseReader(it).asJsonObject }["ideasOnly"]?.asString ?: ""
		}.getOrDefault("")
	}

	@Config
	object TConfig : ManagedConfig("credits", Category.CREDITS) {
		init {
			entries.forEachIndexed { i, e ->
				val label = Component.literal(e.name).withStyle(ChatFormatting.GOLD)
				val desc = Component.empty()
					.append(Component.literal("By ${e.authors}\n").withStyle(ChatFormatting.WHITE))
					.append(Component.literal("Licence: ${e.licence}\n").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(if (e.link.isNotBlank()) "${e.link} (click the button to open)\n" else "").withStyle(ChatFormatting.AQUA))
					.append(Component.literal(e.used.joinToString("\n") { "• $it" }).withStyle(ChatFormatting.GRAY))
				dynamicButton("entry-$i", label, desc) {
					if (e.link.isNotBlank()) Client.openLink(e.link)
				}
			}
			if (ideasOnly.isNotBlank())
				dynamicButton("ideas-only", Component.literal("Ideas only").withStyle(ChatFormatting.GOLD),
					Component.literal(ideasOnly).withStyle(ChatFormatting.GRAY)) {}
			dynamicButton("licences", Component.literal("Licence texts").withStyle(ChatFormatting.GOLD),
				Component.literal("Full licence texts: LICENSES/ in the source and META-INF/apex/LICENSES/ in the jar (with LICENSE and NOTICE).")
					.withStyle(ChatFormatting.GRAY)) {}
		}
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.CREDITS, "credits", "Open the Credits page") {
			onRun { ScreenUtil.setScreenLater(TConfig.getConfigEditor()) }
		}
	}
}
