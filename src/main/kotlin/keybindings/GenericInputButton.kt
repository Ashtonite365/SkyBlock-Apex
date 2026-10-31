package dev.ashtonite365.apex.keybindings

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.MacosUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.StartupStage

@Serializable(with = GenericInputButton.Serializer::class)
sealed interface GenericInputButton {
	@OptIn(ExperimentalSerializationApi::class)
	object Serializer : KSerializer<GenericInputButton> {
		private const val KEY = "keyCode"
		private const val SCAN = "scanCode"
		private const val MOUSE = "mouse"

		override val descriptor: SerialDescriptor = SerialDescriptor("Apex:GenericInputButton", JsonElement.serializer().descriptor)

		override fun serialize(encoder: Encoder, value: GenericInputButton) {
			val tree: JsonElement = when (value) {
				Unbound -> JsonNull
				is KeyCodeButton -> JsonObject(mapOf(KEY to JsonPrimitive(value.keyCode)))
				is ScanCodeButton -> JsonObject(mapOf(SCAN to JsonPrimitive(value.scanCode)))
				is MouseButton -> JsonObject(mapOf(MOUSE to JsonPrimitive(value.mouseButton)))
			}
			encoder.encodeSerializableValue(JsonElement.serializer(), tree)
		}

		override fun deserialize(decoder: Decoder): GenericInputButton {
			val read = decoder.decodeSerializableValue(JsonElement.serializer())
			if (read is JsonNull) return Unbound
			require(read is JsonObject) { "Could not parse GenericInputButton: $read" }
			fun number(field: String): Int = (read.getValue(field) as JsonPrimitive).int
			return when {
				KEY in read -> ofKeyCode(number(KEY))
				SCAN in read -> ofScanCode(number(SCAN))
				MOUSE in read -> mouse(number(MOUSE))
				else -> error("Could not parse GenericInputButton: $read")
			}
		}
	}

	companion object {

		private const val KEY_SLOTS = 512

		private fun validKey(code: Int) = code in 1 until KEY_SLOTS

		@JvmStatic fun unbound(): GenericInputButton = Unbound
		@JvmStatic fun escape(): GenericInputButton = KeyCodeButton(InputConstants.KEY_ESCAPE)
		@JvmStatic fun of(event: KeyEvent): GenericInputButton = ofKeyCode(event.key())
		@JvmStatic fun ofKeyCode(keyCode: Int): GenericInputButton = if (validKey(keyCode)) KeyCodeButton(keyCode) else Unbound
		@JvmStatic fun ofScanCode(scanCode: Int): GenericInputButton = if (validKey(scanCode)) ScanCodeButton(scanCode) else Unbound

		@JvmStatic fun ofScanCodeFromKeyCode(keyCode: Int): GenericInputButton = ofScanCode(keyCode)

		@JvmStatic fun mouse(mouseButton: Int): GenericInputButton =
			if (mouseButton >= InputConstants.MOUSE_BUTTON_LEFT) MouseButton(mouseButton) else Unbound

		@JvmStatic fun ofKeyAndScan(keyCode: Int, scanCode: Int): GenericInputButton =
			if (validKey(keyCode)) KeyCodeButton(keyCode) else ofScanCode(scanCode)
	}

	data object Unbound : GenericInputButton
	data class MouseButton(val mouseButton: Int) : GenericInputButton
	data class KeyCodeButton(val keyCode: Int) : GenericInputButton
	data class ScanCodeButton(val scanCode: Int) : GenericInputButton

	private fun keyIn(keys: List<Int>): Boolean = when (this) {
		is KeyCodeButton -> keyCode in keys
		is ScanCodeButton -> scanCode in keys
		else -> false
	}

	fun isBound(): Boolean = this !is Unbound
	fun isCtrl(): Boolean = keyIn(InputModifiers.controlKeys)
	fun isAlt(): Boolean = keyIn(InputModifiers.altKeys)
	fun isSuper(): Boolean = keyIn(InputModifiers.superKeys)
	fun isShift(): Boolean = keyIn(InputModifiers.shiftKeys)
	fun isModifier(): Boolean = isCtrl() || isAlt() || isSuper() || isShift()

	fun toInputKey(): InputConstants.Key = when (this) {
		Unbound -> InputConstants.UNKNOWN
		is KeyCodeButton -> InputConstants.Type.KEYBOARD.getOrCreate(keyCode)
		is ScanCodeButton -> InputConstants.Type.KEYBOARD.getOrCreate(scanCode)
		is MouseButton -> InputConstants.Type.MOUSE.getOrCreate(mouseButton)
	}

	fun format(): Component =
		if (StartupStage.isAtLeast(StartupStage.RENDER)) toInputKey().displayName.copy()
		else Component.literal(toString())

	fun matches(action: GenericInputAction): Boolean = action.matches(this)

	fun isPressed(): Boolean = when (this) {
		Unbound -> false
		is KeyCodeButton -> InputConstants.isKeyDown(keyCode)
		is ScanCodeButton -> ApexKeyboardState.isScancodeDown(scanCode)
		is MouseButton -> {
			val mouse = Client.game.mouseHandler
			when (mouseButton) {
				InputConstants.MOUSE_BUTTON_LEFT -> mouse.isLeftPressed
				InputConstants.MOUSE_BUTTON_MIDDLE -> mouse.isMiddlePressed
				InputConstants.MOUSE_BUTTON_RIGHT -> mouse.isRightPressed
				else -> false
			}
		}
	}
}

sealed interface GenericInputAction {
	data class MouseInput(val mouseButton: Int) : GenericInputAction
	data class KeyboardInput(val keyCode: Int, val scanCode: Int) : GenericInputAction

	fun matches(button: GenericInputButton): Boolean = when (this) {
		is MouseInput -> button is GenericInputButton.MouseButton && button.mouseButton == mouseButton
		is KeyboardInput -> when (button) {
			is GenericInputButton.KeyCodeButton -> button.keyCode == keyCode
			is GenericInputButton.ScanCodeButton -> button.scanCode == scanCode
			else -> false
		}
	}

	companion object {
		@JvmStatic fun key(keyCode: Int, scanCode: Int): GenericInputAction = KeyboardInput(keyCode, scanCode)

		@JvmStatic fun of(event: KeyEvent): GenericInputAction = KeyboardInput(event.key(), event.key())
		@JvmStatic fun mouse(mouseButton: Int): GenericInputAction = MouseInput(mouseButton)
		@JvmStatic fun mouse(click: MouseButtonEvent): GenericInputAction = MouseInput(click.button())
		@JvmStatic fun of(info: MouseButtonInfo): GenericInputAction = MouseInput(info.button())
	}
}

@Serializable
data class InputModifiers(val modifiers: Int) {
	companion object {
		private val families = listOf(
			"CTRL" to InputConstants.MOD_CONTROL,
			"SHIFT" to InputConstants.MOD_SHIFT,
			"ALT" to InputConstants.MOD_ALT,
			"SUPER" to InputConstants.MOD_SUPER,
		)

		val superKeys: List<Int> = listOf(InputConstants.KEY_LGUI, InputConstants.KEY_RGUI)

		val controlKeys: List<Int> = listOf(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL) +
			(if (MacosUtil.IS_MACOS) superKeys else emptyList())
		val shiftKeys: List<Int> = listOf(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT)
		val altKeys: List<Int> = listOf(InputConstants.KEY_LALT, InputConstants.KEY_RALT)

		@JvmStatic fun current(): InputModifiers {
			if (!Client.game.isWindowActive) return none()
			return ofKeyCodes(*(controlKeys + shiftKeys + altKeys + superKeys).distinct().filter { InputConstants.isKeyDown(it) }.toIntArray())
		}

		@JvmStatic fun of(vararg namedOnly: Boolean, alt: Boolean = false, ctrl: Boolean = false, shift: Boolean = false, `super`: Boolean = false): InputModifiers {
			require(namedOnly.isEmpty()) { "InputModifiers.of takes named arguments only" }
			var flags = 0
			if (ctrl) flags = flags or InputConstants.MOD_CONTROL
			if (shift) flags = flags or InputConstants.MOD_SHIFT
			if (alt) flags = flags or InputConstants.MOD_ALT
			if (`super`) flags = flags or InputConstants.MOD_SUPER
			return InputModifiers(flags)
		}

		@JvmStatic fun ofKeyCodes(vararg keys: Int): InputModifiers {
			var flags = 0
			for (key in keys) {
				if (key in controlKeys) flags = flags or InputConstants.MOD_CONTROL
				if (key in shiftKeys) flags = flags or InputConstants.MOD_SHIFT
				if (key in altKeys) flags = flags or InputConstants.MOD_ALT
				if (key in superKeys) flags = flags or InputConstants.MOD_SUPER
			}
			return InputModifiers(flags)
		}

		@JvmStatic fun of(modifiers: Int): InputModifiers = InputModifiers(modifiers).normalized()
		@JvmStatic fun of(input: InputWithModifiers): InputModifiers = of(input.modifiers())
		@JvmStatic fun none(): InputModifiers = InputModifiers(0)

		@JvmStatic fun ofKey(button: GenericInputButton): InputModifiers = when (button) {
			is GenericInputButton.KeyCodeButton -> ofKeyCodes(button.keyCode)
			is GenericInputButton.ScanCodeButton -> ofKeyCodes(button.scanCode)
			else -> none()
		}
	}

	fun isAtLeast(other: InputModifiers): Boolean = modifiers and other.modifiers.inv() == 0
	fun without(other: InputModifiers): InputModifiers = InputModifiers(modifiers and other.modifiers.inv())
	fun isEmpty(): Boolean = modifiers == 0
	fun getFlag(flag: Int): Boolean = modifiers and flag != 0

	val ctrl: Boolean get() = getFlag(InputConstants.MOD_CONTROL)
	val shift: Boolean get() = getFlag(InputConstants.MOD_SHIFT)
	val alt: Boolean get() = getFlag(InputConstants.MOD_ALT)
	val `super`: Boolean get() = getFlag(InputConstants.MOD_SUPER)

	fun normalized(): InputModifiers =
		InputModifiers(families.fold(0) { acc, (_, mask) -> if (getFlag(mask)) acc or mask else acc })

	fun matches(other: InputModifiers, atLeast: Boolean): Boolean {
		val mine = normalized()
		val theirs = other.normalized()
		return if (atLeast) mine.isAtLeast(theirs) else mine == theirs
	}

	override fun toString(): String = families.filter { (_, mask) -> getFlag(mask) }.joinToString(" + ") { it.first }
	fun format(): Component = Component.literal(toString())
}
