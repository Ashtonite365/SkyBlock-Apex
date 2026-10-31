package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.deps.libninepatch.NinePatch
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter

private val alwaysOn: GetSetter<Boolean> = object : GetSetter<Boolean> {
	override fun get(): Boolean = true
	override fun set(newValue: Boolean) = Unit
	override fun toString(): String = "always enabled"
}

private const val SPRITE_W = 200f
private const val SPRITE_H = 20f
private const val FRAME = 3

private fun widgetSkin(namespace: String, sprite: String): NinePatch<MyResourceLocation> {
	val texture = MyResourceLocation(namespace, "textures/gui/sprites/widget/$sprite.png")
	val corner = FRAME
	return NinePatch.builder(texture)
		.cornerSize(corner)
		.cornerUv(corner / SPRITE_W, corner / SPRITE_H)
		.build()
}

private fun insetFor(skinless: Boolean): Int = if (skinless) 0 else 2

open class ApexButton(
	child: GuiComponent,
	val enabledWhen: GetSetter<Boolean> = alwaysOn,

	val skinless: Boolean = false,
	val action: (Int) -> Unit,
) : PanelComponent(child, insetFor(skinless), PanelComponent.DefaultBackgroundRenderer.TRANSPARENT) {

	constructor(
		child: GuiComponent,
		enabledWhen: GetSetter<Boolean> = alwaysOn,
		skinless: Boolean = false,
		action: Runnable,
	) : this(child, enabledWhen, skinless, { _: Int -> action.run() })

	val skinHover = widgetSkin("minecraft", "button_highlighted")
	val skinIdle = widgetSkin("minecraft", "button")
	val skinDisabled = widgetSkin("minecraft", "button_disabled")
	val skinActive = widgetSkin("apex", "button_active")

	var pressHeld: Boolean = false

	override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext): Boolean {
		val live = enabledWhen.get()
		if (!live) return false
		val click = mouseEvent as? MouseEvent.Click ?: return false
		if (click.mouseState) {
			if (!context.isHovered) return false
			pressHeld = true
			requestFocus()
			return true
		}
		if (!pressHeld) return false
		pressHeld = false
		if (context.isHovered) action(click.mouseButton)
		return true
	}

	open fun skinFor(context: GuiImmediateContext): NinePatch<MyResourceLocation> {
		if (!enabledWhen.get()) return skinDisabled
		val lit = context.isHovered || pressHeld
		return if (lit) skinHover else skinIdle
	}

	override fun render(context: GuiImmediateContext) {
		val target = context.renderContext
		val inset = insets.toFloat()
		target.pushMatrix()
		if (!skinless) target.drawNinePatch(skinFor(context), 0f, 0f, context.width, context.height)
		target.translate(inset, inset)
		element.render(getChildContext(context))
		target.popMatrix()
	}
}
