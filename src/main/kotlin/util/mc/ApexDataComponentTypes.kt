package dev.ashtonite365.apex.util.mc

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.core.component.DataComponentType
import net.minecraft.network.codec.StreamCodec
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.repo.MiningRepoData

object ApexDataComponentTypes {
	private const val NEVER_SENT = "This element should never be encoded or decoded"

	@Subscribe
	fun init(event: ClientInitEvent) {

		val type = BROKEN_FLAG
		check(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) != null)
	}

	private fun <T : Any> register(id: String, configure: DataComponentType.Builder<T>.() -> Unit): DataComponentType<T> {
		val builder = DataComponentType.builder<T>()
		builder.configure()
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Apex.identifier(id), builder.build())
	}

	fun <T : Any> errorCodec(reason: String): StreamCodec<in ByteBuf, T> {
		val fail: () -> Nothing = { throw IllegalStateException(reason) }
		return StreamCodec.of({ _, _ -> fail() }, { _ -> fail() })
	}

	fun <V : Any, Self : DataComponentType.Builder<V>> Self.neverEncode(reason: String = NEVER_SENT): Self {
		networkSynchronized(errorCodec(reason))
		return this
	}

	val BROKEN_FLAG: DataComponentType<Boolean> = register("is_broken") {

		persistent(Codec.BOOL.fieldOf("is_broken").codec())
	}

	val MINING_BLOCK_INFO: DataComponentType<MiningRepoData.CustomMiningBlock> = register("custom_mining_block") { neverEncode() }
}
