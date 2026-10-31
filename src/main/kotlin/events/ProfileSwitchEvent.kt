// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import java.util.UUID

data class ProfileSwitchEvent(val oldProfile
	: UUID?, val newProfile: UUID?) : ApexEvent() {
	companion object Companion : ApexEventBus<ProfileSwitchEvent>()
}
