// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch5Corpus {
	val durations = listOf("4d 13h 2m", "1y 2M", "59s", "Less than an hour", "less than an hour", " Less than an hour ", "soon", "", "0s",
		"1d", "1 d", "1d2h", "12h 30m", "2M", "3y", "10m 5s", "5x", "1D", "INACTIVE", "1h 1h", "99999999d", "0d 2h 59m 22s", "１d", "1d\n2h")

	private val tabBits = listOf("Cookie Buff", "Cookie Buff: 1d 2h", "Cookie Buff:", "Cookie Buff:  ", "  Cookie Buff  ", "4d 13h 2m", "INACTIVE",
		"Not active! Obtain booster cookies from the community shop", "Not active", "Profile: Apple", "", "Less than an hour", "Cookie Buff: INACTIVE",
		"Cookie Buff:Not active", "Cookie Buffs", "soon", "Cookie Buff: soon")

	fun tabLists(): List<List<String>> {
		val r = Random(61)
		return List(400) { List(r.nextInt(0, 5)) { tabBits[r.nextInt(tabBits.size)] } }
	}

	private val loreBits = listOf("Status: Not active!", "Status: Active", "Duration: 0d 2h 59m 22s", "Duration: Not active!", "Duration: inactive",
		"Duration:", "Duration: 0s", "Duration: soon", "  Duration: 1h  ", "Click to get store info!", "Obtain the Cookie Buff from Booster",
		"Status: NOT ACTIVE", "Duration: 0d 0h 0m 0s", "Duration: Less than an hour")

	fun lores(): List<List<String>> {
		val r = Random(62)
		return List(300) { List(r.nextInt(0, 4)) { loreBits[r.nextInt(loreBits.size)] } }
	}

	val statuses = listOf("U", "I", "A:1h:60", "A:10m:10", "A:soon:-", "A:2d:2880", "A:0s:0", "A:29m:29", "A:30m:30", "A:31m:31")

	fun warnRuns(): List<List<String>> {
		val r = Random(63)
		return List(300) { List(r.nextInt(1, 9)) { statuses[r.nextInt(statuses.size)] + "|" + listOf(30, 0, 60)[r.nextInt(3)] + "|" + r.nextBoolean() + "|" + r.nextBoolean() + (if (r.nextInt(8) == 0) "|reset" else "") } }
	}

	fun nows(): List<Long> {
		val r = Random(64)
		val epoch = 1560275700000L
		val fixed = listOf(epoch, epoch - 1, epoch + 1, 1_700_000_000_000L, 1_800_000_000_000L, 0L, epoch - 372L * 20 * 60 * 1000, 1_761_600_000_000L,
			1_761_603_300_000L, 1_761_603_299_999L, 1_761_601_200_000L, 1_761_602_399_999L, 1_761_602_400_000L)
		return fixed + List(300) { 1_560_000_000_000L + r.nextLong(0, 400_000_000_000L) }
	}

	val fmtMillis = listOf(0L, 999L, 1000L, 59_999L, 60_000L, 3_599_999L, 3_600_000L, 86_399_999L, 86_400_000L, 90_061_000L, 1_000_000_000L, -1L, -61_000L)
}
