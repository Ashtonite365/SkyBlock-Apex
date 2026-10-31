// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.features.fishing.FishingHelper.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import kotlin.math.abs
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

object FishingPlusPorts {

	private val bansheePitches = FloatArray(20) { (12 + it) / 63f }
	private const val PITCH_TOLERANCE = 1e-4f

	internal fun isBansheePitch(pitch: Float): Boolean {
		for (p in bansheePitches) if (abs(p - pitch) < PITCH_TOLERANCE) return true
		return false
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!TConfig.muteBanshee || SkyBlockSession.skyblockIsland != SkyBlockIsland.BACKWATER_BAYOU) return
		if (event.sound.value() != SoundEvents.GHAST_AMBIENT || !isBansheePitch(event.pitch)) return
		event.cancel()
	}

	private val BUCKET_TITLES = listOf("'s Chum Bucket", "'s Chumcap Bucket")
	private val EMPTY_BUCKETS = setOf("Empty Chum Bucket", "Empty Chumcap Bucket")

	internal fun isChumTag(name: String, ownName: String?, hideOwn: Boolean): Boolean {
		if (BUCKET_TITLES.none(name::endsWith)) return "/10 Chums" in name
		return hideOwn || ownName == null || !name.startsWith(ownName)
	}

	fun shouldHideChum(entity: Entity): Boolean {
		if (!TConfig.hideChumBuckets || !SkyBlockSession.inSkyBlock || entity !is ArmorStand) return false
		val tag = entity.customName?.string
		if (tag != null && isChumTag(tag, Client.player?.gameProfile?.name, TConfig.hideOwnChumBucket)) return true
		return entity.getItemBySlot(EquipmentSlot.HEAD).hoverName.string in EMPTY_BUCKETS
	}
}
