// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.items

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.tr

object ItemCustomizer {

	@Serializable
	data class Customization(
		val name: String? = null,
		val glint: Boolean? = null,
		val colour: Int? = null,
	) {
		val isEmpty get() = name == null && glint == null && colour == null
	}

	@Serializable
	data class Data(val items: MutableMap<String, Customization> = mutableMapOf())

	@Config
	object TConfig : ManagedConfig("item-customizer", Category.INVENTORY) {
		val enabled by toggle("enabled") { true }
	}

	@Config
	object DConfig : DataHolder<Data>(serializer(), "item-customizations", ::Data)

	private const val FORMAT_CODES = "0123456789abcdefklmnorABCDEFKLMNOR"

	fun legacyCodes(raw: String): String {
		val out = StringBuilder(raw.length)
		for ((i, c) in raw.withIndex())
			out.append(if (c == '&' && i + 1 < raw.length && raw[i + 1] in FORMAT_CODES) '§' else c)
		return out.toString()
	}

	fun parseName(raw: String): Component = Component.literal(legacyCodes(raw))

	fun parseColour(text: String): Int? = text.trim().removePrefix("#").ifEmpty { null }?.toIntOrNull(16)

	fun colourText(colour: Int?): String = colour?.let { "#%06X".format(it and 0xFFFFFF) } ?: ""

	fun nextGlint(glint: Boolean?): Boolean? = when (glint) {
		null -> true
		true -> false
		false -> null
	}

	fun edited(current: Customization, nameField: String, colourField: String) =
		current.copy(name = nameField.ifBlank { null }, colour = parseColour(colourField))

	private fun overridesFor(stack: ItemStack): Customization? {
		val saved = DConfig.data.items
		if (saved.isEmpty() || !TConfig.enabled) return null
		val key = runCatching { stack.view().itemUuid }.getOrNull()?.toString() ?: return null
		return saved[key]
	}

	@JvmStatic
	fun customName(stack: ItemStack): Component? = overridesFor(stack)?.name?.let(::parseName)

	@JvmStatic
	fun customGlint(stack: ItemStack): Boolean? = overridesFor(stack)?.glint

	@JvmStatic
	fun customColour(stack: ItemStack): Int? = overridesFor(stack)?.colour?.let { it or 0xFF000000.toInt() }

	fun set(uuid: UUID, customization: Customization) {
		val saved = DConfig.data.items
		if (customization.isEmpty) saved.remove(uuid.toString()) else saved[uuid.toString()] = customization
		DConfig.markDirty()
	}

	fun get(uuid: UUID) = DConfig.data.items[uuid.toString()] ?: Customization()

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("customize", ManagedConfig.Category.INVENTORY) {
			onRun {
				val held = Client.mainHandStack
				val uuid = held.view().itemUuid
				if (held.isEmpty || uuid == null) source.sendFeedback(tr("apex.customize.nouuid", "Hold a SkyBlock item with a UUID to customise it."))
				else ScreenUtil.setScreenLater(CustomizeScreen(held.copy(), uuid))
			}
		}
	}

	class CustomizeScreen(val stack: ItemStack, val uuid: UUID) : Screen(tr("apex.customize.title", "Customise Item")) {
		private var editing = get(uuid)
		private lateinit var nameField: EditBox
		private lateinit var colourField: EditBox

		private fun glintText(): Component = when (editing.glint) {
			null -> tr("apex.customize.glint.default", "Glint: Default")
			true -> tr("apex.customize.glint.on", "Glint: On")
			false -> tr("apex.customize.glint.off", "Glint: Off")
		}

		private fun field(y: Int, label: Component, max: Int, text: String): EditBox =
			EditBox(font, width / 2 - 100, y, 200, 18, label).also { it.setMaxLength(max); it.value = text; addRenderableWidget(it) }

		override fun init() {
			val x = width / 2 - 100
			val y0 = height / 2 - 50
			nameField = field(y0, tr("apex.customize.name", "Name"), 128, editing.name ?: "")
			colourField = field(y0 + 26, tr("apex.customize.colour", "Dye colour"), 7, colourText(editing.colour))
			addRenderableWidget(Button.builder(glintText()) { button ->
				editing = editing.copy(glint = nextGlint(editing.glint))
				button.message = glintText()
			}.bounds(x, y0 + 52, 200, 20).build())
			addRenderableWidget(Button.builder(tr("apex.customize.reset", "Reset")) {
				nameField.value = ""
				colourField.value = ""
				editing = Customization()
				set(uuid, editing)
				Client.screen = null
			}.bounds(x, y0 + 78, 98, 20).build())
			addRenderableWidget(Button.builder(tr("apex.customize.done", "Done")) {
				editing = edited(editing, nameField.value, colourField.value)
				set(uuid, editing)
				Client.screen = null
			}.bounds(x + 102, y0 + 78, 98, 20).build())
			setInitialFocus(nameField)
		}

		override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
			super.extractRenderState(context, mouseX, mouseY, partialTick)
			val x = width / 2 - 100
			val cy = height / 2
			context.item(stack, x, cy - 72)
			val preview = if (nameField.value.isBlank()) stack.hoverName else parseName(nameField.value)
			context.text(font, preview, x + 20, cy - 68, 0xFFFFFFFF.toInt())
			context.text(font, tr("apex.customize.name.hint", "Name (& colour codes)"), x + 204, cy - 45, 0xFF808080.toInt())
			context.text(font, tr("apex.customize.colour.hint", "Hex, e.g. #FF0000"), x + 204, cy - 19, 0xFF808080.toInt())
		}

		override fun isPauseScreen() = false
	}
}
