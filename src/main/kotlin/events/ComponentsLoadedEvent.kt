// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import dev.ashtonite365.apex.annotations.Subscribe

class ComponentsLoadedEvent
	: ApexEvent() {
	companion object Companion : ApexEventBus<ComponentsLoadedEvent>() {

		var generation: Int = -1

		@Subscribe
		fun onComponentsLoaded(event: ComponentsLoadedEvent) = run { generation += 1 }
	}
}
