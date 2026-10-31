// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.math.abs

@OptIn(NeedsComponents::class)
object GardenAngles {

	val optimal: Map<Crop, Pair<Float, Float>> = buildMap {
		put(Crop.CACTUS, -90f to 0f)
		put(Crop.CARROT, -90f to 2.8f)
		put(Crop.COCOA_BEANS, 180f to -45f)
		put(Crop.MELON, 90f to -58.5f)
		put(Crop.MUSHROOM, 116.5f to 0f)
		put(Crop.NETHER_WART, 90f to 0f)
		put(Crop.POTATO, -90f to 2.8f)
		put(Crop.PUMPKIN, 90f to -58.5f)
		put(Crop.SUGAR_CANE, -135f to 0f)
		put(Crop.WHEAT, 90f to 0f)
	}

	private const val TOLERANCE = 0.05f

	fun format(v: Float, precision: Int): String = String.format("%." + precision.coerceIn(0, 4) + "f", v)

	fun yawDiff(current: Float, target: Float): Float = Mth.wrapDegrees(current - target)

	fun anglesLine(): Component? {
		val player = Client.player ?: return null
		val digits = TConfig.anglePrecision
		return Component.literal("§eYaw: §f" + format(Mth.wrapDegrees(player.yRot), digits) + " §ePitch: §f" + format(player.xRot, digits))
	}

	fun optimalLine(): Component? {
		if (!TConfig.optimalAngles) return null
		val player = Client.player ?: return null
		val tool = player.mainHandItem.view().repoItemId?.skyblockId ?: return null
		val (yaw, pitch) = Crop.forTool(tool)?.let(optimal::get) ?: return null
		val aligned = abs(yawDiff(player.yRot, yaw)) < TOLERANCE && abs(player.xRot - pitch) < TOLERANCE
		return Component.literal("§eOptimal: " + (if (aligned) "§a" else "§c") + "$yaw / $pitch")
	}
}
