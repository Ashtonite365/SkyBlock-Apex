package dev.ashtonite365.apex.features.inventory.buttons

import com.mojang.brigadier.arguments.StringArgumentType
import me.shedaniel.math.Rectangle
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.HandledScreenForegroundEvent
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.impl.v1.ApexAPIImpl
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.gold

object InventoryButtons {
	@Config
	object TConfig : ManagedConfig("inventory-buttons-config", Category.INVENTORY) {
		val editorButton by button("open-editor") { openEditor() }
		val commandTooltip by toggle("hover-text") { true }
		val playerInventoryOnly by toggle("only-inv") { false }
		val cycleProfile by keyBindingWithDefaultUnbound("cycle-profile")
	}

	@Config
	object DConfig : DataHolder<Data>(serializer(), "inventory-buttons", { Data() })

	@Serializable
	data class Data(

		var buttons: MutableList<InventoryButton> = mutableListOf(),

		var activeProfile: String = DEFAULT_PROFILE,

		var profiles: MutableMap<String, MutableList<InventoryButton>> = mutableMapOf(),
	)

	const val DEFAULT_PROFILE = "default"

	private const val PREFIX = "§e[SkyBlock Apex] "
	private val clickGap = 200.milliseconds
	private var lastRun = TimeMark.farPast()
	private var lastFrame: Rectangle? = null

	fun profileNames(): List<String> {
		val data = DConfig.data
		return (data.profiles.keys + data.activeProfile).distinct().sorted()
	}

	fun switchProfile(name: String, copyCurrent: Boolean = false) {
		val data = DConfig.data
		if (name == data.activeProfile) return
		val current = data.buttons
		data.profiles[data.activeProfile] = current
		val incoming = if (copyCurrent) current.mapTo(mutableListOf()) { it.copy() } else data.profiles[name] ?: mutableListOf()
		data.profiles.remove(name)
		data.buttons = incoming
		data.activeProfile = name
		DConfig.markDirty()
	}

	fun nextProfile(): String {
		val names = profileNames()
		val at = names.indexOf(DConfig.data.activeProfile)
		return names[(at + 1).mod(names.size)]
	}

	fun deleteProfile(name: String): Boolean {
		val data = DConfig.data
		if (name == data.activeProfile) return false
		data.profiles.remove(name) ?: return false
		DConfig.markDirty()
		return true
	}

	private fun activeMessage(name: String): Component = Component.literal("${PREFIX}Inventory buttons: §f$name")

	@Subscribe
	fun onKey(event: HandledScreenKeyPressedEvent) {
		if (!TConfig.cycleProfile.isBound || !event.matches(TConfig.cycleProfile)) return
		switchProfile(nextProfile())
		Client.showChat(activeMessage(DConfig.data.activeProfile))
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.INVENTORY, "buttonprofile",
			"Switch, create, copy, delete or list inventory button layout profiles") {
			onRun { source.sendFeedback(activeMessage(DConfig.data.activeProfile)) }
			onLiteral("list") {
				onRun {
					val active = DConfig.data.activeProfile
					val shown = profileNames().joinToString(", ") { if (it == active) "§a$it§f" else it }
					source.sendFeedback(Component.literal("${PREFIX}Button profiles: §f$shown"))
				}
			}
			onLiteral("switch") {
				onArgument("name", StringArgumentType.word()) { name ->
					onRun {
						switchProfile(this[name])
						source.sendFeedback(Component.literal("${PREFIX}Inventory buttons: §f${this[name]}"))
					}
				}
			}
			onLiteral("copy") {
				onArgument("name", StringArgumentType.word()) { name ->
					onRun {
						switchProfile(this[name], copyCurrent = true)
						source.sendFeedback(Component.literal("${PREFIX}Copied buttons to profile §f${this[name]}"))
					}
				}
			}
			onLiteral("delete") {
				onArgument("name", StringArgumentType.word()) { name ->
					onRun {
						val text = if (deleteProfile(this[name])) "${PREFIX}Deleted profile §f${this[name]}"
						else "§c[SkyBlock Apex] Can't delete ${this[name]} (unknown or active)"
						source.sendFeedback(Component.literal(text))
					}
				}
			}
		}
	}

	fun shownOn(screen: AbstractContainerScreen<*>): List<InventoryButton> {
		if (TConfig.playerInventoryOnly && screen !is InventoryScreen) return emptyList()
		if (ApexAPIImpl.extensions.any { it.shouldHideInventoryButtons(screen) }) return emptyList()
		return DConfig.data.buttons.filter { it.isUsable() }
	}

	@Subscribe
	fun keepReiClear(event: HandledScreenPushREIEvent) {
		val frame = event.screen.getProperRectangle()
		for (b in shownOn(event.screen)) event.block(b.areaIn(frame))
	}

	@Subscribe
	fun runClickedButton(event: HandledScreenClickEvent) {
		val frame = event.screen.getProperRectangle()
		val hit = shownOn(event.screen).firstOrNull { it.areaIn(frame).contains(event.mouseX, event.mouseY) } ?: return
		event.cancel()
		if (lastRun.passedTime() < clickGap) return
		lastRun = TimeMark.now()
		val command = hit.command.orEmpty().trim().removePrefix("/")
		MenuPress.press {
			MenuPress.acted()
			Client.runCommand(command)
		}
	}

	@Subscribe
	fun drawButtons(event: HandledScreenForegroundEvent) {
		val frame = event.screen.getProperRectangle()
		lastFrame = frame
		val buttons = shownOn(event.screen)
		if (buttons.isEmpty()) return
		val ctx = event.context
		for (b in buttons) {
			val area = b.areaIn(frame)
			ctx.pose().pushMatrix()
			ctx.pose().translate(area.x.toFloat(), area.y.toFloat())
			b.render(ctx)
			ctx.pose().popMatrix()
		}
		if (!TConfig.commandTooltip) return
		val hovered = buttons.firstOrNull { it.areaIn(frame).contains(event.mouseX, event.mouseY) } ?: return
		val tip = Component.literal("/" + hovered.command.orEmpty().removePrefix("/")).gold()
		ctx.setTooltipForNextFrame(Client.font, tip, event.mouseX, event.mouseY)
	}

	private fun centredFrame(): Rectangle {
		val w = Client.window.guiScaledWidth
		val h = Client.window.guiScaledHeight
		return Rectangle((w - 176) / 2, (h - 166) / 2, 176, 166)
	}

	fun openEditor() = ScreenUtil.setScreenLater(ButtonLayoutEditor(lastFrame ?: centredFrame()))
}
