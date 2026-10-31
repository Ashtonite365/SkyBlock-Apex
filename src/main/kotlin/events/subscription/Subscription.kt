// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events.subscription

import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus

data class Subscription<T : ApexEvent>(val owner: Any, val invoke: (T) -> Unit, val eventBus: ApexEventBus<T>, val methodName: String)
