package dev.ashtonite365.apex.util.data

import kotlinx.serialization.KSerializer
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass

abstract class DataHolder<T>(serializer: KSerializer<T>, name: String, default: () -> T) :
	GenericConfig<T>(name, serializer, default) {
	override val storageClass: ConfigStorageClass get() = ConfigStorageClass.STORAGE
}
