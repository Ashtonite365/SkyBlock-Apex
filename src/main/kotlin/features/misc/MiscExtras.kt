/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): general/misc extras: sound mixer with per-island mutes, particle hider,
 * toast hider, auto-join SkyBlock, limbo timer and utility commands (calc, wiki, todo). Inspired by,
 *, Enhanced Sound Control and Stfu feature descriptions only. Original implementation, no code copied.
 */
package dev.ashtonite365.apex.features.misc

import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.math.Calculator

object MiscExtras {
	@Config
	object TConfig : ManagedConfig("misc-extras", Category.MISC) {
		var mutedSounds by string("muted-sounds") { "" }
		val volumeBoost by toggle("volume-boost") { false }
		val boosts = net.minecraft.sounds.SoundSource.entries.associateWith { integer("boost-${it.getName()}", 0, MAX_BOOST_PERCENT) { 100 } }
		val hiddenParticles by string("hidden-particles") { "" }
		val hideToasts by toggle("hide-toasts") { false }
		val keepSystemToasts by toggle("keep-system-toasts") { true }
		val limboTimer by toggle("limbo-timer") { true }
	}

	@Serializable
	data class Todos(val items: MutableList<String> = mutableListOf())

	@Config
	object Data : DataHolder<Todos>(serializer(), "misc-todos", ::Todos)

	const val MAX_BOOST_PERCENT = 300

	fun boostFactor(percent: Int, masterPercent: Int): Float {
		val f = (percent.coerceIn(0, MAX_BOOST_PERCENT) / 100f) * (masterPercent.coerceIn(0, MAX_BOOST_PERCENT) / 100f)
		return f.coerceIn(0f, MAX_BOOST_PERCENT / 100f)
	}

	@JvmStatic
	fun boostVolume(volume: Float, source: net.minecraft.sounds.SoundSource): Float {
		if (!TConfig.volumeBoost) return volume
		val master = TConfig.boosts[net.minecraft.sounds.SoundSource.MASTER]?.get() ?: 100
		val own = if (source == net.minecraft.sounds.SoundSource.MASTER) 100 else TConfig.boosts[source]?.get() ?: 100
		return safeGain(volume * boostFactor(own, master))
	}

	@JvmStatic
	fun safeGain(v: Float): Float = if (v.isNaN() || v.isInfinite()) 0f else v.coerceIn(0f, MAX_BOOST_PERCENT / 100f)

	data class Rule(val island: String?, val id: String)

	fun parseRules(text: String): List<Rule> = text.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { e ->
		val parts = e.split(':')
		when (parts.size) {
			3 -> Rule(parts[0], parts[1] + ":" + parts[2])
			2 -> Rule(null, e)
			else -> Rule(null, "minecraft:$e")
		}
	}

	fun matches(rules: List<Rule>, id: String, island: String?): Boolean = rules.any { r ->
		(r.island == null || r.island == island) && (if (r.id.endsWith("*")) id.startsWith(r.id.dropLast(1)) else id == r.id)
	}

	private var soundCache = "" to emptyList<Rule>()
	private var particleCache = "" to emptyList<Rule>()
	private fun rules(text: String, cache: Pair<String, List<Rule>>) = if (cache.first == text) cache else text to parseRules(text)

	val recentSounds = ArrayDeque<String>()

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		val sid = event.sound.value().location().toString()
		recentSounds.remove(sid); recentSounds.addFirst(sid)
		while (recentSounds.size > 20) recentSounds.removeLast()
		soundCache = rules(TConfig.mutedSounds, soundCache)
		if (soundCache.second.isEmpty()) return
		if (matches(soundCache.second, event.sound.value().location().toString(), SkyBlockSession.skyblockIsland?.serverMode)) event.cancel()
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		particleCache = rules(TConfig.hiddenParticles, particleCache)
		if (particleCache.second.isEmpty()) return
		val id = BuiltInRegistries.PARTICLE_TYPE.getKey(event.particleEffect.type)?.toString() ?: return
		if (matches(particleCache.second, id, SkyBlockSession.skyblockIsland?.serverMode)) event.cancel()
	}

	@JvmStatic
	fun shouldHideToast(toast: Any): Boolean =
		TConfig.hideToasts && !(TConfig.keepSystemToasts && toast is net.minecraft.client.gui.components.toasts.SystemToast)

	private var limboSince: TimeMark? = null

	@Subscribe
	fun onLocraw(event: SkyblockServerUpdateEvent) {
		val new = event.currentLocation ?: return
		if (new.server == "limbo") { if (limboSince == null) limboSince = TimeMark.now() }
		else limboSince?.let {
			if (TConfig.limboTimer) Client.showChat(Component.literal("§7[SkyBlock Apex] You spent ${ApexFormatters.timespanText(it.passedTime())} in Limbo."))
			limboSince = null
		}

	}

	fun wikiUrl(query: String) = "https://wiki.hypixel.net/index.php?search=" + URLEncoder.encode(query.trim(), Charsets.UTF_8)

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MISC, "calc", "Evaluate a maths expression (k/m/b suffixes)") {
			onArgument("expression", RestArgumentType) { expr ->
				onRun {
					val r = Calculator.evaluateOrNull(this[expr])
					source.sendFeedback(Component.literal(if (r == null) "§cCould not evaluate." else "§e${this[expr]} §7= §a${r.toPlainString()}"))
				}
			}
		}
		event.register(ManagedConfig.Category.MISC, "sounds", "Recently heard sounds; click one to mute it") {
			onLiteral("mute") {
				onArgument("id", RestArgumentType) { id ->
					onRun {
						val v = this[id].trim()
						if (parseRules(TConfig.mutedSounds).none { it.id == v && it.island == null })
							TConfig.mutedSounds = (TConfig.mutedSounds.split(',').map { it.trim() }.filter { it.isNotEmpty() } + v).joinToString(", ")
						TConfig.markDirty()
						source.sendFeedback(Component.literal("§aMuted §e$v§a. Edit the list under Misc Extras > Muted Sounds."))
					}
				}
			}
			onRun {
				if (recentSounds.isEmpty()) source.sendFeedback(Component.literal("§7No sounds heard yet."))
				for (sid in recentSounds.toList()) source.sendFeedback(Component.literal("§e$sid §c[mute]").withStyle {
					it.withClickEvent(ClickEvent.RunCommand("/apex sounds mute $sid")).withHoverEvent(HoverEvent.ShowText(Component.literal("Mute $sid")))
				})
			}
		}
		event.register(ManagedConfig.Category.MISC, "wiki", "Search the official Hypixel SkyBlock wiki") {
			onArgument("query", RestArgumentType) { q ->
				onRun {
					val url = wikiUrl(this[q])
					source.sendFeedback(Component.literal("§e[SkyBlock Apex] Click to search the wiki for §a${this[q]}").withStyle {
						it.withClickEvent(ClickEvent.OpenUrl(java.net.URI.create(url))).withHoverEvent(HoverEvent.ShowText(Component.literal(url)))
					})
				}
			}
		}
		event.register(ManagedConfig.Category.MISC, "todo", "A small to-do list: add, done <number>, list") {
			onLiteral("add") {
				onArgument("text", RestArgumentType) { t ->
					onRun { Data.data.items.add(this[t].trim()); Data.markDirty(); source.sendFeedback(Component.literal("§aAdded.")) }
				}
			}
			onLiteral("done") {
				onArgument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)) { n ->
					onRun {
						val i = this[n] - 1
						if (i in Data.data.items.indices) { Data.data.items.removeAt(i); Data.markDirty(); source.sendFeedback(Component.literal("§aDone.")) }
						else source.sendFeedback(Component.literal("§cNo such item."))
					}
				}
			}
			onRun {
				val items = Data.data.items
				if (items.isEmpty()) source.sendFeedback(Component.literal("§7Nothing to do. Use /apex todo add <text>."))
				items.forEachIndexed { i, s -> source.sendFeedback(Component.literal("§e${i + 1}. §f$s")) }
			}
		}
	}
}
