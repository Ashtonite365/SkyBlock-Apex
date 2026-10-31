/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: clean-room Apex implementation of the extra Spirit Leap menu options (PuzzleSolvers spirit-leap-*):
 * a scaled 2x2 quadrant layout, one keybind per dungeon class (ManagedConfig keybinds, so they sit in the Dungeons
 * keybind group via ApexKeyBindings) and firing on key/mouse release. All off by default; the click options are
 * risky toggles. Each user key press or click sends exactly one left click (container button 0, see
 * [CONTAINER_LEFT_CLICK]); nothing is ever clicked on its own. Input matching still uses
 * InputConstants.MOUSE_BUTTON_LEFT (1 on 26.3). Teammate classes come from the dungeon scoreboard ("[M] Name").
 */
package dev.ashtonite365.apex.features.kit.dungeon

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.HandledScreenKeyReleasedEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScoreboardUtil
import net.minecraft.world.inventory.ContainerInput
import dev.ashtonite365.apex.util.bareText

object LeapMenuOptions {

	const val CONTAINER_LEFT_CLICK = 0

	fun clickLeapSlot(
		containerId: Int, slotIndex: Int,
		send: (containerId: Int, slot: Int, button: Int, input: ContainerInput) -> Unit = ::sendToServer,
	) = send(containerId, slotIndex, CONTAINER_LEFT_CLICK, ContainerInput.PICKUP)

	fun clickLeapSlot(screen: AbstractContainerScreen<*>, slot: Slot) = clickLeapSlot(screen.menu.containerId, slot.index)

	private fun sendToServer(containerId: Int, slot: Int, button: Int, input: ContainerInput) {
		val player = Client.player ?: return
		Client.gameMode?.handleContainerInput(containerId, slot, button, input, player)
	}

	val CLASSES = listOf('A' to "Archer", 'B' to "Berserk", 'H' to "Healer", 'M' to "Mage", 'T' to "Tank")

	private fun classRgb(letter: Char?): Int = when (letter) {
		'A' -> 0xFFFFAA00.toInt()
		'B' -> 0xFFFF5555.toInt()
		'H' -> 0xFFFF55FF.toInt()
		'M' -> 0xFF55FFFF.toInt()
		'T' -> 0xFF00AA00.toInt()
		else -> -1
	}

	private val teammateLine = Regex("^\\[([ABHMT])] (?:\\[\\w+] )?(\\w{2,16})")
	private val trailingName = Regex("(\\w{2,16})$")

	fun classesFromScoreboard(lines: List<String>): Map<String, Char> {
		val out = LinkedHashMap<String, Char>()
		for (raw in lines) {
			val m = teammateLine.find(raw.trim()) ?: continue
			out[m.groupValues[2].lowercase()] = m.groupValues[1].first()
		}
		return out
	}

	fun headName(display: String): String? = trailingName.find(display.trim())?.let { it.groupValues[1] }

	fun quadrantOf(mouseX: Double, mouseY: Double, width: Int, height: Int): Int {
		val right = mouseX >= width / 2.0
		val bottom = mouseY >= height / 2.0
		return (if (bottom) 2 else 0) or (if (right) 1 else 0)
	}

	fun indexOfClass(headNames: List<String?>, classes: Map<String, Char>, letter: Char): Int {
		for ((i, name) in headNames.withIndex()) if (name != null && classes[name.lowercase()] == letter) return i
		return -1
	}

	private val cfg get() = PuzzleSolvers.TConfig
	private var lastMouseX = 0.0
	private var lastMouseY = 0.0

	private class LeapView(val screen: AbstractContainerScreen<*>, val heads: List<Slot>)

	private fun openLeap(): LeapView? {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return null
		val heads = PuzzleSolvers.leapHeads(screen) ?: return null
		return if (heads.isEmpty()) null else LeapView(screen, heads)
	}

	private val leftMouse get() = GenericInputAction.mouse(InputConstants.MOUSE_BUTTON_LEFT)

	private fun boundClassKey(matches: (SavedKeyBinding) -> Boolean): Int =
		cfg.leapClassKeys.indexOfFirst { val bind = it.get(); bind.isBound && matches(bind) }

	private fun handles(input: GenericInputAction, matches: (SavedKeyBinding) -> Boolean): Boolean {
		if (cfg.leapQuadrants && input == leftMouse) return true
		return cfg.leapClassKeybinds && boundClassKey(matches) >= 0
	}

	private fun pick(input: GenericInputAction, matches: (SavedKeyBinding) -> Boolean, heads: List<Slot>): Slot? {
		if (cfg.leapQuadrants && input == leftMouse) {
			val screen = Client.screen ?: return null
			return heads.getOrNull(quadrantOf(lastMouseX, lastMouseY, screen.width, screen.height))
		}
		if (!cfg.leapClassKeybinds) return null
		val which = boundClassKey(matches)
		if (which < 0) return null
		val names = heads.map { headName(it.item.hoverName.bareText) }
		val classes = classesFromScoreboard(ScoreboardUtil.simplifiedScoreboardLines)
		return heads.getOrNull(indexOfClass(names, classes, CLASSES[which].first))
	}

	private fun fire(view: LeapView, input: GenericInputAction, matches: (SavedKeyBinding) -> Boolean) {
		val slot = pick(input, matches, view.heads) ?: return
		clickLeapSlot(view.screen, slot)
	}

	@Subscribe
	fun onPress(event: HandledScreenKeyPressedEvent) {
		if (event.isRepeat) return
		val view = openLeap() ?: return
		val matches: (SavedKeyBinding) -> Boolean = { event.matches(it) }
		if (!handles(event.input, matches)) return
		event.cancel()
		if (!cfg.leapOnRelease) fire(view, event.input, matches)
	}

	@Subscribe
	fun onRelease(event: HandledScreenKeyReleasedEvent) {
		if (!cfg.leapOnRelease) return
		val view = openLeap() ?: return
		val matches: (SavedKeyBinding) -> Boolean = { event.matches(it) }
		if (!handles(event.input, matches)) return
		event.cancel()
		fire(view, event.input, matches)
	}

	private const val BOX_W = 160
	private const val BOX_H = 50
	private const val GAP = 12

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		if (!cfg.leapQuadrants) return
		val view = openLeap() ?: return
		lastMouseX = event.mouseX.toDouble()
		lastMouseY = event.mouseY.toDouble()
		val g = event.drawContext
		g.nextStratum()
		val width = event.screen.width
		val height = event.screen.height
		g.fill(0, 0, width, height, 0xC0101010.toInt())
		val classes = classesFromScoreboard(ScoreboardUtil.simplifiedScoreboardLines)
		val scale = cfg.leapScale / 100f
		val under = quadrantOf(lastMouseX, lastMouseY, width, height)
		for ((i, slot) in view.heads.take(4).withIndex()) {
			val name = headName(slot.item.hoverName.bareText) ?: continue
			val letter = classes[name.lowercase()]
			val left = if (i and 1 == 0) -GAP - BOX_W else GAP
			val top = if (i < 2) -GAP - BOX_H else GAP
			val pose = g.pose()
			pose.pushMatrix()
			pose.translate(width / 2f, height / 2f)
			pose.scale(scale, scale)
			g.fill(left, top, left + BOX_W, top + BOX_H, if (i == under) 0xE0505050.toInt() else 0xC0303030.toInt())
			g.text(Client.font, "§e${i + 1} §f$name", left + 8, top + 12, -1, true)
			val className = CLASSES.find { it.first == letter }?.second ?: "?"
			g.text(Client.font, className, left + 8, top + 28, classRgb(letter), true)
			pose.popMatrix()
		}
	}
}
