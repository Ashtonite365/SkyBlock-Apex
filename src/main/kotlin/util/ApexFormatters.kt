package dev.ashtonite365.apex.util

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

object ApexFormatters {
	private val compactUnits: List<Pair<Double, String>> = listOf(1e12 to "t", 1e9 to "b", 1e6 to "m", 1e3 to "k")

	fun compactNumber(n: Double): String {
		if (n.isNaN() || n.absoluteValue < 1) return n.toString()
		if (n < 0) return "-" + compactNumber(-n)
		val (unit, suffix) = compactUnits.firstOrNull { (limit, _) -> n >= limit } ?: (1.0 to "")
		val tenths = (n / unit * 10).roundToInt()
		return if (tenths < 100) "${tenths / 10}.${tenths % 10}$suffix" else "${tenths / 10}$suffix"
	}

	fun withThousands(n: Int, groupSize: Int = 3): String = withThousands(n.toLong(), groupSize)

	fun withThousands(n: Long, groupSize: Int = 3, includeSign: Boolean = false): String {
		if (n == Long.MIN_VALUE)
			return (if (includeSign) "+" else "") + withThousands(n / 1000) + "," + (n % 1000)
		val prefix = when {
			n < 0 -> "-"
			includeSign -> "+"
			else -> ""
		}
		return prefix + groupDigits(n.absoluteValue.toString())
	}

	private fun groupDigits(digits: String): String {
		val out = StringBuilder(digits.length + digits.length / 3)
		digits.forEachIndexed { i, c ->
			if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
			out.append(c)
		}
		return out.toString()
	}

	fun withThousands(n: Float, fractionalDigits: Int): String = withThousands(n.toDouble(), fractionalDigits)

	fun withThousands(n: Double, fractionalDigits: Int, includeSign: Boolean = false): String {
		val integral = n.toLong()
		val head = withThousands(integral, includeSign = includeSign)
		var multiplier = 1.0
		repeat(fractionalDigits) { multiplier *= 10 }
		val digits = ((n - integral).absoluteValue * multiplier).toInt()
		if (digits == 0) return head
		return head + "." + digits.toString().padStart(fractionalDigits, '0').trimEnd('0')
	}

	fun distanceText(distance: Double): String =
		if (distance < 10) "%.1fm".format(distance) else "%dm".format(distance.toInt())

	fun timespanText(span: Duration, millis: Boolean = false): String {
		if (span.isInfinite()) return if (span.isPositive()) "∞" else "-∞"
		return buildString {
			if (span.isNegative()) append('-')
			span.toComponents { days, hours, minutes, seconds, nanoseconds ->
				if (days > 0) append(days).append('d')
				if (hours > 0) append(hours).append('h')
				if (minutes > 0) append(minutes).append('m')
				val ms = nanoseconds / 1_000_000
				append(seconds)
				when {
					millis -> append("s").append(ms).append("ms")
					span.absoluteValue < 5.seconds && ms / 100 != 0 -> append('.').append((ms / 100).digitToChar()).append('s')
					else -> append('s')
				}
			}
		}
	}

	fun debugPath(path: Path): Component = when {
		!Files.exists(path) -> tr("apex.path.missing", "$path (missing)").red()
		!Files.isReadable(path) -> tr("apex.path.unreadable", "$path (unreadable)").red()
		Files.isRegularFile(path) -> tr("apex.path.regular", "$path (exists ${fileSizeText(path.fileSize())})").lime()
		Files.isDirectory(path) -> tr("apex.path.directory", "$path (${path.listDirectoryEntries().size} entries)").darkGreen()
		else -> tr("apex.path.unknown", "$path (unknown)").grey()
	}

	fun fileSizeText(fileSizeInBytes: Long): String = "${fileSizeInBytes / 1024} KiB"

	fun boolText(flag: Boolean, trueIsGood: Boolean = true): Component {
		val shown = Component.literal(flag.toString())
		return if (flag == trueIsGood) shown.lime() else shown.red()
	}

	fun positionText(position: BlockPos): Component =
		Component.literal("x: ${position.x}, y: ${position.y}, z: ${position.z}")

	fun percentText(value: Double, decimals: Int = 1): String = "%.${decimals}f%%".format(value * 100)
}
