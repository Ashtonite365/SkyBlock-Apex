package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SafeRepoConstants
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SkyBlockIsland.ModeSerializer::class)
class SkyBlockIsland private constructor(val serverMode: String) {

	val hasCustomMining: Boolean
		get() = RepoManager.miningData.customMiningAreas[this]?.isSpecialMining == true

	val userFriendlyName: String
		get() = SafeRepoConstants.areaNames()[serverMode] ?: serverMode

	object ModeSerializer : KSerializer<SkyBlockIsland> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SkyBlockIsland", PrimitiveKind.STRING)
		override fun deserialize(decoder: Decoder): SkyBlockIsland = ofMode(decoder.decodeString())
		override fun serialize(encoder: Encoder, value: SkyBlockIsland) {
			encoder.encodeString(value.serverMode)
		}
	}

	companion object {
		private val byMode = ConcurrentHashMap<String, SkyBlockIsland>()

		fun ofMode(mode: String): SkyBlockIsland = byMode.computeIfAbsent(mode, ::SkyBlockIsland)

		val HUB = ofMode("hub")
		val PRIVATE_ISLAND = ofMode("dynamic")
		val GARDEN = ofMode("garden")
		val JERRY_WORKSHOP = ofMode("winter")
		val RIFT = ofMode("rift")

		val DUNGEON_HUB = ofMode("dungeon_hub")
		val DUNGEON = ofMode("dungeon")
		val KUUDRA = ofMode("kuudra")
		val CRIMSON_ISLE = ofMode("crimson_isle")

		val GOLD_MINE = ofMode("mining_1")
		val DEEP_CAVERNS = ofMode("mining_2")
		val DWARVEN_MINES = ofMode("mining_3")
		val CRYSTAL_HOLLOWS = ofMode("crystal_hollows")
		val MINESHAFT = ofMode("mineshaft")

		val SPIDER = ofMode("combat_1")
		val END = ofMode("combat_3")

		val FARMING_ISLANDS = ofMode("farming_1")
		val PARK = ofMode("foraging_1")
		val GALATEA = ofMode("foraging_2")
		val BACKWATER_BAYOU = ofMode("fishing_1")

		val NIL = ofMode("_")
	}
}
