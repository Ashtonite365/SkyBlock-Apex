package dev.ashtonite365.apex.features.inventory.storageoverlay

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import dev.ashtonite365.apex.util.Client

@Serializable(with = StoragePageSlot.AsIndex::class)
data class StoragePageSlot(val index: Int) : Comparable<StoragePageSlot> {
	val isEnderChest: Boolean get() = index < ENDER_PAGES
	val isBackPack: Boolean get() = !isEnderChest && index <= LAST_INDEX

	private val number: Int get() = if (isEnderChest) index + 1 else index - ENDER_PAGES + 1

	val slotIndexInOverviewPage: Int get() =
		if (isEnderChest) ENDER_ROW_START + index else BACKPACK_ROW_START + (index - ENDER_PAGES)

	fun defaultName(): String = (if (isEnderChest) "Ender Chest #" else "Backpack #") + number

	private fun openCommand(): String = (if (isEnderChest) "enderchest " else "backpack ") + number

	fun navigateTo() {
		val command: String = openCommand()
		StorageOverlay.debugLog { "navigateTo $this: sending /$command connection=${Client.player?.connection != null}" }

		dev.ashtonite365.apex.features.menus.StaticMenus.expectScreen()
		Client.runCommand(command)
	}

	override fun compareTo(other: StoragePageSlot): Int = index.compareTo(other.index)

	object AsIndex : KSerializer<StoragePageSlot> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StoragePageSlot", PrimitiveKind.INT)
		override fun serialize(encoder: Encoder, value: StoragePageSlot) = encoder.encodeInt(value.index)
		override fun deserialize(decoder: Decoder): StoragePageSlot = StoragePageSlot(decoder.decodeInt())
	}

	companion object {
		private const val ENDER_PAGES = 9
		private const val BACKPACK_PAGES = 18
		private const val LAST_INDEX = ENDER_PAGES + BACKPACK_PAGES - 1
		private const val ENDER_ROW_START = 9
		private const val BACKPACK_ROW_START = 27

		fun fromOverviewSlotIndex(slot: Int): StoragePageSlot? = when (slot) {
			in ENDER_ROW_START until ENDER_ROW_START + ENDER_PAGES -> StoragePageSlot(slot - ENDER_ROW_START)
			in BACKPACK_ROW_START until BACKPACK_ROW_START + BACKPACK_PAGES -> StoragePageSlot(ENDER_PAGES + slot - BACKPACK_ROW_START)
			else -> null
		}

		fun ofEnderChestPage(slot: Int): StoragePageSlot {
			require(slot in 1..ENDER_PAGES) { "ender chest page $slot is not in 1..$ENDER_PAGES" }
			return StoragePageSlot(slot - 1)
		}

		fun ofBackPackPage(slot: Int): StoragePageSlot {
			require(slot in 1..BACKPACK_PAGES) { "backpack slot $slot is not in 1..$BACKPACK_PAGES" }
			return StoragePageSlot(ENDER_PAGES + slot - 1)
		}
	}
}
