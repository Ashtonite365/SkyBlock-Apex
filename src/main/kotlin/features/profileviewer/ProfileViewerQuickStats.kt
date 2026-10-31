// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.profileviewer

import dev.ashtonite365.apex.repo.SafeRepoConstants
import com.mojang.brigadier.arguments.StringArgumentType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hover
import dev.ashtonite365.apex.util.tr

object ProfileViewerQuickStats {

	data class Summary(val name: String, val headline: String, val details: List<String>)

	private val cache = ConcurrentHashMap<String, Summary>()
	private val inFlight = ConcurrentHashMap.newKeySet<String>()

	fun cached(name: String): Summary? = cache[name.lowercase()]

	@Subscribe
	fun onSubCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("stalk", ManagedConfig.Category.PROFILE_VIEWER) {
			onArgument("name", StringArgumentType.word()) { name ->
				onRun { printStats(this[name]) }
			}
			onRun { printStats(Client.game.user.name) }
		}
	}

	@Subscribe
	fun onCommands(event: CommandEvent) {
		if (!ProfileViewer.TConfig.registerStalk) return
		event.deleteCommand("stalk")
		event.register("stalk") {
			onArgument("name", StringArgumentType.word()) { name ->
				onRun { printStats(this[name]) }
			}
			onRun { printStats(Client.game.user.name) }
		}
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!event.matches(ProfileViewer.TConfig.lookedAtKeybind)) return
		val target = Client.game.crosshairPickEntity as? Player ?: return
		ProfileViewer.open(target.gameProfile.name)
	}

	fun printStats(name: String) {
		Client.showChat(Component.literal("§7[PV] Loading stats for $name..."))
		fetch(name, force = true) { summary, error ->
			if (summary == null) Client.showChat(Component.literal("§c[PV] ").append(error ?: Component.literal("Unknown error")))
			else {
				Client.showChat(chatLine(summary))
				summary.details.forEach { Client.showChat(Component.literal(" $it")) }
			}
		}
	}

	fun chatLine(summary: Summary): Component =
		Component.literal("§b[PV] §f${summary.name}§7: ${summary.headline}")
			.hover(Component.literal(summary.details.joinToString("\n")))
			.runsOnClick("/apex pv ${summary.name}")
			.withStyle(ChatFormatting.RESET)

	fun fetch(name: String, force: Boolean = false, then: (Summary?, Component?) -> Unit) {
		val key = name.lowercase()
		if (!force) cache[key]?.let { then(it, null); return }
		if (!inFlight.add(key)) return
		Apex.coroutineScope.launch {
			val (summary, error) = try {
				when (val r = ProfileViewer.load(name)) {
					is ProfileViewer.LoadResult.Error -> null to r.message
					is ProfileViewer.LoadResult.Success -> summarise(r.data) to null
				}
			} catch (e: Exception) {
				Apex.logger.warn("Could not load quick stats for $name", e)
				null to Component.literal("Could not load stats: ${e.message}")
			} finally {
				inFlight.remove(key)
			}
			summary?.let { cache[key] = it }
			withContext(MinecraftDispatcher) { then(summary, error) }
		}
	}

	private fun fmt(n: Number?) = ApexFormatters.withThousands((n ?: 0).toLong())

	private fun level(xp: Double, ladder: List<Int>, cap: Int): Int {
		var remaining = xp
		var level = 0
		for (cost in ladder) {
			if (level >= cap || remaining < cost) break
			remaining -= cost; level++
		}
		return level
	}

	private fun runTime(ms: Double?): String = ms?.let { val s = (it / 1000).toLong(); "%d:%02d".format(s / 60, s % 60) } ?: "-"

	fun summarise(data: ProfileViewer.PlayerProfiles): Summary {
		val profile = data.profiles.firstOrNull { it.selected } ?: data.profiles.firstOrNull()
			?: return Summary(data.name, "§cno SkyBlock profiles", listOf())
		val m = profile.member
		val leveling = SafeRepoConstants.leveling()
		val cataLadder = leveling?.catacombsExperienceRequiredPerLevel.orEmpty()
		val d = m.obj("dungeons")
		val cata = level(d?.path("dungeon_types", "catacombs", "experience").num() ?: 0.0, cataLadder, cataLadder.size)
		val cls = d?.get("selected_dungeon_class").str()
		val clsLevel = cls?.let { level(d?.path("player_classes", it, "experience").num() ?: 0.0, cataLadder, cataLadder.size) }
		val classAvg = listOf("healer", "mage", "berserk", "archer", "tank")
			.map { level(d?.path("player_classes", it, "experience").num() ?: 0.0, cataLadder, cataLadder.size) }.average()
		val secrets = d?.get("secrets").long()
		val sbLevel = (m.path("leveling", "experience").long() ?: 0) / 100
		val skillNames = listOf("FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "ENCHANTING", "ALCHEMY", "TAMING")
		val skillLevels = skillNames.mapNotNull { s ->
			m.path("player_data", "experience", "SKILL_$s").num()?.let {
				level(it, leveling?.skillExperienceRequiredPerLevel.orEmpty(), leveling?.maximumLevels?.get(s.lowercase()) ?: 50)
			}
		}
		val skillAvg = if (skillLevels.isEmpty()) null else skillLevels.average()
		val slayers = listOf("zombie" to "Rev", "spider" to "Tara", "wolf" to "Sven", "enderman" to "Eman", "blaze" to "Blaze", "vampire" to "Vamp")
			.joinToString(" ") { (k, label) ->
				val xp = m.path("slayer", "slayer_bosses", k, "xp").long() ?: 0
				val ladder = leveling?.slayerExperienceRequiredPerLevel?.get(k) ?: listOf()
				"§7$label §f${ladder.count { xp >= it }}"
			}
		val kuudra = m.path("nether_island_crimson_isle", "kuudra_completed_tiers") as? JsonObject
		val kuudraLine = listOf("none" to "Basic", "hot" to "Hot", "burning" to "Burn", "fiery" to "Fiery", "infernal" to "Inf")
			.joinToString(" ") { (k, l) -> "§7$l §f${fmt(kuudra?.get(k).long())}" }
		fun pb(type: String, floor: Int) = d?.path("dungeon_types", type, "fastest_time_s_plus", "$floor").num()
		val headline = "§cCata $cata" + (cls?.let { " §7(${it.replaceFirstChar(Char::uppercase)} $clsLevel)" } ?: "") +
			" §7| §bSecrets ${fmt(secrets)} §7| §3SB ${sbLevel}" +
			(skillAvg?.let { " §7| §aSA %.1f".format(it) } ?: "") + " §8(hover)"
		val details = buildList {
			add("§e${data.displayName} §7on §f${profile.cuteName}" + (profile.gameMode?.let { " §7($it)" } ?: ""))
			add("§cCatacombs §f$cata §7| Class avg §f%.1f".format(classAvg) + (cls?.let { " §7| Selected §f$it $clsLevel" } ?: ""))
			add("§bSecrets §f${fmt(secrets)}")
			add("§7PB S+: §fF7 ${runTime(pb("catacombs", 7))} §7| §cM5 ${runTime(pb("master_catacombs", 5))} §7| §cM6 ${runTime(pb("master_catacombs", 6))} §7| §cM7 ${runTime(pb("master_catacombs", 7))}")
			add("§6Kuudra: $kuudraLine")
			add("§5Slayers: $slayers")
			skillAvg?.let { add("§aSkill average §f%.2f".format(it)) } ?: add("§7Skills API disabled")
			add("§6Purse §f${fmt(m.path("currencies", "coin_purse").num())}" + (profile.bank?.let { " §7| Bank §f${fmt(it)}" } ?: ""))
			ProfileViewerPages.magicalPower(m)?.let { add("§dMagical power §f$it") }
		}
		return Summary(data.name, headline, details)
	}
}
