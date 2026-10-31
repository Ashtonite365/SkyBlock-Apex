// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.config.ActionCodec
import dev.ashtonite365.apex.gui.config.DecimalHandler
import dev.ashtonite365.apex.gui.config.GuiAppender
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.PanelStyle

object TimerBoards {

	class OwnPositionsHandler : ManagedConfig.OptionCodec<MutableMap<String, HudPosition>>, ActionCodec {
		private var option: ManagedOption<MutableMap<String, HudPosition>>? = null
		private val serializer = MapSerializer(String.serializer(), HudPosition.serializer())

		override fun onCreated(opt: ManagedOption<MutableMap<String, HudPosition>>) { option = opt }
		override fun encode(element: MutableMap<String, HudPosition>): JsonElement? = Json.encodeToJsonElement(serializer, element.toSortedMap())
		override fun decode(element: JsonElement): MutableMap<String, HudPosition> = LinkedHashMap(Json.decodeFromJsonElement(serializer, element))

		override fun runAction() {
			val opt = option ?: return
			if (opt.value.isEmpty()) return
			opt.value = LinkedHashMap()
			ownHuds.clear()
		}

		override fun appendRows(opt: ManagedOption<MutableMap<String, HudPosition>>, guiAppender: GuiAppender) {
			guiAppender.appendLabeledRow(opt.labelText, ApexButton(TextComponent(Component.translatable("apex.timer-boards.reset-own").string), action = ::runAction))
		}
	}

	@Config
	object TConfig : ManagedConfig("timer-boards", Category.TIMERS) {

		val board1Scale by option("board-1-scale", { 1.0 }, DecimalHandler(this, 0.5, 2.0, 0.05))
		val board1Opacity by integer("board-1-opacity", 0, 100) { 100 }
		val board1TextShadow by toggle("board-1-text-shadow") { true }
		val board1Sort by choice("board-1-sort") { BoardSort.MANUAL }
		val ownHudPositions = option("own-hud-positions", { LinkedHashMap<String, HudPosition>() }, OwnPositionsHandler())

		val board2Enabled by toggle("board-2-enabled") { false }
		val board2Position by position("board-2-position", 160, 90) { Vector2i(5, 330) }
		val board2Background by choice("board-2-background") { PanelStyle.STANDARD }
		val board2Scale by option("board-2-scale", { 1.0 }, DecimalHandler(this, 0.5, 2.0, 0.05))
		val board2Opacity by integer("board-2-opacity", 0, 100) { 100 }
		val board2TextShadow by toggle("board-2-text-shadow") { true }
		val board2Sort by choice("board-2-sort") { BoardSort.MANUAL }

		val inventoryAnchor by choice("inventory-anchor") { InventoryAnchor.LEFT }
		val inventoryOffsetX by integer("inventory-offset-x", -200, 200) { 0 }
		val inventoryOffsetY by integer("inventory-offset-y", -200, 200) { 0 }
		val inventoryScale by option("inventory-scale", { 1.0 }, DecimalHandler(this, 0.5, 2.0, 0.05))
		val inventoryFreePosition by position("inventory-free-position", 120, 60) { Vector2i(5, 210) }
		val inventoryIncludeBoard1 by toggle("inventory-include-board-1") { true }
		val inventoryBackgroundOpacity by integer("inventory-background-opacity", 0, 100) { 100 }
		val inventoryTheme by choice("inventory-theme") { InventoryTheme.FOLLOW_MENUS }

		val showOn: Map<String, ManagedOption<ShowOn>> =
			TimerRouting.SHOW_ON.associate { (id, d) -> id to choice("show-on-$id", ShowOn::class.java) { d } }
	}

	fun showOn(id: String): ShowOn = TConfig.showOn[id]?.value ?: TimerRouting.DEFAULTS[id] ?: ShowOn.BOARD_1

	private val ownHuds = LinkedHashMap<String, HudMeta>()

	fun ownHud(id: String): HudMeta = ownHuds.getOrPut(id) {
		val saved = TConfig.ownHudPositions.value
		val pos = saved.getOrPut(id) {
			val base = Timers.TConfig.hud.position
			val index = TimerRouting.SHOW_ON.indexOfFirst { it.first == id }.coerceAtLeast(0)
			HudPosition(base.x, base.y + 100 + 11 * index, 1F, base.anchor)
		}
		HudMeta(pos, Apex.identifier("timer-boards-own-$id"), Component.translatable("apex.config.timer-boards.show-on-$id"), 120, 10)
	}

	fun ownHudElements(): List<HudMeta> =
		TimerRouting.SHOW_ON.map { it.first }.filter { TimerRouting.needsNewOwnHud(it) && showOn(it) == ShowOn.OWN_HUD }.map(::ownHud)

	private class Captured(val lines: List<Component>, val at: Long)

	private val captured = LinkedHashMap<String, Captured>()
	private const val STALE_MS = 250L

	@JvmStatic
	fun intercept(labelKey: String?, lines: List<Component>): Boolean {
		val id = labelKey?.let { TimerRouting.OWN_HUD_ELEMENTS[it] } ?: return false
		return when (TimerRouting.capture(showOn(id))) {
			TimerRouting.Capture.DRAW -> false
			TimerRouting.Capture.DROP -> true
			TimerRouting.Capture.TO_BOARD -> { captured[id] = Captured(lines, System.currentTimeMillis()); true }
		}
	}

	fun keep(id: String, lines: List<Component>): List<Component> {
		if (lines.isEmpty()) return lines
		return when (TimerRouting.capture(showOn(id))) {
			TimerRouting.Capture.DRAW -> lines
			TimerRouting.Capture.DROP -> emptyList()
			TimerRouting.Capture.TO_BOARD -> { captured[id] = Captured(lines, System.currentTimeMillis()); emptyList() }
		}
	}

	fun keep(id: String, line: Component?): Component? = line?.let { keep(id, listOf(it)).firstOrNull() }

	fun items(entries: List<Timers.TimerEntry>): List<TimerRouting.Item> {
		val now = System.currentTimeMillis()
		captured.values.removeIf { now - it.at > STALE_MS }
		val out = ArrayList<TimerRouting.Item>(entries.size + captured.size)
		for (e in entries) out += TimerRouting.Item(TimerRouting.showOnId(e.id), "§e${e.label}§r: ${e.status}", e.remaining)
		for ((id, c) in captured) for (line in c.lines) out += TimerRouting.Item(id, line.string)
		return out
	}
}
