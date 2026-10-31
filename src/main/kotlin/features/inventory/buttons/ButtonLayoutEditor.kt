// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory.buttons

import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ClipboardUtils
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.PopupHostScreen
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.tr
import io.github.notenoughupdates.moulconfig.common.IItemStack
import io.github.notenoughupdates.moulconfig.xml.Bind
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.hypot

class ButtonLayoutEditor(private val frame: Rectangle) : PopupHostScreen() {
	private companion object {
		const val DRAG_THRESHOLD = 4.0
		const val SIDE_X = 10
		const val ROW = 25
	}

	private class Press(val button: InventoryButton?, val x: Double, val y: Double) {
		var dragging = false
		var changedLayout = false
	}

	private var draft: MutableList<InventoryButton> = workingCopy()
	private var press: Press? = null

	private fun workingCopy(): MutableList<InventoryButton> = InventoryButtons.DConfig.data.buttons.mapTo(mutableListOf()) { it.copy() }

	private fun commit() {
		InventoryButtons.DConfig.data.buttons = draft.mapTo(mutableListOf()) { it.copy() }
		InventoryButtons.DConfig.markDirty()
	}

	private fun remove(button: InventoryButton) {
		draft.removeIf { it === button }
	}

	private fun importLayout(text: String) {
		val decoded = ButtonPresets.decode(text) ?: return
		val (layout, moved) = ButtonLayout.fitImported(ButtonLayout.withoutSlashes(decoded))
		draft = layout
		if (moved > 0) Client.showChat(tr("apex.inventory-buttons.button-moved", "One of your imported buttons intersects with the inventory and has been moved to the top left."))
	}

	override fun onClose() {
		commit()
		super.onClose()
	}

	private fun recentre() {
		val window = Client.window
		frame.setLocation((window.guiScaledWidth - frame.width) / 2, (window.guiScaledHeight - frame.height) / 2)
	}

	override fun resize(width: Int, height: Int) {
		recentre()
		super.resize(width, height)
	}

	override fun init() {
		super.init()
		val wide = frame.width - 2 * SIDE_X
		val left = frame.x + SIDE_X
		addRenderableWidget(Button.builder(Component.literal("Profile: ${InventoryButtons.DConfig.data.activeProfile} ▶")) {
			commit()
			InventoryButtons.switchProfile(InventoryButtons.nextProfile())
			draft = workingCopy()
			rebuildWidgets()
		}.bounds(left, 4, wide, 20).build())
		addRenderableWidget(MultiLineTextWidget(frame.x, 25, tr("apex.inventory-buttons.delete", "Hold L-CTRL and click to delete"), font).setMaxWidth(frame.width).also { it.active = false })
		addRenderableWidget(MultiLineTextWidget(frame.x, 40, tr("apex.inventory-buttons.info", "Hold SHIFT to grid align"), font).setMaxWidth(frame.width).also { it.active = false })
		val actions = listOf<Pair<Component, () -> Unit>>(
			tr("apex.inventory-buttons.reset", "Reset buttons") to { draft = mutableListOf() },
			tr("apex.inventory-buttons.load-preset", "Load Preset") to { importLayout(ClipboardUtils.getTextContents()) },
			tr("apex.inventory-buttons.save-preset", "Save Preset") to { ClipboardUtils.setTextContent(ButtonPresets.encode(draft)) },
			tr("apex.inventory-buttons.simple-preset", "Simple Preset") to { importLayout(ButtonPresets.SIMPLE) },
			tr("apex.inventory-buttons.all-warps-preset", "All Warps Preset") to { importLayout(ButtonPresets.ALL_WARPS) },
		)
		actions.forEachIndexed { i, (label, action) ->
			addRenderableWidget(Button.builder(label) { action() }.bounds(left, frame.y + SIDE_X + i * ROW, wide, 20).build())
		}
	}

	private inline fun withOwnPose(graphics: GuiGraphicsExtractor, block: (GuiGraphicsExtractor) -> Unit) {
		graphics.pose().pushMatrix()
		try { block(graphics) } finally { graphics.pose().popMatrix() }
	}

	private fun paintBackground(graphics: GuiGraphicsExtractor) {
		PanelComponent.DefaultBackgroundRenderer.VANILLA.render(MoulConfigRenderContext(graphics), frame.x, frame.y, frame.width, frame.height)
	}

	private fun paintLayout(graphics: GuiGraphicsExtractor) {
		for (button in draft) {
			val area = button.areaIn(frame)
			withOwnPose(graphics) {
				it.pose().translate(area.x.toFloat(), area.y.toFloat())
				button.render(it)
			}
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		paintBackground(graphics)
		paintLayout(graphics)
		super.extractRenderState(graphics, mouseX, mouseY, partialTick)
		paintPopup(graphics, mouseX, mouseY, partialTick)
	}

	private fun buttonAt(x: Double, y: Double): InventoryButton? = draft.lastOrNull { it.areaIn(frame).contains(x, y) }

	private fun spotUnderMouse(x: Double, y: Double): ButtonLayout.Spot? =
		ButtonLayout.spotAt(x.toInt(), y.toInt(), frame, InputModifiers.current().shift)

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		if (super.mouseClicked(click, doubled)) return true
		return pressAt(click.x, click.y)
	}

	private fun pressAt(x: Double, y: Double): Boolean {
		val hit = buttonAt(x, y)
		val started = Press(hit, x, y)
		if (hit == null) {
			val spot = spotUnderMouse(x, y)
			if (spot != null) {
				draft += InventoryButton(spot.x, spot.y, spot.anchorRight, spot.anchorBottom, null, null)
				started.changedLayout = true
			}
		}
		press = started
		MenuPress.acted()
		return true
	}

	override fun mouseDragged(click: MouseButtonEvent, moveX: Double, moveY: Double): Boolean {
		if (popup != null) return super.mouseDragged(click, moveX, moveY)
		return dragTo(click.x, click.y) || super.mouseDragged(click, moveX, moveY)
	}

	private fun dragTo(x: Double, y: Double): Boolean {
		val held = press ?: return false
		val button = held.button ?: return false
		if (!held.dragging && hypot(x - held.x, y - held.y) < DRAG_THRESHOLD) return false
		held.dragging = true
		val spot = spotUnderMouse(x, y) ?: return true
		ButtonLayout.dragTo(button, spot, frame)
		held.changedLayout = true
		return false
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		if (popup != null) return super.mouseReleased(click)
		return releaseAt(click.x, click.y) || super.mouseReleased(click)
	}

	private fun releaseAt(x: Double, y: Double): Boolean {
		val held = press ?: return false
		press = null
		val button = held.button ?: return false
		if (held.dragging) return false
		if (InputModifiers.current().ctrl) {
			remove(button)
			return true
		}
		openPopup(GuiXml.loadGui("button_editor_fragment", ButtonPopup(button)), Point(x.toInt(), y.toInt()))
		return true
	}

	inner class ButtonPopup(private val target: InventoryButton) {
		@field:Bind var command: String = target.command.orEmpty()
			set(value) { field = value; writeBack() }
		@field:Bind var icon: String = target.icon.orEmpty()
			set(value) { field = value; writeBack() }
		@field:Bind var isGigantic: Boolean = target.isGigantic
			set(value) { field = value; writeBack() }

		private fun writeBack() {
			target.command = command
			target.icon = icon
			target.isGigantic = isGigantic
		}

		@Bind
		@OptIn(NeedsComponents::class)
		fun getItemIcon(): IItemStack = MoulConfigPlatform.wrap(target.iconStack().current())

		@Bind
		fun pickIcon() {
			ScreenUtil.setScreenLater(IconPickerScreen(this@ButtonLayoutEditor) { icon = it })
		}

		@Bind
		fun delete() {
			remove(target)
			popup = null
		}
	}
}
