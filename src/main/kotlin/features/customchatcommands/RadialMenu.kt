// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.customchatcommands

import me.shedaniel.math.Color
import org.joml.Vector2f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldMouseMoveEvent
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.CustomRenderTypes
import dev.ashtonite365.apex.util.render.FULL_TURN
import dev.ashtonite365.apex.util.render.gui.pip.RenderCircleProgressRenderer
import dev.ashtonite365.apex.util.render.lineBetween
import dev.ashtonite365.apex.util.render.normaliseAngle

object PieMenu {

	interface WheelSpec {
		val holdKey: SavedKeyBinding
		val slices: List<PieSlice>
	}

	interface PieSlice {
		val selectable: Boolean
		fun resolve()
		fun drawLabel(drawContext: GuiGraphicsExtractor)
	}

	val DEAD_ZONE_RADIUS: Int = 16
	val reachLimit: Float = 100f

	private const val LABEL_DISTANCE = 40f

	private const val SLICE_SCALE = 64f

	var pointer = Vector2f()

	var shownMenu: WheelSpec? = null
		set(value) {
			field = value?.takeIf { it.slices.isNotEmpty() }
			pointer = Vector2f()
		}

	val isOpen: Boolean get() = shownMenu != null

	fun close() {
		shownMenu = null
	}

	private val deadZoneSquared get() = DEAD_ZONE_RADIUS * DEAD_ZONE_RADIUS

	private fun pointerOutsideCentre() = pointer.lengthSquared() > deadZoneSquared

	private fun pointerAngle(): Float = normaliseAngle(atan2(pointer.y, pointer.x))

	private fun sliceWidth(wheel: WheelSpec): Float = (FULL_TURN / wheel.slices.size).toFloat()

	private fun sliceUnderPointer(count: Int): Int = (pointerAngle() / FULL_TURN * count).toInt().coerceIn(0, count - 1)

	@Subscribe
	fun steerPointer(event: WorldMouseMoveEvent) {
		if (!isOpen) return
		event.cancel()
		pointer.x += event.deltaX.toFloat()
		pointer.y += event.deltaY.toFloat()
		val distanceSquared = pointer.x * pointer.x + pointer.y * pointer.y
		val limitSquared = reachLimit * reachLimit
		if (distanceSquared > limitSquared) pointer.mul(reachLimit / sqrt(distanceSquared))
	}

	@Subscribe
	fun pollRelease(event: TickEvent) {
		val open = shownMenu ?: return
		if (open.holdKey.isPressed()) return
		val count = open.slices.size
		val picked = open.slices[sliceUnderPointer(count)]
		val chosen = pointerOutsideCentre() && picked.selectable
		close()
		if (chosen) picked.resolve()
	}

	@Subscribe
	fun paintWheel(event: HudRenderEvent) {
		val wheel = shownMenu ?: return
		val gfx = event.context
		val stack = gfx.pose()
		val step = sliceWidth(wheel)
		val highlightAt = if (pointerOutsideCentre()) sliceUnderPointer(wheel.slices.size) else -1
		stack.pushMatrix()
		stack.translate(gfx.guiWidth() / 2f, gfx.guiHeight() / 2f)
		for ((index, slice) in wheel.slices.withIndex()) {
			val span = index * step..(index + 1) * step
			val fill = when {
				index == highlightAt -> 0xC0_3A_7A_3A.toInt()
				slice.selectable -> 0xA0_20_20_20.toInt()
				else -> 0x70_20_20_20
			}
			val hole = DEAD_ZONE_RADIUS / SLICE_SCALE
			stack.pushMatrix()
			stack.scale(SLICE_SCALE, SLICE_SCALE)
			RenderCircleProgressRenderer.extractCircularSlice(gfx, CustomRenderTypes.TRANSLUCENT_CIRCLE_GUI, 0f, 1f, 0f, 1f, span, fill, hole)
			stack.popMatrix()
			val labelAngle = (index + 0.5f) * step
			stack.pushMatrix()
			stack.translate(cos(labelAngle) * LABEL_DISTANCE, sin(labelAngle) * LABEL_DISTANCE)
			slice.drawLabel(gfx)
			stack.popMatrix()
		}
		val green = Color.ofRGBA(80, 255, 80, 255)
		gfx.lineBetween(0, 0, pointer.x.toInt(), pointer.y.toInt(), green)
		stack.popMatrix()
	}
}

object RadialChatCommands {
	var wheels: List<ChatCommandWheel> = emptyList()
		private set

	fun setWheels(loaded: List<ChatCommandWheel>) {
		wheels = loaded
		PieMenu.close()
	}

	private class CommandSlice(private val command: ChatCommand) : PieMenu.PieSlice {
		override val selectable: Boolean get() = command.command.isNotBlank()

		override fun resolve() {
			command.execute()
		}

		override fun drawLabel(drawContext: GuiGraphicsExtractor) {
			val label = command.label
			val font = Client.font
			drawContext.text(font, label, -font.width(label) / 2, -font.lineHeight / 2, if (selectable) -1 else 0xFF_80_80_80.toInt(), true)
		}
	}

	private class WheelMenu(wheel: ChatCommandWheel) : PieMenu.WheelSpec {
		override val holdKey: SavedKeyBinding = wheel.keyBinding
		override val slices: List<PieMenu.PieSlice> = wheel.commands.map(::CommandSlice)
	}

	@Subscribe
	fun openWheel(event: WorldKeyboardEvent) {
		if (PieMenu.isOpen) return
		val wheel = wheels.firstOrNull { it.commands.isNotEmpty() && it.keyBinding.matches(event.action, event.modifiers) } ?: return
		PieMenu.shownMenu = WheelMenu(wheel)
		event.cancel()
	}
}
