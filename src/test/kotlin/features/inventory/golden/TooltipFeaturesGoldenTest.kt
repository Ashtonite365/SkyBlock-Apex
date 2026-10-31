// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.inventory.MissingEnchantments
import dev.ashtonite365.apex.features.inventory.PriceData
import dev.ashtonite365.apex.features.inventory.TimerInLore

@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class TooltipFeaturesGoldenTest {
	private val loreSets = listOf(
		listOf("Ends in: 1d 2h", "Event lasts for 3h", "Other"),
		listOf("Event lasts for 3h", "Starts in: 5m"),
		listOf("Starting in: 1 year 2 days 3 hours 4 minutes 5 seconds"),
		listOf("Time left: 0s", "Cooldown: 10s", "On cooldown: 45m 3s"),
		listOf("Auction ends in: 2h 3m", "Auction ends in 4h", "Ends: 3d"),
		listOf("(§e5d 3h)", " (§e5d 3h)", "Duration: 7 days"),
		listOf("Contribute again in 2h", "Next Charge 30s", "Resets in: 1d", "Time Remaining: 12h", "Event ends in: 1h 1m 1s"),
		listOf("Next Stage: 5m", "Decay in 2h", "Lasts for: 20m", "Until ready: 3h", "Time until: 2d", "Interest in: 31h", "Until interest: 3h 3m", "Remaining: 4d", "Starts in: 2D 3H"),
		listOf("Ends in: soon", "Ends in: 5 apples 3h", "Ends in: 3h and 2d", "Remaining: 1d2h3m4s"),
		listOf("Ends in: 1h", "Ends in: 2h", "Ends in: 3h", "Ends in: 4h"),
		listOf(),
	)
	private val now: ZonedDateTime = ZonedDateTime.ofInstant(Instant.parse("2026-03-07T22:15:30Z"), ZoneId.of("US/Eastern"))

	private fun joined(lines: List<Component>) = lines.joinToString(" / ") { Golden.styled(it) }

	@Test
	fun loreTimers() {
		val rows = Golden.check("lore-timers") { key ->
			when {
				key == "formats" -> TimerInLore.TimerFormat.entries.joinToString(",") { it.serializedName }
				key.startsWith("created ") -> key.removePrefix("created ").split('|').let { (format, zone, at) ->
					Golden.styled(TimerInLore.creationLine(Instant.parse(at), TimerInLore.TimerFormat.valueOf(format), ZoneId.of(zone)))
				}
				else -> key.split('|').let { (set, name, format, zone) ->
					val lines = loreSets[set.toInt()].map { Component.literal(it) as Component }.toMutableList()
					TimerInLore.annotate(lines, name, TimerInLore.TimerFormat.valueOf(format), now, ZoneId.of(zone))
					joined(lines)
				}
			}
		}
		assertEquals(185, rows)
	}

	@Test
	fun priceLines() {
		PriceData.TConfig.loadFrom(Unit, kotlinx.serialization.json.JsonObject(mapOf()))
		val quote = PriceData.BazaarQuote(instantBuy = 1234.5, instantSell = 1000.25)
		val rows = Golden.check("price-data") { key ->
			val arg = key.substringAfter(' ')
			if (key.startsWith("format ")) return@check Golden.styled(PriceData.priceRow(Component.literal("Label"), arg.toDouble()))
			val (kind, size, whole, type) = arg.split('|')
			joined(PriceData.tooltipLines(
				quote.takeIf { kind == "bz" },
				5_000_000.0.takeIf { kind == "ah" || kind == "ah-noavg" },
				4_900_000.5.takeIf { kind == "ah" },
				size.toLong(), whole.toBoolean(), PriceData.BazaarRows.valueOf(type), PriceData.TConfig.stackSizeKey.format(),
			))
		}
		assertEquals(47, rows)
	}

	@Test
	fun missingEnchantmentLayout() {
		val upgradeMaps = listOf(mapOf("Sharpness V" to 7), mapOf("Critical VI" to 7, "Sharpness V" to 6), mapOf("Smite VII" to 7), mapOf(),
			mapOf("Growth VI" to 7, "Protection V" to 7), mapOf("Sharpness" to 10))
		val rows = Golden.check("missing-enchantments") { key ->
			val arg = key.substringAfter(' ')
			if (key.startsWith("insert ")) return@check "${MissingEnchantments.insertAt(if (arg.isEmpty()) emptyList() else arg.split('~'))}"
			val (text, map, styled) = arg.split('|')
			val line: Component = if (styled.toBoolean()) Component.literal(text).withStyle(ChatFormatting.BLUE) else Component.literal(text)
			Golden.styled(MissingEnchantments.withUpgrades(line, upgradeMaps[map.toInt()]))
		}
		assertEquals(92, rows)
	}
}
