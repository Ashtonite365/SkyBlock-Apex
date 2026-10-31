package dev.ashtonite365.apex.features.inventory

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.aqua
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.timestamp
import dev.ashtonite365.apex.util.tr

object TimerInLore {
	@Config
	object TConfig : ManagedConfig("lore-timers", Category.INVENTORY) {
		val annotateCountdowns by toggle("show") { true }
		val addCreationLine by toggle("show-creation") { true }
		val clockStyle by choice("format") { TimerFormat.SOCIALIST }
	}

	enum class TimerFormat(val formatter: DateTimeFormatter) : StringRepresentable {
		RFC(DateTimeFormatter.RFC_1123_DATE_TIME),
		LOCAL(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
		SOCIALIST(DateTimeFormatter.ofPattern("EEE dd.MM.uuuu HH:mm:ss")),
		AMERICAN(DateTimeFormatter.ofPattern("EEEE, MMM d h:mm a yyyy")),
		RFCPrecise(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS Z"));

		fun render(moment: ZonedDateTime, zone: ZoneId): String = formatter.format(moment.withZoneSameInstant(zone))

		override fun getSerializedName() = name
	}

	class Countdown(val cue: String, val label: String, val chained: Boolean = false, val dayItemsOnly: Boolean = false)

	val countdowns = listOf(
		Countdown("Starting in:", "Starts at"),
		Countdown("Starts in:", "Starts at"),
		Countdown("Interest in:", "Interest at"),
		Countdown("Until interest:", "Interest at"),
		Countdown("Ends in:", "Ends at"),
		Countdown("Remaining:", "Ends at"),
		Countdown("Duration:", "Finishes at"),
		Countdown("Time left:", "Ends at"),
		Countdown("Event lasts for", "Ends at", chained = true),
		Countdown("Auction ends in:", "Auction ends at"),
		Countdown("Ends:", "Finishes at"),
		Countdown(" (§e", "Starts at", dayItemsOnly = true),
		Countdown("Contribute again", "Come back at"),
		Countdown("Next Charge", "Available at"),
		Countdown("Auction ends in", "Ends at"),
		Countdown("Resets in:", "Resets at"),
		Countdown("Time Remaining:", "Ends at"),
		Countdown("Cooldown:", "Come back at"),
		Countdown("On cooldown:", "Available at"),
		Countdown("Event ends in:", "Ends at"),
		Countdown("Next Stage:", "Come back at"),
		Countdown("Decay", "Decays at"),
		Countdown("Lasts for:", "Water until"),
		Countdown("Until ready:", "Ready at"),
		Countdown("Time until:", "Occurs at"),
	)

	private val unitRanks = mapOf(
		"y" to 0, "year" to 0, "years" to 0,
		"d" to 1, "day" to 1, "days" to 1,
		"h" to 2, "hour" to 2, "hours" to 2,
		"m" to 3, "minute" to 3, "minutes" to 3,
		"s" to 4, "second" to 4, "seconds" to 4,
	)

	private val token = Regex("""(?<!\S)(\d+) ?([a-z]+)(?!\S)""")

	fun durationIn(text: String): List<Long>? {
		var latest: LongArray? = null
		var current: LongArray? = null
		var lastRank = -1
		var lastEnd = -1
		for (hit in token.findAll(text)) {
			val rank = unitRanks[hit.groupValues[2]] ?: continue
			if (rank == 0 && hit.range.last + 1 >= text.length) continue
			val joins = current != null && rank > lastRank && text.substring(lastEnd, hit.range.first).isBlank()
			if (!joins) current = LongArray(5).also { latest = it }
			current!![rank] = hit.groupValues[1].toLong()
			lastRank = rank
			lastEnd = hit.range.last + 1
		}
		return latest?.toList()
	}

	private fun endOf(start: ZonedDateTime, amounts: List<Long>): ZonedDateTime =
		start.plusYears(amounts[0]).plusDays(amounts[1]).plusHours(amounts[2]).plusMinutes(amounts[3])
			.plusSeconds(amounts[4])

	private fun countdownFor(text: String, dayItem: Boolean): Countdown? =
		countdowns.firstOrNull { (dayItem || !it.dayItemsOnly) && it.cue in text }

	fun annotate(lines: MutableList<Component>, itemName: String, format: TimerFormat, now: ZonedDateTime, zone: ZoneId) {
		val dayItem = itemName.startsWith("Day ")
		var baseline: ZonedDateTime? = null
		for (index in 0 until lines.size) {
			val text = lines[index].bareText
			val countdown = countdownFor(text, dayItem) ?: continue
			val amounts = durationIn(text)?.takeIf { parts -> parts.any { it != 0L } } ?: continue
			val origin = if (countdown.chained) baseline else now
			val added = if (origin == null) {
				tr("apex.loretimer.missingrelative", "Found a relative countdown with no baseline").grey()
			} else {
				val end = endOf(origin, amounts)
				baseline = end
				Component.literal("${countdown.label}: ").grey()
					.append(Component.literal(format.render(end, zone)).aqua())
			}
			lines.add(index + 1, added)
		}
	}

	fun creationLine(created: Instant, format: TimerFormat, zone: ZoneId): Component {
		val shown = format.render(created.atZone(zone), zone)
		return tr("apex.lore.creationtimestamp", "Created at: $shown").grey()
	}

	@Subscribe
	fun addCreationTime(event: ItemTooltipEvent) {
		if (!TConfig.addCreationLine) return
		val created = event.stack.view().timestamp ?: return
		event.lines.add(creationLine(created, TConfig.clockStyle, ZoneId.systemDefault()))
	}

	@Subscribe
	fun addCountdownEnds(event: ItemTooltipEvent) {
		if (!TConfig.annotateCountdowns || !SkyBlockSession.inSkyBlock) return
		val title = event.stack.view().displayNameAccordingToNbt.bareText
		annotate(event.lines, title, TConfig.clockStyle, ZonedDateTime.now(), ZoneId.systemDefault())
	}
}
