package dev.ashtonite365.apex.gui.config

import net.minecraft.client.gui.screens.Screen
import dev.ashtonite365.apex.util.compatloader.CompatLoader

interface ApexConfigScreenProvider {

	val key: String

	val isEnabled: Boolean get() = true

	fun open(search: String?, parent: Screen?): Screen

	companion object Registry : CompatLoader<ApexConfigScreenProvider>(ApexConfigScreenProvider::class.java) {
		private const val BUILT_IN = "builtin"

		val providers: List<ApexConfigScreenProvider> by lazy {
			allValidInstances.filter { it.isEnabled }
				.sortedWith(compareBy<ApexConfigScreenProvider> { it.key != BUILT_IN }.thenBy { it.key })
		}
	}
}
