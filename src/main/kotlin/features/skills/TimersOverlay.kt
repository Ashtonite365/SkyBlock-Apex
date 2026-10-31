/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex daily / cooldown task timers.
 */
package dev.ashtonite365.apex.features.skills

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DurationArgumentType
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.misc.TimerFeature
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object TimersOverlay {
	@Serializable
	data class Data(

		val lastDone: MutableMap<String, Long> = mutableMapOf(),

		val customDurations: MutableMap<String, Long> = mutableMapOf(),

		val customStarts: MutableMap<String, Long> = mutableMapOf(),

		val crimsonQuestsDone: MutableSet<String> = mutableSetOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "timers-overlay", ::Data)

	sealed interface Reset {

		data class Daily(val zone: ZoneId, val hour: Int = 0) : Reset

		data class Cooldown(val duration: Duration) : Reset
	}

	enum class Task(val label: String, val reset: Reset, val chat: Regex?) {
		PUZZLER("Puzzler", Reset.Daily(SkyBlockSession.hypixelTimeZone), Regex("Puzzler gave you .+ for solving the puzzle!")),
		FETCHUR("Fetchur", Reset.Daily(SkyBlockSession.hypixelTimeZone),
		        Regex("\\[NPC] Fetchur: (thanks thats probably what i needed|come back another time, maybe tmrw)")),
		MITHRIL_POWDER("Mithril Powder", Reset.Daily(ZoneOffset.UTC),
		               Regex("You've earned .+ Mithril Powder from mining your first Mithril Ore of the day!")),
		GEMSTONE_POWDER("Gemstone Powder", Reset.Daily(ZoneOffset.UTC),
		                Regex("You've earned .+ Gemstone Powder from mining your first Gemstone of the day!")),
		EXPERIMENTS("Experiments", Reset.Daily(ZoneOffset.UTC), null),
		HEAVY_PEARLS("Heavy Pearls", Reset.Daily(SkyBlockSession.hypixelTimeZone, 20), null),
		CRIMSON_QUESTS("Crimson Isle Quests", Reset.Daily(SkyBlockSession.hypixelTimeZone), null),
		CAKE("Century Cake", Reset.Cooldown(48.hours), Regex("(Big )?Yum! You (refresh|gain) .+ for 48 hours!")),
		INFUSION("Free Infusion", Reset.Cooldown(4.hours), Regex("INFUSED! Used one of your free Rift charges!")),
		;

		val timerId get() = name.lowercase().replace('_', '-')
	}

	private const val CRIMSON_LIST_KEY = "crimson-quest-list"
	private const val DAY_MS = 86_400_000L
	private const val QUESTS_PER_DAY = 5
	var godPotion: String? = null
	private val matriarchArea = AABB(-555.0, 36.0, -904.0, -520.0, 52.0, -870.0)
	private val questGivers = setOf("Paul", "Finnegan", "Aatrox", "Cole", "Diana", "Diaz", "Foxy", "Marina")

	fun resetBefore(reset: Reset.Daily, now: Long): Long {
		val clock = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), reset.zone)
		val today = clock.toLocalDate().atStartOfDay(reset.zone).plusHours(reset.hour.toLong())
		val boundary = if (today.isAfter(clock)) today.minusDays(1) else today
		return boundary.toInstant().toEpochMilli()
	}

	fun lastResetMillis(reset: Reset.Daily): Long = resetBefore(reset, System.currentTimeMillis())

	fun waitFor(task: Task, done: Long?, now: Long): Duration? {
		if (done == null) return null
		return when (val reset = task.reset) {
			is Reset.Cooldown -> {
				val ms = done + reset.duration.inWholeMilliseconds - now
				if (ms > 0) ms.milliseconds else null
			}
			is Reset.Daily -> {
				val since = resetBefore(reset, now)
				if (done < since) null else (since + DAY_MS - now).milliseconds
			}
		}
	}

	fun remaining(task: Task): Duration? = waitFor(task, DConfig.data.lastDone[task.name], System.currentTimeMillis())

	fun completedBy(message: String): List<Task> = Task.entries.filter { it.chat?.containsMatchIn(message) == true }

	enum class MenuSignal { NOTHING, EXPERIMENTS_DONE, INFUSION_FREE }

	fun menuSignal(title: String?, tableLore: List<String>, rewardName: String?, infusionLore: List<String>): MenuSignal = when (title) {
		"Experimentation Table" -> if (tableLore.lastOrNull() == "Experiments on cooldown!") MenuSignal.EXPERIMENTS_DONE else MenuSignal.NOTHING
		"Superpairs Rewards" -> if (rewardName == "Superpairs") MenuSignal.EXPERIMENTS_DONE else MenuSignal.NOTHING
		"Dimensional Infusion" -> if (infusionLore.any { "FREE! (Every 4 hours)" in it }) MenuSignal.INFUSION_FREE else MenuSignal.NOTHING
		else -> MenuSignal.NOTHING
	}

	fun godPotionIn(footer: String): String? {
		for (raw in footer.lines()) {
			val line = raw.trim()
			val at = line.indexOf("God Potion")
			if (at >= 0) return line.substring(at + "God Potion".length).trim(':', ' ', '!')
		}
		return null
	}

	data class TabLine(val plain: String, val formatted: String)

	fun finishedQuests(body: List<TabLine>): List<String> {
		val start = body.indexOfFirst { it.plain.trim().startsWith("Faction Quests") }
		if (start < 0) return emptyList()
		val out = ArrayList<String>()
		for (line in body.subList(start + 1, body.size)) {
			if (line.plain.isBlank()) break
			val name = line.plain.trim()
			if (!line.formatted.startsWith(" §a") || name.isEmpty()) continue
			if (questGivers.none { name.startsWith(it) }) out += name
		}
		return out
	}

	fun statusText(task: Task, wait: Duration?, questsDone: Int): String {
		if (task == Task.CAKE) return if (wait == null) "§cEat a cake!" else "§a${ApexFormatters.timespanText(wait)} left"
		if (wait != null) return "§aDone §7(${ApexFormatters.timespanText(wait)})"
		if (task == Task.CRIMSON_QUESTS) return "§c${QUESTS_PER_DAY - minOf(questsDone, QUESTS_PER_DAY)} left"
		return "§cReady"
	}

	fun markDone(task: Task, at: Long = System.currentTimeMillis()) {
		DConfig.data.lastDone[task.name] = at
		DConfig.markDirty()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		completedBy(event.bareText.trim()).forEach { markDone(it) }
	}

	@Subscribe
	fun onChestUpdate(event: ChestInventoryUpdateEvent) {
		if (event !is ChestInventoryUpdateEvent.Multi) return
		val items = event.contents
		fun plainLore(slot: Int) = items.getOrNull(slot)?.view()?.loreAccordingToNbt?.map { it.bareText } ?: emptyList()
		val signal = menuSignal(
			Client.screenTitle, plainLore(22),
			items.getOrNull(13)?.view()?.displayNameAccordingToNbt?.bareText, plainLore(13),
		)
		when (signal) {
			MenuSignal.EXPERIMENTS_DONE -> markDone(Task.EXPERIMENTS)
			MenuSignal.INFUSION_FREE -> {
				DConfig.data.lastDone.remove(Task.INFUSION.name)
				DConfig.markDirty()
			}
			MenuSignal.NOTHING -> {}
		}
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		if (dev.ashtonite365.apex.util.Client.connection == null) return
		val tab = TabListReader.currentSnapshot
		tab.footer.orElse(null)?.let { godPotion = godPotionIn(it.bareText) }
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRIMSON_ISLE) return
		val data = DConfig.data
		val questReset = lastResetMillis(Task.CRIMSON_QUESTS.reset as Reset.Daily)
		if (data.crimsonQuestsDone.isNotEmpty() && (data.lastDone[CRIMSON_LIST_KEY] ?: 0L) < questReset) {
			data.crimsonQuestsDone.clear()
			DConfig.markDirty()
		}
		for (quest in finishedQuests(tab.body.map { TabLine(it.bareText, it.formattedString()) })) {
			if (!data.crimsonQuestsDone.add(quest)) continue
			data.lastDone[CRIMSON_LIST_KEY] = System.currentTimeMillis()
			if (data.crimsonQuestsDone.size >= QUESTS_PER_DAY) markDone(Task.CRIMSON_QUESTS)
			DConfig.markDirty()
		}
		val player = Client.player ?: return
		if (!matriarchArea.contains(player.position())) return
		val pearlsGone = player.level().getEntitiesOfClass(ArmorStand::class.java, matriarchArea) {
			it.customName?.bareText?.contains("Heavy Pearls Available: 0") == true
		}.isNotEmpty()
		if (pearlsGone && remaining(Task.HEAVY_PEARLS) == null) markDone(Task.HEAVY_PEARLS)
	}

	private fun say(text: String) = Client.showChat(Component.literal(text))

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("customtimer", dev.ashtonite365.apex.util.data.ManagedConfig.Category.TIMERS) {
			onLiteral("add") {
				onArgument("duration", DurationArgumentType) { duration ->
					onArgument("name", RestArgumentType) { name ->
						onRun {
							val label = this[name]
							val length = this[duration]
							DConfig.data.customDurations[label] = length.inWholeMilliseconds
							DConfig.data.customStarts[label] = System.currentTimeMillis()
							DConfig.markDirty()
							say("§a[SkyBlock Apex] Custom timer '$label' started (${ApexFormatters.timespanText(length)}). Restart it with /apex customtimer start $label")
						}
					}
				}
			}
			onLiteral("start") {
				onArgument("name", RestArgumentType) { name ->
					onRun {
						val label = this[name]
						if (label in DConfig.data.customDurations) {
							DConfig.data.customStarts[label] = System.currentTimeMillis()
							DConfig.markDirty()
							say("§a[SkyBlock Apex] Restarted custom timer '$label'.")
						} else say("§c[SkyBlock Apex] No custom timer named '$label'.")
					}
				}
			}
			onLiteral("remove") {
				onArgument("name", RestArgumentType) { name ->
					onRun {
						val label = this[name]
						val existed = DConfig.data.customDurations.remove(label) != null
						DConfig.data.customStarts.remove(label)
						DConfig.markDirty()
						say(if (existed) "§a[SkyBlock Apex] Removed custom timer '$label'." else "§c[SkyBlock Apex] No custom timer named '$label'.")
					}
				}
			}
		}
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		val data = DConfig.data
		godPotion?.let { event.add(Timers.TimerEntry("god-potion", "God Potion", "§a$it", false)) }
		val now = System.currentTimeMillis()
		for (task in Task.entries) {
			val wait = waitFor(task, data.lastDone[task.name], now)
			event.add(Timers.TimerEntry(task.timerId, task.label, statusText(task, wait, data.crimsonQuestsDone.size), wait == null, wait))
		}
		for ((label, length) in data.customDurations)
			event.cooldown("custom", label, ((data.customStarts[label] ?: 0L) + length - now).milliseconds)
		for (t in TimerFeature.timers) event.cooldown("custom", t.message, t.timeLeft())
	}
}
