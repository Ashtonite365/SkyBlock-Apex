// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ReloadRegistrationEvent
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.ManagedConfig

object NPCWaypoints {

	var allNpcWaypoints: List<NavigableWaypoint> = emptyList()

	@Subscribe
	fun onRepoReloadRegistration(event: ReloadRegistrationEvent): Unit {
		event.repo.registerReloadListener { repo ->
			allNpcWaypoints = repo.items.itemsSafe.values.mapNotNull { item ->
				if (item.island.isNullOrBlank()) null else NavigableWaypoint.RepoNpc(item)
			}
		}
	}

	@Subscribe
	fun onOpenGui(event: CommandEvent.SubCommand): Unit {
		event.addSubcommand("npcs", ManagedConfig.Category.MISC) {
			onRun {
				val screen = GuiXml.loadScreen("npc_waypoints", NpcWaypointGui(allNpcWaypoints), null)
				ScreenUtil.setScreenLater(screen)
			}
		}
	}
}
