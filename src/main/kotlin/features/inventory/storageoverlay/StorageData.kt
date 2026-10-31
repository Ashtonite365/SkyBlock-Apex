package dev.ashtonite365.apex.features.inventory.storageoverlay

import java.util.SortedMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.util.SortedMapSerializer

@Serializable data class StorageData(
	@SerialName("storageInventories")
	val knownPages: @Serializable(with = SortedMapSerializer::class) SortedMap<StoragePageSlot, RememberedPage> = sortedMapOf(),
	@SerialName("customNames")
	val pageLabels: @Serializable(with = SortedMapSerializer::class) SortedMap<StoragePageSlot, String> = sortedMapOf(),
	@SerialName("hotbarSelectedPage")
	var hotbarPage: StoragePageSlot? = null,
) {

	fun labelFor(slot: StoragePageSlot) = pageLabels[slot] ?: slot.defaultName()

	@Serializable
	data class RememberedPage(val slot: StoragePageSlot, var inventory: VirtualInventory?, var icon: VirtualInventory? = null) {

		val iconStack get() = icon?.stacks?.firstOrNull()
	}
}
