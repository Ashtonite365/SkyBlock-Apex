
package dev.ashtonite365.apex.test.ours.customgui

import me.shedaniel.math.Rectangle
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.customgui.CustomGui

class CustomGuiEquivalenceTest {
	private val gui = object : CustomGui() {
		override fun getBounds() = listOf(Rectangle(0, 0, 100, 50), Rectangle(200, 0, 10, 10))
	}

	@Test
	fun defaultsAndHitTestsAreTheSame() {
		ApexTestBootstrap.bootstrapMinecraft()
		val out = mutableListOf<String>()
		out += "focused=${gui.textInputFocused} fg=${gui.shouldDrawForeground()} exit=${gui.onVoluntaryExit()} scroll=${gui.mouseScrolled(1.0, 2.0, 0.0, 1.0)}"
		val slot = Slot(SimpleContainer(1), 0, 5, 7)
		gui.moveSlot(slot); gui.moveSlot(slot, 30, 40)
		out += "slot after moveSlot ${slot.x},${slot.y}"
		gui.onInit()
		val xs = listOf(-1.0, 0.0, 5.5, 20.0, 99.9, 100.0, 100.5, 150.0, 200.0, 205.0, 210.0, 211.0)
		val ys = listOf(-0.5, 0.0, 9.0, 10.0, 12.0, 30.0, 49.0, 50.0, 60.0)
		for (x in xs) for (y in ys) {
			out += "$x,$y out=${gui.isClickOutsideBounds(x, y)} rect=${gui.isPointWithinBounds(10, 5, 20, 20, x, y)} rect2=${gui.isPointWithinBounds(195, 0, 40, 40, x, y)} slot=${gui.isPointOverSlot(slot, 3, 4, x, y)} slotFar=${gui.isPointOverSlot(slot, 195, -5, x, y)}"
		}
		OursGolden.check("customgui-defaults", out)
	}
}
