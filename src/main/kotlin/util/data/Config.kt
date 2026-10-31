package dev.ashtonite365.apex.util.data

import dev.ashtonite365.apex.util.compatloader.CompatLoader

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Config(val prefix: String = "")

interface IConfigProvider {
	val configs: List<IDataHolder<*>>

	companion object {
		val providers: CompatLoader<IConfigProvider> = CompatLoader(IConfigProvider::class)
	}
}
