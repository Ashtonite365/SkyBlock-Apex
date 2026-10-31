// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jade

import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.config.IPluginConfig
import kotlin.time.DurationUnit
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.tr

object CustomMiningHardnessProvider : IBlockComponentProvider {
	override fun getUid(): Identifier = Apex.identifier("custom_mining_hardness")

	override fun appendTooltip(tooltip: ITooltip, block: BlockAccessor, config: IPluginConfig) {
		val power = CustomFakeBlockProvider.getCustomBlock(block)?.breakingPower ?: return
		if (power <= 0) return
		tooltip.add(tr("apex.jade.breaking_power", "Required Breaking Power: $power"))
	}

	private class StageSample(val pos: BlockPos, val stage: Int, val state: BlockState?, val at: TimeMark = TimeMark.now())

	private var older: StageSample? = null
	private var latest: StageSample? = null

	private fun forget() { older = null; latest = null }

	@Subscribe
	fun onTick(event: TickEvent) { if (Client.gameMode?.isDestroying != true) forget() }

	@JvmStatic
	fun setBreakingInfo(blockPos: BlockPos, stage: Int) {
		val before = latest
		older = before
		latest = StageSample(blockPos.immutable(), stage, Client.level?.getBlockState(blockPos))

		if (stage == 10 && before == null) forget()
	}

	@JvmStatic
	fun replaceBreakProgress(original: Float): Float {
		if (!JadeIntegration.TConfig.miningProgress || !isOnMiningIsland()) return original
		val target = Client.gameMode?.destroyBlockPos ?: return original
		val now = latest
		if (now == null || now.pos != target || now.state != Client.level?.getBlockState(target)) {
			forget()
			return original
		}
		val smoothed = older?.let { extrapolate(it, now) }
		return (smoothed ?: now.stage.toFloat()) / 10F
	}

	private fun extrapolate(prev: StageSample, now: StageSample): Float? {
		val step = (now.stage - prev.stage).toDouble()
		if (step < 1) return null
		val perSecond = step / (now.at - prev.at).toDouble(DurationUnit.SECONDS)
		val ahead = minOf(now.at.passedTime().toDouble(DurationUnit.SECONDS) * perSecond, step)
		return (now.stage + ahead).toFloat()
	}

	@JvmStatic
	fun replaceBlockBreakSpeed(original: Float): Float = if (isOnMiningIsland()) 0F else original
}
