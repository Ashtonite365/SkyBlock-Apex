// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.xml.Bind
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.collections.batchUpdate
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

class NpcWaypointGui(val allWaypoints: List<NavigableWaypoint>) {

	data class NavigableWaypointW(val waypoint: NavigableWaypoint) {
		@Bind
		fun name(): Component = Component.literal(waypoint.name)

		@Bind
		fun isSelected(): Boolean = waypoint == NavigationHelper.targetWaypoint

		@Bind
		fun click(): Unit {
			when {
				SavedKeyBinding.isShiftDown() -> NavigationHelper.run {
					targetWaypoint = waypoint
					tryWarpNear()
				}
				isSelected() -> NavigationHelper.targetWaypoint = null
				else -> NavigationHelper.targetWaypoint = waypoint
			}
		}
	}

	@JvmField
	@field:Bind
	var search: String = ""

	var lastSearch: String? = null

	val results: ObservableList<NavigableWaypointW> = ObservableList(ArrayList())

	@Bind("results")
	fun results(): ObservableList<NavigableWaypointW> = results

	@Bind
	fun tick(): Unit {
		if (lastSearch == search) return
		this.updateSearch()
		this.lastSearch = this.search
	}

	fun updateSearch(): Unit {
		val words = search.split(Regex(" +"))
		val matching = if (search.isBlank()) allWaypoints
		else allWaypoints.filter { candidate -> words.all { candidate.name.contains(it, true) } }
		results.batchUpdate {
			results.clear()
			matching.mapTo(results, ::NavigableWaypointW)
		}
	}
}
