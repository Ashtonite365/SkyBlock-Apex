// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events.subscription

import java.util.ServiceLoader
import dev.ashtonite365.apex.Apex

fun interface SubscriptionList {

	fun provideSubscriptions(addSubscription: (Subscription<*>) -> Unit): Unit

	companion object Discovery {

		val allLists: List<SubscriptionList> by lazy(::discover)

		private fun discover(): List<SubscriptionList> {
			val found = ArrayList<SubscriptionList>()
			for (provider in ServiceLoader.load(SubscriptionList::class.java).stream()) {
				try {
					found += provider.get()
				} catch (t: Throwable) {
					Apex.logger.error("Failed to instantiate subscription list ${provider.type()}", t)
				}
			}
			return found
		}

		init { check(allLists.isNotEmpty()) { "no generated subscription lists were found" } }
	}
}
