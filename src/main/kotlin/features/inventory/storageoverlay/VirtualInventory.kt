package dev.ashtonite365.apex.features.inventory.storageoverlay

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import dev.ashtonite365.apex.util.Base64Util
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.KeptStack

@Serializable(with = VirtualInventory.Serializer::class)
data class VirtualInventory(val stacks: List<KeptStack>) {
	val rows: Int = stacks.size / 9

	val serializationCache: CompletableFuture<ByteArray> = CompletableFuture.supplyAsync { InventoryBlob.pack(stacks) }

	init { assert(stacks.size % 9 == 0 && rows in 1..5) }

	object Serializer : KSerializer<VirtualInventory> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("VirtualInventory", PrimitiveKind.STRING)

		override fun serialize(encoder: Encoder, value: VirtualInventory) =
			encoder.encodeString(value.serializationCache.get().let { bytes -> Base64Util.encodeToString(bytes) })

		override fun deserialize(decoder: Decoder): VirtualInventory =
			VirtualInventory(InventoryBlob.unpack(Base64Util.decodeBytes(decoder.decodeString())))
	}
}

private object InventoryBlob {
	private const val LIST_KEY = "INVENTORY"
	private const val READ_LIMIT = 100_000_000L

	fun pack(stacks: List<KeptStack>): ByteArray {
		val ops = Client.registryNbtOps
		val list = ListTag()
		for (stack in stacks) {
			val tag: Tag = Faults.attempt("Could not serialize item") { KeptStack.CODEC.encodeStart(ops, stack).orThrow }
				.orElse { CompoundTag() }
			list.add(tag)
		}
		val root = CompoundTag()
		root.put(LIST_KEY, list)
		return ByteArrayOutputStream().use { out ->
			NbtIo.writeCompressed(root, out)
			out.toByteArray()
		}
	}

	fun unpack(bytes: ByteArray): List<KeptStack> {
		val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(READ_LIMIT))
		val list = root.getList(LIST_KEY).orElse(null) ?: return emptyList()
		val ops = Client.registryNbtOps
		return list.map { tag ->
			Faults.attempt("Could not deserialize item") { KeptStack.CODEC.decode(ops, tag).orThrow.first }
				.orElse { KeptStack.empty() }
		}
	}
}
