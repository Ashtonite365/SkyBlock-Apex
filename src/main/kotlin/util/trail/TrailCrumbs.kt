/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: breadcrumbs for screen changes and server/world joins.
 */
package dev.ashtonite365.apex.util.trail

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.JoinServerEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.ServerConnectedEvent
import dev.ashtonite365.apex.events.WorldReadyEvent

object TrailCrumbs {
	@Subscribe
	fun onScreen(event: ScreenChangeEvent) = Trail.crumb(Crumb.SCREEN, event.new?.javaClass)

	@Subscribe
	fun onConnected(event: ServerConnectedEvent) = Trail.crumb(Crumb.SERVER_CONNECTED)

	@Subscribe
	fun onJoin(event: JoinServerEvent) = Trail.crumb(Crumb.SERVER_JOINED)

	@Subscribe
	fun onWorldReady(event: WorldReadyEvent) = Trail.crumb(Crumb.WORLD_READY)
}
