// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TIME_PATTERN

object DungeonUtil {

	private val RUN_TIMER_LINE = Regex("Time Elapsed: (?:$TIME_PATTERN\\s*)+")

	val isInDungeonIsland: Boolean
		get() = SkyBlockIsland.DUNGEON == SkyBlockSession.skyblockIsland

	val isInActiveDungeon: Boolean
		get() = isInDungeonIsland && ScoreboardUtil.simplifiedScoreboardLines.any(RUN_TIMER_LINE::matches)
}
