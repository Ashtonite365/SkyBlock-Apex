// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import io.github.moulberry.repo.NEURepository

data class ReloadRegistrationEvent(val repo
	: NEURepository) : ApexEvent() {
	companion object Companion : ApexEventBus<ReloadRegistrationEvent>()
}
