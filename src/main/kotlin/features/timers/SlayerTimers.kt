/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (Timers > Slayer): state of the single slayer timer. Behaviour: the quest starts from the
 * "SLAYER QUEST STARTED!" chat line, or from the scoreboard when you join mid-quest; the boss spawn comes from the
 * scoreboard; each boss counts as one kill (the two kill messages are not counted twice); the boss time is compared
 * against the PB snapshot taken at spawn, and the last kill stays on the HUD for 30 s. PBs are stored by
 * SlayerOverlay (slayer-pbs) and only read here.
 */
package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SlayerTimerState(private val clock: () -> Long = System::currentTimeMillis) {
	data class Kill(val boss: String?, val bossTime: Duration, val questTime: Duration?, val pbBefore: Duration?, val at: Long) {
		val pbDelta: Duration? get() = if (pbBefore == null) null else bossTime - pbBefore
	}

	var questName: String? = null
		private set
	var questStartedAt: Long? = null
		private set
	var bossSpawnedAt: Long? = null
		private set
	var pbAtSpawn: Duration? = null
		private set
	var lastKill: Kill? = null
		private set

	private var finished = false

	private fun clearRun() {
		questStartedAt = null
		bossSpawnedAt = null
		pbAtSpawn = null
		finished = true
	}

	fun onScoreboard(name: String?, progress: String?, pbFor: (String) -> Duration?) {
		if (name == null) {
			questName = null
			finished = false
			return
		}
		questName = name
		if (finished) return
		if (questStartedAt == null) questStartedAt = clock()
		if (bossSpawnedAt == null && progress == "Slay the boss!") {
			bossSpawnedAt = clock()
			pbAtSpawn = pbFor(name)
		}
	}

	private fun isKillLine(message: String) = message == "NICE! SLAYER BOSS SLAIN!" || message == "SLAYER QUEST COMPLETE!"
	private fun isFailLine(message: String) = message == "SLAYER QUEST FAILED!" || message.startsWith("Your Slayer Quest has been cancelled")

	fun onChat(message: String): Kill? {
		if (message == "SLAYER QUEST STARTED!") {
			finished = false
			questStartedAt = clock()
			bossSpawnedAt = null
			pbAtSpawn = null
			return null
		}
		if (isKillLine(message)) {
			val spawnedAt = bossSpawnedAt
			if (spawnedAt == null) {

				if (message == "SLAYER QUEST COMPLETE!") {
					questStartedAt = null
					finished = true
				}
				return null
			}
			val now = clock()
			val questTime = questStartedAt?.let { (now - it).milliseconds }
			val kill = Kill(questName, (now - spawnedAt).milliseconds, questTime, pbAtSpawn, now)
			lastKill = kill
			clearRun()
			return kill
		}
		if (isFailLine(message)) clearRun()
		return null
	}

	fun questElapsed(): Duration? {
		val start = questStartedAt ?: return null
		return (clock() - start).milliseconds
	}

	fun bossElapsed(): Duration? {
		val start = bossSpawnedAt ?: return null
		return (clock() - start).milliseconds
	}

	fun recentKill(keep: Duration = 30.seconds): Kill? {
		val kill = lastKill ?: return null
		return if ((clock() - kill.at).milliseconds <= keep) kill else null
	}

	companion object {

		fun formatDelta(delta: Duration, format: (Duration) -> String): String =
			if (delta.isNegative()) "§a-" + format(-delta) else "§c+" + format(delta)
	}
}
