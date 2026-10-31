// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.customchatcommands

import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.CloseEventListener
import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import io.github.notenoughupdates.moulconfig.xml.Bind
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.KeyBindingStateManager
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.ManagedConfig

class CustomChatCommandsUI {
	companion object {
		const val COMMAND = "customchatcommands"
		const val ALIAS = "ccc"
		private const val INDEX_XML = "config/customchatcommands/index"
		private const val EDITOR_XML = "config/customchatcommands/editor_wheel"

		fun open() {
			ScreenUtil.setScreenLater(GuiXml.loadScreen(INDEX_XML, CustomChatCommandsUI(), Client.screen))
		}

		@Subscribe
		fun registerEditorCommands(event: CommandEvent.SubCommand) {
			val help = "Custom Chat Commands: radial menus, one chat command per slice. " + FeatureRisk.SOFT_TEXT
			event.register(ManagedConfig.Category.CHAT, COMMAND, help) { onRun { open() } }
			event.register(ManagedConfig.Category.CHAT, ALIAS, "Short for /apex $COMMAND") { onRun { open() } }
		}

		private fun closeCurrent() {
			Client.screen?.onClose()
		}
	}

	private var discarding = false

	@field:Bind("wheels")
	val root = Wheels()

	@Bind("beforeClose")
	fun beforeClose(): CloseEventListener.CloseAction {
		if (!discarding) save()
		return CloseEventListener.CloseAction.NO_OBJECTIONS_TO_CLOSE
	}

	fun save() {
		val saved = root.wheels.map { it.toSaved() }
		CustomChatCommandsData.DConfig.data.wheels = saved
		CustomChatCommandsData.DConfig.markDirty()
		RadialChatCommands.setWheels(saved)
	}

	fun saveAndClose() {
		save()
		closeCurrent()
	}

	fun discard() {
		discarding = true
		closeCurrent()
	}

	class Command(@field:Bind("text") var text: String, val owner: Wheel) {
		@Bind("textR")
		fun textR() = StructuredText.of("/$text")

		@Bind("delete")
		fun delete() {
			owner.editableCommands.remove(this)
		}

		fun toSaved(): ChatCommand? = text.trim().removePrefix("/").takeIf { it.isNotBlank() }?.let(::ChatCommand)
	}

	inner class Wheel(var binding: SavedKeyBinding, saved: List<ChatCommand>) {
		private val backing: MutableList<Command> = saved.mapTo(mutableListOf()) { Command(it.command, this) }

		@field:Bind("commands")
		val commands: ObservableList<Command> = ObservableList(backing)

		@field:Bind("editableCommands")
		val editableCommands: ObservableList<Command> = ObservableList(backing)

		private val capture = KeyBindingStateManager(
			read = { binding },
			write = { binding = it },
			dropFocus = { blur() },
			takeFocus = { requestFocus() },
		)

		@field:Bind("button")
		val button = capture.createButton().also { capture.refreshLabel() }

		fun blur(): Unit = button.blur()
		fun requestFocus(): Unit = button.requestFocus()

		@Bind("keyCombo")
		fun keyCombo(): StructuredText = MoulConfigPlatform.wrap(binding.format())

		@Bind("addOption")
		fun addOption() {
			editableCommands.add(Command("", this))
		}

		@Bind("edit")
		fun edit() {
			ScreenUtil.setScreenLater(GuiXml.loadScreen(EDITOR_XML, this, Client.screen))
		}

		@Bind("back")
		fun back() = closeCurrent()

		@Bind("delete")
		fun delete() {
			root.wheels.remove(this)
		}

		fun toSaved() = ChatCommandWheel(binding, commands.mapNotNull { it.toSaved() })
	}

	inner class Wheels {
		@field:Bind("wheels")
		val wheels: ObservableList<Wheel> = ObservableList(
			CustomChatCommandsData.DConfig.data.wheels.mapTo(mutableListOf()) { Wheel(it.keyBinding, it.commands) })

		@Bind("addWheel")
		fun addWheel() {
			wheels.add(Wheel(SavedKeyBinding.unbound(), listOf()))
		}

		@Bind("discard") fun discard() = this@CustomChatCommandsUI.discard()
		@Bind("saveAndClose") fun saveAndClose() = this@CustomChatCommandsUI.saveAndClose()
		@Bind("save") fun save() = this@CustomChatCommandsUI.save()
	}
}
