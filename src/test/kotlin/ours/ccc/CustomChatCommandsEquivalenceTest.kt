
package dev.ashtonite365.apex.test.ours.ccc

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldMouseMoveEvent
import dev.ashtonite365.apex.features.customchatcommands.ChatCommand
import dev.ashtonite365.apex.features.customchatcommands.ChatCommandWheel
import dev.ashtonite365.apex.features.customchatcommands.RadialChatCommands
import dev.ashtonite365.apex.features.customchatcommands.PieMenu
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.test.ours.OursGolden

class CustomChatCommandsEquivalenceTest {
	private val resolved = mutableListOf<Int>()

	private fun menu(n: Int) = object : PieMenu.WheelSpec {
		override val holdKey: SavedKeyBinding = SavedKeyBinding.unbound()
		override val slices: List<PieMenu.PieSlice> = (0 until n).map { i ->
			object : PieMenu.PieSlice {
				override val selectable = true
				override fun resolve() { resolved += i }
				override fun drawLabel(drawContext: GuiGraphicsExtractor) {}
			}
		}
	}

	private fun d() = PieMenu.pointer.let { "(%.3f, %.3f)".format(it.x, it.y) }

	@Test
	fun radialSelectionIsTheSame() {
		ApexTestBootstrap.bootstrapMinecraft()
		val out = mutableListOf<String>()
		val v = PieMenu
		out += "max=${v.reachLimit} inner=${v.DEAD_ZONE_RADIUS}"
		val idle = WorldMouseMoveEvent(5.0, 5.0)
		v.shownMenu = null
		v.steerPointer(idle)
		out += "no menu: cancelled=${idle.cancelled} delta=${d()}"
		v.shownMenu = menu(0)
		out += "empty menu -> ${v.shownMenu}"
		v.shownMenu = menu(3)
		val e = WorldMouseMoveEvent(30.0, -40.0)
		v.steerPointer(e)
		out += "move cancelled=${e.cancelled} delta=${d()}"
		v.steerPointer(WorldMouseMoveEvent(300.0, 0.0))
		out += "clamped delta=${d()} len=${"%.3f".format(v.pointer.length())}"
		v.shownMenu = menu(3)
		out += "reassigned delta=${d()}"
		v.shownMenu = null
		val moves = listOf(0.0 to 0.0, 10.0 to 10.0, 11.4 to 11.4, 11.5 to 11.5, 16.0 to 0.0, 17.0 to 0.0, 0.0 to 17.0, -17.0 to 0.0, 0.0 to -17.0,
			-17.0 to -0.001, 50.0 to 50.0, -50.0 to 50.0, -50.0 to -50.0, 50.0 to -50.0, 200.0 to 1.0, 1.0 to -200.0)
		for (n in listOf(1, 2, 3, 5, 8)) for ((dx, dy) in moves) {
			resolved.clear()
			v.shownMenu = menu(n)
			v.steerPointer(WorldMouseMoveEvent(dx, dy))
			val outcome = runCatching { v.pollRelease(TickEvent(0)) }.exceptionOrNull()?.javaClass?.simpleName ?: "ok"
			out += "n=$n move=($dx,$dy) $outcome resolved=$resolved closed=${v.shownMenu == null}"
		}
		v.shownMenu = menu(2)
		RadialChatCommands.setWheels(listOf(ChatCommandWheel(commands = listOf(ChatCommand("a"), ChatCommand(" ")))))
		out += "setWheels closes=${v.shownMenu == null} wheels=${RadialChatCommands.wheels}"
		RadialChatCommands.setWheels(emptyList())
		val wheel = ChatCommandWheel(commands = listOf(ChatCommand("warp hub"), ChatCommand("")))
		out += "label ${wheel.commands.map { it.label.string }}"
		out += "json ${Apex.json.encodeToString(ChatCommandWheel.serializer(), wheel)} default ${Apex.json.encodeToString(ChatCommandWheel.serializer(), ChatCommandWheel())}"
		out += "decoded ${Apex.json.decodeFromString(ChatCommandWheel.serializer(), "{}")}"
		OursGolden.check("ccc-radial", out)
	}
}
