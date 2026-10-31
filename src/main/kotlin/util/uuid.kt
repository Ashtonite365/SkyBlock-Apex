package dev.ashtonite365.apex.util

import java.math.BigInteger
import java.util.UUID

fun parsePotentiallyDashlessUUID(unknownFormattedUUID: String): UUID =
	if (unknownFormattedUUID.contains('-')) UUID.fromString(unknownFormattedUUID)
	else parseDashlessUUID(unknownFormattedUUID)

fun parseDashlessUUID(dashlessUuid: String): UUID {
	fun half(from: Int) = BigInteger(dashlessUuid.substring(from, from + 16), 16).toLong()
	val high = half(0)
	return UUID(high, half(16))
}
