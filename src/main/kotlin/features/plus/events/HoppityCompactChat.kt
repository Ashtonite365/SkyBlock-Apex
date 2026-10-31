// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object HoppityCompactChat {
	private val eggFound = Regex("^HOPPITY'S HUNT You found an? (?<meal>Chocolate .+ Egg|Hitman Egg|.+ Egg) .*!$|^HOPPITY'S HUNT You found an? (?<meal2>[^!]+ Egg)!$")
	private val bought = Regex("^You bought (?<name>[^!]+?)( for [\\d,]+ Coins)?!$")
	private val rabbit = Regex("^HOPPITY'S HUNT You found (?<name>.+) \\((?<rarity>[A-Z]+)\\)!$")
	private val newRabbit = Regex("^NEW RABBIT! (?<rest>.+)$")
	private val dupe = Regex("^DUPLICATE RABBIT! \\+(?<amount>[\\d,]+) Chocolate$")

	data class Find(val source: String, val rarity: String, val new: Boolean, val chocolate: Long)

	class Compactor(private val onFind: (Find) -> Unit = {}) {

		private class Pending(val source: String) {
			var name: String? = null
			var rarity: String? = null
			val lines = ArrayList<String>()
		}

		private var pending: Pending? = null

		private fun open(source: String, line: String) {
			pending = Pending(source).also { it.lines.add(line) }
		}

		private fun finish(p: Pending, message: String): Pair<Boolean, Pair<String, List<String>>?> {
			val result = message to p.lines.toList()
			pending = null
			return true to result
		}

		fun feed(line: String): Pair<Boolean, Pair<String, List<String>>?> {
			val egg = eggFound.matchEntire(line)
			if (egg != null) {
				open((egg.groups["meal"] ?: egg.groups["meal2"])!!.value + "!", line)
				return true to null
			}
			val p = pending
			if (p == null) {
				if (bought.matchEntire(line) != null) open("Bought Rabbit!", line)
				return false to null
			}
			val found = rabbit.matchEntire(line)
			if (found != null) {
				p.name = found.groups["name"]!!.value
				p.rarity = found.groups["rarity"]!!.value
				p.lines.add(line)
				return true to null
			}
			val name = p.name
			if (name == null) {
				pending = null
				return false to null
			}
			val isNew = newRabbit.matchEntire(line)
			if (isNew != null) {
				p.lines.add(line)
				onFind(Find(p.source.removeSuffix("!"), p.rarity!!, true, 0))
				return finish(p, "§d${p.source} §d§lNEW §6§l${p.rarity} §f$name §7(${isNew.groups["rest"]!!.value}§7)")
			}
			val dup = dupe.matchEntire(line)
			if (dup != null) {
				p.lines.add(line)
				val chocolate = dup.groups["amount"]!!.value.replace(",", "").toDouble()
				onFind(Find(p.source.removeSuffix("!"), p.rarity!!, false, chocolate.toLong()))
				return finish(p, "§d${p.source} §7Duplicate §6${p.rarity} §f$name §7(§6+${ApexFormatters.compactNumber(chocolate)} Chocolate§7)")
			}
			pending = null
			return false to null
		}
	}

	@kotlinx.serialization.Serializable
	data class Summary(
		val eggs: MutableMap<String, Int> = mutableMapOf(),
		val newRabbits: MutableMap<String, Int> = mutableMapOf(),
		val dupeRabbits: MutableMap<String, Int> = mutableMapOf(),
		var dupeChocolate: Long = 0,
	)

	@dev.ashtonite365.apex.util.data.Config
	object SConfig : dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder<Summary>(kotlinx.serialization.serializer(), "hoppity-summary", ::Summary)

	private fun MutableMap<String, Int>.bump(key: String) {
		this[key] = (this[key] ?: 0) + 1
	}

	fun record(s: Summary, f: Find) {
		s.eggs.bump(f.source)
		if (f.new) s.newRabbits.bump(f.rarity) else s.dupeRabbits.bump(f.rarity)
		s.dupeChocolate += f.chocolate
	}

	private fun Map<String, Int>.listed() = entries.joinToString { "${it.key} ${it.value}" }

	fun summaryLines(s: Summary): List<String> {
		val out = ArrayList<String>()
		out.add("§d§lHoppity's Hunt Summary")
		for ((egg, n) in s.eggs.entries.sortedWith(compareByDescending { it.value })) out.add("§7$egg: §f$n")
		out.add("§d§lNEW §frabbits: ${s.newRabbits.values.sum()} §7${s.newRabbits.listed()}")
		out.add("§7Duplicates: §f${s.dupeRabbits.values.sum()} §7${s.dupeRabbits.listed()}")
		out.add("§7Duplicate chocolate: §6${ApexFormatters.withThousands(s.dupeChocolate)}")
		return out
	}

	private val compactor = Compactor { f ->
		val summary = SConfig.data
		if (summary != null) {
			record(summary, f)
			SConfig.markDirty()
		}
	}

	@Subscribe
	fun onCommand(event: dev.ashtonite365.apex.events.CommandEvent.SubCommand) {
		event.addSubcommand("hoppitysummary", dev.ashtonite365.apex.util.data.ManagedConfig.Category.EVENTS) {
			onRun {
				val s = SConfig.data ?: return@onRun
				for (line in summaryLines(s)) Client.showChat(Component.literal(line))
			}
		}
	}

	@Subscribe
	fun onAllow(event: AllowChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return

		val (hide, compact) = compactor.feed(event.bareText.trim())
		if (!hide || !EventPlusPorts.TConfig.hoppityCompactChat) return
		event.cancel()
		if (compact != null) {
			val hover = Component.literal(compact.second.joinToString("\n"))
			Client.showChat(Component.literal(compact.first).withStyle { it.withHoverEvent(HoverEvent.ShowText(hover)) })
		}
	}
}
