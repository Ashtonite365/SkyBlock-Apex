// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.server.packs.resources.PreparableReloadListener

data class FinalizeResourceManagerEvent(val resourceManager: ReloadableResourceManager) : ApexEvent() {
	companion object Companion : ApexEventBus<FinalizeResourceManagerEvent>()

	inline fun registerOnApply(name: String, crossinline function: () -> Unit): Unit {
		val listener = object : PreparableReloadListener {
			override fun getName(): String = name

			override fun reload(store: PreparableReloadListener.SharedState, prepareExecutor: Executor,
					reloadSynchronizer: PreparableReloadListener.PreparationBarrier, applyExecutor: Executor): CompletableFuture<Void> =
				reloadSynchronizer.wait(Unit).thenRunAsync({ function() }, applyExecutor)
		}
		resourceManager.registerReloadListener(listener)
	}
}
