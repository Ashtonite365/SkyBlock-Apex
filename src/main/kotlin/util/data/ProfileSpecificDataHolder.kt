package dev.ashtonite365.apex.util.data

import kotlinx.serialization.KSerializer

abstract class ProfileSpecificDataHolder<S>(dataSerializer: KSerializer<S>, configName: String, configDefault: () -> S & Any) :
	ProfileKeyedConfig<S>(configName, dataSerializer, configDefault)
