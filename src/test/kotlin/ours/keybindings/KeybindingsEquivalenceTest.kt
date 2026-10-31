// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.keybindings

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.serialization.json.Json
import net.minecraft.client.input.KeyEvent
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.keybindings.ApexKeyBindings
import dev.ashtonite365.apex.keybindings.ApexKeyboardState
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.GenericInputButton
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.test.ours.OursGolden

class KeybindingsEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Exception) {
		"threw ${e.javaClass.simpleName}"
	}

	private val codes = listOf(-10, -1, 0, 1, 2, 3, 4, 5, 7, 8, 31, 32, 39, 44, 48, 57, 65, 90, 96, 161, 162, 255, 256, 257, 290, 301, 320, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 400, 512, 1000)

	private fun describe(b: GenericInputButton): String =
		"$b bound=${b.isBound()} mod=${b.isModifier()} c=${b.isCtrl()} a=${b.isAlt()} s=${b.isShift()} w=${b.isSuper()} key=${attempt { b.toInputKey().name }} fmt=${attempt { b.format().string }} hash=${b.hashCode()}"

	@Test
	fun buttonsAreBuiltTheSame() {
		val out = mutableListOf<String>()
		for (c in codes) {
			out += "key $c ${describe(GenericInputButton.ofKeyCode(c))}"
			out += "scan $c ${describe(GenericInputButton.ofScanCode(c))} viaKey=${GenericInputButton.ofScanCodeFromKeyCode(c)}"
			out += "mouse $c ${describe(GenericInputButton.mouse(c))}"
			out += "keyscan $c ${GenericInputButton.ofKeyAndScan(c, 77)} ${GenericInputButton.ofKeyAndScan(-1, c)} ${GenericInputButton.ofKeyAndScan(c, c)}"
			out += "event $c ${GenericInputButton.of(KeyEvent(c, 66, 0))} ${GenericInputButton.of(KeyEvent(-1, c, 3))}"
		}
		out += "fixed ${describe(GenericInputButton.escape())} | ${describe(GenericInputButton.unbound())}"
		out += "eq ${GenericInputButton.ofKeyCode(65) == GenericInputButton.ofKeyCode(65)} ${GenericInputButton.ofKeyCode(65) == GenericInputButton.ofScanCode(65)} ${GenericInputButton.Unbound === GenericInputButton.unbound()}"
		out += "copy ${GenericInputButton.KeyCodeButton(5).copy(keyCode = 9)} ${GenericInputButton.MouseButton(2).component1()} ${GenericInputButton.ScanCodeButton(3).scanCode}"
		OursGolden.check("keybindings-buttons", out)
	}

	@Test
	fun actionsMatchTheSame() {
		val out = mutableListOf<String>()
		val buttons = listOf(GenericInputButton.ofKeyCode(65), GenericInputButton.ofScanCode(30), GenericInputButton.mouse(1), GenericInputButton.mouse(3), GenericInputButton.unbound(), GenericInputButton.ofKeyCode(30))
		val actions = listOf(GenericInputAction.key(65, 30), GenericInputAction.key(30, 65), GenericInputAction.key(-1, 30), GenericInputAction.mouse(1), GenericInputAction.mouse(3), GenericInputAction.mouse(0),
			GenericInputAction.of(KeyEvent(65, 30, 1)), GenericInputAction.KeyboardInput(65, 0), GenericInputAction.MouseInput(3))
		for (a in actions) out += "action $a hash=${a.hashCode()} " + buttons.joinToString(" ") { b -> (if (a.matches(b)) "1" else "0") + (if (b.matches(a)) "1" else "0") }
		OursGolden.check("keybindings-actions", out)
	}

	@Test
	fun modifiersBehaveTheSame() {
		val out = mutableListOf<String>()
		out += "consts ctrl=${InputConstants.MOD_CONTROL} shift=${InputConstants.MOD_SHIFT} alt=${InputConstants.MOD_ALT} super=${InputConstants.MOD_SUPER}"
		out += "lists ${InputModifiers.superKeys} ${InputModifiers.controlKeys} ${InputModifiers.shiftKeys} ${InputModifiers.altKeys}"
		for (m in 0..300) {
			val raw = InputModifiers(m)
			val norm = InputModifiers.of(m)
			out += "mod $m raw=[$raw] ${raw.ctrl}${raw.shift}${raw.alt}${raw.`super`} empty=${raw.isEmpty()} norm=${norm.modifiers} [$norm] fmt=${raw.format().string} n2=${raw.normalized().modifiers}"
		}
		val samples = listOf(0, 1, 2, 3, 4, 12, 15, 48, 64, 255, 1 or 16, InputConstants.MOD_SHIFT or InputConstants.MOD_ALT).map { InputModifiers(it) }
		for (a in samples) out += "rel ${a.modifiers} " + samples.joinToString(" ") { b ->
			"${b.modifiers}:" + listOf(a.isAtLeast(b), a.matches(b, false), a.matches(b, true)).joinToString("") { if (it) "1" else "0" } + "/" + a.without(b).modifiers
		}
		out += "named ${InputModifiers.of(ctrl = true)} ${InputModifiers.of(shift = true, alt = true).modifiers} ${InputModifiers.of(`super` = true).modifiers} ${InputModifiers.of().modifiers} ${attempt { InputModifiers.of(true, ctrl = true) }}"
		for (c in codes) out += "ofkeys $c ${InputModifiers.ofKeyCodes(c).modifiers} key=${InputModifiers.ofKey(GenericInputButton.ofKeyCode(c)).modifiers} scan=${InputModifiers.ofKey(GenericInputButton.ofScanCode(c)).modifiers}"
		out += "ofkeys many ${InputModifiers.ofKeyCodes(340, 341, 342, 343).modifiers} ${InputModifiers.ofKeyCodes().modifiers} none=${InputModifiers.none()}"
		out += "input ${InputModifiers.of(KeyEvent(65, 30, 1 or 4 or 16)).modifiers} flag=${InputModifiers(5).getFlag(4)} ${InputModifiers(5).getFlag(2)}"
		OursGolden.check("keybindings-modifiers", out)
	}

	@Test
	fun savedBindingsBehaveTheSame() {
		val out = mutableListOf<String>()
		val bindings = listOf(
			SavedKeyBinding.unbound(), SavedKeyBinding.keyWithoutMods(65), SavedKeyBinding.keyWithMods(76, InputModifiers.of(shift = true)),
			SavedKeyBinding.keyWithMods(76, InputModifiers.of(ctrl = true, alt = true)), SavedKeyBinding.withoutMods(GenericInputButton.mouse(3)),
			SavedKeyBinding(GenericInputButton.ofScanCode(30), InputModifiers(1)), SavedKeyBinding.keyWithoutMods(-4),
		)
		val json = Apex.json
		for (b in bindings) {
			val encoded = json.encodeToString(SavedKeyBinding.serializer(), b)
			out += "binding [$b] fmt=${b.format().string} bound=${b.isBound} json=$encoded back=${json.decodeFromString(SavedKeyBinding.serializer(), encoded) == b} hash=${b.hashCode()}"
			for (a in listOf(GenericInputAction.key(65, 0), GenericInputAction.key(76, 0), GenericInputAction.key(0, 30), GenericInputAction.mouse(3)))
				out += "  $a " + listOf(InputModifiers.none(), InputModifiers(1), InputModifiers(4), InputModifiers(3 or 12), InputModifiers(InputConstants.MOD_NUM_LOCK)).joinToString(" ") { m ->
					(if (b.matches(a, m)) "1" else "0") + (if (b.matches(a, m, atLeast = true)) "1" else "0")
				}
		}
		for (text in listOf("null", "{\"keyCode\":65}", "{\"mouse\":2}", "{\"scanCode\":30}", "{\"keyCode\":-7}", "{\"mouse\":0}", "{\"other\":1}", "[1]", "{\"keyCode\":65,\"mouse\":2}", "{\"mouse\":2,\"keyCode\":65}", "{\"scanCode\":\"40\"}", "{\"keyCode\":\"x\"}", "{\"keyCode\":null,\"mouse\":3}"))
			out += "decode $text -> ${attempt { json.decodeFromString(GenericInputButton.serializer(), text) }}"
		for (b in listOf(GenericInputButton.unbound(), GenericInputButton.ofKeyCode(65), GenericInputButton.mouse(2), GenericInputButton.ofScanCode(30)))
			out += "encode $b -> ${json.encodeToString(GenericInputButton.serializer(), b)} plain=${Json.encodeToString(GenericInputButton.serializer(), b)}"
		out += "descriptor ${GenericInputButton.serializer().descriptor.serialName} ${GenericInputButton.serializer().descriptor.kind} ${InputModifiers.serializer().descriptor.serialName} ${SavedKeyBinding.serializer().descriptor.serialName}"
		out += "modjson ${json.encodeToString(InputModifiers.serializer(), InputModifiers(5))} ${attempt { json.decodeFromString(InputModifiers.serializer(), "{\"modifiers\":7}") }}"
		out += "decode binding ${attempt { json.decodeFromString(SavedKeyBinding.serializer(), "{\"button\":{\"keyCode\":75},\"modifiers\":{\"modifiers\":1}}") }}"
		OursGolden.check("keybindings-saved", out)
	}

	@Test
	fun keyboardStateTracksTheSame() {
		val out = mutableListOf<String>()
		ApexKeyboardState.maintainState(KeyEvent(65, 30, 0), InputConstants.PRESS)
		out += "after press 65/30: 65=${ApexKeyboardState.isScancodeDown(65)} 30=${ApexKeyboardState.isScancodeDown(30)}"
		ApexKeyboardState.maintainState(KeyEvent(65, 30, 0), InputConstants.REPEAT)
		out += "after repeat: ${ApexKeyboardState.isScancodeDown(65)}"
		ApexKeyboardState.maintainState(KeyEvent(65, 30, 0), InputConstants.RELEASE)
		out += "after release: ${ApexKeyboardState.isScancodeDown(65)}"
		ApexKeyboardState.maintainState(KeyEvent(70, 30, 0), 99)
		out += "after unknown action: ${ApexKeyboardState.isScancodeDown(70)} neg=${attempt { ApexKeyboardState.isScancodeDown(-1) }} negset=${attempt { ApexKeyboardState.maintainState(KeyEvent(-1, 0, 0), InputConstants.PRESS) }}"
		out += "registry cats=${ApexKeyBindings.cats.size >= 0} bindings=${ApexKeyBindings.keyBindings.javaClass.simpleName}"
		OursGolden.check("keybindings-state", out)
	}
}
