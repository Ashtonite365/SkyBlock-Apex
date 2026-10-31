// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features

import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.subscription.Subscription
import dev.ashtonite365.apex.events.subscription.SubscriptionList
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.compatloader.CompatSwitch

object FeatureManager {

	fun subscribeEvents() {
		for (generated in SubscriptionList.allLists.filter { CompatSwitch.mayLoad(it.javaClass.name) })
			Faults.attempt("Error while loading events from $generated") { generated.provideSubscriptions { attach(it) } }
	}

	private fun <E : ApexEvent> attach(handler: Subscription<E>) =
		handler.eventBus.subscribe(false, handler.owner.javaClass.simpleName + ":" + handler.methodName, handler.invoke)
}
