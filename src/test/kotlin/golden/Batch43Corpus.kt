// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch43Discord {
	val r = Random(4343)
	private val values = listOf(null, "", "/run/user/1000", "/run/user/1000/", "/run/user/abc", "/tmp", "/var/tmp", "C:\\Temp", "1000", "/x/0", "/x/١٢", "/run/user/1000")
	val envs: List<Map<String, String?>> = List(400) {
		mapOf("XDG_RUNTIME_DIR" to values[r.nextInt(values.size)], "TMPDIR" to values[r.nextInt(values.size)], "TMP" to values[r.nextInt(values.size)], "TEMP" to values[r.nextInt(values.size)])
	}
	val jsons = listOf("", "{}", "{\"v\":1}", "é", "日本語", "\u0000", "a".repeat(1000), "\uD83D\uDE00", "\uD800")
	val opcodes = listOf(0, 1, 2, 3, 4, -1, Int.MAX_VALUE, Int.MIN_VALUE, 256)
	val texts = listOf(null, "", "Hypixel SkyBlock", "The Hub", "quote\"s", "line\nbreak", "日本", "\\")
	val starts = listOf(null, 0L, 1_700_000_000L, -1L, Long.MAX_VALUE)
	val pids = listOf(0L, 1L, 12345L, Long.MAX_VALUE, -5L)
}
