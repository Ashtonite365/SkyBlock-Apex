package dev.ashtonite365.apex.util.mc

import com.mojang.serialization.DynamicOps
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderOwner
import net.minecraft.resources.RegistryOps

class LenientRegistryOps<T : Any>(format: DynamicOps<T>, infos: RegistryInfoLookup) : RegistryOps<T>(format, infos) {
	constructor(format: DynamicOps<T>, provider: HolderLookup.Provider) : this(format, RegistryOps.HolderLookupAdapter(provider))

	class PermissiveOwner<E : Any> : HolderOwner<E> {
		override fun canSerialize(other: HolderOwner<E>): Boolean = true
	}
}
