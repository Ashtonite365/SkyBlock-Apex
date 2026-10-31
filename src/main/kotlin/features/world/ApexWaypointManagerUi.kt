/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex Waypoints: the manager shown in /apex > Miscellaneous > Render > Apex Waypoints. A form (Name, X, Y, Z,
 * "Use my position", "Add Waypoint") and the saved waypoints grouped by island (an island header, then one row per
 * waypoint with its visible switch and a Delete button). Built from the MoulConfig components the settings screen
 * already uses (TextFieldComponent, SwitchComponent, ApexButton, ArrayComponent).
 */
package dev.ashtonite365.apex.features.world

import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.component.ArrayComponent
import io.github.notenoughupdates.moulconfig.gui.component.ColumnComponent
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent
import io.github.notenoughupdates.moulconfig.gui.component.SwitchComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import kotlinx.serialization.json.JsonElement
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.collections.batchUpdate
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.FixedComponent
import dev.ashtonite365.apex.gui.config.GuiAppender
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.ManagedConfig

object ApexWaypointManagerHandler : ManagedConfig.OptionCodec<Unit> {
	override fun encode(element: Unit): JsonElement? = null
	override fun decode(element: JsonElement) {}
	override fun appendRows(opt: ManagedOption<Unit>, guiAppender: GuiAppender) {
		guiAppender.appendLabeledRow(opt.labelText, ApexWaypointManagerUi.summary())
		guiAppender.appendFullRow(ApexWaypointManagerUi.panel())
	}
}

object ApexWaypointManagerUi {

	private var name = ""
	private var x = ""
	private var y = ""
	private var z = ""

	private var formIsland: String? = null
	private var status: Component = Component.empty()

	private fun text(supplier: () -> Component, width: Int): GuiComponent = TextComponent(
		IMinecraft.INSTANCE.defaultFontRenderer, { MoulConfigPlatform.wrap(supplier()) }, width,
		TextComponent.TextAlignment.LEFT, false, false)

	private fun field(getter: () -> String, setter: (String) -> Unit, width: Int, hint: String) = TextFieldComponent(
		object : GetSetter<String> {
			override fun get(): String = getter()
			override fun set(newValue: String) = setter(newValue)
		}, width, suggestion = hint)

	private fun button(label: String, action: () -> Unit) =
		ApexButton(TextComponent(MoulConfigPlatform.wrap(Component.literal(label)))) { action() }

	private fun targetIsland() = formIsland ?: ApexWaypoints.currentIsland()

	fun summary(): GuiComponent = text({
		val here = ApexWaypoints.currentIsland()
		Component.literal("§7${ApexWaypoints.store.list(here).size} on ${ApexWaypoints.islandLabel(here)}")
	}, 120)

	fun useMyPosition() {
		val p = ApexWaypoints.playerPos()
		if (p == null) {
			status = Component.literal("§cJoin a world first.")
			return
		}
		x = p.x.toString(); y = p.y.toString(); z = p.z.toString()
		formIsland = ApexWaypoints.currentIsland()
		status = Component.literal("§7Filled in your position on ${ApexWaypoints.islandLabel(formIsland!!)}.")
	}

	fun addFromForm() {
		val clean = ApexWaypointStore.cleanName(name)
		if (clean == null) {
			status = Component.literal("§cType a name (1-${ApexWaypointStore.MAX_NAME} characters).")
			return
		}
		val player = ApexWaypoints.playerPos()
		val pos = if (x.isBlank() && y.isBlank() && z.isBlank()) player
		else ApexWaypointParse.coords(x, y, z, player)
		if (pos == null) {
			status = Component.literal("§cX, Y and Z must be numbers (or leave all three empty to use your position).")
			return
		}
		val island = targetIsland()
		val result = ApexWaypoints.add(island, clean, pos)
		status = Component.literal(
			"§a${if (result == ApexWaypointStore.AddResult.ADDED) "Added" else "Moved"} $clean on ${ApexWaypoints.islandLabel(island)}.")
		name = ""; x = ""; y = ""; z = ""; formIsland = null
	}

	private fun row(r: ApexWaypointRow): GuiComponent = when (r) {
		is ApexWaypointRow.Header -> {
			val here = r.island == ApexWaypoints.currentIsland()
			text({ Component.literal("§6§l${ApexWaypoints.headerLabel(r.island)} §7(${r.count})${if (here) " §a• you are here" else ""}") }, 300)
		}
		is ApexWaypointRow.Entry -> RowComponent(
			SwitchComponent(object : GetSetter<Boolean> {
				override fun get(): Boolean = ApexWaypoints.store.find(r.island, r.waypoint.name)?.visible ?: false
				override fun set(newValue: Boolean) = ApexWaypoints.setVisible(r.island, r.waypoint.name, newValue)
			}, 200),
			FixedComponent(GetSetter.constant(4), null, TextComponent("")),
			text({ Component.literal("§f${r.waypoint.name} §7${r.waypoint.x} ${r.waypoint.y} ${r.waypoint.z}") }, 200),
			button("§cDelete") {
				ApexWaypoints.remove(r.island, r.waypoint.name)
				status = Component.literal("§7Deleted ${r.waypoint.name}.")
			},
		)
	}

	private fun emptyAwareRow(r: ApexWaypointRow): GuiComponent =
		if (r is ApexWaypointRow.Header && r.count < 0) text({ Component.literal("§7No waypoints yet. Add one above or with /apex waypoint add <name>.") }, 300)
		else row(r)

	fun panel(): GuiComponent {
		val list = ArrayComponent<ApexWaypointRow>(ObservableList(mutableListOf())) { emptyAwareRow(it) }
		val waypoints = WaypointListAdapter(list)
		return ColumnComponent(
			RowComponent(
				text({ Component.literal("Name") }, 30), field({ name }, { name = it }, 110, "Waypoint name"),
				text({ Component.literal(" X") }, 12), field({ x }, { x = it }, 48, "x"),
				text({ Component.literal(" Y") }, 12), field({ y }, { y = it }, 48, "y"),
				text({ Component.literal(" Z") }, 12), field({ z }, { z = it }, 48, "z"),
			),
			RowComponent(
				button("Use my position") { useMyPosition() },
				button("§aAdd Waypoint") { addFromForm() },
				text({ Component.literal(" §7Island: §f${ApexWaypoints.islandLabel(targetIsland())}") }, 160),
			),
			text({ status }, 330),
			waypoints,
		)
	}

	private class WaypointListAdapter(val list: ArrayComponent<ApexWaypointRow>) :
		io.github.notenoughupdates.moulconfig.gui.component.IndirectComponent({ list }) {
		private var seenStore: ApexWaypointStore? = null
		private var seenVersion = -1
		private var seenIsland: String? = null

		private fun refresh() {
			val store = ApexWaypoints.store
			val island = ApexWaypoints.currentIsland()
			if (store === seenStore && store.version == seenVersion && island == seenIsland) return
			seenStore = store; seenVersion = store.version; seenIsland = island
			list.cache.clear()
			val rows = store.grouped(island, ApexWaypoints::islandLabel)
			list.list.batchUpdate {
				list.list.clear()
				if (rows.isEmpty()) list.list.add(ApexWaypointRow.Header(ApexWaypointStore.UNKNOWN, -1)) else list.list.addAll(rows)
			}
		}

		override fun getHeight(): Int { refresh(); return super.getHeight() }
		override fun getWidth(): Int { refresh(); return super.getWidth() }
		override fun render(context: GuiImmediateContext) { refresh(); super.render(context) }
	}
}
